package com.bd2.app.service;

import com.bd2.app.dao.AuthorizationDAO;
import com.bd2.app.dao.UserDAO;
import com.bd2.app.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Servicio de usuarios que integra MongoDB y Neo4j
 * - MongoDB: almacena datos maestros del usuario
 * - Neo4j: maneja relaciones, roles y permisos
 */
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    private final UserDAO userDAO;
    private final AuthorizationDAO authorizationDAO;
    
    public UserService() {
        this.userDAO = new UserDAO();
        this.authorizationDAO = new AuthorizationDAO();
    }
    
    /**
     * Registra un nuevo usuario
     * - Crea usuario en MongoDB
     * - Sincroniza información básica en Neo4j
     * - Asigna rol por defecto según departamento
     */
    public String registerUser(String fullName, String email, String password, String department) {
        try {
            // 1. Verificar que el email no existe
            if (userDAO.existsByEmail(email)) {
                logger.warn("Intento de registro con email existente: {}", email);
                return null;
            }
            
            // 2. Crear usuario en MongoDB
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setFullName(fullName);
            user.setEmail(email);
            user.setPasswordHash(hashPassword(password));
            user.setStatus("activo");
            user.setDepartment(department);
            
            String userId = userDAO.createUser(user);
            
            if (userId != null) {
                // 3. Sincronizar en Neo4j
                boolean synced = authorizationDAO.syncUserFromMongo(
                    userId, email, fullName, "activo", department
                );
                
                if (synced) {
                    // 4. Asignar rol por defecto según departamento
                    assignDefaultRole(userId, department);
                    
                    logger.info("Usuario registrado exitosamente: {} ({})", fullName, email);
                    return userId;
                } else {
                    // Rollback: eliminar usuario de MongoDB si falló la sincronización
                    userDAO.deleteUserPermanently(userId);
                    logger.error("Error sincronizando usuario en Neo4j, rollback ejecutado");
                }
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Error registrando usuario", e);
            return null;
        }
    }
    
    /**
     * Autentica un usuario
     * - Verifica credenciales en MongoDB
     * - Obtiene permisos desde Neo4j
     */
    public Map<String, Object> authenticateUser(String email, String password) {
        try {
            // 1. Buscar usuario por email
            Optional<User> userOpt = userDAO.findByEmail(email);
            
            if (userOpt.isEmpty()) {
                logger.warn("Intento de login con email no existente: {}", email);
                return null;
            }
            
            User user = userOpt.get();
            
            // 2. Verificar que el usuario está activo
            if (!user.isActive()) {
                logger.warn("Intento de login con usuario inactivo: {}", email);
                return null;
            }
            
            // 3. Verificar contraseña
            if (!verifyPassword(password, user.getPasswordHash())) {
                logger.warn("Intento de login con contraseña incorrecta: {}", email);
                return null;
            }
            
            // 4. Obtener permisos desde Neo4j
            Set<String> permissions = authorizationDAO.getUserPermissions(user.getId());
            
            // 5. Crear respuesta de autenticación
            Map<String, Object> authResult = new HashMap<>();
            authResult.put("userId", user.getId());
            authResult.put("fullName", user.getFullName());
            authResult.put("email", user.getEmail());
            authResult.put("department", user.getDepartment());
            authResult.put("permissions", permissions);
            authResult.put("loginTime", new Date());
            
            logger.info("Usuario autenticado exitosamente: {} ({})", user.getFullName(), email);
            return authResult;
            
        } catch (Exception e) {
            logger.error("Error autenticando usuario", e);
            return null;
        }
    }
    
    /**
     * Obtiene información completa de un usuario
     * - Datos básicos desde MongoDB
     * - Permisos y relaciones desde Neo4j
     */
    public Map<String, Object> getUserProfile(String userId) {
        try {
            // 1. Obtener datos básicos desde MongoDB
            Optional<User> userOpt = userDAO.findById(userId);
            if (userOpt.isEmpty()) {
                return null;
            }
            
            User user = userOpt.get();
            
            // 2. Obtener permisos desde Neo4j
            Set<String> permissions = authorizationDAO.getUserPermissions(userId);
            
            // 3. Crear perfil completo
            Map<String, Object> profile = new HashMap<>();
            profile.put("userId", user.getId());
            profile.put("fullName", user.getFullName());
            profile.put("email", user.getEmail());
            profile.put("department", user.getDepartment());
            profile.put("status", user.getStatus());
            profile.put("registeredAt", user.getRegisteredAt());
            profile.put("updatedAt", user.getUpdatedAt());
            profile.put("permissions", permissions);
            
            return profile;
            
        } catch (Exception e) {
            logger.error("Error obteniendo perfil de usuario: {}", userId, e);
            return null;
        }
    }
    
    /**
     * Actualiza información de un usuario
     * - Actualiza en MongoDB
     * - Sincroniza cambios en Neo4j
     */
    public boolean updateUser(String userId, String fullName, String email, String department) {
        try {
            // 1. Obtener usuario actual
            Optional<User> userOpt = userDAO.findById(userId);
            if (userOpt.isEmpty()) {
                return false;
            }
            
            User user = userOpt.get();
            
            // 2. Verificar que el nuevo email no existe (si cambió)
            if (!email.equals(user.getEmail()) && userDAO.existsByEmail(email)) {
                logger.warn("Intento de actualizar a email existente: {}", email);
                return false;
            }
            
            // 3. Actualizar en MongoDB
            user.setFullName(fullName);
            user.setEmail(email);
            user.setDepartment(department);
            
            boolean updated = userDAO.updateUser(user);
            
            if (updated) {
                // 4. Sincronizar en Neo4j
                authorizationDAO.syncUserFromMongo(
                    userId, email, fullName, user.getStatus(), department
                );
                
                logger.info("Usuario actualizado: {}", userId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error actualizando usuario: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Cambia la contraseña de un usuario
     */
    public boolean changePassword(String userId, String currentPassword, String newPassword) {
        try {
            // 1. Obtener usuario
            Optional<User> userOpt = userDAO.findById(userId);
            if (userOpt.isEmpty()) {
                return false;
            }
            
            User user = userOpt.get();
            
            // 2. Verificar contraseña actual
            if (!verifyPassword(currentPassword, user.getPasswordHash())) {
                logger.warn("Intento de cambio de contraseña con contraseña actual incorrecta: {}", userId);
                return false;
            }
            
            // 3. Actualizar contraseña
            String newPasswordHash = hashPassword(newPassword);
            boolean updated = userDAO.updatePassword(userId, newPasswordHash);
            
            if (updated) {
                logger.info("Contraseña cambiada para usuario: {}", userId);
            }
            
            return updated;
            
        } catch (Exception e) {
            logger.error("Error cambiando contraseña para usuario: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Asigna un rol a un usuario
     * - Requiere permisos administrativos
     */
    public boolean assignRole(String adminUserId, String userId, String roleId) {
        try {
            // 1. Verificar permisos administrativos
            boolean isAdmin = authorizationDAO.canUserExecuteProcess(adminUserId, "pt_admin_usuarios");
            if (!isAdmin) {
                logger.warn("Usuario {} no tiene permisos para asignar roles", adminUserId);
                return false;
            }
            
            // 2. Verificar que el usuario existe
            Optional<User> userOpt = userDAO.findById(userId);
            if (userOpt.isEmpty()) {
                return false;
            }
            
            // 3. Asignar rol en Neo4j
            boolean assigned = authorizationDAO.assignRoleToUser(userId, roleId);
            
            if (assigned) {
                logger.info("Rol {} asignado a usuario {} por admin {}", roleId, userId, adminUserId);
            }
            
            return assigned;
            
        } catch (Exception e) {
            logger.error("Error asignando rol", e);
            return false;
        }
    }
    
    /**
     * Agrega un usuario a un grupo
     */
    public boolean addToGroup(String adminUserId, String userId, String groupId) {
        try {
            // 1. Verificar permisos
            boolean canManageGroups = authorizationDAO.canUserExecuteProcess(adminUserId, "pt_admin_grupos");
            if (!canManageGroups) {
                logger.warn("Usuario {} no tiene permisos para gestionar grupos", adminUserId);
                return false;
            }
            
            // 2. Agregar a grupo en Neo4j
            boolean added = authorizationDAO.addUserToGroup(userId, groupId);
            
            if (added) {
                logger.info("Usuario {} agregado al grupo {} por admin {}", userId, groupId, adminUserId);
            }
            
            return added;
            
        } catch (Exception e) {
            logger.error("Error agregando usuario a grupo", e);
            return false;
        }
    }
    
    /**
     * Obtiene usuarios por departamento
     */
    public List<User> getUsersByDepartment(String department) {
        try {
            return userDAO.findByDepartment(department);
        } catch (Exception e) {
            logger.error("Error obteniendo usuarios por departamento: {}", department, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Desactiva un usuario (soft delete)
     */
    public boolean deactivateUser(String adminUserId, String userId) {
        try {
            // 1. Verificar permisos administrativos
            boolean isAdmin = authorizationDAO.canUserExecuteProcess(adminUserId, "pt_admin_usuarios");
            if (!isAdmin) {
                logger.warn("Usuario {} no tiene permisos para desactivar usuarios", adminUserId);
                return false;
            }
            
            // 2. Desactivar en MongoDB
            boolean deactivated = userDAO.updateUserStatus(userId, "inactivo");
            
            if (deactivated) {
                // 3. Sincronizar estado en Neo4j
                Optional<User> userOpt = userDAO.findById(userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    authorizationDAO.syncUserFromMongo(
                        userId, user.getEmail(), user.getFullName(), "inactivo", user.getDepartment()
                    );
                }
                
                logger.info("Usuario {} desactivado por admin {}", userId, adminUserId);
            }
            
            return deactivated;
            
        } catch (Exception e) {
            logger.error("Error desactivando usuario: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Asigna rol por defecto según departamento
     */
    private void assignDefaultRole(String userId, String department) {
        try {
            String defaultRoleId = switch (department.toLowerCase()) {
                case "sistemas" -> "role_admin";
                case "mantenimiento" -> "role_tecnico";
                default -> "role_usuario";
            };
            
            authorizationDAO.assignRoleToUser(userId, defaultRoleId);
            logger.debug("Rol por defecto {} asignado a usuario {}", defaultRoleId, userId);
            
        } catch (Exception e) {
            logger.warn("Error asignando rol por defecto", e);
        }
    }
    
    /**
     * Hash de contraseña usando SHA-256
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hasheando contraseña", e);
        }
    }
    
    /**
     * Verifica contraseña
     */
    private boolean verifyPassword(String password, String hash) {
        return hashPassword(password).equals(hash);
    }
}
