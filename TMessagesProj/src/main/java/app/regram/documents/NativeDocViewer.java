package app.regram.documents;

import android.app.Activity;
import android.content.*;
import android.os.Looper;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.FileLog;

/** Explicit client file-open path. Does not load plugins, intercept methods or fetch documents. */
public final class NativeDocViewer {
    private static final CopyOnWriteArrayList<WeakReference<DocumentViewerDialog>> viewers = new CopyOnWriteArrayList<>();
    private NativeDocViewer() { }
    public static SharedPreferences settings() { return ApplicationLoader.applicationContext.getSharedPreferences("regram_doc_viewer", Context.MODE_PRIVATE); }
    public static boolean option(String name, boolean fallback) { return settings().getBoolean(name, fallback); }
    public static boolean tryOpen(Activity activity, File file, String name, String mime, boolean restrict, int account) {
        if (!option("enabled", false) || activity == null || activity.isFinishing() || activity.isDestroyed()
                || file == null || !file.isFile() || Looper.myLooper() != Looper.getMainLooper()
                || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) return false;
        name = name == null || name.isEmpty() ? file.getName() : name;
        String format = DocumentFormat.detect(name, mime);
        if (restrict && MessageObject.isV(DocumentFormat.extension(name))) return false;
        if (format == null || !option(DocumentFormat.setting(format), true)) return false;
        try {
            if (format.equals("sniff") || format.equals("text") || format.equals("json") || format.equals("csv") || format.equals("ipynb")) {
                if (!DocumentFormat.textual(file)) return false;
                if (format.equals("sniff")) format = "text";
            }
            return show(activity, file, name, mime, format, restrict, account);
        } catch (Exception e) { FileLog.e("NativeDocViewer: cannot open", e); return false; }
    }
    static boolean show(Activity activity, File file, String name, String mime, String format, boolean restrict, int account) {
        if (!option("enabled", false) || activity == null || activity.isFinishing() || activity.isDestroyed()
                || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) return false;
        closeFor(activity);
        DocumentViewerDialog dialog = null;
        try {
            dialog = new DocumentViewerDialog(activity, file, name, mime, format, restrict, account);
            viewers.add(new WeakReference<>(dialog)); dialog.show(); dialog.load(); return true;
        } catch (Exception e) {
            if (dialog != null) dialog.dispose();
            FileLog.e("NativeDocViewer: cannot show", e); return false;
        }
    }
    public static void closeFor(Activity activity) { for (WeakReference<DocumentViewerDialog> ref : viewers) { DocumentViewerDialog dialog = ref.get(); if (dialog != null && dialog.getOwnerActivity() == activity) dialog.dismiss(); } prune(); }
    public static void closeAll() { for (WeakReference<DocumentViewerDialog> ref : viewers) { DocumentViewerDialog dialog = ref.get(); if (dialog != null) dialog.dismiss(); } viewers.clear(); }
    static void removed(DocumentViewerDialog target) { for (WeakReference<DocumentViewerDialog> ref : viewers) if (ref.get() == null || ref.get() == target) viewers.remove(ref); }
    private static void prune() { for (WeakReference<DocumentViewerDialog> ref : viewers) if (ref.get() == null) viewers.remove(ref); }
    static String positionKey(File file, int account, String format) throws Exception {
        String identity = account + "|" + format + "|" + file.getCanonicalPath() + "|" + file.length() + "|" + file.lastModified();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 255)); return out.toString();
    }
    private static SharedPreferences positions() { return ApplicationLoader.applicationContext.getSharedPreferences("regram_doc_positions", Context.MODE_PRIVATE); }
    static int position(String key) {
        if (!option("remember", true)) return 0;
        try { return Integer.parseInt(positions().getString(key, "0:0").split(":", 2)[1]); } catch (Exception e) { return 0; }
    }
    static void savePosition(String key, int page) {
        if (!option("remember", true) || key == null) return;
        SharedPreferences preferences = positions(); SharedPreferences.Editor edit = preferences.edit();
        Map<String, ?> entries = preferences.getAll();
        if (entries.size() >= 200 && !entries.containsKey(key)) {
            String oldest = null; long time = Long.MAX_VALUE;
            for (Map.Entry<String, ?> entry : entries.entrySet()) try { long stamp = Long.parseLong(entry.getValue().toString().split(":", 2)[0]); if (stamp < time) { time = stamp; oldest = entry.getKey(); } } catch (Exception ignored) { oldest = entry.getKey(); break; }
            if (oldest != null) edit.remove(oldest);
        }
        edit.putString(key, System.currentTimeMillis() + ":" + Math.max(0, page)).apply();
    }
}
