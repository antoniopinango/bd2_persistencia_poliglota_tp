package com.bd2.app.service;

import com.bd2.app.dao.AuthorizationDAO;
import com.bd2.app.dao.MeasurementDAO;
import com.bd2.app.database.MongoConnectionManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Servicio simple para gestionar procesos y reportes
 */
public class ProcessService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessService.class);
    
    private final MongoDatabase mongoDb;
    private final AuthorizationDAO authorizationDAO;
    private final MeasurementDAO measurementDAO;
    
    public ProcessService() {
        this.mongoDb = MongoConnectionManager.getInstance().getDatabase();
        this.authorizationDAO = new AuthorizationDAO();
        this.measurementDAO = new MeasurementDAO();
    }
    
    /**
     * Solicita un nuevo proceso (simple)
     */
    public String requestProcess(String userId, String processTypeId, Map<String, String> params) {
        try {
            // Verificar que el usuario tenga permisos
            if (!authorizationDAO.canUserExecuteProcess(userId, processTypeId)) {
                logger.warn("Usuario {} no tiene permisos para proceso {}", userId, processTypeId);
                return null;
            }
            
            // Crear solicitud
            String requestId = UUID.randomUUID().toString();
            
            Document request = new Document("_id", requestId)
                .append("userId", userId)
                .append("processId", processTypeId)
                .append("status", "pendiente")
                .append("requestedAt", new Date())
                .append("parameters", params);
            
            mongoDb.getCollection("process_requests").insertOne(request);
            
            logger.info("Proceso solicitado: {} por usuario {}", processTypeId, userId);
            return requestId;
            
        } catch (Exception e) {
            logger.error("Error solicitando proceso", e);
            return null;
        }
    }
    
    /**
     * Ejecuta un proceso y genera el reporte (SIMPLE)
     */
    public Map<String, Object> executeProcess(String requestId) {
        try {
            MongoCollection<Document> requests = mongoDb.getCollection("process_requests");
            
            Document request = requests.find(new Document("_id", requestId)).first();
            if (request == null) {
                return null;
            }
            
            String processTypeId = request.getString("processId");
            @SuppressWarnings("unchecked")
            Map<String, String> params = (Map<String, String>) request.get("parameters");
            
            // Marcar como ejecutando
            requests.updateOne(
                new Document("_id", requestId),
                new Document("$set", new Document("status", "ejecutando"))
            );
            
            // Ejecutar según tipo de proceso
            Map<String, Object> result = new HashMap<>();
            
            switch (processTypeId) {
                case "pt_maxmin" -> result = generateMaxMinReport(params);
                case "pt_prom" -> result = generateAverageReport(params);
                case "pt_alerts" -> result = generateAlertReport(params);
                default -> {
                    result.put("error", "Tipo de proceso no soportado");
                    return result;
                }
            }
            
            // Marcar como completado
            requests.updateOne(
                new Document("_id", requestId),
                new Document("$set", new Document()
                    .append("status", "completado")
                    .append("completedAt", new Date()))
            );
            
            // Guardar resultado
            mongoDb.getCollection("process_results").insertOne(
                new Document("_id", UUID.randomUUID().toString())
                    .append("requestId", requestId)
                    .append("result", result)
                    .append("generatedAt", new Date())
            );
            
            logger.info("Proceso {} ejecutado exitosamente", requestId);
            return result;
            
        } catch (Exception e) {
            logger.error("Error ejecutando proceso", e);
            return Map.of("error", e.getMessage());
        }
    }
    
    /**
     * Genera reporte de Max/Min con rango de fechas y por ciudad o país
     */
    private Map<String, Object> generateMaxMinReport(Map<String, String> params) {
        String scope = params.getOrDefault("scope", "city");
        String location = scope.equals("city") ? 
                         params.getOrDefault("city", "Buenos Aires") : 
                         params.getOrDefault("country", "Argentina");
        
        String startDateStr = params.getOrDefault("startDate", LocalDate.now().toString());
        String endDateStr = params.getOrDefault("endDate", LocalDate.now().toString());
        
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        
        // Acumular estadísticas de todo el rango
        double maxTemp = Double.MIN_VALUE;
        double minTemp = Double.MAX_VALUE;
        double maxHum = Double.MIN_VALUE;
        double minHum = Double.MAX_VALUE;
        int totalMeasurements = 0;
        int daysWithData = 0;
        
        // Iterar por cada día en el rango
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            Map<String, Object> dayStats = measurementDAO.getTemperatureStats(location, currentDate);
            
            int count = (int) dayStats.getOrDefault("count", 0);
            if (count > 0) {
                maxTemp = Math.max(maxTemp, (double) dayStats.get("maxTemp"));
                minTemp = Math.min(minTemp, (double) dayStats.get("minTemp"));
                maxHum = Math.max(maxHum, (double) dayStats.get("maxHumidity"));
                minHum = Math.min(minHum, (double) dayStats.get("minHumidity"));
                totalMeasurements += count;
                daysWithData++;
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        Map<String, Object> report = new HashMap<>();
        report.put("tipo", "Reporte Max/Min");
        report.put("ambito", scope.equals("city") ? "Ciudad" : "País");
        report.put("ubicacion", location);
        report.put("fecha_inicio", startDate.toString());
        report.put("fecha_fin", endDate.toString());
        report.put("dias_en_rango", daysWithData);
        report.put("temperatura_max", totalMeasurements > 0 ? maxTemp : 0);
        report.put("temperatura_min", totalMeasurements > 0 ? minTemp : 0);
        report.put("humedad_max", totalMeasurements > 0 ? maxHum : 0);
        report.put("humedad_min", totalMeasurements > 0 ? minHum : 0);
        report.put("mediciones_totales", totalMeasurements);
        
        return report;
    }
    
    /**
     * Genera reporte de Promedios con rango de fechas y por ciudad o país
     */
    private Map<String, Object> generateAverageReport(Map<String, String> params) {
        String scope = params.getOrDefault("scope", "city");
        String location = scope.equals("city") ? 
                         params.getOrDefault("city", "Buenos Aires") : 
                         params.getOrDefault("country", "Argentina");
        
        String startDateStr = params.getOrDefault("startDate", LocalDate.now().toString());
        String endDateStr = params.getOrDefault("endDate", LocalDate.now().toString());
        
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        
        // Acumular para calcular promedios
        double sumTemp = 0;
        double sumHum = 0;
        int totalMeasurements = 0;
        int daysWithData = 0;
        
        // Iterar por cada día en el rango
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            Map<String, Object> dayStats = measurementDAO.getTemperatureStats(location, currentDate);
            
            int count = (int) dayStats.getOrDefault("count", 0);
            if (count > 0) {
                double avgTemp = (double) dayStats.get("avgTemp");
                double avgHum = (double) dayStats.get("avgHumidity");
                
                sumTemp += avgTemp * count; // Ponderado por cantidad de mediciones
                sumHum += avgHum * count;
                totalMeasurements += count;
                daysWithData++;
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        Map<String, Object> report = new HashMap<>();
        report.put("tipo", "Reporte de Promedios");
        report.put("ambito", scope.equals("city") ? "Ciudad" : "País");
        report.put("ubicacion", location);
        report.put("fecha_inicio", startDate.toString());
        report.put("fecha_fin", endDate.toString());
        report.put("dias_en_rango", daysWithData);
        report.put("temperatura_promedio", totalMeasurements > 0 ? sumTemp / totalMeasurements : 0);
        report.put("humedad_promedio", totalMeasurements > 0 ? sumHum / totalMeasurements : 0);
        report.put("mediciones_totales", totalMeasurements);
        
        return report;
    }
    
    /**
     * Genera reporte de Alertas (SIMPLE)
     */
    private Map<String, Object> generateAlertReport(Map<String, String> params) {
        String city = params.getOrDefault("city", "Buenos Aires");
        
        Map<String, Object> report = new HashMap<>();
        report.put("tipo", "Reporte de Alertas");
        report.put("ciudad", city);
        report.put("alertas_activas", 0);
        report.put("mensaje", "Sistema de alertas en desarrollo");
        
        return report;
    }
    
    /**
     * Lista procesos de un usuario (SIMPLE)
     */
    public List<Map<String, Object>> listUserProcesses(String userId) {
        try {
            List<Map<String, Object>> processes = new ArrayList<>();
            
            MongoCollection<Document> requests = mongoDb.getCollection("process_requests");
            
            for (Document doc : requests.find(new Document("userId", userId))
                    .sort(new Document("requestedAt", -1))
                    .limit(20)) {
                
                Map<String, Object> process = new HashMap<>();
                process.put("requestId", doc.getString("_id"));
                process.put("processId", doc.getString("processId"));
                process.put("status", doc.getString("status"));
                process.put("requestedAt", doc.getDate("requestedAt"));
                process.put("completedAt", doc.getDate("completedAt"));
                
                processes.add(process);
            }
            
            return processes;
            
        } catch (Exception e) {
            logger.error("Error listando procesos de usuario", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene el resultado de un proceso
     */
    public Map<String, Object> getProcessResult(String requestId) {
        try {
            Document result = mongoDb.getCollection("process_results")
                .find(new Document("requestId", requestId))
                .first();
            
            if (result != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultData = (Map<String, Object>) result.get("result");
                return resultData != null ? resultData : new HashMap<>();
            }
            
            return new HashMap<>();
            
        } catch (Exception e) {
            logger.error("Error obteniendo resultado de proceso", e);
            return new HashMap<>();
        }
    }
}

