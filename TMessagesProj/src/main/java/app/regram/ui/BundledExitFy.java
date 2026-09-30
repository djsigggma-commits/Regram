package app.regram.ui;

import android.content.Context;
import android.os.Build;

import app.regram.plugins.PluginsController;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Installs the unmodified exitFy 4.2 runtime from a pinned APK asset on explicit request. */
public final class BundledExitFy {
    public static final String ID = "exitFy_v2";
    private static final String ASSET = "regram/exitFy_v2.py";
    private static final String SHA256 = "c073c4c6ac6915c36675d7f1eab6cc6e8e8d9e36af0d7fbc352968039c1438a6";
    private static final int SIZE = 418356;
    private static boolean installing;

    public interface Callback {
        void onResult(boolean ok, String error);
    }

    private BundledExitFy() {}

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 29 && android.os.Process.is64Bit()
                && Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                && "arm64-v8a".equals(Build.SUPPORTED_ABIS[0]);
    }

    public static boolean isInstalled(PluginsController controller) {
        return controller.getPlugin(ID) != null
                || new File(controller.getPluginsDir(), ID + ".py").exists()
                || new File(controller.getPluginsDir(), ID + ".plugin").exists();
    }

    public static void install(Context context, Callback callback) {
        if (!isSupported()) {
            callback.onResult(false, "exitFy 4.2 requires Android 10+ and a 64-bit arm64 process");
            return;
        }
        PluginsController controller = PluginsController.getInstance();
        if (isInstalled(controller)) {
            callback.onResult(false, "exitFy_v2 is already installed; open it in Plugins");
            return;
        }
        synchronized (BundledExitFy.class) {
            if (installing) {
                callback.onResult(false, "exitFy installation is already in progress");
                return;
            }
            installing = true;
        }
        Context app = context.getApplicationContext();
        Utilities.globalQueue.postRunnable(() -> {
            File staged = null;
            try {
                staged = File.createTempFile("exitfy-bundled-", ".py", app.getCacheDir());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                int length = 0;
                try (InputStream input = app.getAssets().open(ASSET);
                     FileOutputStream output = new FileOutputStream(staged)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = input.read(buf)) != -1) {
                        length += n;
                        if (length > SIZE) {
                            throw new SecurityException("exitFy asset size mismatch");
                        }
                        digest.update(buf, 0, n);
                        output.write(buf, 0, n);
                    }
                }
                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest()) {
                    hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
                }
                if (length != SIZE || !SHA256.equals(hash.toString())) {
                    throw new SecurityException("exitFy asset digest mismatch");
                }
                File finalStaged = staged;
                // Do not enable the engine or other installed plugins implicitly.
                // The plugin is initially disabled; the caller may enable it after consent.
                controller.installPlugin(staged, false, (ok, error, plugin) -> {
                    finalStaged.delete();
                    synchronized (BundledExitFy.class) {
                        installing = false;
                    }
                    callback.onResult(ok, error);
                });
            } catch (Exception e) {
                if (staged != null) staged.delete();
                synchronized (BundledExitFy.class) {
                    installing = false;
                }
                String message = e.getMessage();
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                        callback.onResult(false, message));
            }
        });
    }
}
