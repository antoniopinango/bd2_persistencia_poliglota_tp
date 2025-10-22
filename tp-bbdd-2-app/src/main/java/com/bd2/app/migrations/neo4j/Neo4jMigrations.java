package com.bd2.app.migrations.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migraciones para Neo4j - Creación de constraints, índices y nodos iniciales
 */
public class Neo4jMigrations {
    
    private static final Logger logger = LoggerFactory.getLogger(Neo4jMigrations.class);
    private final Driver driver;
    
    public Neo4jMigrations(Driver driver) {
        this.driver = driver;
    }
    
    /**
     * Ejecuta todas las migraciones de Neo4j
     */
    public void runAllMigrations() {
        logger.info("=== Iniciando migraciones Neo4j ===");
        
        try (Session session = driver.session()) {
            createConstraints(session);
            createInitialNodes(session);
            createBasicRelationships(session);
            
            logger.info("=== Migraciones Neo4j completadas exitosamente ===");
            
        } catch (Exception e) {
            logger.error("Error ejecutando migraciones Neo4j", e);
            throw new RuntimeException("Fallo en migraciones Neo4j", e);
        }
    }
    
    private void createConstraints(Session session) {
        logger.info("Creando constraints y índices únicos...");
        
        // User constraints
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE");
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT user_email IF NOT EXISTS FOR (u:User) REQUIRE u.email IS UNIQUE");
        
        // Role constraints
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT role_id IF NOT EXISTS FOR (r:Role) REQUIRE r.id IS UNIQUE");
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT role_name IF NOT EXISTS FOR (r:Role) REQUIRE r.name IS UNIQUE");
        
        // Group constraints
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT group_id IF NOT EXISTS FOR (g:Group) REQUIRE g.id IS UNIQUE");
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT group_name IF NOT EXISTS FOR (g:Group) REQUIRE g.name IS UNIQUE");
        
        // ProcessType constraints
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT processtype_id IF NOT EXISTS FOR (p:ProcessType) REQUIRE p.id IS UNIQUE");
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT processtype_name IF NOT EXISTS FOR (p:ProcessType) REQUIRE p.name IS UNIQUE");
        
        // Sensor constraints
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT sensor_id IF NOT EXISTS FOR (s:Sensor) REQUIRE s.id IS UNIQUE");
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT sensor_code IF NOT EXISTS FOR (s:Sensor) REQUIRE s.code IS UNIQUE");
        
        // Geography constraints
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT city_name IF NOT EXISTS FOR (c:City) REQUIRE c.name IS UNIQUE");
        executeIgnoringExisting(session, 
            "CREATE CONSTRAINT country_name IF NOT EXISTS FOR (c:Country) REQUIRE c.name IS UNIQUE");
        
        logger.info("Constraints creados");
    }
    
    private void createInitialNodes(Session session) {
        logger.info("Creando nodos iniciales...");
        
        // Roles básicos
        session.run("""
            MERGE (admin:Role {id: 'role_admin', name: 'admin'})
            SET admin.description = 'Administrador del sistema'
            """);
        
        session.run("""
            MERGE (user:Role {id: 'role_user', name: 'usuario'})
            SET user.description = 'Usuario estándar'
            """);
        
        session.run("""
            MERGE (tech:Role {id: 'role_tech', name: 'tecnico'})
            SET tech.description = 'Técnico de campo'
            """);
        
        // Tipos de procesos
        session.run("""
            MERGE (p1:ProcessType {id: 'pt_maxmin', name: 'Reporte Max/Min'})
            SET p1.description = 'Reporte de valores máximos y mínimos'
            """);
        
        session.run("""
            MERGE (p2:ProcessType {id: 'pt_prom', name: 'Reporte Promedios'})
            SET p2.description = 'Reporte de valores promedio'
            """);
        
        session.run("""
            MERGE (p3:ProcessType {id: 'pt_alerts', name: 'Alertas por rango'})
            SET p3.description = 'Consulta de alertas por rango de fechas'
            """);
        
        // Geografía básica
        session.run("""
            MERGE (ar:Country {name: 'Argentina'})
            MERGE (ba:City {name: 'Buenos Aires'})
            MERGE (co:City {name: 'Córdoba'})
            MERGE (ro:City {name: 'Rosario'})
            MERGE (ba)-[:IN_COUNTRY]->(ar)
            MERGE (co)-[:IN_COUNTRY]->(ar)
            MERGE (ro)-[:IN_COUNTRY]->(ar)
            """);
        
        // Grupos básicos
        session.run("""
            MERGE (g1:Group {id: 'group_admins', name: 'Administradores'})
            SET g1.description = 'Grupo de administradores del sistema'
            """);
        
        session.run("""
            MERGE (g2:Group {id: 'group_techs', name: 'Técnicos'})
            SET g2.description = 'Grupo de técnicos de campo'
            """);
        
        logger.info("Nodos iniciales creados");
    }
    
    private void createBasicRelationships(Session session) {
        logger.info("Creando relaciones básicas de permisos...");
        
        // Admin puede ejecutar todos los procesos
        session.run("""
            MATCH (admin:Role {name: 'admin'}), (p:ProcessType)
            MERGE (admin)-[:CAN_EXECUTE]->(p)
            """);
        
        // Usuario puede ejecutar solo reportes de promedios
        session.run("""
            MATCH (user:Role {name: 'usuario'}), (p:ProcessType {name: 'Reporte Promedios'})
            MERGE (user)-[:CAN_EXECUTE]->(p)
            """);
        
        // Técnico puede ejecutar alertas y reportes
        session.run("""
            MATCH (tech:Role {name: 'tecnico'}), (p:ProcessType)
            WHERE p.name IN ['Alertas por rango', 'Reporte Max/Min']
            MERGE (tech)-[:CAN_EXECUTE]->(p)
            """);
        
        // Grupo de admins puede ejecutar todos los procesos
        session.run("""
            MATCH (g:Group {name: 'Administradores'}), (p:ProcessType)
            MERGE (g)-[:CAN_EXECUTE]->(p)
            """);
        
        logger.info("Relaciones básicas creadas");
    }
    
    /**
     * Inserta datos de prueba básicos
     */
    public void insertSampleData() {
        logger.info("Insertando datos de prueba en Neo4j...");
        
        try (Session session = driver.session()) {
            // Usuario administrador de prueba
            session.run("""
                MERGE (u:User {id: 'user_admin_test', email: 'admin@test.com'})
                SET u.fullName = 'Administrador Test',
                    u.status = 'activo',
                    u.department = 'IT'
                """);
            
            // Asignar rol de admin
            session.run("""
                MATCH (u:User {id: 'user_admin_test'}), (r:Role {name: 'admin'})
                MERGE (u)-[:HAS_ROLE]->(r)
                """);
            
            // Sensor de prueba
            session.run("""
                MERGE (s:Sensor {id: 'sensor_test_001', code: 'TEMP_BA_001'})
                SET s.type = 'temperatura',
                    s.state = 'activo'
                """);
            
            // Ubicar sensor en Buenos Aires
            session.run("""
                MATCH (s:Sensor {code: 'TEMP_BA_001'}), (c:City {name: 'Buenos Aires'})
                MERGE (s)-[:IN_CITY]->(c)
                """);
            
            logger.info("Datos de prueba insertados en Neo4j");
            
        } catch (Exception e) {
            logger.warn("Error insertando datos de prueba: {}", e.getMessage());
        }
    }
    
    private void executeIgnoringExisting(Session session, String query) {
        try {
            session.run(query);
        } catch (Exception e) {
            if (e.getMessage().contains("already exists") || e.getMessage().contains("equivalent")) {
                logger.debug("Constraint ya existe: {}", query);
            } else {
                throw e;
            }
        }
    }
}
