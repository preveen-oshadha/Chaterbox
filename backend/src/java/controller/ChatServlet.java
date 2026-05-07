package controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.Query;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

@WebServlet(name = "ChatServlet", urlPatterns = {"/ChatServlet"})
public class ChatServlet extends HttpServlet {

    private static EntityManagerFactory emf = null;

    private synchronized EntityManagerFactory getEMF() {
        if (emf == null) {
            try {
                emf = Persistence.createEntityManagerFactory("WhatsAppPU");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return emf;
    }

    private void addCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        addCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        addCorsHeaders(response);
        response.setContentType("application/json;charset=UTF-8");
        
        String action = request.getParameter("action");
        String userIdStr = request.getParameter("userId");
        
        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            response.getWriter().write("[]");
            return;
        }
        
        EntityManagerFactory factory = getEMF();
        if (factory == null) {
            response.getWriter().write("{\"status\":\"error\", \"message\":\"Database factory failed\"}");
            return;
        }

        try {
            int userId = Integer.parseInt(userIdStr.trim());
            if ("getChats".equals(action)) {
                handleGetChats(userId, response);
            } else if ("getProfile".equals(action)) {
                handleGetProfile(userId, response);
            } else if ("updateProfile".equals(action)) {
                handleUpdateProfile(request, response);
            } else if ("addContact".equals(action)) {
                handleAddContact(request, response);
            }
        } catch (Exception e) {
            response.getWriter().write("{\"status\":\"error\", \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleGetChats(int userId, HttpServletResponse response) throws IOException {
        EntityManager em = getEMF().createEntityManager();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            
            String sql = "SELECT c.id, " +
                         "u1.name as u1name, u1.is_online as u1online, u1.id as u1id, " +
                         "u2.name as u2name, u2.is_online as u2online, u2.id as u2id, " +
                         "c.user1_id, c.user2_id " +
                         "FROM chat c " +
                         "LEFT JOIN user u1 ON c.user1_id = u1.id " +
                         "LEFT JOIN user u2 ON c.user2_id = u2.id " +
                         "WHERE c.user1_id = ?1 OR c.user2_id = ?1";
            
            Query query = em.createNativeQuery(sql);
            query.setParameter(1, userId);
            
            List<Object[]> chats = query.getResultList();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < chats.size(); i++) {
                Object[] row = chats.get(i);
                
                // BULLETPROOF PARSING
                String rawId = row[0] != null ? row[0].toString() : "0";
                long chatId = Long.parseLong(rawId);
                
                String rawU1Id = row[3] != null ? row[3].toString() : "0";
                long u1Id = Long.parseLong(rawU1Id);
                
                String rawU2Id = row[6] != null ? row[6].toString() : "0";
                long u2Id = Long.parseLong(rawU2Id);
                
                String otherName = "Unknown";
                boolean isOnline = false;
                long otherId = 0;
                
                // Compare using string for absolute safety
                if (String.valueOf(u1Id).equals(String.valueOf(userId))) {
                    otherName = row[4] != null ? row[4].toString() : "User " + u2Id;
                    isOnline = row[5] != null && (row[5].toString().equals("1") || row[5].toString().equalsIgnoreCase("true"));
                    otherId = u2Id;
                } else {
                    otherName = row[1] != null ? row[1].toString() : "User " + u1Id;
                    isOnline = row[2] != null && (row[2].toString().equals("1") || row[2].toString().equalsIgnoreCase("true"));
                    otherId = u1Id;
                }
                
                Query msgQuery = em.createNativeQuery(
                    "SELECT content, sent_at FROM message WHERE chat_id = ?1 ORDER BY sent_at DESC LIMIT 1");
                msgQuery.setParameter(1, chatId);
                List<Object[]> lastMsgs = msgQuery.getResultList();
                
                String lastMsg = "No messages yet";
                String lastTime = "";
                
                if (!lastMsgs.isEmpty()) {
                    Object[] mRow = lastMsgs.get(0);
                    lastMsg = mRow[0] != null ? mRow[0].toString() : "";
                    lastMsg = lastMsg.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
                    if (mRow[1] != null) {
                        try {
                            lastTime = sdf.format(mRow[1]);
                        } catch (Exception e) {
                            lastTime = mRow[1].toString();
                        }
                    }
                }

                json.append("{")
                    .append("\"id\":").append(chatId).append(",")
                    .append("\"receiverId\":").append(otherId).append(",")
                    .append("\"name\":\"").append(otherName).append("\",")
                    .append("\"lastMessage\":\"").append(lastMsg).append("\",")
                    .append("\"time\":\"").append(lastTime).append("\",")
                    .append("\"isOnline\":").append(isOnline)
                    .append("}");
                if (i < chats.size() - 1) json.append(",");
            }
            json.append("]");
            response.getWriter().write(json.toString());
        } catch (Exception e) {
            e.printStackTrace(); // Log error for developers
            response.getWriter().write("[]");
        } finally {
            em.close();
        }
    }

    private void handleGetProfile(int userId, HttpServletResponse response) throws IOException {
        EntityManager em = getEMF().createEntityManager();
        try {
            Query query = em.createNativeQuery("SELECT name, mobile, status, is_online FROM user WHERE id = ?1");
            query.setParameter(1, userId);
            List<Object[]> results = query.getResultList();
            
            if (!results.isEmpty()) {
                Object[] data = results.get(0);
                String name = data[0] != null ? data[0].toString().replace("\"", "\\\"") : "";
                String mobile = data[1] != null ? data[1].toString() : "";
                String status = data[2] != null ? data[2].toString().replace("\"", "\\\"") : "";
                boolean online = data[3] != null && (data[3].toString().equals("1") || data[3].toString().equalsIgnoreCase("true"));
                
                String json = "{" +
                        "\"name\":\"" + name + "\"," +
                        "\"mobile\":\"" + mobile + "\"," +
                        "\"status\":\"" + status + "\"," +
                        "\"isOnline\":" + online +
                        "}";
                response.getWriter().write(json);
            }
        } finally {
            em.close();
        }
    }

    private void handleUpdateProfile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = Integer.parseInt(request.getParameter("userId"));
        String name = request.getParameter("name");
        String status = request.getParameter("status");

        EntityManager em = getEMF().createEntityManager();
        try {
            em.getTransaction().begin();
            if (name != null) {
                em.createNativeQuery("UPDATE user SET name = ?1 WHERE id = ?2")
                        .setParameter(1, name).setParameter(2, userId).executeUpdate();
            }
            if (status != null) {
                em.createNativeQuery("UPDATE user SET status = ?1 WHERE id = ?2")
                        .setParameter(1, status).setParameter(2, userId).executeUpdate();
            }
            em.getTransaction().commit();
            response.getWriter().write("{\"status\":\"success\"}");
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            response.getWriter().write("{\"status\":\"error\"}");
        } finally {
            em.close();
        }
    }

    private void handleAddContact(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = Integer.parseInt(request.getParameter("userId"));
        String mobile = request.getParameter("mobile");

        EntityManager em = getEMF().createEntityManager();
        try {
            em.getTransaction().begin();
            Query tQuery = em.createNativeQuery("SELECT id FROM user WHERE mobile = ?1");
            tQuery.setParameter(1, mobile);
            List<?> targets = tQuery.getResultList();

            if (targets.isEmpty()) {
                response.getWriter().write("{\"status\":\"error\", \"message\":\"User not found\"}");
                return;
            }

            long targetId = Long.parseLong(targets.get(0).toString());
            if (targetId == (long)userId) {
                response.getWriter().write("{\"status\":\"error\", \"message\":\"Cannot chat with yourself\"}");
                return;
            }

            Query eQuery = em.createNativeQuery("SELECT id FROM chat WHERE (user1_id = ?1 AND user2_id = ?2) OR (user1_id = ?2 AND user2_id = ?1)");
            eQuery.setParameter(1, userId).setParameter(2, targetId);
            List<?> existing = eQuery.getResultList();

            if (!existing.isEmpty()) {
                response.getWriter().write("{\"status\":\"success\", \"message\":\"Chat already exists\"}");
                return;
            }

            em.createNativeQuery("INSERT INTO chat (user1_id, user2_id) VALUES (?1, ?2)")
                .setParameter(1, userId).setParameter(2, targetId)
                .executeUpdate();

            em.getTransaction().commit();
            response.getWriter().write("{\"status\":\"success\", \"message\":\"Contact added\"}");
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            response.getWriter().write("{\"status\":\"error\", \"message\":\"Error adding contact\"}");
        } finally {
            em.close();
        }
    }
}
