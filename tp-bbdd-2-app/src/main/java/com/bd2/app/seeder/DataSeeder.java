package com.bd2.app.seeder;

import com.bd2.app.database.CassandraConnectionManager;
import com.bd2.app.database.MongoConnectionManager;
import com.bd2.app.database.Neo4jConnectionManager;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Seeder de datos para poblar las bases de datos con información realista
 */
public class DataSeeder {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);
    
    private final MongoDatabase mongoDb;
    private final CqlSession cassandraSession;
    private final Driver neo4jDriver;
    
    // IDs compartidos entre bases de datos
    private final List<String> userIds = new ArrayList<>();
    private final List<String> sensorIds = new ArrayList<>();
    private final Map<String, String> cityCountryMap = new HashMap<>();
    
    public DataSeeder() {
        this.mongoDb = MongoConnectionManager.getInstance().getDatabase();
        this.cassandraSession = CassandraConnectionManager.getInstance().getSession();
        this.neo4jDriver = Neo4jConnectionManager.getInstance().getDriver();
        
        initializeCityCountryMap();
    }
    
    private void initializeCityCountryMap() {
        cityCountryMap.put("Buenos Aires", "Argentina");
        cityCountryMap.put("Córdoba", "Argentina");
        cityCountryMap.put("Rosario", "Argentina");
        cityCountryMap.put("Mendoza", "Argentina");
        cityCountryMap.put("La Plata", "Argentina");
    }
    
    /**
     * Ejecuta el seeding completo de todas las bases de datos
     */
    public void seedAll() {
        logger.info("🌱 Iniciando seeding de datos...");
        
        try {
            // Verificar si ya hay datos en MongoDB
            boolean dataExists = dataAlreadyExists();
            
            if (dataExists) {
                logger.info("⚠️  Datos de MongoDB ya existen. Saltando seeding de MongoDB.");
                logger.info("✅ Sincronizando usuarios existentes a Neo4j...");
                
                // Obtener usuarios de MongoDB y sincronizar a Neo4j
                syncExistingUsersToNeo4j();
            } else {
                // Seed completo si no hay datos
                seedMongoData();
                seedNeo4jData();
                seedCassandraData();
                
                logger.info("✅ Seeding completado exitosamente!");
                logSeedingSummary();
            }
            
        } catch (Exception e) {
            logger.error("❌ Error durante el seeding", e);
            throw new RuntimeException("Fallo en el seeding de datos", e);
        }
    }
    
    /**
     * Sincroniza usuarios existentes de MongoDB a Neo4j y asigna permisos
     */
    private void syncExistingUsersToNeo4j() {
        logger.info("🔄 Sincronizando usuarios de MongoDB a Neo4j...");
        
        try (Session session = neo4jDriver.session()) {
            MongoCollection<Document> users = mongoDb.getCollection("users");
            
            // Obtener todos los usuarios de MongoDB
            for (Document userDoc : users.find()) {
                String userId = userDoc.getString("_id");
                String email = userDoc.getString("email");
                String fullName = userDoc.getString("fullName");
                String status = userDoc.getString("status");
                String department = userDoc.getString("department");
                
                // Crear/actualizar usuario en Neo4j
                session.run(
                    "MERGE (u:User {id: $userId}) " +
                    "SET u.email = $email, u.fullName = $fullName, u.status = $status, u.department = $department",
                    Map.of("userId", userId, "email", email, "fullName", fullName, 
                           "status", status, "department", department)
                );
                
                // Si es el admin, asignar TODOS los permisos
                if (email.equals("admin@admin.com")) {
                    logger.info("🔐 Configurando permisos completos para admin...");
                    
                    // Asignar TODOS los roles
                    session.run(
                        "MATCH (u:User {id: $userId}), (r:Role) " +
                        "MERGE (u)-[:HAS_ROLE]->(r)",
                        Map.of("userId", userId)
                    );
                    
                    // Asignar TODOS los permisos directos
                    session.run(
                        "MATCH (u:User {id: $userId}), (p:ProcessType) " +
                        "MERGE (u)-[:CAN_EXECUTE]->(p)",
                        Map.of("userId", userId)
                    );
                    
                    // Agregar a TODOS los grupos
                    session.run(
                        "MATCH (u:User {id: $userId}), (g:Group) " +
                        "MERGE (u)-[:MEMBER_OF]->(g)",
                        Map.of("userId", userId)
                    );
                    
                    logger.info("✅ Admin configurado con acceso total");
                }
            }
            
            logger.info("✅ Sincronización completada");
            
        } catch (Exception e) {
            logger.error("Error sincronizando usuarios a Neo4j", e);
        }
    }
    
    private boolean dataAlreadyExists() {
        // Verificar si ya hay usuarios en MongoDB
        long userCount = mongoDb.getCollection("users").countDocuments();
        return userCount > 0;
    }
    
    /**
     * Seed de datos en MongoDB
     */
    private void seedMongoData() {
        logger.info("📊 Seeding MongoDB...");
        
        seedRoles();
        seedUsers();
        seedSensors();
        seedProcesses();
        seedAccounts();
        
        logger.info("✅ MongoDB seeding completado");
    }
    
    private void seedRoles() {
        logger.info("Insertando roles...");
        
        MongoCollection<Document> roles = mongoDb.getCollection("roles");
        
        List<Document> rolesDocs = Arrays.asList(
            new Document("_id", "role_admin")
                .append("name", "Administrador")
                .append("description", "Acceso completo al sistema")
                .append("permissions", Arrays.asList("read", "write", "delete", "admin")),
            
            new Document("_id", "role_operator")
                .append("name", "Operador")
                .append("description", "Gestión de sensores y mediciones")
                .append("permissions", Arrays.asList("read", "write")),
            
            new Document("_id", "role_analyst")
                .append("name", "Analista")
                .append("description", "Consulta y análisis de datos")
                .append("permissions", Arrays.asList("read")),
            
            new Document("_id", "role_technician")
                .append("name", "Técnico")
                .append("description", "Mantenimiento de sensores")
                .append("permissions", Arrays.asList("read", "maintenance"))
        );
        
        try {
            roles.insertMany(rolesDocs);
            logger.info("✓ {} roles insertados", rolesDocs.size());
        } catch (Exception e) {
            logger.warn("Roles ya existen o error: {}", e.getMessage());
        }
    }
    
    private void seedUsers() {
        logger.info("Insertando usuarios...");
        
        MongoCollection<Document> users = mongoDb.getCollection("users");
        
        // Usuario administrador principal
        String adminId = UUID.randomUUID().toString();
        userIds.add(adminId);
        
        Document adminUser = new Document("_id", adminId)
            .append("fullName", "Admin Sistema")
            .append("email", "admin@admin.com")
            .append("passwordHash", "admin") // Contraseña en texto plano
            .append("status", "activo")
            .append("department", "Administración")
            .append("roleId", "role_admin")
            .append("registeredAt", new Date())
            .append("updatedAt", new Date())
            .append("lastLogin", new Date());
        
        String[][] usersData = {
            {"Juan", "Pérez", "juan.perez@uade.edu.ar", "Ingeniería", "role_admin"},
            {"María", "González", "maria.gonzalez@uade.edu.ar", "Ciencias", "role_operator"},
            {"Carlos", "Rodríguez", "carlos.rodriguez@uade.edu.ar", "Ingeniería", "role_analyst"},
            {"Ana", "Martínez", "ana.martinez@uade.edu.ar", "Arquitectura", "role_technician"},
            {"Luis", "Fernández", "luis.fernandez@uade.edu.ar", "Ingeniería", "role_operator"},
            {"Laura", "López", "laura.lopez@uade.edu.ar", "Ciencias", "role_analyst"},
            {"Diego", "Sánchez", "diego.sanchez@uade.edu.ar", "Ingeniería", "role_operator"},
            {"Sofía", "Romero", "sofia.romero@uade.edu.ar", "Arquitectura", "role_analyst"},
            {"Pablo", "Torres", "pablo.torres@uade.edu.ar", "Ingeniería", "role_technician"},
            {"Valentina", "Díaz", "valentina.diaz@uade.edu.ar", "Ciencias", "role_operator"}
        };
        
        List<Document> userDocs = new ArrayList<>();
        userDocs.add(adminUser); // Agregar admin primero
        
        for (String[] userData : usersData) {
            String userId = UUID.randomUUID().toString();
            userIds.add(userId);
            
            Document user = new Document("_id", userId)
                .append("fullName", userData[0] + " " + userData[1])
                .append("email", userData[2])
                .append("passwordHash", "password123") // Password por defecto en texto plano
                .append("status", "activo")
                .append("department", userData[3])
                .append("roleId", userData[4])
                .append("registeredAt", new Date())
                .append("updatedAt", new Date())
                .append("lastLogin", new Date());
            
            userDocs.add(user);
        }
        
        try {
            users.insertMany(userDocs);
            logger.info("✓ {} usuarios insertados (incluyendo admin@admin.com)", userDocs.size());
        } catch (Exception e) {
            logger.warn("Error insertando usuarios: {}", e.getMessage());
        }
    }
    
    private void seedSensors() {
        logger.info("Insertando sensores...");
        
        MongoCollection<Document> sensors = mongoDb.getCollection("sensors");
        
        // IDs fijos para facilitar testing
        String[] fixedSensorIds = {
            "550e8400-e29b-41d4-a716-446655440001", // Buenos Aires - Lab A
            "550e8400-e29b-41d4-a716-446655440002", // Buenos Aires - Lab B
            "550e8400-e29b-41d4-a716-446655440003", // Córdoba - Aula Magna
            "550e8400-e29b-41d4-a716-446655440004", // Córdoba - Biblioteca
            "550e8400-e29b-41d4-a716-446655440005", // Rosario - Servidores
            "550e8400-e29b-41d4-a716-446655440006", // Mendoza - Lab C
            "550e8400-e29b-41d4-a716-446655440007", // La Plata - Aula 101
            "550e8400-e29b-41d4-a716-446655440008", // Buenos Aires - Oficina
            "550e8400-e29b-41d4-a716-446655440009", // Córdoba - Cafetería
            "550e8400-e29b-41d4-a716-446655440010"  // Rosario - Auditorio
        };
        
        String[][] sensorsData = {
            {"Buenos Aires", "Laboratorio A", "temperature_humidity", "-34.6037", "-58.3816"},
            {"Buenos Aires", "Laboratorio B", "temperature_humidity", "-34.6037", "-58.3816"},
            {"Córdoba", "Aula Magna", "temperature_humidity", "-31.4201", "-64.1888"},
            {"Córdoba", "Biblioteca", "temperature_humidity", "-31.4201", "-64.1888"},
            {"Rosario", "Sala de Servidores", "temperature_humidity", "-32.9442", "-60.6505"},
            {"Mendoza", "Laboratorio C", "temperature_humidity", "-32.8895", "-68.8458"},
            {"La Plata", "Aula 101", "temperature_humidity", "-34.9215", "-57.9545"},
            {"Buenos Aires", "Oficina Administración", "temperature_humidity", "-34.6037", "-58.3816"},
            {"Córdoba", "Cafetería", "temperature_humidity", "-31.4201", "-64.1888"},
            {"Rosario", "Auditorio", "temperature_humidity", "-32.9442", "-60.6505"}
        };
        
        List<Document> sensorDocs = new ArrayList<>();
        
        for (int i = 0; i < sensorsData.length; i++) {
            String[] sensorData = sensorsData[i];
            String sensorId = fixedSensorIds[i];
            sensorIds.add(sensorId);
            
            Document sensor = new Document("_id", sensorId)
                .append("name", sensorData[1])
                .append("type", sensorData[2])
                .append("location", new Document()
                    .append("city", sensorData[0])
                    .append("country", cityCountryMap.get(sensorData[0]))
                    .append("coordinates", new Document()
                        .append("latitude", Double.parseDouble(sensorData[3]))
                        .append("longitude", Double.parseDouble(sensorData[4]))))
                .append("status", "active")
                .append("installDate", new Date())
                .append("lastMaintenance", new Date())
                .append("ownerId", userIds.get(new Random().nextInt(userIds.size())));
            
            sensorDocs.add(sensor);
        }
        
        try {
            sensors.insertMany(sensorDocs);
            logger.info("✓ {} sensores insertados", sensorDocs.size());
        } catch (Exception e) {
            logger.warn("Error insertando sensores: {}", e.getMessage());
        }
    }
    
    private void seedProcesses() {
        logger.info("Insertando procesos...");
        
        MongoCollection<Document> processes = mongoDb.getCollection("processes");
        
        List<Document> processDocs = Arrays.asList(
            new Document("_id", "proc_temp_report")
                .append("name", "Reporte de Temperatura")
                .append("type", "reporte")
                .append("description", "Genera reporte de temperaturas por período")
                .append("baseCost", 10.0)
                .append("active", true),
            
            new Document("_id", "proc_humidity_report")
                .append("name", "Reporte de Humedad")
                .append("type", "reporte")
                .append("description", "Genera reporte de humedad por período")
                .append("baseCost", 10.0)
                .append("active", true),
            
            new Document("_id", "proc_alert_check")
                .append("name", "Verificación de Alertas")
                .append("type", "consulta")
                .append("description", "Verifica condiciones de alerta")
                .append("baseCost", 5.0)
                .append("active", true),
            
            new Document("_id", "proc_statistics")
                .append("name", "Estadísticas Generales")
                .append("type", "reporte")
                .append("description", "Estadísticas agregadas del sistema")
                .append("baseCost", 15.0)
                .append("active", true)
        );
        
        try {
            processes.insertMany(processDocs);
            logger.info("✓ {} procesos insertados", processDocs.size());
        } catch (Exception e) {
            logger.warn("Error insertando procesos: {}", e.getMessage());
        }
    }
    
    private void seedAccounts() {
        logger.info("Insertando cuentas...");
        
        MongoCollection<Document> accounts = mongoDb.getCollection("accounts");
        
        List<Document> accountDocs = new ArrayList<>();
        Random random = new Random();
        
        for (String userId : userIds) {
            Document account = new Document()
                .append("userId", userId)
                .append("balance", 100.0 + random.nextDouble() * 900.0)
                .append("currency", "ARS")
                .append("createdAt", new Date())
                .append("status", "active");
            
            accountDocs.add(account);
        }
        
        try {
            accounts.insertMany(accountDocs);
            logger.info("✓ {} cuentas insertadas", accountDocs.size());
        } catch (Exception e) {
            logger.warn("Error insertando cuentas: {}", e.getMessage());
        }
    }
    
    /**
     * Seed de datos en Neo4j
     */
    private void seedNeo4jData() {
        logger.info("🕸️  Seeding Neo4j...");
        
        try (Session session = neo4jDriver.session()) {
            // Crear usuarios con información completa
            for (int i = 0; i < userIds.size(); i++) {
                String email = i == 0 ? "admin@admin.com" : "user" + i + "@uade.edu.ar";
                String fullName = i == 0 ? "Admin Sistema" : "Usuario " + i;
                String status = "activo";
                String department = i == 0 ? "Administración" : "Departamento " + (i % 3);
                
                session.run(
                    "MERGE (u:User {id: $userId}) " +
                    "SET u.email = $email, u.fullName = $fullName, u.status = $status, u.department = $department",
                    Map.of("userId", userIds.get(i), "email", email, "fullName", fullName, 
                           "status", status, "department", department)
                );
            }
            
            // Asignar roles a usuarios usando los roles de las migraciones
            // El primer usuario (admin@admin.com) obtiene TODOS los roles
            logger.info("Asignando TODOS los roles al usuario admin...");
            session.run(
                "MATCH (u:User {id: $userId}), (r:Role) " +
                "MERGE (u)-[:HAS_ROLE]->(r)",
                Map.of("userId", userIds.get(0))
            );
            
            // Asignar roles a los demás usuarios
            for (int i = 1; i < userIds.size(); i++) {
                String roleName = i % 3 == 0 ? "usuario" : i % 3 == 1 ? "tecnico" : "usuario";
                session.run(
                    "MATCH (u:User {id: $userId}), (r:Role {name: $roleName}) " +
                    "MERGE (u)-[:HAS_ROLE]->(r)",
                    Map.of("userId", userIds.get(i), "roleName", roleName)
                );
            }
            
            // Crear grupos adicionales
            session.run("MERGE (g:Group {id: 'group_engineering', name: 'Ingeniería'})");
            session.run("MERGE (g:Group {id: 'group_science', name: 'Ciencias'})");
            session.run("MERGE (g:Group {id: 'group_architecture', name: 'Arquitectura'})");
            
            // Dar permisos a los grupos adicionales
            session.run(
                "MATCH (g:Group {id: 'group_engineering'}), (p:ProcessType) " +
                "WHERE p.id IN ['pt_maxmin', 'pt_prom'] " +
                "MERGE (g)-[:CAN_EXECUTE]->(p)"
            );
            
            session.run(
                "MATCH (g:Group {id: 'group_science'}), (p:ProcessType {id: 'pt_prom'}) " +
                "MERGE (g)-[:CAN_EXECUTE]->(p)"
            );
            
            // Asignar usuarios a grupos
            for (int i = 0; i < userIds.size(); i++) {
                String groupId = i % 3 == 0 ? "group_engineering" : 
                                i % 3 == 1 ? "group_science" : "group_architecture";
                session.run(
                    "MATCH (u:User {id: $userId}), (g:Group {id: $groupId}) " +
                    "MERGE (u)-[:MEMBER_OF]->(g)",
                    Map.of("userId", userIds.get(i), "groupId", groupId)
                );
            }
            
            // Crear ubicaciones adicionales
            for (String city : cityCountryMap.keySet()) {
                String country = cityCountryMap.get(city);
                session.run(
                    "MERGE (c:City {name: $city}) " +
                    "MERGE (co:Country {name: $country}) " +
                    "MERGE (c)-[:IN_COUNTRY]->(co)",
                    Map.of("city", city, "country", country)
                );
            }
            
            // Asignar sensores a ubicaciones
            int cityIndex = 0;
            String[] cities = cityCountryMap.keySet().toArray(new String[0]);
            
            for (String sensorId : sensorIds) {
                String city = cities[cityIndex % cities.length];
                session.run(
                    "MERGE (s:Sensor {id: $sensorId}) " +
                    "WITH s " +
                    "MATCH (c:City {name: $city}) " +
                    "MERGE (s)-[:IN_CITY]->(c)",
                    Map.of("sensorId", sensorId, "city", city)
                );
                cityIndex++;
            }
            
            // Crear ProcessTypes adicionales si no existen
            session.run("""
                MERGE (p:ProcessType {id: 'pt_admin_usuarios', name: 'Administrar Usuarios'})
                SET p.description = 'Gestión completa de usuarios'
                """);
            
            session.run("""
                MERGE (p:ProcessType {id: 'pt_admin_grupos', name: 'Administrar Grupos'})
                SET p.description = 'Gestión completa de grupos'
                """);
            
            session.run("""
                MERGE (p:ProcessType {id: 'pt_admin_sensores', name: 'Administrar Sensores'})
                SET p.description = 'Gestión completa de sensores'
                """);
            
            // Asignar permisos administrativos al rol admin
            logger.info("Asignando todos los ProcessTypes al rol admin...");
            session.run("""
                MATCH (r:Role {name: 'admin'}), (p:ProcessType)
                MERGE (r)-[:CAN_EXECUTE]->(p)
                """);
            
            // BONUS: Asignar DIRECTAMENTE al usuario admin TODOS los permisos
            // Esto garantiza que el admin tenga permisos incluso si hay problemas con roles
            logger.info("Asignando DIRECTAMENTE todos los ProcessTypes al usuario admin...");
            session.run(
                "MATCH (u:User {id: $userId}), (p:ProcessType) " +
                "MERGE (u)-[:CAN_EXECUTE]->(p)",
                Map.of("userId", userIds.get(0))
            );
            
            // Agregar al usuario admin a TODOS los grupos
            logger.info("Agregando usuario admin a TODOS los grupos...");
            session.run(
                "MATCH (u:User {id: $userId}), (g:Group) " +
                "MERGE (u)-[:MEMBER_OF]->(g)",
                Map.of("userId", userIds.get(0))
            );
            
            logger.info("✓ Datos de Neo4j insertados");
            logger.info("✓ Usuario admin configurado con TODOS los permisos, roles y grupos");
        }
        
        logger.info("✅ Neo4j seeding completado");
    }
    
    /**
     * Seed de datos en Cassandra
     */
    private void seedCassandraData() {
        logger.info("📈 Seeding Cassandra...");
        
        seedMeasurements();
        
        logger.info("✅ Cassandra seeding completado");
    }
    
    private void seedMeasurements() {
        logger.info("Insertando mediciones...");
        
        try {
            PreparedStatement insertStmt = cassandraSession.prepare(
                "INSERT INTO measurements_by_sensor_day " +
                "(sensor_id, day, ts, temperature, humidity, type, city, country) " +
                "VALUES (?, ?, now(), ?, ?, ?, ?, ?)"
            );
            
            Random random = new Random();
            LocalDate today = LocalDate.now();
            int measurementsInserted = 0;
            
            // Generar mediciones para los últimos 7 días
            for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                LocalDate day = today.minusDays(dayOffset);
                
                // Para cada sensor
                for (int sensorIndex = 0; sensorIndex < Math.min(sensorIds.size(), 10); sensorIndex++) {
                    String sensorId = sensorIds.get(sensorIndex);
                    String city = getCityForSensor(sensorIndex);
                    String country = cityCountryMap.get(city);
                    
                    BatchStatement batch = BatchStatement.newInstance(DefaultBatchType.UNLOGGED);
                    
                    // Generar 10 mediciones por día por sensor
                    for (int i = 0; i < 10; i++) {
                        double temperature = 18.0 + random.nextDouble() * 12.0; // 18-30°C
                        double humidity = 40.0 + random.nextDouble() * 40.0;    // 40-80%
                        
                        batch = batch.add(insertStmt.bind(
                            UUID.fromString(sensorId),
                            day,
                            temperature,
                            humidity,
                            "temperature_humidity",
                            city,
                            country
                        ));
                    }
                    
                    cassandraSession.execute(batch);
                    measurementsInserted += 10;
                }
            }
            
            logger.info("✓ {} mediciones insertadas", measurementsInserted);
            
        } catch (Exception e) {
            logger.error("Error insertando mediciones", e);
        }
    }
    
    private String getCityForSensor(int sensorIndex) {
        String[] cities = {"Buenos Aires", "Córdoba", "Rosario", "Mendoza", "La Plata"};
        return cities[sensorIndex % cities.length];
    }
    
    private void logSeedingSummary() {
        logger.info("📊 === Resumen del Seeding ===");
        logger.info("   Usuarios: {}", userIds.size());
        logger.info("   Sensores: {}", sensorIds.size());
        logger.info("   Ciudades: {}", cityCountryMap.size());
        logger.info("   Roles: 4");
        logger.info("   Procesos: 4");
        logger.info("   Mediciones: ~{} (últimos 7 días)", sensorIds.size() * 70);
    }
}


