package com.example.application;

import java.io.Serializable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.vaadin.collaborationengine.CollaborationEngineConfiguration;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.startup.ApplicationConfiguration;
import com.vaadin.flow.theme.Theme;

import jakarta.servlet.http.HttpSessionActivationListener;
import jakarta.servlet.http.HttpSessionEvent;

/**
 * The entry point of the Spring Boot application.
 *
 * Use the @PWA annotation make the application installable on phones, tablets
 * and some desktop browsers.
 *
 */
@SpringBootApplication
@Theme(value = "pg")
@Push
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
    @Bean
    CollaborationEngineConfiguration ceConfig(PgBackend backend) {
        CollaborationEngineConfiguration config = new CollaborationEngineConfiguration(event -> {
           System.out.println(event); 
        });
        
        config.setBackend(backend);
        
        
        return config;
    }

    // https://github.com/vaadin/collaboration-engine/issues/75
    private static final class SessionCleanupListener
            implements HttpSessionActivationListener, Serializable {
        private final VaadinSession session;
        private final Class<?> clz;

        public SessionCleanupListener(VaadinSession session) {
            this.session = session;

            try {
                // Cannot directly access package-private class
                clz = Class.forName(
                        "com.vaadin.collaborationengine.ServiceDestroyDelegate");
            } catch (ClassNotFoundException e1) {
                throw new RuntimeException(e1);
            }
        }

        public void sessionWillPassivate(HttpSessionEvent se) {
            if (session.getLockInstance() == null) {
                return;
            }
            session.accessSynchronously(() -> {
                session.setAttribute(clz, null);
                session.getRequestHandlers().stream()
                        .filter(handler -> handler.getClass().getName().equals(
                                "com.vaadin.collaborationengine.BeaconHandler"))
                        .findFirst().ifPresent(session::removeRequestHandler);
            });
        };
    }

    @Bean
    VaadinServiceInitListener serviceInitListener() {
        return serviceEvent -> {
            VaadinService service = serviceEvent.getSource();
            ApplicationConfiguration config = ApplicationConfiguration
                    .get(service.getContext());
            if (config.isProductionMode()
                    || config.isDevModeSessionSerializationEnabled()) {
                return;
            }
            service.addSessionInitListener(sessionEvent -> {
                VaadinSession session = sessionEvent.getSession();
                session.getSession().setAttribute("hack",
                        new SessionCleanupListener(session));
            });
        };
    }

}
