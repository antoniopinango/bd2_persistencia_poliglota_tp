package com.bd2.app.database;

import com.bd2.app.config.DatabaseConfig;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Manager para conexiones Neo4j con pool de conexiones
 */
public class Neo4jConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(Neo4jConnectionManager.class);
    private static Neo4jConnectionManager instance;
    
    private Driver driver;
    private final DatabaseConfig config;
    
    private Neo4jConnectionManager() {
        this.config = DatabaseConfig.getInstance();
        initializeConnection();
    }
    
    public static synchronized Neo4jConnectionManager getInstance() {
        if (instance == null) {
            instance = new Neo4jConnectionManager();
        }
        return instance;
    }
    
    private void initializeConnection() {
        try {
            logger.info("Inicializando conexión Neo4j...");
            
            // Configurar el driver con pool de conexiones
            Config driverConfig = Config.builder()
                    .withMaxConnectionPoolSize(config.getNeo4jMaxConnectionPoolSize())
                    .withConnectionAcquisitionTimeout(config.getNeo4jConnectionAcquisitionTimeout(), TimeUnit.MILLISECONDS)
                    .withConnectionTimeout(config.getNeo4jConnectionTimeout(), TimeUnit.MILLISECONDS)
                    .withMaxTransactionRetryTime(config.getNeo4jMaxTransactionRetryTime(), TimeUnit.MILLISECONDS)
                    .withConnectionLivenessCheckTimeout(30, TimeUnit.SECONDS)
                    .withLogging(Logging.slf4j())
                    .build();
            
            // Crear driver con autenticación
            this.driver = GraphDatabase.driver(
                config.getNeo4jUri(),
                AuthTokens.basic(config.getNeo4jUsername(), config.getNeo4jPassword()),
                driverConfig
            );
            
            // Verificar conexión
            verifyConnectivity();
            
            logger.info("Conexión Neo4j establecida exitosamente");
            logger.info("URI: {}", config.getNeo4jUri());
            logger.info("Usuario: {}", config.getNeo4jUsername());
            logger.info("Base de datos: {}", config.getNeo4jDatabase());
            logger.info("Pool configurado - Max connections: {}", config.getNeo4jMaxConnectionPoolSize());
            
        } catch (Exception e) {
            logger.error("Error inicializando conexión Neo4j", e);
            throw new RuntimeException("No se pudo conectar a Neo4j", e);
        }
    }
    
    private void verifyConnectivity() {
        try (Session session = driver.session(SessionConfig.forDatabase(config.getNeo4jDatabase()))) {
            Result result = session.run("RETURN 'Conexión exitosa' AS message");
            if (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                logger.debug("Verificación Neo4j: {}", record.get("message").asString());
            }
        }
    }
    
    /**
     * Obtiene el driver Neo4j
     */
    public Driver getDriver() {
        if (driver == null) {
            throw new IllegalStateException("Neo4j driver no está inicializado");
        }
        return driver;
    }
    
    /**
     * Crea una nueva sesión Neo4j
     */
    public Session createSession() {
        return driver.session(SessionConfig.forDatabase(config.getNeo4jDatabase()));
    }
    
    /**
     * Crea una nueva sesión con configuración específica
     */
    public Session createSession(AccessMode accessMode) {
        return driver.session(SessionConfig.builder()
                .withDatabase(config.getNeo4jDatabase())
                .withDefaultAccessMode(accessMode)
                .build());
    }
    
    /**
     * Ejecuta una consulta de solo lectura
     */
    public Result executeRead(String query) {
        try (Session session = createSession(AccessMode.READ)) {
            return session.run(query);
        }
    }
    
    /**
     * Ejecuta una consulta de solo lectura con parámetros
     */
    public Result executeRead(String query, Value parameters) {
        try (Session session = createSession(AccessMode.READ)) {
            return session.run(query, parameters);
        }
    }
    
    /**
     * Ejecuta una consulta de escritura
     */
    public Result executeWrite(String query) {
        try (Session session = createSession(AccessMode.WRITE)) {
            return session.run(query);
        }
    }
    
    /**
     * Ejecuta una consulta de escritura con parámetros
     */
    public Result executeWrite(String query, Value parameters) {
        try (Session session = createSession(AccessMode.WRITE)) {
            return session.run(query, parameters);
        }
    }
    
    /**
     * Ejecuta una transacción de escritura
     */
    public <T> T executeWriteTransaction(TransactionCallback<T> work) {
        try (Session session = createSession(AccessMode.WRITE)) {
            return session.executeWrite(work);
        }
    }
    
    /**
     * Ejecuta una transacción de lectura
     */
    public <T> T executeReadTransaction(TransactionCallback<T> work) {
        try (Session session = createSession(AccessMode.READ)) {
            return session.executeRead(work);
        }
    }
    
    /**
     * Verifica si la conexión está activa
     */
    public boolean isConnected() {
        try {
            verifyConnectivity();
            return true;
        } catch (Exception e) {
            logger.warn("Verificación de conexión Neo4j falló", e);
            return false;
        }
    }
    
    /**
     * Obtiene información del servidor Neo4j
     */
    public void logServerInfo() {
        try (Session session = createSession()) {
            Result result = session.run("CALL dbms.components() YIELD name, versions, edition");
            
            logger.info("=== Información del Servidor Neo4j ===");
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                logger.info("Componente: {} - Versión: {} - Edición: {}", 
                           record.get("name").asString(),
                           record.get("versions").asList(),
                           record.get("edition").asString());
            }
            
            // Información de la base de datos
            Result dbResult = session.run("CALL db.info()");
            if (dbResult.hasNext()) {
                org.neo4j.driver.Record dbRecord = dbResult.next();
                logger.info("Base de datos: {}", dbRecord.get("name").asString());
            }
            
        } catch (Exception e) {
            logger.error("Error obteniendo información del servidor Neo4j", e);
        }
    }
    
    /**
     * Obtiene estadísticas del pool de conexiones
     */
    public void logConnectionPoolStats() {
        try {
            // Neo4j driver no expone métricas detalladas del pool por defecto
            // Verificamos el estado básico
            if (isConnected()) {
                logger.info("Neo4j Pool Status: ACTIVE - Max connections: {}", 
                           config.getNeo4jMaxConnectionPoolSize());
                
                // Información básica de conectividad
                try (Session session = createSession()) {
                    Result result = session.run("CALL dbms.cluster.overview()");
                    if (result.hasNext()) {
                        logger.info("Cluster mode detectado");
                        while (result.hasNext()) {
                            org.neo4j.driver.Record record = result.next();
                            logger.info("Servidor: {} - Rol: {} - Estado: {}", 
                                       record.get("addresses").asList(),
                                       record.get("role").asString(),
                                       record.get("database").asString());
                        }
                    }
                } catch (Neo4jException e) {
                    // No es un cluster, modo standalone
                    logger.info("Modo standalone detectado");
                }
            } else {
                logger.warn("Neo4j Pool Status: INACTIVE");
            }
        } catch (Exception e) {
            logger.error("Error obteniendo estadísticas del pool Neo4j", e);
        }
    }
    
    /**
     * Ejecuta consulta con reintentos
     */
    public Result executeWithRetry(String query, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try (Session session = createSession()) {
                return session.run(query);
            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (attempts < maxRetries) {
                    logger.warn("Intento {} falló, reintentando... Error: {}", attempts, e.getMessage());
                    try {
                        Thread.sleep(config.getRetryBackoffDelay() * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrumpido durante reintento", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("Falló después de " + maxRetries + " intentos", lastException);
    }
    
    /**
     * Reconectar en caso de pérdida de conexión
     */
    public synchronized void reconnect() {
        logger.info("Intentando reconectar Neo4j...");
        try {
            close();
            initializeConnection();
            logger.info("Reconexión Neo4j exitosa");
        } catch (Exception e) {
            logger.error("Error en reconexión Neo4j", e);
            throw new RuntimeException("No se pudo reconectar a Neo4j", e);
        }
    }
    
    /**
     * Cierra el driver Neo4j
     */
    public void close() {
        if (driver != null) {
            try {
                logger.info("Cerrando driver Neo4j...");
                driver.close();
                driver = null;
                logger.info("Driver Neo4j cerrado");
            } catch (Exception e) {
                logger.error("Error cerrando driver Neo4j", e);
            }
        }
    }
    
    /**
     * Shutdown hook para cerrar conexiones al terminar la aplicación
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Ejecutando shutdown hook para Neo4j...");
            close();
        }));
    }
}
