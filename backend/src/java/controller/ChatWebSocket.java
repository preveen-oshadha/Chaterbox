package controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/chat/{userId}")
public class ChatWebSocket {

    private static final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
    private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory("WhatsAppPU");

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") int userId) {
        sessions.put(userId, session);
        updateUserStatus(userId, true);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("userId") int senderId) {
        try {
            // Manual parsing to avoid dependency issues
            String cleanedJson = message.trim();
            int chatId = extractInt(cleanedJson, "chatId");
            int receiverId = extractInt(cleanedJson, "receiverId");
            String content = extractString(cleanedJson, "content");
            String type = extractString(cleanedJson, "type");
            if (type == null || type.isEmpty()) type = "TEXT";

            // Save to DB using Native SQL for maximum stability
            saveMessage(chatId, senderId, content, type);

            // Forward to receiver if online
            if (sessions.containsKey(receiverId)) {
                sessions.get(receiverId).getBasicRemote().sendText(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") int userId) {
        sessions.remove(userId);
        updateUserStatus(userId, false);
    }

    @OnError
    public void onError(Throwable error) {
        error.printStackTrace();
    }

    private void updateUserStatus(int userId, boolean online) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createNativeQuery("UPDATE user SET is_online = ? WHERE id = ?")
                    .setParameter(1, online).setParameter(2, userId).executeUpdate();
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
        } finally {
            em.close();
        }
    }

    private void saveMessage(int chatId, int senderId, String content, String type) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO message (chat_id, sender_id, content, sent_at, is_read, message_type) VALUES (?, ?, ?, NOW(), ?, ?)")
                    .setParameter(1, chatId)
                    .setParameter(2, senderId)
                    .setParameter(3, content)
                    .setParameter(4, false)
                    .setParameter(5, type)
                    .executeUpdate();
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    private int extractInt(String json, String key) {
        try {
            String pattern = "\"" + key + "\":";
            int start = json.indexOf(pattern) + pattern.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            String val = json.substring(start, end).trim().replace("\"", "");
            return Integer.parseInt(val);
        } catch (Exception e) { return 0; }
    }

    private String extractString(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern) + pattern.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) { return ""; }
    }
}
