package com.bd2.app.model;

import java.util.Date;
import java.util.Map;

/**
 * Modelo simple para solicitudes de procesos
 */
public class ProcessRequest {
    
    private String id;
    private String userId;
    private String processId;
    private String status; // pendiente, ejecutando, completado, error
    private Date requestedAt;
    private Date completedAt;
    private Map<String, String> parameters;
    
    public ProcessRequest() {
        this.requestedAt = new Date();
        this.status = "pendiente";
    }
    
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Date getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Date requestedAt) { this.requestedAt = requestedAt; }
    
    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }
    
    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }
}

