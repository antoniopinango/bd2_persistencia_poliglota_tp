package com.bd2.app.service;

import com.bd2.app.dao.AuthorizationDAO;
import com.bd2.app.dao.MeasurementDAO;
import com.bd2.app.dao.UserDAO;
import com.bd2.app.model.Measurement;
import com.bd2.app.model.Sensor;
import com.bd2.app.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio principal que integra las operaciones de sensores
 * usando las tres bases de datos (MongoDB, Cassandra, Neo4j)
 */
public class SensorService {
    
    private static final Logger logger = LoggerFactory.getLogger(SensorService.class);
    
    private final UserDAO userDAO;
    private final MeasurementDAO measurementDAO;
    private final AuthorizationDAO authorizationDAO;
    
    public SensorService() {
        this.userDAO = new UserDAO();
        this.measurementDAO = new MeasurementDAO();
        this.authorizationDAO = new AuthorizationDAO();
    }
    
    /**
     * Registra una nueva medición de sensor
     * - Valida permisos en Neo4j
     * - Almacena medición en Cassandra
     * - Actualiza metadatos si es necesario
     */
    public boolean recordMeasurement(String userId, Measurement measurement) {
        try {
            // 1. Verificar que el usuario existe y está activo
            Optional<User> userOpt = userDAO.findById(userId);
            if (userOpt.isEmpty() || !userOpt.get().isActive()) {
                logger.warn("Usuario no encontrado o inactivo: {}", userId);
                return false;
            }
            
            // 2. Verificar permisos para registrar mediciones en la ciudad
            boolean hasPermission = authorizationDAO.canUserExecuteProcessInCity(
                userId, "pt_registro_mediciones", measurement.getCity()
            );
            
            if (!hasPermission) {
                logger.warn("Usuario {} no tiene permisos para registrar mediciones en {}", 
                           userId, measurement.getCity());
                return false;
            }
            
            // 3. Validar la medición
            if (!measurement.isValidMeasurement()) {
                logger.warn("Medición inválida: {}", measurement);
                return false;
            }
            
            // 4. Establecer timestamp si no está definido
            if (measurement.getTimestamp() == null) {
                measurement.setTimestamp(LocalDateTime.now());
            }
            
            // 5. Insertar en Cassandra (denormalizado en múltiples tablas)
            boolean inserted = measurementDAO.insertMeasurement(measurement);
            
            if (inserted) {
                logger.info("Medición registrada - Sensor: {}, Usuario: {}, Ciudad: {}", 
                           measurement.getSensorId(), userId, measurement.getCity());
                
                // 6. Sincronizar información del sensor en Neo4j si es necesario
                syncSensorLocationIfNeeded(measurement);
                
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error registrando medición", e);
            return false;
        }
    }
    
    /**
     * Obtiene las últimas mediciones de un sensor
     * - Verifica permisos en Neo4j
     * - Consulta datos en Cassandra
     */
    public List<Measurement> getLatestMeasurements(String userId, String sensorId, int limit) {
        try {
            // 1. Verificar que el usuario existe
            Optional<User> userOpt = userDAO.findById(userId);
            if (userOpt.isEmpty() || !userOpt.get().isActive()) {
                logger.warn("Usuario no encontrado o inactivo: {}", userId);
                return new ArrayList<>();
            }
            
            // 2. Obtener información del sensor desde Neo4j para verificar ubicación
            List<Map<String, Object>> sensorInfo = authorizationDAO.getSensorsByCountry("Argentina");
            Optional<Map<String, Object>> sensor = sensorInfo.stream()
                .filter(s -> sensorId.equals(s.get("sensorId")))
                .findFirst();
            
            if (sensor.isEmpty()) {
                logger.warn("Sensor no encontrado: {}", sensorId);
                return new ArrayList<>();
            }
            
            String city = (String) sensor.get().get("city");
            
            // 3. Verificar permisos para consultar datos en esa ciudad
            boolean hasPermission = authorizationDAO.canUserExecuteProcessInCity(
                userId, "pt_consulta_mediciones", city
            );
            
            if (!hasPermission) {
                logger.warn("Usuario {} no tiene permisos para consultar mediciones en {}", userId, city);
                return new ArrayList<>();
            }
            
            // 4. Obtener mediciones desde Cassandra
            List<Measurement> measurements = measurementDAO.getTodayMeasurements(sensorId, limit);
            
            logger.info("Obtenidas {} mediciones para sensor {} por usuario {}", 
                       measurements.size(), sensorId, userId);
            
            return measurements;
            
        } catch (Exception e) {
            logger.error("Error obteniendo mediciones", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene mediciones por ciudad
     * - Verifica permisos geográficos en Neo4j
     * - Consulta agregados en Cassandra
     */
    public List<Measurement> getMeasurementsByCity(String userId, String cityName, LocalDate date, int limit) {
        try {
            // 1. Verificar usuario
            Optional<User> userOpt = userDAO.findById(userId);
            if (userOpt.isEmpty() || !userOpt.get().isActive()) {
                return new ArrayList<>();
            }
            
            // 2. Verificar permisos para la ciudad
            boolean hasPermission = authorizationDAO.canUserExecuteProcessInCity(
                userId, "pt_consulta_ciudad", cityName
            );
            
            if (!hasPermission) {
                logger.warn("Usuario {} no tiene permisos para consultar datos de {}", userId, cityName);
                return new ArrayList<>();
            }
            
            // 3. Obtener mediciones por ciudad desde Cassandra
            List<Measurement> measurements = measurementDAO.getMeasurementsByCity(cityName, date, limit);
            
            logger.info("Obtenidas {} mediciones para ciudad {} por usuario {}", 
                       measurements.size(), cityName, userId);
            
            return measurements;
            
        } catch (Exception e) {
            logger.error("Error obteniendo mediciones por ciudad", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Asigna un técnico a una ciudad
     * - Actualiza relaciones en Neo4j
     * - Verifica permisos administrativos
     */
    public boolean assignTechnicianToCity(String adminUserId, String technicianId, String cityName) {
        try {
            // 1. Verificar que el admin tiene permisos
            boolean isAdmin = authorizationDAO.canUserExecuteProcess(adminUserId, "pt_admin_usuarios");
            if (!isAdmin) {
                logger.warn("Usuario {} no tiene permisos administrativos", adminUserId);
                return false;
            }
            
            // 2. Verificar que el técnico existe y está activo
            Optional<User> technicianOpt = userDAO.findById(technicianId);
            if (technicianOpt.isEmpty() || !technicianOpt.get().isActive()) {
                logger.warn("Técnico no encontrado o inactivo: {}", technicianId);
                return false;
            }
            
            // 3. Asignar cobertura en Neo4j
            boolean assigned = authorizationDAO.assignCityCoverage(technicianId, cityName);
            
            if (assigned) {
                logger.info("Técnico {} asignado a ciudad {} por admin {}", 
                           technicianId, cityName, adminUserId);
            }
            
            return assigned;
            
        } catch (Exception e) {
            logger.error("Error asignando técnico a ciudad", e);
            return false;
        }
    }
    
    /**
     * Obtiene técnicos disponibles para una ciudad
     * - Consulta relaciones en Neo4j
     */
    public List<Map<String, Object>> getAvailableTechnicians(String cityName) {
        try {
            return authorizationDAO.getTechniciansForCity(cityName);
        } catch (Exception e) {
            logger.error("Error obteniendo técnicos para ciudad: {}", cityName, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Registra múltiples mediciones en batch
     * - Optimizado para alta carga de datos
     */
    public boolean recordMeasurementsBatch(String userId, List<Measurement> measurements) {
        try {
            // 1. Verificar usuario
            Optional<User> userOpt = userDAO.findById(userId);
            if (userOpt.isEmpty() || !userOpt.get().isActive()) {
                return false;
            }
            
            // 2. Validar todas las mediciones
            List<Measurement> validMeasurements = measurements.stream()
                .filter(Measurement::isValidMeasurement)
                .toList();
            
            if (validMeasurements.isEmpty()) {
                logger.warn("No hay mediciones válidas en el batch");
                return false;
            }
            
            // 3. Verificar permisos para las ciudades involucradas
            Set<String> cities = validMeasurements.stream()
                .map(Measurement::getCity)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
            
            for (String city : cities) {
                boolean hasPermission = authorizationDAO.canUserExecuteProcessInCity(
                    userId, "pt_registro_mediciones", city
                );
                if (!hasPermission) {
                    logger.warn("Usuario {} no tiene permisos para registrar en ciudad {}", userId, city);
                    return false;
                }
            }
            
            // 4. Insertar batch en Cassandra
            boolean inserted = measurementDAO.insertMeasurementsBatch(validMeasurements);
            
            if (inserted) {
                logger.info("Batch de {} mediciones registrado por usuario {}", 
                           validMeasurements.size(), userId);
            }
            
            return inserted;
            
        } catch (Exception e) {
            logger.error("Error registrando batch de mediciones", e);
            return false;
        }
    }
    
    /**
     * Obtiene el estado actual de todos los sensores
     * - Combina datos de Cassandra (última medición) y Neo4j (ubicación)
     */
    public List<Map<String, Object>> getCurrentSensorStatus(String userId) {
        try {
            // 1. Verificar permisos de monitoreo
            boolean canMonitor = authorizationDAO.canUserExecuteProcess(userId, "pt_monitoreo_sensores");
            if (!canMonitor) {
                logger.warn("Usuario {} no tiene permisos de monitoreo", userId);
                return new ArrayList<>();
            }
            
            // 2. Obtener sensores desde Neo4j
            List<Map<String, Object>> sensors = authorizationDAO.getSensorsByCountry("Argentina");
            
            // 3. Para cada sensor, obtener última medición desde Cassandra
            List<Map<String, Object>> sensorStatus = new ArrayList<>();
            
            for (Map<String, Object> sensor : sensors) {
                String sensorId = (String) sensor.get("sensorId");
                Measurement lastMeasurement = measurementDAO.getLastMeasurement(sensorId);
                
                Map<String, Object> status = new HashMap<>(sensor);
                if (lastMeasurement != null) {
                    status.put("lastTemperature", lastMeasurement.getTemperature());
                    status.put("lastHumidity", lastMeasurement.getHumidity());
                    status.put("lastMeasurementTime", lastMeasurement.getTimestamp());
                    status.put("measurementQuality", lastMeasurement.getQuality());
                } else {
                    status.put("lastMeasurementTime", null);
                    status.put("status", "Sin datos");
                }
                
                sensorStatus.add(status);
            }
            
            return sensorStatus;
            
        } catch (Exception e) {
            logger.error("Error obteniendo estado de sensores", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Sincroniza información de ubicación del sensor en Neo4j
     */
    private void syncSensorLocationIfNeeded(Measurement measurement) {
        try {
            // Esta operación podría implementarse para mantener sincronizada
            // la información de ubicación entre las bases de datos
            logger.debug("Sincronizando ubicación de sensor: {}", measurement.getSensorId());
        } catch (Exception e) {
            logger.warn("Error sincronizando ubicación de sensor", e);
        }
    }
    
    /**
     * Obtiene estadísticas del dashboard
     */
    public Map<String, Object> getDashboardStats(String userId) {
        try {
            // Verificar permisos de administrador
            boolean isAdmin = authorizationDAO.canUserExecuteProcess(userId, "pt_dashboard_admin");
            if (!isAdmin) {
                logger.warn("Usuario {} no tiene permisos de dashboard", userId);
                return new HashMap<>();
            }
            
            return authorizationDAO.getDashboardInfo();
            
        } catch (Exception e) {
            logger.error("Error obteniendo estadísticas del dashboard", e);
            return new HashMap<>();
        }
    }
}
