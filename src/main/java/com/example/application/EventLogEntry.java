package com.example.application;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class EventLogEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id;

    private UUID eventId;

    @Column(columnDefinition = "text")
    private String payload;

    private String logId;

    public EventLogEntry() {
        // Hibernate constructor
    }

    public EventLogEntry(UUID eventId, String logId, String payload) {
        this.eventId = eventId;
        this.logId = logId;
        this.payload = payload;
    }

    public long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getPayload() {
        return payload;
    }

    public String getLogId() {
        return logId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }
}
