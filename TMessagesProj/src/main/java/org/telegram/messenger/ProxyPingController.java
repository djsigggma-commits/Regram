// Ported from Nagram XF. Keep the active proxy's ping fresh only when requested.
package org.telegram.messenger;

import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.Components.ForegroundDetector;

import tw.nekomimi.nekogram.NekoConfig;

public class ProxyPingController implements ForegroundDetector.Listener {

    private static final long PING_INTERVAL_MS = 10_000L;
    private static final long CHECK_TIMEOUT_MS = 30_000L;
    private static final ProxyPingController INSTANCE = new ProxyPingController();

    private final Runnable pingRunnable = this::doPing;
    private SharedConfig.ProxyInfo checkingProxy;
    private int checkingAccount;
    private int generation;
    private long checkStarted;
    private boolean registered;

    private boolean isForeground() {
        ForegroundDetector detector = ForegroundDetector.getInstance();
        return detector == null || detector.isForeground();
    }

    // All fields are accessed on the UI thread, including callbacks from checkProxy.
    private void doPing() {
        if (!NekoConfig.regramLiveProxyPing.Bool() || !isForeground()) {
            onSettingChangedInternal();
            return;
        }
        scheduleNextPing(PING_INTERVAL_MS); // A missing native callback must not stop polling forever.
        SharedConfig.ProxyInfo proxy = SharedConfig.currentProxy;
        if (!SharedConfig.isProxyEnabled() || proxy == null) return;

        int account = UserConfig.selectedAccount;
        if (checkingProxy != null && checkingProxy == proxy && checkingAccount == account &&
                SystemClock.elapsedRealtime() - checkStarted < CHECK_TIMEOUT_MS) return;

        checkingProxy = proxy;
        checkingAccount = account;
        checkStarted = SystemClock.elapsedRealtime();
        int request = ++generation;
        ConnectionsManager.getInstance(account).checkProxy(proxy.settings, time ->
                AndroidUtilities.runOnUIThread(() -> onPingResult(proxy, account, request, time)));
    }

    private void onPingResult(SharedConfig.ProxyInfo proxy, int account, int request, long time) {
        if (request != generation) return; // timed-out or superseded request
        checkingProxy = null;
        if (!NekoConfig.regramLiveProxyPing.Bool() || !isForeground() || !SharedConfig.isProxyEnabled() ||
                proxy != SharedConfig.currentProxy || account != UserConfig.selectedAccount) return;

        proxy.ping = time == -1 ? 0 : time;
        proxy.available = time != -1;
        proxy.availableCheckTime = SystemClock.elapsedRealtime();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyPingUpdated, time);
    }

    private void scheduleNextPing(long delay) {
        AndroidUtilities.cancelRunOnUIThread(pingRunnable);
        AndroidUtilities.runOnUIThread(pingRunnable, delay);
    }

    private void onSettingChangedInternal() {
        AndroidUtilities.cancelRunOnUIThread(pingRunnable);
        ++generation;
        checkingProxy = null;
        if (NekoConfig.regramLiveProxyPing.Bool() && isForeground()) scheduleNextPing(0);
    }

    public static void onSettingChanged() {
        AndroidUtilities.runOnUIThread(INSTANCE::onSettingChangedInternal);
    }

    @Override
    public void onBecameForeground() {
        onSettingChangedInternal();
    }

    @Override
    public void onBecameBackground() {
        AndroidUtilities.cancelRunOnUIThread(pingRunnable);
        ++generation; // Late native callbacks may not update a background view.
        checkingProxy = null;
    }

    public static void init() {
        AndroidUtilities.runOnUIThread(() -> {
            ForegroundDetector detector = ForegroundDetector.getInstance();
            if (detector != null && !INSTANCE.registered) {
                detector.addListener(INSTANCE);
                INSTANCE.registered = true;
            }
            INSTANCE.onSettingChangedInternal();
        });
    }
}
