package com.example.application;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;

@Repository
public interface SnapshotRepository
        extends JpaRepository<SnapshotEntity, String> {

    @Transactional
    @Modifying
    @Query("update Snapshot set payload = :payload, snapshotId = :newId where logId = :name and snapshotId = :expectedId")
    void update(String name, UUID expectedId, UUID newId, String payload);

    @Transactional
    @Modifying
    @Query(value = "insert into Snapshot (log_id, snapshot_id, payload) values(?,?,?)", nativeQuery = true)
    void insert(String name, UUID newId, String payload);
}
