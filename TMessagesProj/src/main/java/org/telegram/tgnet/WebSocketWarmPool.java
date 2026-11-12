package org.telegram.tgnet;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Unauthenticated sockets only. A leased connection is NEVER returned to this pool. */
final class WebSocketWarmPool<K, C extends WebSocketWarmPool.Connection> {
    interface Connection {
        boolean isOpen();
        void close();
    }
    interface Factory<C> { C connect(BooleanSupplier cancelled) throws Exception; }
    private static final long MAX_AGE_MS = 90_000;
    private static final long INACTIVE_MS = 120_000;
    private final int globalLimit;
    private final Executor executor;
    private final LongSupplier clock;
    private final Map<K, Bucket> buckets = new LinkedHashMap<>();

    private final class Idle {
        final C connection;
        final long created;
        Idle(C connection) { this.connection = connection; created = clock.getAsLong(); }
    }
    private final class Bucket {
        final ArrayDeque<Idle> idle = new ArrayDeque<>();
        Factory<C> factory;
        int target, pending, failures;
        long used, retryAt;
        volatile boolean removed;
    }

    WebSocketWarmPool(int globalLimit, Executor executor, LongSupplier clock) {
        this.globalLimit = globalLimit;
        this.executor = executor;
        this.clock = clock;
    }

    synchronized C take(K key) {
        prune();
        Bucket bucket = buckets.get(key);
        if (bucket == null) return null;
        bucket.used = clock.getAsLong();
        while (!bucket.idle.isEmpty()) {
            C connection = bucket.idle.removeFirst().connection;
            if (connection.isOpen()) return connection;
            connection.close();
        }
        return null;
    }

    synchronized void prepare(K key, int target, Factory<C> factory) {
        if (target <= 0) return;
        prune();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            // Bound keys as well as sockets; failed/inactive routes must not accumulate forever.
            if (buckets.size() >= globalLimit) return;
            bucket = new Bucket();
            buckets.put(key, bucket);
        }
        bucket.used = clock.getAsLong();
        bucket.target = Math.min(8, target);
        if (bucket.factory == null) bucket.factory = factory;
        refill(bucket);
    }

    synchronized void maintain() {
        prune();
        for (Bucket bucket : buckets.values()) refill(bucket);
    }

    private void refill(Bucket bucket) {
        if (clock.getAsLong() < bucket.retryAt) return;
        while (!bucket.removed && clock.getAsLong() >= bucket.retryAt
                && bucket.idle.size() + bucket.pending < bucket.target && reserved() < globalLimit) {
            bucket.pending++;
            try {
                executor.execute(() -> connect(bucket));
            } catch (RuntimeException error) {
                bucket.pending--;
                bucket.retryAt = clock.getAsLong() + 5000;
                break;
            }
        }
    }

    private void connect(Bucket bucket) {
        C connection = null;
        try {
            if (!bucket.removed) connection = bucket.factory.connect(() -> bucket.removed);
        } catch (Exception ignored) { }
        synchronized (this) {
            bucket.pending--;
            if (bucket.removed) {
                if (connection != null) connection.close();
                return;
            }
            if (connection != null && connection.isOpen()) {
                bucket.idle.addLast(new Idle(connection));
                bucket.failures = 0;
                bucket.retryAt = 0;
            } else {
                if (connection != null) connection.close();
                bucket.failures = Math.min(bucket.failures + 1, 4);
                bucket.retryAt = clock.getAsLong() + Math.min(60_000L, 5000L << (bucket.failures - 1));
            }
        }
    }

    private void prune() {
        long now = clock.getAsLong();
        Iterator<Bucket> bucketsIterator = buckets.values().iterator();
        while (bucketsIterator.hasNext()) {
            Bucket bucket = bucketsIterator.next();
            if (now - bucket.used >= INACTIVE_MS) {
                remove(bucket);
                bucketsIterator.remove();
                continue;
            }
            Iterator<Idle> idle = bucket.idle.iterator();
            while (idle.hasNext()) {
                Idle item = idle.next();
                if (now - item.created >= MAX_AGE_MS || !item.connection.isOpen()) {
                    item.connection.close();
                    idle.remove();
                }
            }
        }
    }

    synchronized void reset() {
        for (Bucket bucket : buckets.values()) remove(bucket);
        buckets.clear();
    }

    private void remove(Bucket bucket) {
        bucket.removed = true;
        for (Idle idle : bucket.idle) idle.connection.close();
        bucket.idle.clear();
    }

    private int reserved() {
        int count = 0;
        for (Bucket bucket : buckets.values()) count += bucket.idle.size() + bucket.pending;
        return count;
    }

    synchronized int idleCount() {
        int count = 0;
        for (Bucket bucket : buckets.values()) count += bucket.idle.size();
        return count;
    }
    synchronized int pendingCount() { return reserved() - idleCount(); }
    synchronized boolean isEmpty() { return buckets.isEmpty(); }
}
