package com.bd2.app.migrations;

import com.bd2.app.database.CassandraConnectionManager;
import com.bd2.app.database.MongoConnectionManager;
import com.bd2.app.database.Neo4jConnectionManager;
import com.bd2.app.migrations.cassandra.CassandraMigrations;
import com.bd2.app.migrations.mongodb.MongoMigrations;
import com.bd2.app.migrations.neo4j.Neo4jMigrations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejecutor principal de migraciones para todas las bases de datos
 */
public class MigrationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationRunner.class);
    
    /**
     * Ejecuta todas las migraciones de todas las bases de datos
     */
    public static void runAllMigrations() {
        logger.info("🚀 Iniciando migraciones de persistencia políglota...");
        
        boolean mongoSuccess = false;
        boolean cassandraSuccess = false;
        boolean neo4jSuccess = false;
        
        // MongoDB Migrations
        try {
            logger.info("📊 Ejecutando migraciones MongoDB...");
            MongoConnectionManager mongoManager = MongoConnectionManager.getInstance();
            MongoMigrations mongoMigrations = new MongoMigrations(mongoManager.getDatabase());
            mongoMigrations.runAllMigrations();
            mongoMigrations.insertSampleData();
            mongoSuccess = true;
            logger.info("✅ MongoDB migraciones completadas");
        } catch (Exception e) {
            logger.error("❌ Error en migraciones MongoDB: {}", e.getMessage());
        }
        
        // Cassandra Migrations
        try {
            logger.info("📈 Ejecutando migraciones Cassandra...");
            CassandraConnectionManager cassandraManager = CassandraConnectionManager.getInstance();
            CassandraMigrations cassandraMigrations = new CassandraMigrations(cassandraManager.getSession());
            cassandraMigrations.runAllMigrations();
            cassandraMigrations.insertSampleData();
            cassandraSuccess = true;
            logger.info("✅ Cassandra migraciones completadas");
        } catch (Exception e) {
            logger.error("❌ Error en migraciones Cassandra: {}", e.getMessage());
        }
        
        // Neo4j Migrations
        try {
            logger.info("🕸️ Ejecutando migraciones Neo4j...");
            Neo4jConnectionManager neo4jManager = Neo4jConnectionManager.getInstance();
            Neo4jMigrations neo4jMigrations = new Neo4jMigrations(neo4jManager.getDriver());
            neo4jMigrations.runAllMigrations();
            neo4jMigrations.insertSampleData();
            neo4jSuccess = true;
            logger.info("✅ Neo4j migraciones completadas");
        } catch (Exception e) {
            logger.error("❌ Error en migraciones Neo4j: {}", e.getMessage());
        }
        
        // Resumen final
        logger.info("📋 Resumen de migraciones:");
        logger.info("   MongoDB: {}", mongoSuccess ? "✅ Exitoso" : "❌ Falló");
        logger.info("   Cassandra: {}", cassandraSuccess ? "✅ Exitoso" : "❌ Falló");
        logger.info("   Neo4j: {}", neo4jSuccess ? "✅ Exitoso" : "❌ Falló");
        
        int successCount = (mongoSuccess ? 1 : 0) + (cassandraSuccess ? 1 : 0) + (neo4jSuccess ? 1 : 0);
        
        if (successCount == 3) {
            logger.info("🎉 Todas las migraciones completadas exitosamente!");
        } else if (successCount > 0) {
            logger.warn("⚠️ Migraciones parcialmente completadas ({}/3)", successCount);
        } else {
            logger.error("💥 Todas las migraciones fallaron");
        }
    }
    
    /**
     * Ejecuta migraciones solo para MongoDB
     */
    public static void runMongoMigrations() {
        logger.info("📊 Ejecutando solo migraciones MongoDB...");
        try {
            MongoConnectionManager mongoManager = MongoConnectionManager.getInstance();
            MongoMigrations mongoMigrations = new MongoMigrations(mongoManager.getDatabase());
            mongoMigrations.runAllMigrations();
            mongoMigrations.insertSampleData();
            logger.info("✅ MongoDB migraciones completadas");
        } catch (Exception e) {
            logger.error("❌ Error en migraciones MongoDB", e);
            throw new RuntimeException("Fallo en migraciones MongoDB", e);
        }
    }
    
    /**
     * Ejecuta migraciones solo para Cassandra
     */
    public static void runCassandraMigrations() {
        logger.info("📈 Ejecutando solo migraciones Cassandra...");
        try {
            CassandraConnectionManager cassandraManager = CassandraConnectionManager.getInstance();
            CassandraMigrations cassandraMigrations = new CassandraMigrations(cassandraManager.getSession());
            cassandraMigrations.runAllMigrations();
            cassandraMigrations.insertSampleData();
            logger.info("✅ Cassandra migraciones completadas");
        } catch (Exception e) {
            logger.error("❌ Error en migraciones Cassandra", e);
            throw new RuntimeException("Fallo en migraciones Cassandra", e);
        }
    }
    
    /**
     * Ejecuta migraciones solo para Neo4j
     */
    public static void runNeo4jMigrations() {
        logger.info("🕸️ Ejecutando solo migraciones Neo4j...");
        try {
            Neo4jConnectionManager neo4jManager = Neo4jConnectionManager.getInstance();
            Neo4jMigrations neo4jMigrations = new Neo4jMigrations(neo4jManager.getDriver());
            neo4jMigrations.runAllMigrations();
            neo4jMigrations.insertSampleData();
            logger.info("✅ Neo4j migraciones completadas");
        } catch (Exception e) {
            logger.error("❌ Error en migraciones Neo4j", e);
            throw new RuntimeException("Fallo en migraciones Neo4j", e);
        }
    }
    
    /**
     * Método main para ejecutar migraciones independientemente
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            String database = args[0].toLowerCase();
            switch (database) {
                case "mongodb", "mongo" -> runMongoMigrations();
                case "cassandra" -> runCassandraMigrations();
                case "neo4j" -> runNeo4jMigrations();
                case "all" -> runAllMigrations();
                default -> {
                    logger.error("Base de datos no reconocida: {}. Opciones: mongodb, cassandra, neo4j, all", database);
                    System.exit(1);
                }
            }
        } else {
            runAllMigrations();
        }
    }
}
