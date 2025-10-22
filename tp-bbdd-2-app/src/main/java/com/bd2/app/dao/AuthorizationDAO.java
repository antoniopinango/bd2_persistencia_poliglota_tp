package com.bd2.app.dao;

import com.bd2.app.database.Neo4jConnectionManager;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * DAO para operaciones de autorización y permisos en Neo4j
 */
public class AuthorizationDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationDAO.class);
    
    private final Neo4jConnectionManager connectionManager;
    
    public AuthorizationDAO() {
        this.connectionManager = Neo4jConnectionManager.getInstance();
    }
    
    /**
     * Obtiene los permisos efectivos de un usuario (procesos que puede ejecutar)
     */
    public Set<String> getUserPermissions(String userId) {
        String query = """
            MATCH (u:User {id: $userId})-[:HAS_ROLE|MEMBER_OF]->(x)-[:CAN_EXECUTE]->(p:ProcessType) 
            WHERE u.status = 'activo'
            RETURN DISTINCT p.id AS processId, p.name AS processName
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.READ)) {
            Result result = session.run(query, Values.parameters("userId", userId));
            
            Set<String> permissions = new HashSet<>();
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                permissions.add(record.get("processId").asString());
            }
            
            logger.debug("Usuario {} tiene {} permisos", userId, permissions.size());
            return permissions;
            
        } catch (Exception e) {
            logger.error("Error obteniendo permisos de usuario: {}", userId, e);
            return new HashSet<>();
        }
    }
    
    /**
     * Verifica si un usuario puede ejecutar un proceso específico
     */
    public boolean canUserExecuteProcess(String userId, String processId) {
        String query = """
            MATCH (u:User {id: $userId})-[:HAS_ROLE|MEMBER_OF]->()-[:CAN_EXECUTE]->(p:ProcessType {id: $processId}) 
            WHERE u.status = 'activo'
            RETURN count(p) > 0 AS allowed
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.READ)) {
            Result result = session.run(query, Values.parameters("userId", userId, "processId", processId));
            
            if (result.hasNext()) {
                return result.next().get("allowed").asBoolean();
            }
            return false;
            
        } catch (Exception e) {
            logger.error("Error verificando permisos - Usuario: {}, Proceso: {}", userId, processId, e);
            return false;
        }
    }
    
    /**
     * Verifica si un usuario puede ejecutar un proceso en una ciudad específica
     */
    public boolean canUserExecuteProcessInCity(String userId, String processId, String cityName) {
        String query = """
            MATCH (u:User {id: $userId})-[:HAS_ROLE|MEMBER_OF]->()-[:CAN_EXECUTE]->(p:ProcessType {id: $processId})
            MATCH (u)-[:COVERS_CITY|COVERS_COUNTRY*1..2]->(geo)
            WHERE u.status = 'activo' 
              AND ((geo:City AND geo.name = $cityName) OR (geo:Country))
            RETURN count(p) > 0 AND count(geo) > 0 AS allowed
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.READ)) {
            Result result = session.run(query, Values.parameters(
                "userId", userId, 
                "processId", processId, 
                "cityName", cityName
            ));
            
            if (result.hasNext()) {
                return result.next().get("allowed").asBoolean();
            }
            return false;
            
        } catch (Exception e) {
            logger.error("Error verificando permisos geográficos - Usuario: {}, Proceso: {}, Ciudad: {}", 
                        userId, processId, cityName, e);
            return false;
        }
    }
    
    /**
     * Obtiene los miembros de un grupo
     */
    public List<Map<String, Object>> getGroupMembers(String groupId) {
        String query = """
            MATCH (g:Group {id: $groupId})<-[:MEMBER_OF]-(u:User)
            WHERE u.status = 'activo'
            RETURN u.id AS userId, u.fullName AS fullName, u.email AS email, u.department AS department
            ORDER BY u.fullName
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.READ)) {
            Result result = session.run(query, Values.parameters("groupId", groupId));
            
            List<Map<String, Object>> members = new ArrayList<>();
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                Map<String, Object> member = new HashMap<>();
                member.put("userId", record.get("userId").asString());
                member.put("fullName", record.get("fullName").asString());
                member.put("email", record.get("email").asString());
                member.put("department", record.get("department").asString(""));
                members.add(member);
            }
            
            return members;
            
        } catch (Exception e) {
            logger.error("Error obteniendo miembros del grupo: {}", groupId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene técnicos que cubren una ciudad específica
     */
    public List<Map<String, Object>> getTechniciansForCity(String cityName) {
        String query = """
            MATCH (c:City {name: $cityName})<-[:COVERS_CITY]-(t:User)-[:HAS_ROLE]->(r:Role {name: 'tecnico'})
            WHERE t.status = 'activo'
            RETURN t.id AS technicianId, t.fullName AS fullName, t.email AS email, t.department AS department
            ORDER BY t.fullName
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.READ)) {
            Result result = session.run(query, Values.parameters("cityName", cityName));
            
            List<Map<String, Object>> technicians = new ArrayList<>();
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                Map<String, Object> technician = new HashMap<>();
                technician.put("technicianId", record.get("technicianId").asString());
                technician.put("fullName", record.get("fullName").asString());
                technician.put("email", record.get("email").asString());
                technician.put("department", record.get("department").asString(""));
                technicians.add(technician);
            }
            
            return technicians;
            
        } catch (Exception e) {
            logger.error("Error obteniendo técnicos para ciudad: {}", cityName, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene sensores por país usando la jerarquía geográfica
     */
    public List<Map<String, Object>> getSensorsByCountry(String countryName) {
        String query = """
            MATCH (s:Sensor)-[:IN_CITY]->(c:City)-[:IN_COUNTRY]->(co:Country {name: $countryName})
            RETURN s.id AS sensorId, s.code AS sensorCode, s.type AS sensorType, 
                   s.state AS sensorState, c.name AS city
            ORDER BY c.name, s.code
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.READ)) {
            Result result = session.run(query, Values.parameters("countryName", countryName));
            
            List<Map<String, Object>> sensors = new ArrayList<>();
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                Map<String, Object> sensor = new HashMap<>();
                sensor.put("sensorId", record.get("sensorId").asString());
                sensor.put("sensorCode", record.get("sensorCode").asString());
                sensor.put("sensorType", record.get("sensorType").asString());
                sensor.put("sensorState", record.get("sensorState").asString());
                sensor.put("city", record.get("city").asString());
                sensors.add(sensor);
            }
            
            return sensors;
            
        } catch (Exception e) {
            logger.error("Error obteniendo sensores por país: {}", countryName, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Sincroniza un usuario desde MongoDB a Neo4j
     */
    public boolean syncUserFromMongo(String userId, String email, String fullName, String status, String department) {
        String query = """
            MERGE (u:User {id: $userId}) 
            SET u.email = $email, 
                u.fullName = $fullName, 
                u.status = $status,
                u.department = $department,
                u.updatedAt = datetime()
            RETURN u.id AS userId
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.WRITE)) {
            Result result = session.run(query, Values.parameters(
                "userId", userId,
                "email", email,
                "fullName", fullName,
                "status", status,
                "department", department
            ));
            
            boolean success = result.hasNext();
            if (success) {
                logger.debug("Usuario sincronizado en Neo4j: {}", userId);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error sincronizando usuario en Neo4j: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Asigna un rol a un usuario
     */
    public boolean assignRoleToUser(String userId, String roleId) {
        String query = """
            MATCH (u:User {id: $userId}), (r:Role {id: $roleId}) 
            MERGE (u)-[:HAS_ROLE]->(r)
            RETURN count(*) AS assigned
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.WRITE)) {
            Result result = session.run(query, Values.parameters("userId", userId, "roleId", roleId));
            
            boolean success = result.hasNext() && result.next().get("assigned").asInt() > 0;
            if (success) {
                logger.info("Rol {} asignado a usuario {}", roleId, userId);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error asignando rol {} a usuario {}", roleId, userId, e);
            return false;
        }
    }
    
    /**
     * Agrega un usuario a un grupo
     */
    public boolean addUserToGroup(String userId, String groupId) {
        String query = """
            MATCH (u:User {id: $userId}), (g:Group {id: $groupId}) 
            MERGE (u)-[:MEMBER_OF]->(g)
            RETURN count(*) AS added
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.WRITE)) {
            Result result = session.run(query, Values.parameters("userId", userId, "groupId", groupId));
            
            boolean success = result.hasNext() && result.next().get("added").asInt() > 0;
            if (success) {
                logger.info("Usuario {} agregado al grupo {}", userId, groupId);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error agregando usuario {} al grupo {}", userId, groupId, e);
            return false;
        }
    }
    
    /**
     * Asigna cobertura de ciudad a un técnico
     */
    public boolean assignCityCoverage(String technicianId, String cityName) {
        String query = """
            MATCH (u:User {id: $technicianId}), (c:City {name: $cityName})
            MERGE (u)-[:COVERS_CITY]->(c)
            RETURN count(*) AS assigned
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.WRITE)) {
            Result result = session.run(query, Values.parameters("technicianId", technicianId, "cityName", cityName));
            
            boolean success = result.hasNext() && result.next().get("assigned").asInt() > 0;
            if (success) {
                logger.info("Cobertura de ciudad {} asignada a técnico {}", cityName, technicianId);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error asignando cobertura de ciudad {} a técnico {}", cityName, technicianId, e);
            return false;
        }
    }
    
    /**
     * Obtiene información del dashboard para administradores
     */
    public Map<String, Object> getDashboardInfo() {
        String query = """
            MATCH (u:User) WITH count(u) AS totalUsers
            MATCH (u:User {status: 'activo'}) WITH totalUsers, count(u) AS activeUsers
            MATCH (s:Sensor) WITH totalUsers, activeUsers, count(s) AS totalSensors
            MATCH (s:Sensor {state: 'activo'}) WITH totalUsers, activeUsers, totalSensors, count(s) AS activeSensors
            MATCH (g:Group) WITH totalUsers, activeUsers, totalSensors, activeSensors, count(g) AS totalGroups
            RETURN {
                users: {total: totalUsers, active: activeUsers},
                sensors: {total: totalSensors, active: activeSensors},
                groups: totalGroups
            } AS summary
            """;
        
        try (Session session = connectionManager.createSession(AccessMode.READ)) {
            Result result = session.run(query);
            
            if (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                return record.get("summary").asMap();
            }
            
            return new HashMap<>();
            
        } catch (Exception e) {
            logger.error("Error obteniendo información del dashboard", e);
            return new HashMap<>();
        }
    }
}
