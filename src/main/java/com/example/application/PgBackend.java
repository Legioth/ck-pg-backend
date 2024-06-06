package com.example.application;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import org.postgresql.jdbc.PgConnection;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.example.application.PgNotifyListener.ListenResult;
import com.vaadin.collaborationengine.Backend;
import com.vaadin.collaborationengine.MembershipListener;
import com.vaadin.flow.shared.Registration;
import com.zaxxer.hikari.util.DriverDataSource;

@Service
public class PgBackend extends Backend {

    private final class Subscription {
        private final String logId;
        private final BiConsumer<UUID, String> eventConsumer;

        private long lastSeenId = -1;

        public Subscription(String logId, UUID newerThan,
                BiConsumer<UUID, String> eventConsumer) {
            this.logId = logId;
            this.eventConsumer = eventConsumer;

            if (newerThan != null) {
                lastSeenId = eventLogs.getSequenceIdByEventId(newerThan);
            }
        }

        public void handleNotification() {
            try {
                lock.lock();
                List<EventLogEntry> events = eventLogs
                        .findAllNewerThan(lastSeenId, logId);
                events.forEach(event -> {
                    eventConsumer.accept(event.getEventId(),
                            event.getPayload());
                    lastSeenId = event.getId();
                });
            } finally {
                lock.unlock();
            }
        }
    }

    private final class EventLogImplementation implements EventLog {
        private final String logId;

        private EventLogImplementation(String logId) {
            this.logId = logId;
        }

        @Override
        public void truncate(UUID olderThan) {
            System.out
                    .println("Truncate is left as an exercise for the reader");
        }

        @Override
        public Registration subscribe(UUID newerThan,
                BiConsumer<UUID, String> eventConsumer)
                throws EventIdNotFoundException {
            try {
                lock.lock();

                Subscription subscription = new Subscription(logId, newerThan,
                        eventConsumer);
                Set<Subscription> subscriptions = logs.computeIfAbsent(logId,
                        x -> new HashSet<>());
                subscriptions.add(subscription);

                ListenResult listenResult = notifyListener.listen(logId,
                        ignore -> subscription.handleNotification());
                // Deliver initial updates
                listenResult.whenRegistered()
                        .thenRun(subscription::handleNotification);

                return () -> {
                    try {
                        lock.lock();

                        subscriptions.remove(subscription);
                        if (subscriptions.isEmpty()) {
                            logs.remove(logId);

                            listenResult.unregister();
                        }
                    } finally {
                        lock.unlock();
                    }
                };
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void submitEvent(UUID trackingId, String eventPayload) {
            eventLogs.submitEvent(trackingId, logId, eventPayload);
        }
    }

    private final UUID nodeId = UUID.randomUUID();

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Set<Subscription>> logs = new HashMap<>();

    private final PgNotifyListener notifyListener;

    private final EventLogRepository eventLogs;

    private final SnapshotRepository snapshots;

    public PgBackend(EventLogRepository eventLogs, SnapshotRepository snapshots,
            DataSourceProperties props) {
        this.eventLogs = eventLogs;
        this.snapshots = snapshots;

        PgConnection pgConnection = openPgConnection(props);
        notifyListener = new PgNotifyListener(pgConnection,
                (channel, payload) -> eventLogs.notify(channel));
    }

    private static PgConnection openPgConnection(DataSourceProperties props) {
        DriverDataSource ds = new DriverDataSource(props.determineUrl(),
                props.determineDriverClassName(), new Properties(),
                props.determineUsername(), props.determinePassword());

        try {
            return ds.getConnection().unwrap(PgConnection.class);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @EventListener({ ContextClosedEvent.class })
    public void onApplicationEvent(ContextClosedEvent event) {
        notifyListener.close();
    }

    @Override
    public EventLog openEventLog(String logId) {
        EventLogImplementation eventLogImplementation = new EventLogImplementation(
                logId);
        return eventLogImplementation;
    }

    @Override
    public Registration addMembershipListener(
            MembershipListener membershipListener) {
        System.out.println(
                "Member ship listeners are left as an exersice for the reader");
        return () -> {
        };
    }

    @Override
    public UUID getNodeId() {
        return nodeId;
    }

    @Override
    public CompletableFuture<Snapshot> loadLatestSnapshot(String name) {
        Snapshot snapshot = snapshots.findById(name)
                .map(SnapshotEntity::asSnapshot).orElse(null);
        return CompletableFuture.completedFuture(snapshot);
    }

    @Override
    public CompletableFuture<Void> replaceSnapshot(String name, UUID expectedId,
            UUID newId, String payload) {
        if (expectedId == null) {
            snapshots.insert(name, newId, payload);
        } else {
            snapshots.update(name, expectedId, newId, payload);
        }
        return CompletableFuture.completedFuture(null);
    }
}
