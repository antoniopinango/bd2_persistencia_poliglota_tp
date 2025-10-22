package com.bd2.app.database;

import com.bd2.app.config.DatabaseConfig;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Manager para conexiones Cassandra con pool de conexiones
 */
public class CassandraConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(CassandraConnectionManager.class);
    private static CassandraConnectionManager instance;
    
    private CqlSession session;
    private final DatabaseConfig config;
    
    private CassandraConnectionManager() {
        this.config = DatabaseConfig.getInstance();
        initializeConnection();
    }
    
    public static synchronized CassandraConnectionManager getInstance() {
        if (instance == null) {
            instance = new CassandraConnectionManager();
        }
        return instance;
    }
    
    private void initializeConnection() {
        try {
            logger.info("Inicializando conexión Cassandra...");
            
            // Configurar el driver programáticamente
            ProgrammaticDriverConfigLoaderBuilder configBuilder = DriverConfigLoader.programmaticBuilder();
            
            // Configuración del pool de conexiones
            configBuilder
                .withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, config.getCassandraCoreConnections())
                .withInt(DefaultDriverOption.CONNECTION_POOL_REMOTE_SIZE, config.getCassandraCoreConnections())
                .withInt(DefaultDriverOption.CONNECTION_MAX_REQUESTS, config.getCassandraMaxRequestsPerConnection())
                .withDuration(DefaultDriverOption.HEARTBEAT_INTERVAL, Duration.ofMillis(config.getCassandraHeartbeatInterval()))
                .withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofMillis(30000))
                .withDuration(DefaultDriverOption.CONNECTION_SET_KEYSPACE_TIMEOUT, Duration.ofMillis(30000));
            
            // Configuración de timeouts
            configBuilder
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofMillis(config.getQueryTimeout()))
                .withDuration(DefaultDriverOption.CONNECTION_CONNECT_TIMEOUT, Duration.ofMillis(30000));
            
            // Configuración de reconexión
            configBuilder
                .withString(DefaultDriverOption.RECONNECTION_POLICY_CLASS, "ExponentialReconnectionPolicy")
                .withDuration(DefaultDriverOption.RECONNECTION_BASE_DELAY, Duration.ofSeconds(1))
                .withDuration(DefaultDriverOption.RECONNECTION_MAX_DELAY, Duration.ofSeconds(60));
            
            DriverConfigLoader configLoader = configBuilder.build();
            
            // Construir sesión (sin keyspace para permitir creación)
            CqlSessionBuilder sessionBuilder = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(config.getCassandraHost(), config.getCassandraPort()))
                .withLocalDatacenter(config.getCassandraDatacenter())
                .withConfigLoader(configLoader);
            
            // Agregar credenciales si están configuradas
            String username = config.getCassandraUsername();
            String password = config.getCassandraPassword();
            
            if (username != null && !username.trim().isEmpty() && 
                password != null && !password.trim().isEmpty()) {
                sessionBuilder.withAuthCredentials(username, password);
                logger.info("Configurando autenticación Cassandra para usuario: {}", username);
            }
            
            // Crear sesión
            this.session = sessionBuilder.build();
            
            // Verificar conexión
            Metadata metadata = session.getMetadata();
            logger.info("Conexión Cassandra establecida exitosamente");
            logger.info("Cluster: {}", metadata.getClusterName().orElse("Unknown"));
            logger.info("Keyspace: {}", config.getCassandraKeyspace());
            logger.info("Datacenter: {}", config.getCassandraDatacenter());
            logger.info("Nodos conectados: {}", metadata.getNodes().size());
            
            // Log de configuración del pool
            logger.info("Pool configurado - Core connections: {}, Max requests per connection: {}", 
                       config.getCassandraCoreConnections(), 
                       config.getCassandraMaxRequestsPerConnection());
            
        } catch (Exception e) {
            logger.error("Error inicializando conexión Cassandra", e);
            throw new RuntimeException("No se pudo conectar a Cassandra", e);
        }
    }
    
    /**
     * Obtiene la sesión CQL
     */
    public CqlSession getSession() {
        if (session == null || session.isClosed()) {
            throw new IllegalStateException("Cassandra session no está disponible");
        }
        return session;
    }
    
    /**
     * Verifica si la conexión está activa
     */
    public boolean isConnected() {
        try {
            if (session != null && !session.isClosed()) {
                // Ejecutar una consulta simple para verificar conectividad
                session.execute("SELECT release_version FROM system.local");
                return true;
            }
        } catch (Exception e) {
            logger.warn("Verificación de conexión Cassandra falló", e);
        }
        return false;
    }
    
    /**
     * Obtiene información del cluster
     */
    public void logClusterInfo() {
        try {
            if (session != null && !session.isClosed()) {
                Metadata metadata = session.getMetadata();
                
                logger.info("=== Información del Cluster Cassandra ===");
                logger.info("Cluster: {}", metadata.getClusterName().orElse("Unknown"));
                logger.info("Keyspace: {}", config.getCassandraKeyspace());
                logger.info("Nodos en el cluster: {}", metadata.getNodes().size());
                
                metadata.getNodes().forEach((uuid, node) -> {
                    logger.info("Nodo: {} - Datacenter: {} - Estado: {}", 
                               node.getEndPoint(), 
                               node.getDatacenter(),
                               node.getState());
                });
                
                // Información del keyspace
                metadata.getKeyspace(config.getCassandraKeyspace()).ifPresent(keyspace -> {
                    logger.info("Tablas en keyspace: {}", keyspace.getTables().size());
                    keyspace.getTables().forEach((name, table) -> {
                        logger.debug("Tabla: {} - Columnas: {}", name, table.getColumns().size());
                    });
                });
            }
        } catch (Exception e) {
            logger.error("Error obteniendo información del cluster", e);
        }
    }
    
    /**
     * Obtiene estadísticas de la sesión
     */
    public void logSessionStats() {
        try {
            if (session != null && !session.isClosed()) {
                // Las métricas detalladas requieren configuración adicional
                // Por ahora, log básico del estado
                logger.info("Cassandra Session Status: ACTIVE");
                logger.info("Keyspace actual: {}", session.getKeyspace().map(k -> k.asInternal()).orElse("None"));
                
                // Información básica del metadata
                Metadata metadata = session.getMetadata();
                logger.info("Nodos disponibles: {}", metadata.getNodes().size());
            } else {
                logger.warn("Cassandra Session Status: INACTIVE");
            }
        } catch (Exception e) {
            logger.error("Error obteniendo estadísticas de sesión", e);
        }
    }
    
    /**
     * Reconectar en caso de pérdida de conexión
     */
    public synchronized void reconnect() {
        logger.info("Intentando reconectar Cassandra...");
        try {
            close();
            initializeConnection();
            logger.info("Reconexión Cassandra exitosa");
        } catch (Exception e) {
            logger.error("Error en reconexión Cassandra", e);
            throw new RuntimeException("No se pudo reconectar a Cassandra", e);
        }
    }
    
    /**
     * Ejecuta una consulta con manejo de errores
     */
    public void executeWithRetry(String query, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            try {
                session.execute(query);
                return; // Éxito
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
     * Cierra la sesión Cassandra
     */
    public void close() {
        if (session != null && !session.isClosed()) {
            try {
                logger.info("Cerrando sesión Cassandra...");
                session.close();
                session = null;
                logger.info("Sesión Cassandra cerrada");
            } catch (Exception e) {
                logger.error("Error cerrando sesión Cassandra", e);
            }
        }
    }
    
    /**
     * Shutdown hook para cerrar conexiones al terminar la aplicación
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Ejecutando shutdown hook para Cassandra...");
            close();
        }));
    }
}
