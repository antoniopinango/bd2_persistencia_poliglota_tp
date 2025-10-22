package com.bd2.app.database;

import com.bd2.app.config.DatabaseConfig;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ConnectionPoolSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Manager para conexiones MongoDB con pool de conexiones
 */
public class MongoConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoConnectionManager.class);
    private static MongoConnectionManager instance;
    
    private MongoClient mongoClient;
    private MongoDatabase database;
    private final DatabaseConfig config;
    
    private MongoConnectionManager() {
        this.config = DatabaseConfig.getInstance();
        initializeConnection();
    }
    
    public static synchronized MongoConnectionManager getInstance() {
        if (instance == null) {
            instance = new MongoConnectionManager();
        }
        return instance;
    }
    
    private void initializeConnection() {
        try {
            logger.info("Inicializando conexión MongoDB...");
            
            // Configurar pool de conexiones
            ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
                    .minSize(config.getMongoPoolMinSize())
                    .maxSize(config.getMongoPoolMaxSize())
                    .maxWaitTime(config.getMongoMaxWaitTime(), TimeUnit.MILLISECONDS)
                    .maxConnectionIdleTime(config.getMongoMaxConnectionIdleTime(), TimeUnit.MILLISECONDS)
                    .maxConnectionLifeTime(config.getMongoMaxConnectionLifeTime(), TimeUnit.MILLISECONDS)
                    .build();
            
            // Construir URI de conexión
            String connectionUri = buildConnectionUri();
            ConnectionString connectionString = new ConnectionString(connectionUri);
            
            // Configurar cliente MongoDB
            MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings));
            
            // Agregar credenciales si están configuradas
            String username = config.getMongoUsername();
            String password = config.getMongoPassword();
            
            if (username != null && !username.trim().isEmpty() && 
                password != null && !password.trim().isEmpty()) {
                
                MongoCredential credential = MongoCredential.createCredential(
                    username, 
                    config.getMongoAuthDatabase(), 
                    password.toCharArray()
                );
                settingsBuilder.credential(credential);
                logger.info("Configurando autenticación MongoDB para usuario: {}", username);
            }
            
            MongoClientSettings settings = settingsBuilder.build();
            
            // Crear cliente y obtener base de datos
            this.mongoClient = MongoClients.create(settings);
            this.database = mongoClient.getDatabase(config.getMongoDatabase());
            
            // Verificar conexión
            database.runCommand(new org.bson.Document("ping", 1));
            
            logger.info("Conexión MongoDB establecida exitosamente");
            logger.info("Base de datos: {}", config.getMongoDatabase());
            logger.info("Pool configurado - Min: {}, Max: {}", 
                       config.getMongoPoolMinSize(), config.getMongoPoolMaxSize());
            
        } catch (Exception e) {
            logger.error("Error inicializando conexión MongoDB", e);
            throw new RuntimeException("No se pudo conectar a MongoDB", e);
        }
    }
    
    private String buildConnectionUri() {
        StringBuilder uri = new StringBuilder("mongodb://");
        
        String username = config.getMongoUsername();
        String password = config.getMongoPassword();
        
        if (username != null && !username.trim().isEmpty() && 
            password != null && !password.trim().isEmpty()) {
            uri.append(username).append(":").append(password).append("@");
        }
        
        uri.append(config.getMongoHost())
           .append(":")
           .append(config.getMongoPort())
           .append("/")
           .append(config.getMongoDatabase());
        
        // Agregar parámetros adicionales
        uri.append("?authSource=").append(config.getMongoAuthDatabase());
        
        return uri.toString();
    }
    
    /**
     * Obtiene la base de datos MongoDB
     */
    public MongoDatabase getDatabase() {
        if (database == null) {
            throw new IllegalStateException("MongoDB no está inicializado");
        }
        return database;
    }
    
    /**
     * Obtiene el cliente MongoDB
     */
    public MongoClient getClient() {
        if (mongoClient == null) {
            throw new IllegalStateException("MongoDB cliente no está inicializado");
        }
        return mongoClient;
    }
    
    /**
     * Verifica si la conexión está activa
     */
    public boolean isConnected() {
        try {
            if (database != null) {
                database.runCommand(new org.bson.Document("ping", 1));
                return true;
            }
        } catch (Exception e) {
            logger.warn("Verificación de conexión MongoDB falló", e);
        }
        return false;
    }
    
    /**
     * Obtiene estadísticas del pool de conexiones
     */
    public void logConnectionPoolStats() {
        try {
            // MongoDB driver no expone estadísticas detalladas del pool directamente
            // Pero podemos verificar el estado de la conexión
            if (isConnected()) {
                logger.info("MongoDB Pool Status: ACTIVE - Database: {}", config.getMongoDatabase());
            } else {
                logger.warn("MongoDB Pool Status: INACTIVE");
            }
        } catch (Exception e) {
            logger.error("Error obteniendo estadísticas del pool MongoDB", e);
        }
    }
    
    /**
     * Reconectar en caso de pérdida de conexión
     */
    public synchronized void reconnect() {
        logger.info("Intentando reconectar MongoDB...");
        try {
            close();
            initializeConnection();
            logger.info("Reconexión MongoDB exitosa");
        } catch (Exception e) {
            logger.error("Error en reconexión MongoDB", e);
            throw new RuntimeException("No se pudo reconectar a MongoDB", e);
        }
    }
    
    /**
     * Cierra la conexión MongoDB
     */
    public void close() {
        if (mongoClient != null) {
            try {
                logger.info("Cerrando conexión MongoDB...");
                mongoClient.close();
                mongoClient = null;
                database = null;
                logger.info("Conexión MongoDB cerrada");
            } catch (Exception e) {
                logger.error("Error cerrando conexión MongoDB", e);
            }
        }
    }
    
    /**
     * Shutdown hook para cerrar conexiones al terminar la aplicación
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Ejecutando shutdown hook para MongoDB...");
            close();
        }));
    }
}
