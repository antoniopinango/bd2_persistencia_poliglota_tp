package com.bd2.app.service;

import com.bd2.app.database.MongoConnectionManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Servicio simple de alertas
 */
public class AlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    private final MongoDatabase mongoDb;
    
    // Umbrales por defecto
    private static final double TEMP_MAX_THRESHOLD = 30.0;
    private static final double TEMP_MIN_THRESHOLD = 10.0;
    private static final double HUM_MAX_THRESHOLD = 80.0;
    private static final double HUM_MIN_THRESHOLD = 30.0;
    
    public AlertService() {
        this.mongoDb = MongoConnectionManager.getInstance().getDatabase();
    }
    
    /**
     * Verifica umbrales y crea alerta si es necesario
     */
    public void checkThresholdsAndAlert(String sensorId, String city, double temperature, double humidity, String type) {
        try {
            List<String> alerts = new ArrayList<>();
            
            // Verificar temperatura
            if (type.equals("temperature") || type.equals("temperature_humidity")) {
                if (temperature > TEMP_MAX_THRESHOLD) {
                    alerts.add("Temperatura muy alta: " + temperature + "°C (umbral: " + TEMP_MAX_THRESHOLD + "°C)");
                } else if (temperature < TEMP_MIN_THRESHOLD) {
                    alerts.add("Temperatura muy baja: " + temperature + "°C (umbral: " + TEMP_MIN_THRESHOLD + "°C)");
                }
            }
            
            // Verificar humedad
            if (type.equals("humidity") || type.equals("temperature_humidity")) {
                if (humidity > HUM_MAX_THRESHOLD) {
                    alerts.add("Humedad muy alta: " + humidity + "% (umbral: " + HUM_MAX_THRESHOLD + "%)");
                } else if (humidity < HUM_MIN_THRESHOLD) {
                    alerts.add("Humedad muy baja: " + humidity + "% (umbral: " + HUM_MIN_THRESHOLD + "%)");
                }
            }
            
            // Crear alertas si hay
            for (String description : alerts) {
                createAlert(sensorId, city, "sensor", description);
            }
            
        } catch (Exception e) {
            logger.error("Error verificando umbrales", e);
        }
    }
    
    /**
     * Crea una alerta
     */
    public String createAlert(String sensorId, String city, String type, String description) {
        try {
            String alertId = UUID.randomUUID().toString();
            
            Document alert = new Document("_id", alertId)
                .append("sensorId", sensorId)
                .append("city", city)
                .append("type", type)
                .append("description", description)
                .append("status", "activa")
                .append("openedAt", new Date())
                .append("severity", "alta");
            
            mongoDb.getCollection("alerts").insertOne(alert);
            
            logger.info("Alerta creada: {} - {}", alertId, description);
            return alertId;
            
        } catch (Exception e) {
            logger.error("Error creando alerta", e);
            return null;
        }
    }
    
    /**
     * Lista alertas activas
     */
    public List<Map<String, Object>> getActiveAlerts(String city) {
        try {
            List<Map<String, Object>> alerts = new ArrayList<>();
            
            MongoCollection<Document> alertsColl = mongoDb.getCollection("alerts");
            
            Document query = new Document("status", "activa");
            if (city != null && !city.isEmpty()) {
                query.append("city", city);
            }
            
            for (Document doc : alertsColl.find(query).sort(new Document("openedAt", -1)).limit(20)) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("alertId", doc.getString("_id"));
                alert.put("sensorId", doc.getString("sensorId"));
                alert.put("city", doc.getString("city"));
                alert.put("description", doc.getString("description"));
                alert.put("openedAt", doc.getDate("openedAt"));
                alert.put("severity", doc.getString("severity"));
                
                alerts.add(alert);
            }
            
            return alerts;
            
        } catch (Exception e) {
            logger.error("Error obteniendo alertas", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Resuelve una alerta
     */
    public boolean resolveAlert(String alertId) {
        try {
            mongoDb.getCollection("alerts").updateOne(
                new Document("_id", alertId),
                new Document("$set", new Document()
                    .append("status", "resuelta")
                    .append("resolvedAt", new Date()))
            );
            
            logger.info("Alerta resuelta: {}", alertId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error resolviendo alerta", e);
            return false;
        }
    }
}

