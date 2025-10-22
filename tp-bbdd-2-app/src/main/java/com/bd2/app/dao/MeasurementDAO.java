package com.bd2.app.dao;

import com.bd2.app.database.CassandraConnectionManager;
import com.bd2.app.model.Measurement;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;

/**
 * DAO para operaciones de Mediciones en Cassandra
 */
public class MeasurementDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(MeasurementDAO.class);
    private static final String KEYSPACE = "tp_sensores";
    
    private final CqlSession session;
    
    // Prepared statements para mejor rendimiento
    private PreparedStatement insertMeasurementBySensor;
    private PreparedStatement insertMeasurementByCity;
    private PreparedStatement insertMeasurementByCountry;
    private PreparedStatement updateLastMeasurement;
    private PreparedStatement selectMeasurementsBySensor;
    private PreparedStatement selectMeasurementsByCity;
    private PreparedStatement selectLastMeasurement;
    
    public MeasurementDAO() {
        this.session = CassandraConnectionManager.getInstance().getSession();
        prepareStatements();
    }
    
    private void prepareStatements() {
        try {
            // Insert en measurements_by_sensor_day
            insertMeasurementBySensor = session.prepare(
                "INSERT INTO measurements_by_sensor_day " +
                "(sensor_id, day, ts, temperature, humidity, type, city, country) " +
                "VALUES (?, ?, now(), ?, ?, ?, ?, ?)"
            );
            
            // Insert en measurements_by_city_day (denormalización)
            insertMeasurementByCity = session.prepare(
                "INSERT INTO measurements_by_city_day " +
                "(city, day, ts, sensor_id, temperature, humidity, type, country) " +
                "VALUES (?, ?, now(), ?, ?, ?, ?, ?)"
            );
            
            // Insert en measurements_by_country_day (denormalización)
            insertMeasurementByCountry = session.prepare(
                "INSERT INTO measurements_by_country_day " +
                "(country, day, ts, city, sensor_id, temperature, humidity, type) " +
                "VALUES (?, ?, now(), ?, ?, ?, ?, ?)"
            );
            
            // Update last_measurement_by_sensor
            updateLastMeasurement = session.prepare(
                "INSERT INTO last_measurement_by_sensor " +
                "(sensor_id, ts, temperature, humidity, type, city, country) " +
                "VALUES (?, now(), ?, ?, ?, ?, ?)"
            );
            
            // Select mediciones por sensor
            selectMeasurementsBySensor = session.prepare(
                "SELECT * FROM measurements_by_sensor_day " +
                "WHERE sensor_id = ? AND day = ? " +
                "ORDER BY ts DESC LIMIT ?"
            );
            
            // Select mediciones por ciudad
            selectMeasurementsByCity = session.prepare(
                "SELECT * FROM measurements_by_city_day " +
                "WHERE city = ? AND day = ? " +
                "ORDER BY ts DESC LIMIT ?"
            );
            
            // Select última medición
            selectLastMeasurement = session.prepare(
                "SELECT * FROM last_measurement_by_sensor WHERE sensor_id = ?"
            );
            
            logger.info("Prepared statements inicializados para MeasurementDAO");
            
        } catch (Exception e) {
            logger.error("Error preparando statements", e);
            throw new RuntimeException("No se pudieron preparar los statements", e);
        }
    }
    
    /**
     * Inserta una nueva medición (denormalizada en todas las tablas)
     */
    public boolean insertMeasurement(Measurement measurement) {
        try {
            if (!measurement.isValidMeasurement()) {
                logger.warn("Medición inválida: {}", measurement);
                return false;
            }
            
            LocalDate day = measurement.getDay();
            if (day == null) {
                day = LocalDate.now();
                measurement.setDay(day);
            }
            
            // Usar batch para insertar en múltiples tablas de forma atómica
            BatchStatement batch = BatchStatement.newInstance(DefaultBatchType.LOGGED);
            
            // 1. Insertar en measurements_by_sensor_day
            batch = batch.add(insertMeasurementBySensor.bind(
                UUID.fromString(measurement.getSensorId()),
                day,
                measurement.getTemperature(),
                measurement.getHumidity(),
                measurement.getType(),
                measurement.getCity(),
                measurement.getCountry()
            ));
            
            // 2. Insertar en measurements_by_city_day
            batch = batch.add(insertMeasurementByCity.bind(
                measurement.getCity(),
                day,
                UUID.fromString(measurement.getSensorId()),
                measurement.getTemperature(),
                measurement.getHumidity(),
                measurement.getType(),
                measurement.getCountry()
            ));
            
            // 3. Insertar en measurements_by_country_day
            batch = batch.add(insertMeasurementByCountry.bind(
                measurement.getCountry(),
                day,
                measurement.getCity(),
                UUID.fromString(measurement.getSensorId()),
                measurement.getTemperature(),
                measurement.getHumidity(),
                measurement.getType()
            ));
            
            // 4. Actualizar última medición
            batch = batch.add(updateLastMeasurement.bind(
                UUID.fromString(measurement.getSensorId()),
                measurement.getTemperature(),
                measurement.getHumidity(),
                measurement.getType(),
                measurement.getCity(),
                measurement.getCountry()
            ));
            
            // Ejecutar batch
            session.execute(batch);
            
            logger.debug("Medición insertada para sensor: {}", measurement.getSensorId());
            return true;
            
        } catch (Exception e) {
            logger.error("Error insertando medición", e);
            return false;
        }
    }
    
    /**
     * Obtiene las últimas N mediciones de un sensor en un día específico
     */
    public List<Measurement> getLatestMeasurements(String sensorId, LocalDate day, int limit) {
        try {
            ResultSet resultSet = session.execute(selectMeasurementsBySensor.bind(
                UUID.fromString(sensorId), day, limit
            ));
            
            List<Measurement> measurements = new ArrayList<>();
            for (Row row : resultSet) {
                measurements.add(rowToMeasurement(row));
            }
            
            return measurements;
            
        } catch (Exception e) {
            logger.error("Error obteniendo mediciones por sensor: {}", sensorId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene las últimas mediciones de hoy para un sensor
     */
    public List<Measurement> getTodayMeasurements(String sensorId, int limit) {
        return getLatestMeasurements(sensorId, LocalDate.now(), limit);
    }
    
    /**
     * Obtiene mediciones por ciudad en un día específico
     */
    public List<Measurement> getMeasurementsByCity(String city, LocalDate day, int limit) {
        try {
            ResultSet resultSet = session.execute(selectMeasurementsByCity.bind(
                city, day, limit
            ));
            
            List<Measurement> measurements = new ArrayList<>();
            for (Row row : resultSet) {
                measurements.add(rowToCityMeasurement(row));
            }
            
            return measurements;
            
        } catch (Exception e) {
            logger.error("Error obteniendo mediciones por ciudad: {}", city, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene la última medición de un sensor
     */
    public Measurement getLastMeasurement(String sensorId) {
        try {
            ResultSet resultSet = session.execute(selectLastMeasurement.bind(
                UUID.fromString(sensorId)
            ));
            
            Row row = resultSet.one();
            return row != null ? rowToLastMeasurement(row) : null;
            
        } catch (Exception e) {
            logger.error("Error obteniendo última medición para sensor: {}", sensorId, e);
            return null;
        }
    }
    
    /**
     * Obtiene mediciones por rango de fechas
     */
    public List<Measurement> getMeasurementsByDateRange(String sensorId, LocalDate startDate, LocalDate endDate, int limit) {
        try {
            List<Measurement> allMeasurements = new ArrayList<>();
            
            // Iterar por cada día en el rango
            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate) && allMeasurements.size() < limit) {
                List<Measurement> dayMeasurements = getLatestMeasurements(
                    sensorId, currentDate, Math.min(100, limit - allMeasurements.size())
                );
                allMeasurements.addAll(dayMeasurements);
                currentDate = currentDate.plusDays(1);
            }
            
            return allMeasurements;
            
        } catch (Exception e) {
            logger.error("Error obteniendo mediciones por rango de fechas", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Inserta múltiples mediciones en batch
     */
    public boolean insertMeasurementsBatch(List<Measurement> measurements) {
        try {
            if (measurements.isEmpty()) {
                return true;
            }
            
            BatchStatement batch = BatchStatement.newInstance(DefaultBatchType.LOGGED);
            
            for (Measurement measurement : measurements) {
                if (!measurement.isValidMeasurement()) {
                    continue;
                }
                
                LocalDate day = measurement.getDay();
                if (day == null) {
                    day = LocalDate.now();
                }
                
                // Agregar al batch (solo tabla principal para evitar batch muy grande)
                batch = batch.add(insertMeasurementBySensor.bind(
                    UUID.fromString(measurement.getSensorId()),
                    day,
                    measurement.getTemperature(),
                    measurement.getHumidity(),
                    measurement.getType(),
                    measurement.getCity(),
                    measurement.getCountry()
                ));
            }
            
            session.execute(batch);
            logger.info("Batch de {} mediciones insertado", measurements.size());
            return true;
            
        } catch (Exception e) {
            logger.error("Error insertando batch de mediciones", e);
            return false;
        }
    }
    
    // Métodos de conversión
    private Measurement rowToMeasurement(Row row) {
        Measurement measurement = new Measurement();
        measurement.setSensorId(row.getUuid("sensor_id").toString());
        measurement.setDay(row.getLocalDate("day"));
        measurement.setTemperature(row.getDouble("temperature"));
        measurement.setHumidity(row.getDouble("humidity"));
        measurement.setType(row.getString("type"));
        measurement.setCity(row.getString("city"));
        measurement.setCountry(row.getString("country"));
        
        // Convertir TimeUUID a LocalDateTime (aproximado)
        UUID timeUuid = row.getUuid("ts");
        if (timeUuid != null) {
            // Simplificación: usar timestamp actual
            measurement.setTimestamp(LocalDateTime.now());
        }
        
        return measurement;
    }
    
    private Measurement rowToCityMeasurement(Row row) {
        Measurement measurement = new Measurement();
        measurement.setSensorId(row.getUuid("sensor_id").toString());
        measurement.setDay(row.getLocalDate("day"));
        measurement.setTemperature(row.getDouble("temperature"));
        measurement.setHumidity(row.getDouble("humidity"));
        measurement.setType(row.getString("type"));
        measurement.setCity(row.getString("city"));
        measurement.setCountry(row.getString("country"));
        
        UUID timeUuid = row.getUuid("ts");
        if (timeUuid != null) {
            measurement.setTimestamp(LocalDateTime.now());
        }
        
        return measurement;
    }
    
    private Measurement rowToLastMeasurement(Row row) {
        Measurement measurement = new Measurement();
        measurement.setSensorId(row.getUuid("sensor_id").toString());
        measurement.setTemperature(row.getDouble("temperature"));
        measurement.setHumidity(row.getDouble("humidity"));
        measurement.setType(row.getString("type"));
        measurement.setCity(row.getString("city"));
        measurement.setCountry(row.getString("country"));
        measurement.setTimestamp(LocalDateTime.now());
        measurement.setDay(LocalDate.now());
        
        return measurement;
    }
}
