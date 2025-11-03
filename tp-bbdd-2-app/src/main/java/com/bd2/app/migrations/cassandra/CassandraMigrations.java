package com.bd2.app.migrations.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migraciones para Cassandra - Creación de keyspace y tablas
 */
public class CassandraMigrations {
    
    private static final Logger logger = LoggerFactory.getLogger(CassandraMigrations.class);
    private final CqlSession session;
    private static final String KEYSPACE = "tp_sensores";
    
    public CassandraMigrations(CqlSession session) {
        this.session = session;
    }
    
    /**
     * Ejecuta todas las migraciones de Cassandra
     */
    public void runAllMigrations() {
        logger.info("=== Iniciando migraciones Cassandra ===");
        
        try {
            createKeyspace();
            createMeasurementTables();
            createAggregationTables();
            createMessagingTables();
            createAlertTables();
            createExecutionLogTables();
            
            logger.info("=== Migraciones Cassandra completadas exitosamente ===");
            
        } catch (Exception e) {
            logger.error("Error ejecutando migraciones Cassandra", e);
            throw new RuntimeException("Fallo en migraciones Cassandra", e);
        }
    }
    
    private void createKeyspace() {
        logger.info("Creando keyspace '{}'...", KEYSPACE);
        
        String createKeyspace = String.format("""
            CREATE KEYSPACE IF NOT EXISTS %s 
            WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'} 
            AND durable_writes = true
            """, KEYSPACE);
        
        session.execute(createKeyspace);
        session.execute("USE " + KEYSPACE);
        
        logger.info("Keyspace '{}' creado y seleccionado", KEYSPACE);
    }
    
    private void createMeasurementTables() {
        logger.info("Creando tablas de mediciones...");
        
        // measurements_by_sensor_day
        session.execute("""
            CREATE TABLE IF NOT EXISTS measurements_by_sensor_day (
                sensor_id uuid,
                day date,
                ts timeuuid,
                temperature double,
                humidity double,
                type text,
                city text,
                country text,
                PRIMARY KEY ((sensor_id, day), ts)
            ) WITH CLUSTERING ORDER BY (ts DESC)
              AND compaction = {'class': 'TimeWindowCompactionStrategy','compaction_window_unit':'DAYS','compaction_window_size':'1'}
              AND compression = {'class':'LZ4Compressor'}
              AND default_time_to_live = 15552000
              AND gc_grace_seconds = 86400
            """);
        
        // measurements_by_city_day
        session.execute("""
            CREATE TABLE IF NOT EXISTS measurements_by_city_day (
                city text,
                day date,
                ts timeuuid,
                sensor_id uuid,
                temperature double,
                humidity double,
                type text,
                country text,
                PRIMARY KEY ((city, day), ts, sensor_id)
            ) WITH CLUSTERING ORDER BY (ts DESC, sensor_id ASC)
              AND compaction = {'class': 'TimeWindowCompactionStrategy','compaction_window_unit':'DAYS','compaction_window_size':'1'}
              AND compression = {'class':'LZ4Compressor'}
              AND default_time_to_live = 15552000
              AND gc_grace_seconds = 86400
            """);
        
        // measurements_by_country_day
        session.execute("""
            CREATE TABLE IF NOT EXISTS measurements_by_country_day (
                country text,
                day date,
                ts timeuuid,
                city text,
                sensor_id uuid,
                temperature double,
                humidity double,
                type text,
                PRIMARY KEY ((country, day), ts, city, sensor_id)
            ) WITH CLUSTERING ORDER BY (ts DESC, city ASC, sensor_id ASC)
              AND compaction = {'class': 'TimeWindowCompactionStrategy','compaction_window_unit':'DAYS','compaction_window_size':'1'}
              AND compression = {'class':'LZ4Compressor'}
              AND default_time_to_live = 15552000
              AND gc_grace_seconds = 86400
            """);
        
        // last_measurement_by_sensor
        session.execute("""
            CREATE TABLE IF NOT EXISTS last_measurement_by_sensor (
                sensor_id uuid PRIMARY KEY,
                ts timeuuid,
                temperature double,
                humidity double,
                type text,
                city text,
                country text
            ) WITH compression = {'class':'LZ4Compressor'}
            """);
        
        logger.info("Tablas de mediciones creadas");
    }
    
    private void createAggregationTables() {
        logger.info("Creando tablas de agregaciones...");
        
        // agg_city_day
        session.execute("""
            CREATE TABLE IF NOT EXISTS agg_city_day (
                city text,
                year int,
                day date,
                temp_min double,
                temp_max double,
                temp_sum double,
                temp_count bigint,
                hum_min double,
                hum_max double,
                hum_sum double,
                hum_count bigint,
                updated_at timestamp,
                PRIMARY KEY ((city, year), day)
            ) WITH CLUSTERING ORDER BY (day ASC)
              AND compaction = {'class': 'TimeWindowCompactionStrategy','compaction_window_unit':'DAYS','compaction_window_size':'7'}
              AND compression = {'class':'LZ4Compressor'}
            """);
        
        // agg_city_month
        session.execute("""
            CREATE TABLE IF NOT EXISTS agg_city_month (
                city text,
                month int,
                temp_min double,
                temp_max double,
                temp_sum double,
                temp_count bigint,
                hum_min double,
                hum_max double,
                hum_sum double,
                hum_count bigint,
                updated_at timestamp,
                PRIMARY KEY ((city), month)
            ) WITH CLUSTERING ORDER BY (month ASC)
              AND compression = {'class':'LZ4Compressor'}
            """);
        
        // agg_country_month
        session.execute("""
            CREATE TABLE IF NOT EXISTS agg_country_month (
                country text,
                month int,
                temp_min double,
                temp_max double,
                temp_sum double,
                hum_min double,
                hum_max double,
                hum_sum double,
                hum_count bigint,
                updated_at timestamp,
                PRIMARY KEY ((country), month)
            ) WITH CLUSTERING ORDER BY (month ASC)
              AND compression = {'class':'LZ4Compressor'}
            """);
        
        logger.info("Tablas de agregaciones creadas");
    }
    
    private void createMessagingTables() {
        logger.info("Creando tablas de mensajería...");
        
        // messages_by_conversation
        session.execute("""
            CREATE TABLE IF NOT EXISTS messages_by_conversation (
                conversation_id uuid,
                ts timeuuid,
                message_id timeuuid,
                sender_id uuid,
                type text,
                content text,
                metadata map<text,text>,
                PRIMARY KEY ((conversation_id), ts, message_id)
            ) WITH CLUSTERING ORDER BY (ts DESC, message_id ASC)
              AND compaction = {'class': 'TimeWindowCompactionStrategy','compaction_window_unit':'DAYS','compaction_window_size':'1'}
              AND compression = {'class':'LZ4Compressor'}
            """);
        // Agregar destinatario y cambiar type por booleano. (true: privado, false: grupal)
        
        // conversations_by_user
        session.execute("""
            CREATE TABLE IF NOT EXISTS conversations_by_user (
                user_id uuid,
                last_activity_ts timeuuid,
                conversation_id uuid,
                last_message_snippet text,
                unread_count int,
                PRIMARY KEY ((user_id), last_activity_ts, conversation_id)
            ) WITH CLUSTERING ORDER BY (last_activity_ts DESC, conversation_id ASC)
              AND compression = {'class':'LZ4Compressor'}
            """);
        
        logger.info("Tablas de mensajería creadas");
    }
    
    private void createAlertTables() {
        logger.info("Creando tablas de alertas...");
        
        // alerts_by_sensor
        session.execute("""
            CREATE TABLE IF NOT EXISTS alerts_by_sensor (
                sensor_id uuid,
                ts timeuuid,
                alert_id uuid,
                type text,
                status text,
                severity text,
                description text,
                city text,
                country text,
                PRIMARY KEY ((sensor_id), ts, alert_id)
            ) WITH CLUSTERING ORDER BY (ts DESC, alert_id ASC)
              AND compaction = {'class': 'TimeWindowCompactionStrategy','compaction_window_unit':'DAYS','compaction_window_size':'7'}
              AND compression = {'class':'LZ4Compressor'}
            """);
        
        // alerts_by_city_day
        session.execute("""
            CREATE TABLE IF NOT EXISTS alerts_by_city_day (
                city text,
                day date,
                ts timeuuid,
                sensor_id uuid,
                alert_id uuid,
                type text,
                status text,
                severity text,
                description text,
                PRIMARY KEY ((city, day), ts, sensor_id, alert_id)
            ) WITH CLUSTERING ORDER BY (ts DESC, sensor_id ASC, alert_id ASC)
              AND compaction = {'class': 'TimeWindowCompactionStrategy','compaction_window_unit':'DAYS','compaction_window_size':'1'}
              AND compression = {'class':'LZ4Compressor'}
            """);
        
        // sensor_health_checks
        session.execute("""
            CREATE TABLE IF NOT EXISTS sensor_health_checks (
                sensor_id uuid,
                day date,
                ts timeuuid,
                state text,
                notes text,
                PRIMARY KEY ((sensor_id, day), ts)
            ) WITH CLUSTERING ORDER BY (ts DESC)
              AND compaction = {'class': 'TimeWindowCompactionStrategy','compaction_window_unit':'DAYS','compaction_window_size':'1'}
              AND compression = {'class':'LZ4Compressor'}
            """);
        
        logger.info("Tablas de alertas creadas");
    }
    
    private void createExecutionLogTables() {
        logger.info("Creando tablas de logs de ejecución...");
        
        // Solicitud ?? Id de la solicitud 

        // Metrics eliminar.

        // exec_log_by_request
        session.execute("""
            CREATE TABLE IF NOT EXISTS exec_log_by_request (
                request_id uuid,
                ts timeuuid,
                step text,
                status text,
                message text,
                result_pointer text,
                metrics map<text,text>,
                PRIMARY KEY ((request_id), ts)
            ) WITH CLUSTERING ORDER BY (ts ASC)
              AND compression = {'class':'LZ4Compressor'}
            """);
        
        logger.info("Tablas de logs de ejecución creadas");
    }
    
    /**
     * Inserta datos de prueba básicos
     */
    public void insertSampleData() {
        logger.info("Insertando datos de prueba en Cassandra...");
        
        try {
            // Insertar medición de ejemplo
            session.execute("""
                INSERT INTO measurements_by_sensor_day 
                (sensor_id, day, ts, temperature, humidity, type, city, country) 
                VALUES (uuid(), '2024-01-01', now(), 25.5, 60.0, 'temperatura', 'Buenos Aires', 'Argentina')
                """);
            
            logger.info("Datos de prueba insertados en Cassandra");
            
        } catch (Exception e) {
            logger.warn("Error insertando datos de prueba: {}", e.getMessage());
        }
    }
}
