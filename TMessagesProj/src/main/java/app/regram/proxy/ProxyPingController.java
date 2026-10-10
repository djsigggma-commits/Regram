package app.regram.proxy;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.Components.ForegroundDetector;

/** Publishes the connection's cached ping without idle/background polling. */
public final class ProxyPingController implements ForegroundDetector.Listener,
        NotificationCenter.NotificationCenterDelegate {

    private static final long INTERVAL_MS = 10_000L;
    private static final ProxyPingController INSTANCE = new ProxyPingController();
    private static final Runnable PING = INSTANCE::ping;
    private boolean initialized;

    private ProxyPingController() {
    }

    public static void init() {
        AndroidUtilities.runOnUIThread(() -> {
            if (INSTANCE.initialized) return;
            INSTANCE.initialized = true;
            ForegroundDetector detector = ForegroundDetector.getInstance();
            if (detector != null) detector.addListener(INSTANCE);
            NotificationCenter.getGlobalInstance().addObserver(INSTANCE, NotificationCenter.proxySettingsChanged);
            INSTANCE.update();
        });
    }

    private boolean shouldPoll() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        return (detector == null || detector.isForeground())
                && SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null;
    }

    private void ping() {
        if (!shouldPoll()) return;
        SharedConfig.ProxyInfo proxy = SharedConfig.currentProxy;
        int ping = ConnectionsManager.native_getCurrentPingTime(UserConfig.selectedAccount);
        if (ping > 0) {
            proxy.ping = ping;
            proxy.available = true;
            proxy.availableCheckTime = SystemClock.elapsedRealtime();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyPingUpdated, ping);
        }
        // An observer may disable the proxy or background the app during notification dispatch.
        update();
    }

    private void update() {
        AndroidUtilities.cancelRunOnUIThread(PING);
        if (shouldPoll()) AndroidUtilities.runOnUIThread(PING, INTERVAL_MS);
    }

    @Override
    public void onBecameForeground() {
        update();
    }

    @Override
    public void onBecameBackground() {
        AndroidUtilities.cancelRunOnUIThread(PING);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        update();
    }
}
