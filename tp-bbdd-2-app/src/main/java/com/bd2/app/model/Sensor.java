package com.bd2.app.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Modelo de Sensor - Usado en MongoDB para metadatos
 * Referenciado en Neo4j para ubicación geográfica
 * Referenciado en Cassandra para mediciones
 */
public class Sensor {
    
    @BsonId
    @JsonProperty("_id")
    private String id;
    
    @BsonProperty("code")
    @JsonProperty("code")
    private String code; // Código único visible (ej: TEMP_001_CABA)
    
    @BsonProperty("type")
    @JsonProperty("type")
    private String type; // temperatura, humedad
    
    @BsonProperty("state")
    @JsonProperty("state")
    private String state; // activo, inactivo, falla, mantenimiento
    
    @BsonProperty("city")
    @JsonProperty("city")
    private String city;
    
    @BsonProperty("country")
    @JsonProperty("country")
    private String country;
    
    @BsonProperty("latitude")
    @JsonProperty("latitude")
    private Double latitude;
    
    @BsonProperty("longitude")
    @JsonProperty("longitude")
    private Double longitude;
    
    @BsonProperty("model")
    @JsonProperty("model")
    private String model;
    
    @BsonProperty("installDate")
    @JsonProperty("installDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate installDate;
    
    @BsonProperty("startedAt")
    @JsonProperty("startedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startedAt;
    
    @BsonProperty("ownerUserId")
    @JsonProperty("ownerUserId")
    private String ownerUserId; // ID del usuario propietario (opcional)
    
    @BsonProperty("updatedAt")
    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    // Constructores
    public Sensor() {}
    
    public Sensor(String id, String code, String type, String state, String city, String country) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.state = state;
        this.city = city;
        this.country = country;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters y Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getCountry() {
        return country;
    }
    
    public void setCountry(String country) {
        this.country = country;
        this.updatedAt = LocalDateTime.now();
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
        this.updatedAt = LocalDateTime.now();
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDate getInstallDate() {
        return installDate;
    }
    
    public void setInstallDate(LocalDate installDate) {
        this.installDate = installDate;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public String getOwnerUserId() {
        return ownerUserId;
    }
    
    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Métodos de utilidad
    public boolean isActive() {
        return "activo".equals(state);
    }
    
    public boolean hasFault() {
        return "falla".equals(state);
    }
    
    public boolean isInMaintenance() {
        return "mantenimiento".equals(state);
    }
    
    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
    
    public void markAsUpdated() {
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Genera el código del sensor basado en tipo, número y ciudad
     */
    public static String generateCode(String type, int number, String cityCode) {
        String typePrefix = type.toUpperCase().substring(0, Math.min(4, type.length()));
        return String.format("%s_%03d_%s", typePrefix, number, cityCode.toUpperCase());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sensor sensor = (Sensor) o;
        return Objects.equals(id, sensor.id) && Objects.equals(code, sensor.code);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, code);
    }
    
    @Override
    public String toString() {
        return "Sensor{" +
                "id='" + id + '\'' +
                ", code='" + code + '\'' +
                ", type='" + type + '\'' +
                ", state='" + state + '\'' +
                ", city='" + city + '\'' +
                ", country='" + country + '\'' +
                ", model='" + model + '\'' +
                ", startedAt=" + startedAt +
                '}';
    }
}
