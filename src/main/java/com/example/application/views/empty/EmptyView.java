package com.example.application.views.empty;

import java.util.UUID;

import com.vaadin.collaborationengine.CollaborationEngine;
import com.vaadin.collaborationengine.CollaborationMap;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.shared.Registration;

@PageTitle("Empty")
@Route(value = "")
@RouteAlias(value = "")
public class EmptyView extends VerticalLayout {

    public EmptyView() {
        String userId = UUID.randomUUID().toString();
        
        Button clickCounter = new Button("Initializing");
        clickCounter.setEnabled(false);
        
        add(new Span("User id: " + userId), clickCounter);
        
        CollaborationEngine.getInstance().openTopicConnection(this, "test", new UserInfo(userId), connection -> {
            CollaborationMap map = connection.getNamedMap("values");
            
            map.subscribe(event -> {
                clickCounter.setText("Click count: " + event.getValue(Integer.class));

            });
            
            Integer count = map.get("value", Integer.class);
            if (count == null) {
                map.replace("value", null, Integer.valueOf(0));
            }
            
            clickCounter.setEnabled(true);
            Registration registration = clickCounter.addClickListener(event -> incrementValue(map));
            
            return () -> {
                clickCounter.setEnabled(false);
                registration.remove();
            };
        });
    }

    private void incrementValue(CollaborationMap map) {
        Integer count = map.get("value", Integer.class);
        int newCount = count != null ? count.intValue() + 1 : 1;
        
        map.replace("value", count, newCount).thenAccept(success -> {
           if (!success.booleanValue()) {
               incrementValue(map);
           }
        });
    }
}
