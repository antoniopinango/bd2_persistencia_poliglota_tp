package com.bd2.app.service;

import com.bd2.app.database.CassandraConnectionManager;
import com.bd2.app.database.MongoConnectionManager;
import com.bd2.app.model.User;
import com.bd2.app.dao.UserDAO;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Servicio simple de mensajería usando Cassandra
 */
public class MessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private final CqlSession session;
    private final UserDAO userDAO;
    
    public MessageService() {
        this.session = CassandraConnectionManager.getInstance().getSession();
        this.userDAO = new UserDAO();
    }
    
    /**
     * Envía un mensaje privado usando email (más user-friendly)
     */
    public boolean sendPrivateMessageByEmail(String fromUserId, String toEmail, String content) {
        try {
            // Buscar destinatario por email
            Optional<User> toUserOpt = userDAO.findByEmail(toEmail);
            
            if (toUserOpt.isEmpty()) {
                logger.warn("Usuario destinatario no encontrado: {}", toEmail);
                return false;
            }
            
            String toUserId = toUserOpt.get().getId();
            return sendPrivateMessage(fromUserId, toUserId, content);
            
        } catch (Exception e) {
            logger.error("Error enviando mensaje por email", e);
            return false;
        }
    }
    
    /**
     * Envía un mensaje privado (SIMPLE)
     */
    public boolean sendPrivateMessage(String fromUserId, String toUserId, String content) {
        try {
            UUID conversationId = generateConversationId(fromUserId, toUserId);
            UUID messageId = Uuids.timeBased();
            
            // Insertar mensaje en Cassandra
            String query = "INSERT INTO messages_by_conversation " +
                          "(conversation_id, ts, message_id, sender_id, content, type, metadata) " +
                          "VALUES (?, now(), ?, ?, ?, ?, ?)";
            
            PreparedStatement prepared = session.prepare(query);
            BoundStatement bound = prepared.bind(
                conversationId,
                messageId,
                UUID.fromString(fromUserId),
                content,
                "privado",
                Map.of("recipient", toUserId)
            );
            
            session.execute(bound);
            
            // Actualizar conversación para ambos usuarios
            updateUserConversation(fromUserId, conversationId, content);
            updateUserConversation(toUserId, conversationId, content);
            
            logger.info("Mensaje enviado de {} a {}", fromUserId, toUserId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error enviando mensaje", e);
            return false;
        }
    }
    
    /**
     * Actualiza la conversación del usuario (SIMPLE)
     */
    private void updateUserConversation(String userId, UUID conversationId, String snippet) {
        try {
            String query = "INSERT INTO conversations_by_user " +
                          "(user_id, last_activity_ts, conversation_id, last_message_snippet, unread_count) " +
                          "VALUES (?, now(), ?, ?, 1)";
            
            PreparedStatement prepared = session.prepare(query);
            BoundStatement bound = prepared.bind(
                UUID.fromString(userId),
                conversationId,
                snippet.substring(0, Math.min(100, snippet.length()))
            );
            
            session.execute(bound);
            
        } catch (Exception e) {
            logger.debug("Error actualizando conversación: {}", e.getMessage());
        }
    }
    
    /**
     * Genera ID de conversación consistente para dos usuarios
     */
    private UUID generateConversationId(String user1, String user2) {
        // Ordenar para que siempre genere el mismo ID
        String[] users = {user1, user2};
        Arrays.sort(users);
        String combined = users[0] + "_" + users[1];
        return UUID.nameUUIDFromBytes(combined.getBytes());
    }
    
    /**
     * Lista conversaciones de un usuario (SIMPLE)
     */
    public List<Map<String, Object>> getUserConversations(String userId) {
        try {
            List<Map<String, Object>> conversations = new ArrayList<>();
            
            String query = "SELECT conversation_id, last_message_snippet, unread_count " +
                          "FROM conversations_by_user WHERE user_id = ? LIMIT 20";
            
            PreparedStatement prepared = session.prepare(query);
            BoundStatement bound = prepared.bind(UUID.fromString(userId));
            
            ResultSet rs = session.execute(bound);
            
            for (Row row : rs) {
                Map<String, Object> conv = new HashMap<>();
                conv.put("conversationId", row.getUuid("conversation_id").toString());
                conv.put("lastMessage", row.getString("last_message_snippet"));
                conv.put("unreadCount", row.getInt("unread_count"));
                
                conversations.add(conv);
            }
            
            return conversations;
            
        } catch (Exception e) {
            logger.error("Error listando conversaciones", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene mensajes de una conversación (SIMPLE)
     */
    public List<Map<String, Object>> getConversationMessages(String conversationId, int limit) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            
            String query = "SELECT ts, sender_id, content, type " +
                          "FROM messages_by_conversation " +
                          "WHERE conversation_id = ? LIMIT ?";
            
            PreparedStatement prepared = session.prepare(query);
            BoundStatement bound = prepared.bind(UUID.fromString(conversationId), limit);
            
            ResultSet rs = session.execute(bound);
            
            for (Row row : rs) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("senderId", row.getUuid("sender_id").toString());
                msg.put("content", row.getString("content"));
                msg.put("type", row.getString("type"));
                
                messages.add(msg);
            }
            
            return messages;
            
        } catch (Exception e) {
            logger.error("Error obteniendo mensajes", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Obtiene mensajes de conversación usando email del otro usuario (más user-friendly)
     */
    public List<Map<String, Object>> getConversationMessagesByEmail(String currentUserId, String otherUserEmail, int limit) {
        try {
            // Buscar el otro usuario por email
            Optional<User> otherUserOpt = userDAO.findByEmail(otherUserEmail);
            
            if (otherUserOpt.isEmpty()) {
                logger.warn("Usuario no encontrado: {}", otherUserEmail);
                return new ArrayList<>();
            }
            
            String otherUserId = otherUserOpt.get().getId();
            
            // Generar ID de conversación
            UUID conversationId = generateConversationId(currentUserId, otherUserId);
            
            // Obtener mensajes
            List<Map<String, Object>> messages = getConversationMessages(conversationId.toString(), limit);
            
            // Agregar información del usuario al mensaje
            for (Map<String, Object> msg : messages) {
                String senderId = (String) msg.get("senderId");
                
                // Marcar si el mensaje es del usuario actual o del otro
                if (senderId.equals(currentUserId)) {
                    msg.put("sender", "Tú");
                } else {
                    msg.put("sender", otherUserEmail);
                }
            }
            
            return messages;
            
        } catch (Exception e) {
            logger.error("Error obteniendo mensajes por email", e);
            return new ArrayList<>();
        }
    }
}

