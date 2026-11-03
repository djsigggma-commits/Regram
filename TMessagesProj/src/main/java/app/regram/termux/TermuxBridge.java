package app.regram.termux;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;

/** Built-in bridge. No BasePlugin, method interception, downloaded DEX or plugin loader. */
public final class TermuxBridge {
    public static final String AI_URL = "https://api.groq.com/openai/v1/chat/completions";
    public static final String AI_MODEL = "openai/gpt-oss-120b";
    private static final TermuxBridge INSTANCE = new TermuxBridge();
    public static TermuxBridge getInstance() { return INSTANCE; }
    public static SharedPreferences settings() {
        return ApplicationLoader.applicationContext.getSharedPreferences("regram_termux", Context.MODE_PRIVATE);
    }
    public static boolean enabled() { return settings().getBoolean("enabled", false); }
    public static boolean allowExec() { return settings().getBoolean("allow_exec", false); }
    private final Set<Session> clients = ConcurrentHashMap.newKeySet();
    private final Set<HttpURLConnection> requests = ConcurrentHashMap.newKeySet();
    private final TermuxWire.Pairing pairing = new TermuxWire.Pairing();
    private final Semaphore pythonBusy = new Semaphore(1);
    private final TermuxUi ui = new TermuxUi(this);
    private final TermuxJava java = new TermuxJava(this, ui);
    private volatile ServerSocket server;
    private volatile int epoch;
    private volatile int port = 42691;
    private volatile String token = "";
    private volatile String status = "";
    private WeakReference<Activity> activity = new WeakReference<>(null);

    public synchronized void attach(Activity current) {
        activity = new WeakReference<>(current);
        if (enabled() && server == null) start();
    }
    public synchronized void detach(Activity current) {
        if (activity.get() == current) { stop(); activity.clear(); }
    }
    public synchronized void start() {
        stop();
        if (!enabled()) return;
        try {
            int wanted = Integer.parseInt(settings().getString("port", "42691"));
            if (wanted < 1024 || wanted > 65535) throw new IllegalArgumentException("port: 1024–65535");
            token = settings().getString("token", "");
            if (!token.matches("[0-9a-f]{48}")) {
                token = TermuxWire.secret();
                settings().edit().putString("token", token).commit();
            }
            ServerSocket socket = new ServerSocket();
            try {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), wanted), 8);
            } catch (Exception e) { socket.close(); throw e; }
            port = wanted;
            server = socket;
            status = "127.0.0.1:" + port;
            final int generation = epoch;
            thread("termux-accept", () -> accept(socket, generation));
        } catch (Exception e) {
            status = e.getClass().getSimpleName() + ": " + e.getMessage();
            FileLog.e("TermuxBridge: cannot start", e);
        }
    }
    public synchronized void stop() {
        epoch++;
        pairing.clear();
        ServerSocket old = server;
        server = null;
        if (old != null) try { old.close(); } catch (IOException ignored) { }
        for (Session s : clients) s.close();
        for (HttpURLConnection request : requests) request.disconnect();
        requests.clear();
        // Ordered before any UI tasks from a subsequent start; never retain an Activity.
        AndroidUtilities.runOnUIThread(() -> { ui.clearAll(); java.clearAll(); });
        status = "";
    }
    public String status() { return status; }
    public boolean running() { return server != null; }
    public synchronized String pairingCommand() {
        if (!enabled()) settings().edit().putBoolean("enabled", true).commit();
        if (server == null) start();
        if (server == null) throw new IllegalStateException(status);
        String path = pairing.open(SystemClock.elapsedRealtime());
        return "pkg install -y python curl && curl --fail --silent --show-error http://127.0.0.1:"
                + port + path + " | python";
    }
    public void locked() {
        pairing.clear();
        for (Session s : clients) s.close();
        for (HttpURLConnection request : requests) request.disconnect();
        ui.clearAll(); java.clearAll();
    }
    public void selfTest() {
        try { ui.selfTest(); } catch (Exception e) { FileLog.e("TermuxBridge: overlay test failed", e); }
    }
    public void testAi(java.util.function.Consumer<String> result) {
        thread("termux-ai-test", () -> {
            Session s = new Session(new Socket(), epoch);
            try {
                JSONObject response = ai(s, new JSONObject().put("prompt", "Reply with one word: ok"));
                result.accept(response.optBoolean("ok") ? response.optString("text") : response.optString("error"));
            } catch (Exception e) { result.accept(message(e)); }
            finally { s.close(); }
        });
    }
    public synchronized void rotateToken() {
        settings().edit().putString("token", TermuxWire.secret()).commit();
        start();
    }
    private static void thread(String name, Runnable work) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        thread.start();
    }
    private void accept(ServerSocket socket, int generation) {
        while (server == socket && epoch == generation) {
            try {
                Socket connection = socket.accept();
                synchronized (this) {
                    if (server != socket || epoch != generation || clients.size() >= 8) {
                        connection.close(); continue;
                    }
                    connection.setSoTimeout(120_000);
                    connection.setTcpNoDelay(true);
                    Session session = new Session(connection, generation);
                    clients.add(session);
                    thread("termux-reader", () -> serve(session));
                    thread("termux-writer", session::writeLoop);
                }
            } catch (IOException e) { break; }
        }
    }
    public final class Session {
        final Socket socket;
        final int generation;
        final BlockingQueue<byte[]> outgoing = new ArrayBlockingQueue<>(64);
        volatile String sid = "";
        volatile boolean subscribed;
        volatile boolean closed;
        Session(Socket socket, int generation) { this.socket = socket; this.generation = generation; }
        void send(JSONObject value) { send((value.toString() + "\n").getBytes(StandardCharsets.UTF_8)); }
        void send(byte[] data) { if (!closed && !outgoing.offer(data)) close(); }
        void writeLoop() {
            try {
                OutputStream output = socket.getOutputStream();
                while (!closed) {
                    byte[] data = outgoing.poll(1, TimeUnit.SECONDS);
                    if (data != null) { output.write(data); output.flush(); }
                }
            } catch (Exception ignored) { close(); }
        }
        void close() {
            synchronized (this) { if (closed) return; closed = true; }
            clients.remove(this);
            try { socket.close(); } catch (IOException ignored) { }
            outgoing.clear();
            if (!sid.isEmpty()) AndroidUtilities.runOnUIThread(() -> {
                // Subscribers own interactive scripts; plain RPC users also release their last connection.
                boolean retained = clients.stream().anyMatch(s -> s.sid.equals(sid) && (!subscribed || s.subscribed));
                if (generation == epoch && !retained) { ui.cleanupOwner(sid); java.cleanupOwner(sid); }
            });
        }
    }
    private void serve(Session s) {
        try {
            InputStream input = new BufferedInputStream(s.socket.getInputStream());
            String line;
            while (!s.closed && s.generation == epoch && (line = TermuxWire.readLine(input)) != null) {
                if (line.startsWith("GET ")) { http(s, input, line); return; }
                if (line.trim().isEmpty()) continue;
                try {
                    JSONObject msg = new JSONObject(line);
                    if (!TermuxWire.matches(token, msg.optString("token", ""))) {
                        s.send(error("bad token")); continue;
                    }
                    String sid = msg.optString("sid", "");
                    if (!sid.matches("[a-zA-Z0-9_-]{1,64}") || (!s.sid.isEmpty() && !sid.equals(s.sid))) {
                        s.send(error("invalid session")); continue;
                    }
                    s.sid = sid;
                    check(s);
                    if ("subscribe".equals(msg.optString("cmd"))) {
                        s.socket.setSoTimeout(0);
                        s.send(ok());
                        s.subscribed = true;
                    } else s.send(dispatch(s, msg));
                } catch (Exception e) { s.send(error(message(e))); }
            }
        } catch (Exception ignored) { }
        finally { s.close(); }
    }
    private void http(Session s, InputStream input, String line) throws Exception {
        // Consume headers with strict bounds before serving one-use installer.
        s.socket.setSoTimeout(10_000);
        int total = 0;
        String header;
        while ((header = TermuxWire.readLine(input)) != null && !header.isEmpty()) {
            total += header.length();
            if (total > 16384) throw new IOException("headers too large");
        }
        if (header == null) return;
        String[] first = line.split(" ");
        boolean allowed = first.length == 3 && "HTTP/1.1".equals(first[2]);
        String body;
        synchronized (this) {
            allowed = allowed && s.generation == epoch && !SharedConfig.appLocked
                    && !SharedConfig.isWaitingForPasscodeEnter
                    && pairing.consume(first[1], SystemClock.elapsedRealtime());
            body = allowed ? installer() : "print('Pairing closed. Open Termux Bridge settings in re:gram.')\n";
        }
        // HTTP has a single response, written on this worker (not on the Android UI).
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        OutputStream output = s.socket.getOutputStream();
        output.write(("HTTP/1.1 " + (allowed ? "200 OK" : "403 Forbidden")
                + "\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: "
                + data.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(data); output.flush();
    }
    String asset(String name) throws IOException {
        try (InputStream in = ApplicationLoader.applicationContext.getAssets().open("regram/" + name)) {
            return new String(readLimited(in, TermuxWire.MAX_LINE), StandardCharsets.UTF_8);
        }
    }
    private String installer() throws Exception {
        String client = asset("termux_client.py.txt").replace("@@PORT@@", String.valueOf(port))
                .replace("@@TOKEN@@", token).replace("@@PACKAGE@@", ApplicationLoader.applicationContext.getPackageName());
        return asset("termux_installer.py.txt").replace("@@SRC@@", JSONObject.quote(client));
    }
    void check(Session s) {
        if (s.closed || s.generation != epoch || server == null || !enabled()) throw new IllegalStateException("bridge stopped");
        if (SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) throw new IllegalStateException("unlock re:gram first");
    }
    void requireExec(Session s) {
        check(s);
        if (!allowExec()) throw new IllegalStateException("exec disabled in Termux Bridge settings");
    }
    void requireListener(Session s) {
        if (s.generation != epoch || server == null || !enabled() || !allowExec()
                || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter)
            throw new IllegalStateException("listener revoked");
        if (s.closed && clients.stream().noneMatch(c -> c.subscribed && c.sid.equals(s.sid)))
            throw new IllegalStateException("listener session closed");
    }
    Activity activity() {
        Activity current = activity.get();
        if (current == null || current.isFinishing() || current.isDestroyed()) throw new IllegalStateException("open re:gram first");
        return current;
    }
    public BaseFragment fragment() { return LaunchActivity.getLastFragment(); }
    <T> T onUi(Session s, Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(() -> { check(s); return action.call(); });
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) task.run();
        else AndroidUtilities.runOnUIThread(task);
        try { return task.get(5, TimeUnit.SECONDS); }
        catch (TimeoutException e) { task.cancel(false); throw new IllegalStateException("UI timeout"); }
        catch (ExecutionException e) { throw new IllegalStateException(message(e.getCause())); }
    }
    void emit(String sid, JSONObject event) {
        for (Session session : clients) if (session.subscribed && session.sid.equals(sid) && session.generation == epoch) session.send(event);
    }
    public String executeLocal(Session s, String raw) throws Exception {
        requireExec(s);
        return dispatch(s, new JSONObject(raw)).toString();
    }
    public void pythonUi(Session s, PyObject callable) {
        requireExec(s);
        AndroidUtilities.runOnUIThread(() -> {
            try { requireExec(s); callable.call(); }
            catch (Exception e) { FileLog.e("TermuxBridge: Python UI callback failed"); }
        });
    }
    private JSONObject dispatch(Session s, JSONObject msg) throws Exception {
        check(s);
        String cmd = msg.optString("cmd");
        switch (cmd) {
            case "ping": return ok().put("pong", true);
            case "diag": return ok().put("version", "1.0.0-native").put("port", port).put("exec", allowExec())
                    .put("ai", !settings().getString("ai_endpoint", AI_URL).isEmpty())
                    .put("ai_key", !settings().getString("ai_key", "").isEmpty())
                    .put("screen", activity.get() != null).put("panels", onUi(s, ui::panelCount));
            case "exec": return python(s, msg);
            case "ai": return ai(s, msg);
            case "ai_docs": return ok().put("text", aiDocs());
            case "j_info": return onUi(s, () -> java.info());
            default:
                if (cmd.startsWith("j_")) {
                    requireExec(s);
                    return onUi(s, () -> { requireExec(s); return java.command(s, msg); });
                }
                return onUi(s, () -> ui.command(s, msg));
        }
    }
    private JSONObject python(Session s, JSONObject msg) throws Exception {
        requireExec(s);
        if (!pythonBusy.tryAcquire()) return error("Python execution already in progress");
        try {
            // Shared startup lock only; never load a plugin or import plugin_loader here.
            synchronized (Python.class) {
                if (!Python.isStarted()) Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
            requireExec(s);
            JSONObject result = new JSONObject(Python.getInstance().getModule("regram_termux_exec")
                    .callAttr("run", msg.optString("code"), this, s).toJava(String.class));
            check(s);
            if (msg.optBoolean("overlay") && !result.optString("output").trim().isEmpty())
                onUi(s, () -> ui.command(s, new JSONObject().put("cmd", "append").put("text", result.optString("output"))));
            return result;
        } finally { pythonBusy.release(); }
    }
    public String aiDocs() throws Exception {
        return asset("termux_ai_docs.txt") + "\nUSER NOTES\n" + settings().getString("ai_extra", "");
    }
    private JSONObject ai(Session s, JSONObject msg) throws Exception {
        String endpoint = settings().getString("ai_endpoint", AI_URL).trim().replaceAll("/+$", "");
        if (!endpoint.endsWith("/chat/completions")) endpoint += "/chat/completions";
        URL url = new URL(endpoint);
        if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null) throw new IllegalArgumentException("AI endpoint must use HTTPS");
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", aiDocs()));
        JSONArray history = msg.optJSONArray("history");
        if (history != null) for (int i = Math.max(0, history.length() - 12); i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item != null && ("user".equals(item.optString("role")) || "assistant".equals(item.optString("role"))))
                messages.put(new JSONObject().put("role", item.optString("role")).put("content", truncate(item.optString("content"), 8000)));
        }
        messages.put(new JSONObject().put("role", "user").put("content", truncate(msg.optString("prompt"), 4000)));
        byte[] body = new JSONObject().put("model", settings().getString("ai_model", AI_MODEL))
                .put("temperature", 0.2).put("messages", messages).toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection request = (HttpURLConnection) url.openConnection();
        requests.add(request);
        try {
            check(s);
            request.setConnectTimeout(15000); request.setReadTimeout(60000);
            request.setInstanceFollowRedirects(false);
            request.setRequestMethod("POST"); request.setDoOutput(true);
            request.setRequestProperty("Content-Type", "application/json");
            String key = settings().getString("ai_key", "").trim();
            if (!key.isEmpty()) request.setRequestProperty("Authorization", "Bearer " + key);
            request.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = request.getOutputStream()) { out.write(body); }
            int code = request.getResponseCode();
            if (code != 200) return error("AI HTTP " + code); // Do not echo server bodies containing credentials.
            try (InputStream in = request.getInputStream()) {
                JSONObject result = new JSONObject(new String(readLimited(in, TermuxWire.MAX_LINE), StandardCharsets.UTF_8));
                check(s);
                return ok().put("text", result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
            }
        } finally { requests.remove(request); request.disconnect(); }
    }
    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192]; int count;
        while ((count = in.read(chunk)) != -1) {
            if (out.size() + count > limit) throw new IOException("response too large");
            out.write(chunk, 0, count);
        }
        return out.toByteArray();
    }
    static String truncate(String text, int limit) { return text.length() > limit ? text.substring(0, limit) : text; }
    static String message(Throwable error) {
        while (error.getCause() != null && error.getCause() != error) error = error.getCause();
        return truncate(error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()), 300);
    }
    static JSONObject ok() { try { return new JSONObject().put("ok", true); } catch (Exception e) { throw new IllegalStateException(e); } }
    static JSONObject error(String text) { try { return new JSONObject().put("ok", false).put("error", text); } catch (Exception e) { throw new IllegalStateException(e); } }
}
