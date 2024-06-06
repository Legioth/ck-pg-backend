package com.example.application;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;

import com.vaadin.flow.shared.Registration;

public class PgNotifyListener implements AutoCloseable {
    public record ListenResult(CompletableFuture<Void> whenRegistered,
            Registration unregister) {
    }

    private class Channel {
        private CompletableFuture<Void> registrationListener;

        private final String name;

        private final ReentrantLock lock = new ReentrantLock();
        private final Set<Consumer<String>> listeners = new HashSet<>();

        public Channel(String name) {
            this.name = name;
        }

        public ListenResult addListener(Consumer<String> listener) {
            try {
                lock.lock();
                listeners.add(listener);

                if (registrationListener == null) {
                    registrationListener = new CompletableFuture<Void>();

                    scheduleUpdate(() -> {
                        runUpdate("LISTEN", name);
                        registrationListener.complete(null);
                    });
                }

                return new ListenResult(registrationListener,
                        () -> removeListener(listener));
            } finally {
                lock.unlock();
            }
        }

        private void removeListener(Consumer<String> listener) {
            try {
                lock.lock();

                if (listeners.remove(listener) && listeners.isEmpty()) {
                    registrationListener = null;
                    scheduleUpdate(() -> {
                        runUpdate("UNLISTEN ", name);
                    });
                }
            } finally {
                lock.unlock();
            }
        }

        public void notifyListeners(String payload) {
            copyListeners().forEach(listener -> listener.accept(payload));
        }

        private Collection<Consumer<String>> copyListeners() {
            try {
                lock.lock();
                return new HashSet<>(listeners);
            } finally {
                lock.unlock();
            }
        }
    }

    private final String internalNotifyId = UUID.randomUUID().toString();

    @SuppressWarnings("unused")
    // Just hold a reference to prevent it from being GC'ed
    private final Thread pollerThread;
    private final BiConsumer<String, String> notifier;
    private final PgConnection connection;

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final Queue<Runnable> pendingUpdates = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public PgNotifyListener(PgConnection connection,
            BiConsumer<String, String> notifier) {
        this.connection = connection;
        this.notifier = notifier;

        // Make it possible to interrupt the poller thread when it should run
        // updates against its JDBC connection
        runUpdate("LISTEN", internalNotifyId);

        pollerThread = Thread.startVirtualThread(() -> {
            try {
                while (true) {
                    PGNotification[] notifications = connection
                            .getNotifications(0);
                    
                    Runnable action;
                    while ((action = pendingUpdates.poll()) != null) {
                        action.run();
                    }

                    if (shuttingDown.get()) {
                        return;
                    }

                    for (PGNotification notification : notifications) {
                        Channel channel = channels.get(notification.getName());
                        if (channel != null) {
                            channel.notifyListeners(
                                    notification.getParameter());
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void scheduleUpdate(Runnable command) {
        pendingUpdates.add(command);
        notify(internalNotifyId);
    }

    public ListenResult listen(String channelName, Consumer<String> listener) {
        return channels.computeIfAbsent(channelName, Channel::new)
                .addListener(listener);
    }

    private void runUpdate(String command, String channel) {
        try (Statement statement = connection.createStatement()) {
            // LISTEN and UNLISTEN doesn't support prepared statements
            statement.execute(
                    command + " " + connection.escapeIdentifier(channel));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void notify(String channel) {
        notify(channel, null);
    }

    public void notify(String channel, String payload) {
        notifier.accept(channel, payload);
    }

    @Override
    public void close() {
        if (shuttingDown.getAndSet(true) == false) {
            pendingUpdates.clear();
            pendingUpdates.add(() -> {
                try {
                    connection.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            notify(internalNotifyId);
        }
    }
}
