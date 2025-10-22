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
            // Verificar si ya hay datos
            if (dataAlreadyExists()) {
                logger.info("⚠️  Los datos ya existen. Saltando seeding.");
                return;
            }
            
            // Seed en orden de dependencias
            seedMongoData();
            seedNeo4jData();
            seedCassandraData();
            
            logger.info("✅ Seeding completado exitosamente!");
            logSeedingSummary();
            
        } catch (Exception e) {
            logger.error("❌ Error durante el seeding", e);
            throw new RuntimeException("Fallo en el seeding de datos", e);
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
        
        for (String[] userData : usersData) {
            String userId = UUID.randomUUID().toString();
            userIds.add(userId);
            
            Document user = new Document("_id", userId)
                .append("firstName", userData[0])
                .append("lastName", userData[1])
                .append("email", userData[2])
                .append("department", userData[3])
                .append("roleId", userData[4])
                .append("active", true)
                .append("createdAt", new Date())
                .append("lastLogin", new Date());
            
            userDocs.add(user);
        }
        
        try {
            users.insertMany(userDocs);
            logger.info("✓ {} usuarios insertados", userDocs.size());
        } catch (Exception e) {
            logger.warn("Error insertando usuarios: {}", e.getMessage());
        }
    }
    
    private void seedSensors() {
        logger.info("Insertando sensores...");
        
        MongoCollection<Document> sensors = mongoDb.getCollection("sensors");
        
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
        
        for (String[] sensorData : sensorsData) {
            String sensorId = UUID.randomUUID().toString();
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
            // Crear usuarios
            for (int i = 0; i < userIds.size(); i++) {
                session.run(
                    "MERGE (u:User {id: $userId}) " +
                    "SET u.email = $email, u.active = true",
                    Map.of("userId", userIds.get(i), "email", "user" + i + "@uade.edu.ar")
                );
            }
            
            // Crear roles
            session.run("MERGE (r:Role {id: 'role_admin', name: 'Administrador'})");
            session.run("MERGE (r:Role {id: 'role_operator', name: 'Operador'})");
            session.run("MERGE (r:Role {id: 'role_analyst', name: 'Analista'})");
            session.run("MERGE (r:Role {id: 'role_technician', name: 'Técnico'})");
            
            // Asignar roles a usuarios
            String[] roleAssignments = {"role_admin", "role_operator", "role_analyst", "role_technician", 
                                       "role_operator", "role_analyst", "role_operator", "role_analyst", 
                                       "role_technician", "role_operator"};
            
            for (int i = 0; i < Math.min(userIds.size(), roleAssignments.length); i++) {
                session.run(
                    "MATCH (u:User {id: $userId}), (r:Role {id: $roleId}) " +
                    "MERGE (u)-[:HAS_ROLE]->(r)",
                    Map.of("userId", userIds.get(i), "roleId", roleAssignments[i])
                );
            }
            
            // Crear grupos
            session.run("MERGE (g:Group {id: 'group_engineering', name: 'Ingeniería'})");
            session.run("MERGE (g:Group {id: 'group_science', name: 'Ciencias'})");
            session.run("MERGE (g:Group {id: 'group_architecture', name: 'Arquitectura'})");
            
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
            
            // Crear ubicaciones
            for (String city : cityCountryMap.keySet()) {
                String country = cityCountryMap.get(city);
                session.run(
                    "MERGE (c:City {name: $city}) " +
                    "MERGE (co:Country {name: $country}) " +
                    "MERGE (c)-[:LOCATED_IN]->(co)",
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
                    "MERGE (s)-[:LOCATED_IN]->(c)",
                    Map.of("sensorId", sensorId, "city", city)
                );
                cityIndex++;
            }
            
            // Crear permisos
            session.run("MERGE (p:Permission {id: 'perm_read', name: 'Lectura'})");
            session.run("MERGE (p:Permission {id: 'perm_write', name: 'Escritura'})");
            session.run("MERGE (p:Permission {id: 'perm_delete', name: 'Eliminación'})");
            session.run("MERGE (p:Permission {id: 'perm_admin', name: 'Administración'})");
            
            // Asignar permisos a roles
            session.run(
                "MATCH (r:Role {id: 'role_admin'}), (p:Permission) " +
                "MERGE (r)-[:HAS_PERMISSION]->(p)"
            );
            
            session.run(
                "MATCH (r:Role {id: 'role_operator'}), (p:Permission) " +
                "WHERE p.id IN ['perm_read', 'perm_write'] " +
                "MERGE (r)-[:HAS_PERMISSION]->(p)"
            );
            
            session.run(
                "MATCH (r:Role {id: 'role_analyst'}), (p:Permission {id: 'perm_read'}) " +
                "MERGE (r)-[:HAS_PERMISSION]->(p)"
            );
            
            logger.info("✓ Datos de Neo4j insertados");
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


