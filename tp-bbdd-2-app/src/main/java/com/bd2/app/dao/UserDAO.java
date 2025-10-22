package com.bd2.app.dao;

import com.bd2.app.database.MongoConnectionManager;
import com.bd2.app.model.User;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO para operaciones de Usuario en MongoDB
 */
public class UserDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);
    private static final String COLLECTION_NAME = "users";
    
    private final MongoCollection<Document> collection;
    
    public UserDAO() {
        MongoDatabase database = MongoConnectionManager.getInstance().getDatabase();
        this.collection = database.getCollection(COLLECTION_NAME);
    }
    
    /**
     * Crea un nuevo usuario
     */
    public String createUser(User user) {
        try {
            if (user.getId() == null) {
                user.setId(UUID.randomUUID().toString());
            }
            
            user.setRegisteredAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            
            Document doc = userToDocument(user);
            collection.insertOne(doc);
            
            logger.info("Usuario creado: {}", user.getId());
            return user.getId();
            
        } catch (Exception e) {
            logger.error("Error creando usuario", e);
            throw new RuntimeException("No se pudo crear el usuario", e);
        }
    }
    
    /**
     * Busca un usuario por ID
     */
    public Optional<User> findById(String id) {
        try {
            Document doc = collection.find(Filters.eq("_id", id)).first();
            return doc != null ? Optional.of(documentToUser(doc)) : Optional.empty();
        } catch (Exception e) {
            logger.error("Error buscando usuario por ID: {}", id, e);
            return Optional.empty();
        }
    }
    
    /**
     * Busca un usuario por email
     */
    public Optional<User> findByEmail(String email) {
        try {
            Document doc = collection.find(Filters.eq("email", email)).first();
            return doc != null ? Optional.of(documentToUser(doc)) : Optional.empty();
        } catch (Exception e) {
            logger.error("Error buscando usuario por email: {}", email, e);
            return Optional.empty();
        }
    }
    
    /**
     * Obtiene todos los usuarios activos
     */
    public List<User> findActiveUsers() {
        try {
            List<User> users = new ArrayList<>();
            collection.find(Filters.eq("status", "activo"))
                     .forEach(doc -> users.add(documentToUser(doc)));
            return users;
        } catch (Exception e) {
            logger.error("Error obteniendo usuarios activos", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene usuarios por departamento
     */
    public List<User> findByDepartment(String department) {
        try {
            List<User> users = new ArrayList<>();
            Bson filter = Filters.and(
                Filters.eq("department", department),
                Filters.eq("status", "activo")
            );
            collection.find(filter).forEach(doc -> users.add(documentToUser(doc)));
            return users;
        } catch (Exception e) {
            logger.error("Error obteniendo usuarios por departamento: {}", department, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Actualiza un usuario
     */
    public boolean updateUser(User user) {
        try {
            user.setUpdatedAt(LocalDateTime.now());
            
            Bson updates = Updates.combine(
                Updates.set("fullName", user.getFullName()),
                Updates.set("email", user.getEmail()),
                Updates.set("status", user.getStatus()),
                Updates.set("department", user.getDepartment()),
                Updates.set("updatedAt", user.getUpdatedAt())
            );
            
            UpdateResult result = collection.updateOne(
                Filters.eq("_id", user.getId()), 
                updates
            );
            
            boolean success = result.getModifiedCount() > 0;
            if (success) {
                logger.info("Usuario actualizado: {}", user.getId());
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error actualizando usuario: {}", user.getId(), e);
            return false;
        }
    }
    
    /**
     * Actualiza el estado de un usuario
     */
    public boolean updateUserStatus(String userId, String status) {
        try {
            Bson updates = Updates.combine(
                Updates.set("status", status),
                Updates.set("updatedAt", LocalDateTime.now())
            );
            
            UpdateResult result = collection.updateOne(
                Filters.eq("_id", userId), 
                updates
            );
            
            boolean success = result.getModifiedCount() > 0;
            if (success) {
                logger.info("Estado de usuario actualizado: {} -> {}", userId, status);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error actualizando estado de usuario: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Actualiza la contraseña de un usuario
     */
    public boolean updatePassword(String userId, String newPasswordHash) {
        try {
            Bson updates = Updates.combine(
                Updates.set("passwordHash", newPasswordHash),
                Updates.set("updatedAt", LocalDateTime.now())
            );
            
            UpdateResult result = collection.updateOne(
                Filters.eq("_id", userId), 
                updates
            );
            
            boolean success = result.getModifiedCount() > 0;
            if (success) {
                logger.info("Contraseña actualizada para usuario: {}", userId);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error actualizando contraseña de usuario: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Elimina un usuario (soft delete - cambiar estado a inactivo)
     */
    public boolean deleteUser(String userId) {
        return updateUserStatus(userId, "inactivo");
    }
    
    /**
     * Elimina un usuario permanentemente
     */
    public boolean deleteUserPermanently(String userId) {
        try {
            DeleteResult result = collection.deleteOne(Filters.eq("_id", userId));
            boolean success = result.getDeletedCount() > 0;
            
            if (success) {
                logger.info("Usuario eliminado permanentemente: {}", userId);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error eliminando usuario permanentemente: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Cuenta usuarios por estado
     */
    public long countByStatus(String status) {
        try {
            return collection.countDocuments(Filters.eq("status", status));
        } catch (Exception e) {
            logger.error("Error contando usuarios por estado: {}", status, e);
            return 0;
        }
    }
    
    /**
     * Verifica si existe un usuario con el email dado
     */
    public boolean existsByEmail(String email) {
        try {
            return collection.countDocuments(Filters.eq("email", email)) > 0;
        } catch (Exception e) {
            logger.error("Error verificando existencia de email: {}", email, e);
            return false;
        }
    }
    
    // Métodos de conversión
    private Document userToDocument(User user) {
        Document doc = new Document()
                .append("_id", user.getId())
                .append("fullName", user.getFullName())
                .append("email", user.getEmail())
                .append("passwordHash", user.getPasswordHash())
                .append("status", user.getStatus())
                .append("registeredAt", user.getRegisteredAt())
                .append("updatedAt", user.getUpdatedAt());
        
        if (user.getDepartment() != null) {
            doc.append("department", user.getDepartment());
        }
        
        return doc;
    }
    
    private User documentToUser(Document doc) {
        User user = new User();
        user.setId(doc.getString("_id"));
        user.setFullName(doc.getString("fullName"));
        user.setEmail(doc.getString("email"));
        user.setPasswordHash(doc.getString("passwordHash"));
        user.setStatus(doc.getString("status"));
        user.setDepartment(doc.getString("department"));
        
        // Manejar fechas
        Object registeredAt = doc.get("registeredAt");
        if (registeredAt instanceof LocalDateTime) {
            user.setRegisteredAt((LocalDateTime) registeredAt);
        }
        
        Object updatedAt = doc.get("updatedAt");
        if (updatedAt instanceof LocalDateTime) {
            user.setUpdatedAt((LocalDateTime) updatedAt);
        }
        
        return user;
    }
}
