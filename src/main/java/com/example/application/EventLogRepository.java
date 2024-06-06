package com.example.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EventLogRepository extends JpaRepository<EventLogEntry, Long> {

    @Query(value = "SELECT pg_notify(?, '')", nativeQuery = true)
    void notify(String channel);

    default void submitEvent(UUID trackingId, String logId,
            String eventPayload) {
        EventLogEntry eventLogEntry = new EventLogEntry(trackingId, logId,
                eventPayload);
        save(eventLogEntry);

        notify(logId);
    }

    @Query("select id from EventLogEntry where eventId = :eventId")
    long getSequenceIdByEventId(UUID eventId);

    @Query("from EventLogEntry where id > :sequenceId and logId = :logId")
    List<EventLogEntry> findAllNewerThan(long sequenceId, String logId);
}
