package com.bd2.app.service;

import com.bd2.app.database.MongoConnectionManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Servicio simple de facturación
 */
public class InvoiceService {
    
    private static final Logger logger = LoggerFactory.getLogger(InvoiceService.class);
    private final MongoDatabase mongoDb;
    
    // Costos por tipo de proceso
    private static final Map<String, Double> PROCESS_COSTS = Map.of(
        "pt_maxmin", 15.0,
        "pt_prom", 10.0,
        "pt_alerts", 5.0
    );
    
    public InvoiceService() {
        this.mongoDb = MongoConnectionManager.getInstance().getDatabase();
    }
    
    /**
     * Genera factura para un proceso completado (SIMPLE)
     */
    public String generateInvoiceForProcess(String processRequestId) {
        try {
            // Obtener el proceso
            Document request = mongoDb.getCollection("process_requests")
                .find(new Document("_id", processRequestId)).first();
            
            if (request == null || !"completado".equals(request.getString("status"))) {
                logger.warn("Proceso no encontrado o no completado: {}", processRequestId);
                return null;
            }
            
            String userId = request.getString("userId");
            String processId = request.getString("processId");
            Double cost = PROCESS_COSTS.getOrDefault(processId, 10.0);
            
            // Crear factura
            String invoiceId = UUID.randomUUID().toString();
            
            Document invoice = new Document("_id", invoiceId)
                .append("userId", userId)
                .append("processRequestId", processRequestId)
                .append("processId", processId)
                .append("amount", cost)
                .append("status", "pendiente")
                .append("issuedAt", new Date());
            
            mongoDb.getCollection("invoices").insertOne(invoice);
            
            // Debitar de cuenta corriente si existe
            debitFromAccount(userId, cost);
            
            logger.info("Factura {} generada para proceso {} - Monto: ${}", invoiceId, processRequestId, cost);
            return invoiceId;
            
        } catch (Exception e) {
            logger.error("Error generando factura", e);
            return null;
        }
    }
    
    /**
     * Debita de la cuenta corriente (SIMPLE)
     */
    private void debitFromAccount(String userId, Double amount) {
        try {
            MongoCollection<Document> accounts = mongoDb.getCollection("accounts");
            
            Document account = accounts.find(new Document("userId", userId)).first();
            
            if (account != null) {
                double currentBalance = account.getDouble("balance");
                double newBalance = currentBalance - amount;
                
                // Actualizar saldo
                accounts.updateOne(
                    new Document("userId", userId),
                    new Document("$set", new Document("balance", newBalance))
                );
                
                // Registrar movimiento
                mongoDb.getCollection("account_movements").insertOne(
                    new Document("_id", UUID.randomUUID().toString())
                        .append("accountId", account.get("_id"))
                        .append("type", "debito")
                        .append("amount", amount)
                        .append("balance", newBalance)
                        .append("description", "Pago de proceso")
                        .append("ts", new Date())
                );
                
                logger.info("Cuenta de usuario {} debitada: ${}", userId, amount);
            }
        } catch (Exception e) {
            logger.warn("No se pudo debitar cuenta: {}", e.getMessage());
        }
    }
    
    /**
     * Registra un pago (SIMPLE)
     */
    public boolean processPayment(String invoiceId, Double amount) {
        try {
            MongoCollection<Document> invoices = mongoDb.getCollection("invoices");
            
            // Actualizar estado de factura
            invoices.updateOne(
                new Document("_id", invoiceId),
                new Document("$set", new Document()
                    .append("status", "pagada")
                    .append("paidAt", new Date()))
            );
            
            // Registrar pago
            mongoDb.getCollection("payments").insertOne(
                new Document("_id", UUID.randomUUID().toString())
                    .append("invoiceId", invoiceId)
                    .append("amount", amount)
                    .append("paidAt", new Date())
                    .append("method", "cuenta_corriente")
            );
            
            logger.info("Pago de factura {} procesado: ${}", invoiceId, amount);
            return true;
            
        } catch (Exception e) {
            logger.error("Error procesando pago", e);
            return false;
        }
    }
    
    /**
     * Lista facturas de un usuario (SIMPLE)
     */
    public List<Map<String, Object>> listUserInvoices(String userId) {
        try {
            List<Map<String, Object>> invoices = new ArrayList<>();
            
            for (Document doc : mongoDb.getCollection("invoices")
                    .find(new Document("userId", userId))
                    .sort(new Document("issuedAt", -1))
                    .limit(20)) {
                
                Map<String, Object> invoice = new HashMap<>();
                invoice.put("invoiceId", doc.getString("_id"));
                invoice.put("processId", doc.getString("processId"));
                invoice.put("amount", doc.getDouble("amount"));
                invoice.put("status", doc.getString("status"));
                invoice.put("issuedAt", doc.getDate("issuedAt"));
                
                invoices.add(invoice);
            }
            
            return invoices;
            
        } catch (Exception e) {
            logger.error("Error listando facturas", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene saldo de cuenta corriente (SIMPLE)
     */
    public Double getAccountBalance(String userId) {
        try {
            Document account = mongoDb.getCollection("accounts")
                .find(new Document("userId", userId)).first();
            
            if (account != null) {
                return account.getDouble("balance");
            }
            
            return 0.0;
            
        } catch (Exception e) {
            logger.error("Error obteniendo saldo", e);
            return 0.0;
        }
    }
    
    /**
     * Obtiene facturas pendientes de un usuario
     */
    public List<Map<String, Object>> getPendingInvoices(String userId) {
        try {
            List<Map<String, Object>> invoices = new ArrayList<>();
            
            for (Document doc : mongoDb.getCollection("invoices")
                    .find(new Document("userId", userId)
                        .append("status", "pendiente"))
                    .sort(new Document("issuedAt", 1))) {
                
                Map<String, Object> invoice = new HashMap<>();
                invoice.put("invoiceId", doc.getString("_id"));
                invoice.put("processId", doc.getString("processId"));
                invoice.put("amount", doc.getDouble("amount"));
                invoice.put("issuedAt", doc.getDate("issuedAt"));
                
                invoices.add(invoice);
            }
            
            return invoices;
            
        } catch (Exception e) {
            logger.error("Error obteniendo facturas pendientes", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Paga una factura pendiente (verifica saldo primero)
     */
    public boolean payInvoice(String invoiceId, String userId) {
        try {
            // Obtener factura
            Document invoice = mongoDb.getCollection("invoices")
                .find(new Document("_id", invoiceId)
                    .append("userId", userId)
                    .append("status", "pendiente"))
                .first();
            
            if (invoice == null) {
                logger.warn("Factura no encontrada o ya pagada: {}", invoiceId);
                return false;
            }
            
            Double amount = invoice.getDouble("amount");
            Double balance = getAccountBalance(userId);
            
            // Verificar saldo suficiente
            if (balance < amount) {
                logger.warn("Saldo insuficiente. Saldo: ${}, Monto: ${}", balance, amount);
                return false;
            }
            
            // Procesar pago
            boolean success = processPayment(invoiceId, amount);
            
            if (success) {
                // Actualizar saldo de cuenta corriente
                debitFromAccount(userId, amount);
                logger.info("Factura {} pagada exitosamente", invoiceId);
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("Error pagando factura", e);
            return false;
        }
    }
    
    /**
     * Paga todas las facturas pendientes (verifica saldo total primero)
     */
    public int payAllPendingInvoices(String userId) {
        try {
            List<Map<String, Object>> pending = getPendingInvoices(userId);
            
            if (pending.isEmpty()) {
                return 0;
            }
            
            // Calcular total
            double totalAmount = pending.stream()
                .mapToDouble(inv -> (Double) inv.get("amount"))
                .sum();
            
            Double balance = getAccountBalance(userId);
            
            // Verificar saldo suficiente
            if (balance < totalAmount) {
                logger.warn("Saldo insuficiente para pagar todas. Saldo: ${}, Total: ${}", balance, totalAmount);
                return -1; // -1 indica saldo insuficiente
            }
            
            // Pagar todas
            int paid = 0;
            for (Map<String, Object> inv : pending) {
                String invoiceId = (String) inv.get("invoiceId");
                if (payInvoice(invoiceId, userId)) {
                    paid++;
                }
            }
            
            logger.info("{} facturas pagadas de {} pendientes", paid, pending.size());
            return paid;
            
        } catch (Exception e) {
            logger.error("Error pagando facturas pendientes", e);
            return 0;
        }
    }
    
    /**
     * Carga saldo a cuenta corriente
     */
    public boolean addBalanceToAccount(String userId, Double amount) {
        try {
            MongoCollection<Document> accounts = mongoDb.getCollection("accounts");
            
            Document account = accounts.find(new Document("userId", userId)).first();
            double newBalance;
            String accountId;
            
            if (account == null) {
                // Crear cuenta si no existe
                accountId = UUID.randomUUID().toString();
                newBalance = amount;
                account = new Document("_id", accountId)
                    .append("userId", userId)
                    .append("balance", newBalance)
                    .append("createdAt", new Date());
                accounts.insertOne(account);
            } else {
                // Actualizar saldo existente
                // Manejar _id que puede ser String o ObjectId
                Object idObj = account.get("_id");
                if (idObj instanceof String) {
                    accountId = (String) idObj;
                } else {
                    accountId = idObj.toString();
                }
                
                double currentBalance = account.getDouble("balance");
                newBalance = currentBalance + amount;
                
                accounts.updateOne(
                    new Document("userId", userId),
                    new Document("$set", new Document("balance", newBalance))
                );
            }
            
            // Registrar movimiento
            mongoDb.getCollection("account_movements").insertOne(
                new Document("_id", UUID.randomUUID().toString())
                    .append("accountId", accountId)
                    .append("type", "credito")
                    .append("amount", amount)
                    .append("balance", newBalance)
                    .append("description", "Carga de saldo")
                    .append("ts", new Date())
            );
            
            logger.info("Saldo cargado a cuenta de usuario {}: ${}", userId, amount);
            return true;
            
        } catch (Exception e) {
            logger.error("Error cargando saldo", e);
            return false;
        }
    }
}

