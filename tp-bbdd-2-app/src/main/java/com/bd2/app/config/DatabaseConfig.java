package com.bd2.app.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuración centralizada para las bases de datos
 * Carga la configuración desde application.properties
 */
public class DatabaseConfig {
    
    private static DatabaseConfig instance;
    private final Properties properties;
    
    private DatabaseConfig() {
        this.properties = new Properties();
        loadProperties();
    }
    
    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }
    
    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            
            if (input == null) {
                throw new RuntimeException("No se pudo encontrar application.properties");
            }
            
            properties.load(input);
            
        } catch (IOException e) {
            throw new RuntimeException("Error cargando configuración", e);
        }
    }
    
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
    
    public long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }
    
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    // ========================================================================
    // CONFIGURACIÓN MONGODB
    // ========================================================================
    
    public String getMongoHost() {
        return getProperty("mongodb.host", "localhost");
    }
    
    public int getMongoPort() {
        return getIntProperty("mongodb.port", 27017);
    }
    
    public String getMongoDatabase() {
        return getProperty("mongodb.database", "tp_sensores");
    }
    
    public String getMongoUsername() {
        return getProperty("mongodb.username", "");
    }
    
    public String getMongoPassword() {
        return getProperty("mongodb.password", "");
    }
    
    public String getMongoAuthDatabase() {
        return getProperty("mongodb.authDatabase", "admin");
    }
    
    public int getMongoPoolMinSize() {
        return getIntProperty("mongodb.pool.minSize", 5);
    }
    
    public int getMongoPoolMaxSize() {
        return getIntProperty("mongodb.pool.maxSize", 20);
    }
    
    public long getMongoMaxWaitTime() {
        return getLongProperty("mongodb.pool.maxWaitTime", 30000);
    }
    
    public long getMongoMaxConnectionIdleTime() {
        return getLongProperty("mongodb.pool.maxConnectionIdleTime", 60000);
    }
    
    public long getMongoMaxConnectionLifeTime() {
        return getLongProperty("mongodb.pool.maxConnectionLifeTime", 300000);
    }
    
    // ========================================================================
    // CONFIGURACIÓN CASSANDRA
    // ========================================================================
    
    public String getCassandraHost() {
        return getProperty("cassandra.host", "localhost");
    }
    
    public int getCassandraPort() {
        return getIntProperty("cassandra.port", 9042);
    }
    
    public String getCassandraKeyspace() {
        return getProperty("cassandra.keyspace", "tp_sensores");
    }
    
    public String getCassandraDatacenter() {
        return getProperty("cassandra.datacenter", "datacenter1");
    }
    
    public String getCassandraUsername() {
        return getProperty("cassandra.username", "");
    }
    
    public String getCassandraPassword() {
        return getProperty("cassandra.password", "");
    }
    
    public int getCassandraCoreConnections() {
        return getIntProperty("cassandra.pool.coreConnections", 2);
    }
    
    public int getCassandraMaxConnections() {
        return getIntProperty("cassandra.pool.maxConnections", 8);
    }
    
    public int getCassandraMaxRequestsPerConnection() {
        return getIntProperty("cassandra.pool.maxRequestsPerConnection", 1024);
    }
    
    public long getCassandraHeartbeatInterval() {
        return getLongProperty("cassandra.pool.heartbeatInterval", 30000);
    }
    
    public long getCassandraIdleTimeout() {
        return getLongProperty("cassandra.pool.idleTimeout", 120000);
    }
    
    // ========================================================================
    // CONFIGURACIÓN NEO4J
    // ========================================================================
    
    public String getNeo4jUri() {
        return getProperty("neo4j.uri", "bolt://localhost:7687");
    }
    
    public String getNeo4jUsername() {
        return getProperty("neo4j.username", "neo4j");
    }
    
    public String getNeo4jPassword() {
        return getProperty("neo4j.password", "neo4j");
    }
    
    public String getNeo4jDatabase() {
        return getProperty("neo4j.database", "neo4j");
    }
    
    public int getNeo4jMaxConnectionPoolSize() {
        return getIntProperty("neo4j.pool.maxConnectionPoolSize", 50);
    }
    
    public long getNeo4jConnectionAcquisitionTimeout() {
        return getLongProperty("neo4j.pool.connectionAcquisitionTimeout", 60000);
    }
    
    public long getNeo4jConnectionTimeout() {
        return getLongProperty("neo4j.pool.connectionTimeout", 30000);
    }
    
    public long getNeo4jMaxTransactionRetryTime() {
        return getLongProperty("neo4j.pool.maxTransactionRetryTime", 30000);
    }
    
    // ========================================================================
    // CONFIGURACIÓN GENERAL
    // ========================================================================
    
    public String getAppName() {
        return getProperty("app.name", "TP BBDD 2");
    }
    
    public String getAppVersion() {
        return getProperty("app.version", "1.0.0");
    }
    
    public String getAppEnvironment() {
        return getProperty("app.environment", "development");
    }
    
    public long getQueryTimeout() {
        return getLongProperty("app.timeout.query", 30000);
    }
    
    public long getTransactionTimeout() {
        return getLongProperty("app.timeout.transaction", 60000);
    }
    
    public int getMaxRetryAttempts() {
        return getIntProperty("app.retry.maxAttempts", 3);
    }
    
    public long getRetryBackoffDelay() {
        return getLongProperty("app.retry.backoffDelay", 1000);
    }
}
