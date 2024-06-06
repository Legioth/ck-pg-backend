package com.example.application;

import java.util.UUID;

import com.vaadin.collaborationengine.Backend.Snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name = "Snapshot")
public class SnapshotEntity {
    @Id
    private String logId;

    private UUID snapshotId;

    @Column(columnDefinition = "text")
    private String payload;

    public SnapshotEntity() {
        // Hibernate constructor
    }

    public SnapshotEntity(String logId, Snapshot snapshot) {
        this.logId = logId;
        this.snapshotId = snapshot.getId();
        this.payload = snapshot.getPayload();
    }

    public Snapshot asSnapshot() {
        return new Snapshot(snapshotId, payload);
    }

}
