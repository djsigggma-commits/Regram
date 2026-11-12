package org.telegram.tgnet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class WebSocketWarmPoolTest {
    static final class Socket implements WebSocketWarmPool.Connection {
        boolean open = true;
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        @Override public void execute(Runnable task) { jobs.add(task); }
        void drain() {
            int count = 0;
            while (!jobs.isEmpty()) {
                if (++count > 100) throw new AssertionError("Unbounded refill loop");
                jobs.removeFirst().run();
            }
        }
    }
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }

    public static void main(String[] args) {
        AtomicLong clock = new AtomicLong();
        Queue queue = new Queue();
        WebSocketWarmPool<String, Socket> pool = new WebSocketWarmPool<>(16, queue, clock::get);
        List<Socket> created = new ArrayList<>();
        WebSocketWarmPool.Factory<Socket> factory = cancelled -> {
            check(!cancelled.getAsBoolean());
            Socket socket = new Socket(); created.add(socket); return socket;
        };
        pool.prepare("dc2-media-production-cf", 4, factory);
        check(pool.pendingCount() == 4 && pool.idleCount() == 0);
        pool.prepare("dc2-media-production-cf", 4, factory);
        check(queue.jobs.size() == 4); // duplicate requests cannot overfill the target
        queue.drain();
        check(pool.idleCount() == 4 && pool.pendingCount() == 0);
        check(pool.take("dc2-media-test-cf") == null); // backend/media/routes stay isolated
        Set<Socket> borrowed = new HashSet<>();
        for (int i = 0; i < 4; i++) borrowed.add(pool.take("dc2-media-production-cf"));
        check(borrowed.size() == 4 && !borrowed.contains(null));
        check(pool.take("dc2-media-production-cf") == null);
        pool.prepare("dc2-media-production-cf", 4, factory);
        queue.drain();
        check(pool.idleCount() == 4 && created.size() == 8);
        pool.reset();
        check(pool.isEmpty() && pool.idleCount() == 0);
        for (Socket socket : borrowed) check(socket.open); // reset never closes an active lease
        for (int i = 4; i < created.size(); i++) check(!created.get(i).open);

        // Closed or 90-second-old sockets are rejected before use.
        pool.prepare("dc1", 4, factory); queue.drain();
        created.get(created.size() - 1).close();
        for (int i = 0; i < 3; i++) check(pool.take("dc1").isOpen());
        check(pool.take("dc1") == null); // dead sockets are never leased
        pool.prepare("dc1", 4, factory); queue.drain();
        clock.set(90_000);
        check(pool.take("dc1") == null && pool.idleCount() == 0);
        pool.prepare("dc1", 4, factory); queue.drain();
        check(pool.idleCount() == 4);
        clock.set(210_000); pool.maintain();
        check(pool.isEmpty() && queue.jobs.isEmpty()); // no eternal background warming

        // Reset invalidates in-flight jobs; old generations cannot reinsert a live socket.
        pool.prepare("dc3", 4, factory); pool.reset(); queue.drain();
        check(pool.isEmpty() && pool.idleCount() == 0);
        Socket late = new Socket();
        pool.prepare("dc3", 1, cancelled -> {
            pool.reset(); check(cancelled.getAsBoolean()); return late;
        });
        queue.drain(); check(!late.open && pool.isEmpty());

        // A failed wave backs off rather than immediately launching another wave.
        pool.prepare("unavailable", 4, cancelled -> null); queue.drain();
        pool.maintain(); check(queue.jobs.isEmpty());
        clock.addAndGet(60_000); pool.maintain();
        check(queue.jobs.size() == 4); queue.drain(); pool.reset();

        // Four per key, but never more than the global socket+pending budget.
        for (int dc = 0; dc < 20; dc++) pool.prepare("route-" + dc, 4, factory);
        check(pool.pendingCount() == 16 && queue.jobs.size() == 16);
        queue.drain(); check(pool.idleCount() == 16);
        pool.reset(); check(pool.isEmpty());
        pool.prepare("no-preconnect", 0, factory);
        check(pool.isEmpty() && queue.jobs.isEmpty());

        // Executor rejection also releases reservations and backs off.
        WebSocketWarmPool<String, Socket> rejected = new WebSocketWarmPool<>(16,
                task -> { throw new java.util.concurrent.RejectedExecutionException(); }, clock::get);
        rejected.prepare("dc4", 4, factory);
        check(rejected.pendingCount() == 0 && rejected.idleCount() == 0);
        System.out.println("WebSocketWarmPool: OK");
    }
}
