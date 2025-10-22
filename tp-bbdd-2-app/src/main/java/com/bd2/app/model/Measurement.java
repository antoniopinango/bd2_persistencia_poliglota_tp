package com.bd2.app.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo de Medición - Usado principalmente en Cassandra
 * Representa una medición de sensor en un momento específico
 */
public class Measurement {
    
    @JsonProperty("sensorId")
    private String sensorId;
    
    @JsonProperty("day")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate day;
    
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @JsonProperty("temperature")
    private Double temperature;
    
    @JsonProperty("humidity")
    private Double humidity;
    
    @JsonProperty("type")
    private String type; // temperatura, humedad
    
    @JsonProperty("city")
    private String city;
    
    @JsonProperty("country")
    private String country;
    
    // Campos adicionales para contexto
    @JsonProperty("batteryLevel")
    private Double batteryLevel; // 0.0 - 1.0
    
    @JsonProperty("signalStrength")
    private Integer signalStrength; // -100 a 0 dBm
    
    @JsonProperty("quality")
    private String quality; // good, fair, poor
    
    // Constructores
    public Measurement() {}
    
    public Measurement(String sensorId, Double temperature, Double humidity, String type, String city, String country) {
        this.sensorId = sensorId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.type = type;
        this.city = city;
        this.country = country;
        this.timestamp = LocalDateTime.now();
        this.day = LocalDate.now();
        this.quality = "good";
    }
    
    // Getters y Setters
    public String getSensorId() {
        return sensorId;
    }
    
    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }
    
    public LocalDate getDay() {
        return day;
    }
    
    public void setDay(LocalDate day) {
        this.day = day;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
        if (timestamp != null) {
            this.day = timestamp.toLocalDate();
        }
    }
    
    public Double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
    
    public Double getHumidity() {
        return humidity;
    }
    
    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getCountry() {
        return country;
    }
    
    public void setCountry(String country) {
        this.country = country;
    }
    
    public Double getBatteryLevel() {
        return batteryLevel;
    }
    
    public void setBatteryLevel(Double batteryLevel) {
        this.batteryLevel = batteryLevel;
    }
    
    public Integer getSignalStrength() {
        return signalStrength;
    }
    
    public void setSignalStrength(Integer signalStrength) {
        this.signalStrength = signalStrength;
    }
    
    public String getQuality() {
        return quality;
    }
    
    public void setQuality(String quality) {
        this.quality = quality;
    }
    
    // Métodos de utilidad
    public boolean hasTemperature() {
        return temperature != null;
    }
    
    public boolean hasHumidity() {
        return humidity != null;
    }
    
    public boolean isValidMeasurement() {
        return sensorId != null && timestamp != null && 
               (hasTemperature() || hasHumidity());
    }
    
    public boolean isTemperatureType() {
        return "temperatura".equals(type);
    }
    
    public boolean isHumidityType() {
        return "humedad".equals(type);
    }
    
    public boolean hasLowBattery() {
        return batteryLevel != null && batteryLevel < 0.2;
    }
    
    public boolean hasWeakSignal() {
        return signalStrength != null && signalStrength < -80;
    }
    
    public boolean isGoodQuality() {
        return "good".equals(quality);
    }
    
    /**
     * Crea una medición de temperatura
     */
    public static Measurement createTemperatureMeasurement(String sensorId, Double temperature, String city, String country) {
        Measurement measurement = new Measurement(sensorId, temperature, null, "temperatura", city, country);
        return measurement;
    }
    
    /**
     * Crea una medición de humedad
     */
    public static Measurement createHumidityMeasurement(String sensorId, Double humidity, String city, String country) {
        Measurement measurement = new Measurement(sensorId, null, humidity, "humedad", city, country);
        return measurement;
    }
    
    /**
     * Crea una medición combinada (temperatura + humedad)
     */
    public static Measurement createCombinedMeasurement(String sensorId, Double temperature, Double humidity, String city, String country) {
        return new Measurement(sensorId, temperature, humidity, "combinado", city, country);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Measurement that = (Measurement) o;
        return Objects.equals(sensorId, that.sensorId) && 
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sensorId, timestamp);
    }
    
    @Override
    public String toString() {
        return "Measurement{" +
                "sensorId='" + sensorId + '\'' +
                ", timestamp=" + timestamp +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", type='" + type + '\'' +
                ", city='" + city + '\'' +
                ", country='" + country + '\'' +
                ", quality='" + quality + '\'' +
                '}';
    }
}
