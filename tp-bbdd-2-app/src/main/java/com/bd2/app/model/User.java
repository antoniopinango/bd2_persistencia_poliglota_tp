package com.bd2.app.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Modelo de Usuario - Usado principalmente en MongoDB
 * También referenciado en Neo4j y Cassandra por ID
 */
public class User {
    
    @BsonId
    @JsonProperty("_id")
    private String id;
    
    @BsonProperty("fullName")
    @JsonProperty("fullName")
    private String fullName;
    
    @BsonProperty("email")
    @JsonProperty("email")
    private String email;
    
    @BsonProperty("passwordHash")
    @JsonProperty("passwordHash")
    private String passwordHash;
    
    @BsonProperty("status")
    @JsonProperty("status")
    private String status; // activo, inactivo
    
    @BsonProperty("department")
    @JsonProperty("department")
    private String department;
    
    @BsonProperty("registeredAt")
    @JsonProperty("registeredAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime registeredAt;
    
    @BsonProperty("updatedAt")
    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    // Constructores
    public User() {}
    
    public User(String id, String fullName, String email, String passwordHash, String status) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.registeredAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters y Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
    
    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Métodos de utilidad
    public boolean isActive() {
        return "activo".equals(status);
    }
    
    public void markAsUpdated() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(email, user.email);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", status='" + status + '\'' +
                ", department='" + department + '\'' +
                ", registeredAt=" + registeredAt +
                '}';
    }
    
    /**
     * Crea una copia del usuario sin información sensible (password)
     */
    public User toSafeUser() {
        User safeUser = new User();
        safeUser.id = this.id;
        safeUser.fullName = this.fullName;
        safeUser.email = this.email;
        safeUser.status = this.status;
        safeUser.department = this.department;
        safeUser.registeredAt = this.registeredAt;
        safeUser.updatedAt = this.updatedAt;
        // No incluir passwordHash
        return safeUser;
    }
}
