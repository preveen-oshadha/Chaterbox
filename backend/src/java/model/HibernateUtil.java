package model;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import java.lang.reflect.Proxy;

public class HibernateUtil {

    private static final EntityManagerFactory emf;

    static {
        try {
            // This uses the built-in GlassFish persistence engine (EclipseLink)
            emf = Persistence.createEntityManagerFactory("WhatsAppPU");
        } catch (Throwable ex) {
            System.err.println("CRITICAL: JPA Initialization failed!");
            ex.printStackTrace();
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        // We return a proxy that translates Hibernate calls to JPA calls
        // This allows your existing AuthServlet code to work without changes!
        return (SessionFactory) Proxy.newProxyInstance(
                SessionFactory.class.getClassLoader(),
                new Class[]{SessionFactory.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("openSession")) {
                        return emf.createEntityManager().unwrap(Session.class);
                    }
                    return null;
                }
        );
    }
}
