package app.regram.ui;

import android.content.SharedPreferences;
import android.os.Looper;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe built-in replacement for the parts of exitFy that can be implemented
 * without bundling its opaque DEX/native core: importing Telegram proxy links
 * from a subscription and selecting the fastest available proxy.
 */
public final class ExitFyLite {
    public interface ResultCallback {
        void onResult(String message);
    }

    private static final String PREF_SUBSCRIPTION = "regram_exitfy_lite_subscription";
    private static final int MAX_DOWNLOAD = 512 * 1024;
    private static final int MAX_PROXIES = 200;
    private static final int MAX_PARALLEL_CHECKS = 4;
    private static final long CHECK_BATCH_TIMEOUT_MS = 45_000L;
    // Accessed only from the UI thread, like ProxyInfo.checking and notification dispatch.
    private static ProbeBatch activeBatch;
    private static final Pattern TELEGRAM_PROXY_LINK = Pattern.compile(
            "(?:tg://(?:proxy|socks)|https://t\\.me/(?:proxy|socks))\\?[^\\s\\\"'<>]+",
            Pattern.CASE_INSENSITIVE);

    private ExitFyLite() {
    }

    public static String getSubscriptionUrl() {
        return MessagesController.getGlobalMainSettings().getString(PREF_SUBSCRIPTION, "");
    }

    public static void setSubscriptionUrl(String url) {
        MessagesController.getGlobalMainSettings().edit()
                .putString(PREF_SUBSCRIPTION, url == null ? "" : url.trim())
                .apply();
    }

    public static void refreshSubscription(String url, ResultCallback callback) {
        setSubscriptionUrl(url);
        final String normalized = url == null ? "" : url.trim();
        if (TextUtils.isEmpty(normalized)) {
            post(callback, "exitFy Lite: subscription URL is empty");
            return;
        }
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(normalized).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "regram-exitfy-lite/1.0");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("HTTP " + code);
                }
                String body = readLimited(connection.getInputStream());
                List<SharedConfig.ProxyInfo> imported = importTelegramProxies(body);
                post(callback, "exitFy Lite: imported " + imported.size() + " Telegram proxy links");
            } catch (Throwable e) {
                post(callback, "exitFy Lite: " + e.getMessage());
            }
        });
    }

    public static List<SharedConfig.ProxyInfo> importTelegramProxies(String text) {
        SharedConfig.loadProxyList();
        LinkedHashSet<String> links = extractLinks(text);
        ArrayList<SharedConfig.ProxyInfo> result = new ArrayList<>();
        for (String link : links) {
            if (result.size() >= MAX_PROXIES) {
                break;
            }
            try {
                SharedConfig.ProxyInfo info = SharedConfig.ProxyInfo.fromUrl(link);
                if (info == null || TextUtils.isEmpty(info.settings.getAddress()) || info.settings.getPort() <= 0) {
                    continue;
                }
                result.add(SharedConfig.addProxy(info));
            } catch (Throwable ignore) {
            }
        }
        return result;
    }

    public static void optimizeNow(ResultCallback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(() -> optimizeNow(callback));
            return;
        }
        if (activeBatch != null) {
            post(callback, "exitFy Lite: proxy check already running");
            return;
        }
        SharedConfig.loadProxyList();
        ArrayList<SharedConfig.ProxyInfo> candidates = new ArrayList<>();
        for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
            if (info != null && info.settings.getType() != ProxySettings.Type.WEB && !info.checking) {
                candidates.add(info);
                if (candidates.size() == MAX_PROXIES) break;
            }
        }
        if (candidates.isEmpty()) {
            post(callback, "exitFy Lite: no Telegram proxies to check");
            return;
        }
        ProbeBatch batch = new ProbeBatch(candidates, UserConfig.selectedAccount,
                SharedConfig.currentProxy, callback);
        activeBatch = batch;
        AndroidUtilities.runOnUIThread(batch.timeout, CHECK_BATCH_TIMEOUT_MS);
        batch.startMore();
    }

    /** Keeps native proxy checks bounded instead of launching hundreds in one UI frame. */
    private static final class ProbeBatch {
        final ArrayList<SharedConfig.ProxyInfo> candidates;
        final ArrayList<SharedConfig.ProxyInfo> pending = new ArrayList<>();
        final int account;
        final SharedConfig.ProxyInfo originalProxy;
        final ResultCallback callback;
        final Runnable timeout = () -> finish(true);
        int next;

        ProbeBatch(ArrayList<SharedConfig.ProxyInfo> candidates, int account,
                   SharedConfig.ProxyInfo originalProxy, ResultCallback callback) {
            this.candidates = candidates;
            this.account = account;
            this.originalProxy = originalProxy;
            this.callback = callback;
        }

        void startMore() {
            if (activeBatch != this) return;
            while (pending.size() < MAX_PARALLEL_CHECKS && next < candidates.size()) {
                SharedConfig.ProxyInfo info = candidates.get(next++);
                pending.add(info);
                info.checking = true;
                try {
                    ConnectionsManager.getInstance(account).checkProxy(info.settings,
                            time -> AndroidUtilities.runOnUIThread(() -> onResult(info, time)));
                } catch (Exception e) {
                    AndroidUtilities.runOnUIThread(() -> onResult(info, -1));
                }
            }
            if (next == candidates.size() && pending.isEmpty()) finish(false);
        }

        void onResult(SharedConfig.ProxyInfo info, long time) {
            if (activeBatch != this || !pending.remove(info)) return;
            info.checking = false;
            info.available = time >= 0;
            info.ping = time >= 0 ? time : 0;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, info);
            startMore();
        }

        void finish(boolean expired) {
            if (activeBatch != this) return;
            activeBatch = null;
            AndroidUtilities.cancelRunOnUIThread(timeout);
            for (SharedConfig.ProxyInfo info : pending) info.checking = false;
            pending.clear();
            if (expired) {
                post(callback, "exitFy Lite: proxy check timed out");
            } else if (account != UserConfig.selectedAccount || originalProxy != SharedConfig.currentProxy) {
                post(callback, "exitFy Lite: proxy changed during check; no switch made");
            } else {
                applyFastest(candidates, callback);
            }
        }
    }

    private static void applyFastest(List<SharedConfig.ProxyInfo> candidates, ResultCallback callback) {
        SharedConfig.ProxyInfo best = candidates.stream()
                .filter(info -> info.available && info.ping > 0)
                .min(Comparator.comparingLong(info -> info.ping))
                .orElse(null);
        if (best == null) {
            post(callback, "exitFy Lite: no available proxy found");
            return;
        }
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", true);
        best.settings.toSharedPreferences(editor);
        if (!best.settings.getSecret().isEmpty()) {
            editor.putBoolean("proxy_enabled_calls", false);
        }
        editor.apply();
        SharedConfig.currentProxy = best;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        ConnectionsManager.setProxySettings(true, best.settings);
        post(callback, "exitFy Lite: enabled fastest proxy (" + best.ping + " ms)");
    }

    private static LinkedHashSet<String> extractLinks(String text) {
        LinkedHashSet<String> output = new LinkedHashSet<>();
        if (text == null) {
            return output;
        }
        Matcher matcher = TELEGRAM_PROXY_LINK.matcher(text);
        while (matcher.find()) {
            String link = matcher.group();
            while (link.endsWith(",") || link.endsWith(";") || link.endsWith(")")) {
                link = link.substring(0, link.length() - 1);
            }
            output.add(link);
        }
        return output;
    }

    private static String readLimited(InputStream stream) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int total = 0;
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_DOWNLOAD) {
                    throw new IllegalStateException("response is too large");
                }
                out.append(buffer, 0, read);
            }
        }
        return out.toString();
    }

    private static void post(ResultCallback callback, String message) {
        if (callback != null) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(message));
        }
    }
}
