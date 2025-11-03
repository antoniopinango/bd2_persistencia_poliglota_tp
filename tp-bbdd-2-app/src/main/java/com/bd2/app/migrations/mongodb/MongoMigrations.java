package com.bd2.app.migrations.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Migraciones para MongoDB - Creación de colecciones, índices y validadores
 */
public class MongoMigrations {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoMigrations.class);
    private final MongoDatabase database;
    
    public MongoMigrations(MongoDatabase database) {
        this.database = database;
    }
    
    /**
     * Ejecuta todas las migraciones de MongoDB
     */
    public void runAllMigrations() {
        logger.info("=== Iniciando migraciones MongoDB ===");
        
        try {
            // Agregar ID incremental (No UUID)
            createUsersCollection();
            createSessionsCollection();
            createRolesCollection();
            createSensorsCollection();
            createProcessesCollection();
            createProcessRequestsCollection();
            createProcessResultsCollection();
            createInvoicesCollection();
            createPaymentsCollection();
            createAccountsCollection();
            createAccountMovementsCollection();
            createAlertsCollection();
            createGroupsMetaCollection();
            
            logger.info("=== Migraciones MongoDB completadas exitosamente ===");
            
        } catch (Exception e) {
            logger.error("Error ejecutando migraciones MongoDB", e);
            throw new RuntimeException("Fallo en migraciones MongoDB", e);
        }
    }
    
    private void createUsersCollection() {
        logger.info("Creando colección 'users' con índices...");
        
        MongoCollection<Document> collection = database.getCollection("users");
        
        // Índices
        collection.createIndex(Indexes.ascending("email"), 
            new IndexOptions().unique(true).name("idx_users_email"));
        collection.createIndex(Indexes.ascending("status"), 
            new IndexOptions().name("idx_users_status"));
        collection.createIndex(Indexes.descending("registeredAt"), 
            new IndexOptions().name("idx_users_registered"));
        
        // Validador JSON Schema
        Document validator = Document.parse("""
            {
                "$jsonSchema": {
                    "bsonType": "object",
                    "required": ["_id", "fullName", "email", "passwordHash", "status", "registeredAt"],
                    "properties": {
                        "_id": {"bsonType": "string"},
                        "fullName": {"bsonType": "string", "minLength": 1},
                        "email": {"bsonType": "string", "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}$"},
                        "passwordHash": {"bsonType": "string", "minLength": 1},
                        "status": {"enum": ["activo", "inactivo"]},
                        "registeredAt": {"bsonType": "date"},
                        "department": {"bsonType": "string"}
                    }
                }
            }
            """); // Revisar department
        
        database.runCommand(new Document("collMod", "users")
            .append("validator", validator));
        
        logger.info("Colección 'users' creada con validador");
    }
    
    private void createSessionsCollection() {
        logger.info("Creando colección 'sessions' con TTL...");
        
        MongoCollection<Document> collection = database.getCollection("sessions");
        
        // Índice TTL para expiración automática
        collection.createIndex(Indexes.ascending("expiresAt"), 
            new IndexOptions().expireAfter(0L, TimeUnit.SECONDS).name("idx_sessions_ttl"));
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.ascending("status")), 
            new IndexOptions().name("idx_sessions_user_status"));

        // Agregar ROLE
        // Fechar y hora de inicio de la sesión y fecha y hora de cierre de la sesión

        logger.info("Colección 'sessions' creada con TTL");
    }
    
    private void createRolesCollection() {
        logger.info("Creando colección 'roles'...");
        
        MongoCollection<Document> collection = database.getCollection("roles");
        
        collection.createIndex(Indexes.ascending("name"), 
            new IndexOptions().unique(true).name("idx_roles_name"));
        // Agregar descripcion. 
        
        logger.info("Colección 'roles' creada");
    }
    
    private void createSensorsCollection() {
        logger.info("Creando colección 'sensors' con índice geoespacial...");
        
        MongoCollection<Document> collection = database.getCollection("sensors");
        
        // Índices
        collection.createIndex(Indexes.ascending("code"), 
            new IndexOptions().unique(true).name("idx_sensors_code"));
        collection.createIndex(Indexes.ascending("state"), 
            new IndexOptions().name("idx_sensors_state"));
        collection.createIndex(Indexes.ascending("city"), 
            new IndexOptions().name("idx_sensors_city"));
        collection.createIndex(Indexes.ascending("country"), 
            new IndexOptions().name("idx_sensors_country"));
        collection.createIndex(Indexes.geo2dsphere("loc"), 
            new IndexOptions().name("idx_sensors_location"));
        
        logger.info("Colección 'sensors' creada con índice geoespacial");
    }
    
    private void createProcessesCollection() {
        logger.info("Creando colección 'processes'...");
        
        MongoCollection<Document> collection = database.getCollection("processes");

        // Agregar description. 
        
        collection.createIndex(Indexes.ascending("name"), 
            new IndexOptions().unique(true).name("idx_processes_name"));
        collection.createIndex(Indexes.ascending("type"), 
            new IndexOptions().name("idx_processes_type"));
        
        logger.info("Colección 'processes' creada");
    }
    
    private void createProcessRequestsCollection() {
        logger.info("Creando colección 'process_requests'...");
        
        MongoCollection<Document> collection = database.getCollection("process_requests");


        // Agregar fecha de solicitud y estado debe ser un booleano. (true: completado, false: cancelado)
        
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.descending("requestedAt")), 
            new IndexOptions().name("idx_requests_user_date"));
        collection.createIndex(Indexes.ascending("status"), 
            new IndexOptions().name("idx_requests_status"));
        collection.createIndex(Indexes.ascending("processId"), 
            new IndexOptions().name("idx_requests_process"));
        
        logger.info("Colección 'process_requests' creada");
    }
    
    private void createProcessResultsCollection() {
        logger.info("Creando colección 'process_results'...");
        
        MongoCollection<Document> collection = database.getCollection("process_results");
        
        collection.createIndex(Indexes.ascending("requestId"), 
            new IndexOptions().unique(true).name("idx_results_request"));
        collection.createIndex(Indexes.descending("generatedAt"), 
            new IndexOptions().name("idx_results_generated"));
        
        logger.info("Colección 'process_results' creada");
    }
    
    private void createInvoicesCollection() {
        logger.info("Creando colección 'invoices'...");

        // Cambiar invoice por bill
        
        
        MongoCollection<Document> collection = database.getCollection("invoices");
        
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.descending("issuedAt")), 
            new IndexOptions().name("idx_invoices_user_date"));
        collection.createIndex(Indexes.ascending("status"), 
            new IndexOptions().name("idx_invoices_status"));
        
        logger.info("Colección 'invoices' creada");
    }
    
    private void createPaymentsCollection() {
        logger.info("Creando colección 'payments'...");
        
        MongoCollection<Document> collection = database.getCollection("payments");
        
        collection.createIndex(Indexes.ascending("invoiceId"), 
            new IndexOptions().name("idx_payments_invoice"));
        collection.createIndex(Indexes.descending("paidAt"), 
            new IndexOptions().name("idx_payments_date"));
        
        logger.info("Colección 'payments' creada");
    }
    
    private void createAccountsCollection() {
        logger.info("Creando colección 'accounts'...");
        
        MongoCollection<Document> collection = database.getCollection("accounts");
        
        collection.createIndex(Indexes.ascending("userId"), 
            new IndexOptions().unique(true).name("idx_accounts_user"));
        
        logger.info("Colección 'accounts' creada");
    }
    
    private void createAccountMovementsCollection() {
        logger.info("Creando colección 'account_movements'...");
        
        MongoCollection<Document> collection = database.getCollection("account_movements");
        
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("accountId"), Indexes.descending("ts")), 
            new IndexOptions().name("idx_movements_account_date"));
        
        logger.info("Colección 'account_movements' creada");
    }
    
    private void createAlertsCollection() {
        logger.info("Creando colección 'alerts'...");
        
        MongoCollection<Document> collection = database.getCollection("alerts");
        
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("status"), Indexes.descending("openedAt")), 
            new IndexOptions().name("idx_alerts_status_date"));
        collection.createIndex(Indexes.compoundIndex(Indexes.ascending("sensorId"), Indexes.ascending("status")), 
            new IndexOptions().name("idx_alerts_sensor_status"));
        
        logger.info("Colección 'alerts' creada");
    }
    
    private void createGroupsMetaCollection() {
        logger.info("Creando colección 'groups_meta'...");
        
        MongoCollection<Document> collection = database.getCollection("groups_meta");
        
        collection.createIndex(Indexes.ascending("name"), 
            new IndexOptions().unique(true).name("idx_groups_name"));
        
        logger.info("Colección 'groups_meta' creada");
    }
    
    /**
     * Inserta datos de prueba básicos
     */
    public void insertSampleData() {
        logger.info("Insertando datos de prueba en MongoDB...");
        
        try {
            // Roles básicos
            MongoCollection<Document> roles = database.getCollection("roles");
            roles.insertMany(Arrays.asList(
                new Document("_id", "role_admin").append("name", "admin").append("description", "Administrador del sistema"),
                new Document("_id", "role_user").append("name", "usuario").append("description", "Usuario estándar"),
                new Document("_id", "role_tech").append("name", "tecnico").append("description", "Técnico de campo")
            ));
            
            // Procesos básicos
            MongoCollection<Document> processes = database.getCollection("processes");
            processes.insertMany(Arrays.asList(
                new Document("_id", "proc_maxmin").append("name", "Reporte Max/Min").append("type", "reporte").append("baseCost", 10.0),
                new Document("_id", "proc_avg").append("name", "Reporte Promedios").append("type", "reporte").append("baseCost", 15.0),
                new Document("_id", "proc_alerts").append("name", "Alertas por Rango").append("type", "consulta").append("baseCost", 5.0)
            ));
            
            logger.info("Datos de prueba insertados en MongoDB");
            
        } catch (Exception e) {
            logger.warn("Error insertando datos de prueba: {}", e.getMessage());
        }
    }
}
