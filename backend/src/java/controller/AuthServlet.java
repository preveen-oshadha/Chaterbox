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
import java.util.List;

@WebServlet(name = "AuthServlet", urlPatterns = {"/AuthServlet"})
public class AuthServlet extends HttpServlet {

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
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        addCorsHeaders(response);
        response.setContentType("application/json;charset=UTF-8");
        
        String action = request.getParameter("action");
        
        EntityManagerFactory factory = getEMF();
        if (factory == null) {
            response.getWriter().write("{\"status\":\"error\", \"message\":\"Auth Database failed\"}");
            return;
        }

        if ("signup".equals(action)) {
            handleSignup(request, response);
        } else if ("login".equals(action)) {
            handleLogin(request, response);
        }
    }

    private void handleSignup(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = request.getParameter("name");
        String mobile = request.getParameter("mobile");
        String password = request.getParameter("password");

        if (name == null || mobile == null || password == null) {
            response.getWriter().write("{\"status\":\"error\", \"message\":\"Missing parameters\"}");
            return;
        }

        EntityManager em = getEMF().createEntityManager();
        try {
            em.getTransaction().begin();
            Query checkQuery = em.createNativeQuery("SELECT id FROM user WHERE mobile = ?1");
            checkQuery.setParameter(1, mobile.trim());
            List<?> existing = checkQuery.getResultList();
            
            if (!existing.isEmpty()) {
                response.getWriter().write("{\"status\":\"error\", \"message\":\"Mobile number already exists\"}");
                return;
            }

            em.createNativeQuery("INSERT INTO user (name, mobile, password, status, is_online) VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, name.trim())
                .setParameter(2, mobile.trim())
                .setParameter(3, password.trim())
                .setParameter(4, "Hey there! I am using MYCHAT")
                .setParameter(5, false)
                .executeUpdate();

            em.getTransaction().commit();
            response.getWriter().write("{\"status\":\"success\"}");
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            response.getWriter().write("{\"status\":\"error\", \"message\":\"" + e.getMessage() + "\"}");
        } finally {
            em.close();
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String mobile = request.getParameter("mobile");
        String password = request.getParameter("password");

        EntityManager em = getEMF().createEntityManager();
        try {
            Query loginQuery = em.createNativeQuery("SELECT id, name FROM user WHERE mobile = ?1 AND password = ?2");
            loginQuery.setParameter(1, mobile.trim()).setParameter(2, password.trim());
            List<Object[]> results = loginQuery.getResultList();

            if (!results.isEmpty()) {
                Object[] user = results.get(0);
                response.getWriter().write("{\"status\":\"success\", \"userId\":" + user[0] + ", \"name\":\"" + user[1] + "\"}");
            } else {
                response.getWriter().write("{\"status\":\"error\", \"message\":\"Invalid credentials\"}");
            }
        } catch (Exception e) {
            response.getWriter().write("{\"status\":\"error\", \"message\":\"" + e.getMessage() + "\"}");
        } finally {
            em.close();
        }
    }
}
