package org.telegram.tgnet;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructTimeval;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.IDN;
import java.net.Proxy;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** In-process tgnet transport, not an MTProxy listener or an Android service.
 * Routing convention: Flowseal/tg-ws-proxy and amurcanov/tg-ws-proxy-android (GPLv3).
 * Uses platform trust/hostname verification, never upstream's NoVerify/fronting. */
public final class TelegramWebSocketTransport {
    public static final int DEFAULT_POOL_SIZE = 4;
    public static final boolean DEFAULT_CLOUDFLARE = true;
    private static final Object lifecycleLock = new Object();
    private static final Object poolTimerLock = new Object();
    private static final int MAX_SESSIONS = 64;
    private static final long MAX_QUEUE = 16L * 1024 * 1024;
    private static final Semaphore slots = new Semaphore(MAX_SESSIONS);
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private static final Set<WsChannel> channels = ConcurrentHashMap.newKeySet();
    private static final ThreadPoolExecutor warmWorkers = new ThreadPoolExecutor(2, 2, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), r -> {
                Thread thread = new Thread(r, "regram-ws-warmup");
                thread.setDaemon(true);
                return thread;
            });
    static { warmWorkers.allowCoreThreadTimeOut(true); }
    private static final WebSocketWarmPool<Route, WsChannel> warmPool = new WebSocketWarmPool<>(16,
            warmWorkers, SystemClock::elapsedRealtime);
    private static final ScheduledExecutorService poolTimer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "regram-ws-pool");
        thread.setDaemon(true);
        return thread;
    });
    private static ScheduledFuture<?> poolTick;
    private static final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    private static final ExecutorService io = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "regram-ws-io");
        thread.setDaemon(true);
        return thread;
    });
    private static final class Client {
        static final OkHttpClient INSTANCE = new OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(3, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS).pingInterval(30, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    }

    private TelegramWebSocketTransport() { }

    public static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("regram_websocket", Context.MODE_PRIVATE);
    }

    public static boolean defaultBoolean(String key) {
        return "cloudflare".equals(key) && DEFAULT_CLOUDFLARE;
    }

    public static int poolSize() {
        return Math.max(0, Math.min(8, preferences().getInt("pool_size", DEFAULT_POOL_SIZE)));
    }

    private static boolean mayWarm() {
        return preferences().getBoolean("enabled", false) && poolSize() > 0 && ApplicationLoader.isNetworkOnline()
                && !ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                .getBoolean("proxy_enabled", false);
    }

    /** External proxies must not leave unused direct/Cloudflare sockets running in the background. */
    public static void stopPool() {
        synchronized (poolTimerLock) {
            if (poolTick != null) poolTick.cancel(false);
            poolTick = null;
            warmPool.reset();
            for (WsChannel channel : channels) channel.closeIfIdle();
        }
    }

    private static void preparePool(Route route) {
        synchronized (poolTimerLock) {
            if (!mayWarm()) return;
            warmPool.prepare(route, poolSize(), cancelled -> dial(route, () -> cancelled.getAsBoolean() || !mayWarm(), true));
            if (poolTick == null) {
                poolTick = poolTimer.scheduleWithFixedDelay(() -> {
                    synchronized (poolTimerLock) {
                        if (!mayWarm()) { stopPool(); return; }
                        warmPool.maintain();
                        if (warmPool.isEmpty()) stopPool();
                    }
                }, 5, 5, TimeUnit.SECONDS);
            }
        }
    }

    public static void settingsChanged() {
        synchronized (lifecycleLock) {
            stopPool();
            // Also cancel pending handshakes and blocked I/O before reconnecting accounts.
            for (Session session : sessions) session.close();
            for (WsChannel channel : channels) channel.close();
            cooldowns.clear();
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            ConnectionsManager.native_onTransportChanged(a);
        }
    }

    public static synchronized String diagnostics() {
        StringBuilder text = new StringBuilder("Active: ").append(sessions.size()).append('\n')
                .append("WS pool target/route: ").append(poolSize()).append("; idle: ").append(warmPool.idleCount())
                .append("; pending: ").append(warmPool.pendingCount()).append('\n');
        for (String event : events) text.append(event).append('\n');
        return text.toString();
    }

    private static synchronized void event(String text) {
        if (events.size() == 80) events.removeFirst();
        // No messages, auth keys, IPs, custom host names or account identifiers in diagnostics.
        events.addLast((SystemClock.elapsedRealtime() / 1000) + "s " + text);
    }

    /** Base domains only, no paths/ports/credentials; empty selects bundled upstream pool. */
    public static String normalizeDomains(String input) {
        List<String> domains = new ArrayList<>();
        for (String raw : input.trim().split("[\\s,;]+")) {
            if (raw.isEmpty()) continue;
            String domain = IDN.toASCII(raw, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US);
            if (domain.length() > 253 || !domain.contains(".") || domain.endsWith(".")) {
                throw new IllegalArgumentException("Invalid domain");
            }
            for (String label : domain.split("\\.", -1)) {
                if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                    throw new IllegalArgumentException("Invalid domain");
                }
            }
            if (!domains.contains(domain)) domains.add(domain);
            if (domains.size() > 20) throw new IllegalArgumentException("Too many domains");
        }
        return String.join(", ", domains);
    }

    private static List<String> cloudflareDomains(String custom) {
        List<String> result = new ArrayList<>();
        if (!custom.isEmpty()) {
            Collections.addAll(result, normalizeDomains(custom).split(", "));
        } else {
            // Encoded base-domain pool from upstream config.rs; no background list downloads.
            String[] encoded = {"virkgj", "vmmzovy", "mkuosckvso", "zaewayzmplad", "twdmbzcm",
                    "awzwsldi", "clngqrflngqin", "tjacxbqtj", "bxaxtxmrw", "dmohrsgmohcrwb",
                    "vwbmtmoi", "khgrre", "ulihssf", "tmhqsdqmfpmk", "xwuwoqbm", "orgcnunpj",
                    "zhkuldz", "zypoljnslxa", "efabnxaowuzs", "zaftuzsftqdq"};
            for (String value : encoded) {
                StringBuilder domain = new StringBuilder();
                for (char c : value.toCharArray()) domain.append((char) ('a' + (c - 'a' - value.length() % 26 + 26) % 26));
                result.add(domain + ".co.uk");
            }
        }
        Collections.shuffle(result);
        return result;
    }

    /** Called on the native network thread. No DNS, TLS, connect or wait on this thread.
     * -1 means disabled; -2 is a failed enabled transport and MUST NOT silently use TCP. */
    static int open(int dc, boolean media, boolean test, String address, int port) {
        synchronized (lifecycleLock) {
            return openLocked(dc, media, test, address, port);
        }
    }

    private static int openLocked(int dc, boolean media, boolean test, String address, int port) {
        SharedPreferences prefs = preferences();
        if (!prefs.getBoolean("enabled", false)) return -1;
        if (!slots.tryAcquire()) { event("WS connection limit reached"); return -2; }
        ParcelFileDescriptor[] pair = null;
        Session session = null;
        try {
            pair = ParcelFileDescriptor.createSocketPair();
            Os.setsockoptTimeval(pair[1].getFileDescriptor(), OsConstants.SOL_SOCKET,
                    OsConstants.SO_SNDTIMEO, StructTimeval.fromMillis(5000));
            session = new Session(pair[1], dc == 203 ? 2 : dc, media, test,
                    prefs.getBoolean("cloudflare", DEFAULT_CLOUDFLARE), prefs.getString("domains", ""),
                    prefs.getBoolean("tcp_fallback", false), address, port);
            sessions.add(session);
            Session worker = session;
            io.execute(worker::run);
            return pair[0].detachFd(); // owned and closed exclusively by ConnectionSocket
        } catch (Exception e) {
            if (session != null) session.close();
            else {
                slots.release();
                if (pair != null) try { pair[1].close(); } catch (Exception ignored) { }
            }
            if (pair != null) try { pair[0].close(); } catch (Exception ignored) { }
            event("WS allocation failed");
            FileLog.e(e);
            return -2;
        }
    }

    private static final class Route {
        final int dc;
        final boolean media, test, cloudflare;
        final String custom;
        Route(int dc, boolean media, boolean test, boolean cloudflare, String custom) {
            this.dc = dc;
            this.media = media;
            this.test = test;
            this.cloudflare = cloudflare;
            this.custom = custom;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Route)) return false;
            Route route = (Route) other;
            return dc == route.dc && media == route.media && test == route.test
                    && cloudflare == route.cloudflare && custom.equals(route.custom);
        }
        @Override public int hashCode() { return Objects.hash(dc, media, test, cloudflare, custom); }
    }

    private static WsChannel dial(Route route, BooleanSupplier cancelled, boolean warming) throws Exception {
        List<String> routes = new ArrayList<>();
        if (route.cloudflare) {
            for (String base : cloudflareDomains(route.custom)) {
                String host = "kws" + route.dc + "." + base;
                if (cooldowns.getOrDefault(host, 0L) <= SystemClock.elapsedRealtime()) routes.add(host);
                if (routes.size() == 2) break;
            }
        }
        routes.add("kws" + route.dc + (route.media ? "-1" : "") + ".web.telegram.org");
        routes.add("kws" + route.dc + (route.media ? "" : "-1") + ".web.telegram.org");
        for (String host : routes) {
            if (cancelled.getAsBoolean()) return null;
            WsChannel channel = new WsChannel(warming);
            channels.add(channel);
            boolean ready = false;
            try {
                Request request = new Request.Builder().url("https://" + host + (route.test ? "/apiws_test" : "/apiws"))
                        .header("Sec-WebSocket-Protocol", "binary").build();
                channel.start(request);
                channel.done.await(3500, TimeUnit.MILLISECONDS);
                ready = !cancelled.getAsBoolean() && channel.isOpen();
                if (ready) return channel;
                if (channel.rateLimited) cooldowns.put(host, SystemClock.elapsedRealtime() + 45000);
                if (!cancelled.getAsBoolean()) event("DC" + route.dc
                        + (host.endsWith(".web.telegram.org") ? " direct WSS" : " Cloudflare")
                        + (channel.rateLimited ? " HTTP 429; cooldown 45s" : " handshake failed"));
            } finally {
                if (!ready) channel.close();
            }
        }
        return null;
    }

    /** May be idle only BEFORE the MTProto init; never reused after attachment. */
    private static final class WsChannel extends WebSocketListener implements WebSocketWarmPool.Connection {
        final CountDownLatch done = new CountDownLatch(1);
        volatile WebSocket socket;
        volatile boolean rateLimited;
        private boolean opened, terminated;
        private Session owner;
        private final boolean warming;

        WsChannel(boolean warming) { this.warming = warming; }

        void start(Request request) {
            WebSocket candidate = Client.INSTANCE.newWebSocket(request, this);
            synchronized (this) {
                socket = candidate;
                if (terminated) candidate.cancel();
            }
        }
        @Override public synchronized boolean isOpen() { return opened && !terminated; }
        synchronized boolean attach(Session session) throws Exception {
            if (!isOpen() || owner != null || session.closed.get()) return false;
            owner = session;
            session.output.write(1); // readiness marker precedes all remote payload bytes
            event("DC" + session.dc + (session.media ? " media" : "") + " WSS connected");
            return true;
        }
        void closeIfIdle() {
            synchronized (this) {
                if (!warming || owner != null) return;
                close();
            }
        }
        @Override public void close() {
            Session session;
            WebSocket ws;
            synchronized (this) {
                if (terminated) return;
                terminated = true;
                session = owner;
                ws = socket;
                done.countDown();
            }
            if (ws != null) ws.cancel();
            channels.remove(this);
            if (session != null) session.close();
        }
        @Override public synchronized void onOpen(WebSocket ws, Response response) {
            if (terminated) { ws.cancel(); return; }
            opened = true;
            done.countDown();
        }
        @Override public void onMessage(WebSocket ws, ByteString bytes) {
            Session session;
            synchronized (this) { session = owner; }
            // An unused socket must not buffer unsolicited data or any MTProto state.
            if (session == null || bytes.size() > WebSocketPacketFramer.MAX_PACKET + 4) { close(); return; }
            if (session.closed.get()) return;
            try { bytes.write(session.output); } catch (Exception error) { close(); }
        }
        @Override public void onMessage(WebSocket ws, String text) { close(); }
        @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
            rateLimited = response != null && response.code() == 429;
            close();
        }
        @Override public void onClosing(WebSocket ws, int code, String reason) { close(); }
        @Override public void onClosed(WebSocket ws, int code, String reason) { close(); }
    }

    private static final class Session {
        final ParcelFileDescriptor endpoint;
        final InputStream input;
        final OutputStream output;
        final int dc, port;
        final boolean media, fallback;
        final String address;
        final Route route;
        final AtomicBoolean closed = new AtomicBoolean();
        volatile WsChannel channel;
        volatile Socket tcp;

        Session(ParcelFileDescriptor endpoint, int dc, boolean media, boolean test,
                boolean cloudflare, String custom, boolean fallback, String address, int port) {
            this.endpoint = endpoint;
            this.dc = dc;
            this.media = media;
            this.route = new Route(dc, media, test, cloudflare, custom);
            this.fallback = fallback;
            this.address = address;
            this.port = port;
            input = new ParcelFileDescriptor.AutoCloseInputStream(endpoint);
            output = new ParcelFileDescriptor.AutoCloseOutputStream(endpoint);
        }

        void run() {
            try {
                WsChannel ready = warmPool.take(route);
                boolean hit = ready != null;
                if (ready == null) ready = dial(route, closed::get, false);
                if (ready != null) {
                    channel = ready;
                    if (!ready.attach(this)) { ready.close(); return; }
                    event("DC" + dc + (hit ? " WS pool hit" : " WS pool miss"));
                    if (!closed.get()) preparePool(route);
                }
                if (ready == null) {
                    if (fallback && !closed.get()) bridgeTcp();
                    else event("DC" + dc + " WS unavailable");
                    return;
                }
                WebSocketPacketFramer framer = new WebSocketPacketFramer(input);
                send(framer.handshake());
                while (!closed.get()) send(framer.packet());
            } catch (Exception ignored) {
                if (!closed.get()) event("DC" + dc + " transport closed; tgnet will reconnect");
                // The normal tgnet request/reconnect machinery handles EOF and failures.
            } finally {
                close();
            }
        }

        void send(byte[] bytes) throws Exception {
            WebSocket socket = channel.socket;
            while (!closed.get() && socket.queueSize() + bytes.length > MAX_QUEUE) Thread.sleep(10);
            if (closed.get() || !socket.send(ByteString.of(bytes))) throw new java.io.IOException("WS closed");
        }

        void bridgeTcp() throws Exception {
            Socket direct = new Socket(Proxy.NO_PROXY);
            tcp = direct;
            if (closed.get()) return;
            direct.connect(new InetSocketAddress(address, port), 3000);
            direct.setTcpNoDelay(true);
            output.write(1);
            event("DC" + dc + " explicit TCP fallback");
            io.execute(() -> {
                try { copy(direct.getInputStream(), output); } catch (Exception ignored) { }
                finally { close(); }
            });
            copy(input, direct.getOutputStream());
        }

        void copy(InputStream source, OutputStream destination) throws Exception {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while (!closed.get() && (n = source.read(buffer)) >= 0) {
                if (n > 0) destination.write(buffer, 0, n);
            }
        }

        void close() {
            if (!closed.compareAndSet(false, true)) return;
            WsChannel ws = channel;
            if (ws != null) ws.close();
            Socket direct = tcp;
            if (direct != null) try { direct.close(); } catch (Exception ignored) { }
            // shutdown wakes both directions before close, including blocked native/Java reads.
            FileDescriptor fd = endpoint.getFileDescriptor();
            try { Os.shutdown(fd, OsConstants.SHUT_RDWR); } catch (Exception ignored) { }
            try { endpoint.close(); } catch (Exception ignored) { }
            sessions.remove(this);
            slots.release();
        }
    }
}
