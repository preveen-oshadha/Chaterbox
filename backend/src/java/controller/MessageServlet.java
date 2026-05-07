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

@WebServlet(name = "MessageServlet", urlPatterns = {"/MessageServlet"})
public class MessageServlet extends HttpServlet {

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
        addCorsHeaders(response);
        response.setContentType("application/json;charset=UTF-8");
        
        String chatIdStr = request.getParameter("chatId");
        if (chatIdStr == null || chatIdStr.isEmpty()) {
            response.getWriter().write("[]");
            return;
        }
        
        EntityManagerFactory factory = getEMF();
        if (factory == null) {
            response.getWriter().write("{\"status\":\"error\", \"message\":\"Database factory failed\"}");
            return;
        }

        EntityManager em = factory.createEntityManager();
        try {
            int chatId = Integer.parseInt(chatIdStr);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

            // Switching to Numbered Parameter (?1) for EclipseLink Native Query
            Query query = em.createNativeQuery(
                "SELECT sender_id, content, sent_at, is_read, message_type FROM message WHERE chat_id = ?1 ORDER BY sent_at ASC");
            query.setParameter(1, chatId);
            List<Object[]> messages = query.getResultList();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < messages.size(); i++) {
                Object[] m = messages.get(i);
                
                String timeStr = "";
                if (m[2] != null) {
                    try {
                        if (m[2] instanceof java.util.Date) {
                            timeStr = sdf.format((java.util.Date)m[2]);
                        } else {
                            timeStr = m[2].toString().replace(" ", "T") + "Z";
                        }
                    } catch (Exception e) {
                        timeStr = m[2].toString();
                    }
                }

                json.append("{")
                    .append("\"senderId\":").append(m[0]).append(",")
                    .append("\"content\":\"").append(m[1].toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")).append("\",")
                    .append("\"sentAt\":\"").append(timeStr).append("\",")
                    .append("\"isRead\":").append(m[3] != null && (m[3].toString().equals("1") || m[3].toString().equalsIgnoreCase("true"))).append(",")
                    .append("\"type\":\"").append(m[4] != null ? m[4].toString() : "TEXT").append("\"")
                    .append("}");
                if (i < messages.size() - 1) json.append(",");
            }
            json.append("]");
            response.getWriter().write(json.toString());
        } catch (Exception e) {
            response.getWriter().write("[]");
        } finally {
            em.close();
        }
    }
}
