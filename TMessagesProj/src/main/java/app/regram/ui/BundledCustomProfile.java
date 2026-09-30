package app.regram.ui;

import android.content.Context;

import app.regram.plugins.PluginsController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Bundled Custom Profile 1.9, with its original server-facing DEX kept unchanged. */
public final class BundledCustomProfile {
    public static final String ID = "custom_profile";
    private static final String ASSET = "regram/custom_profile.py";
    private static final String SHA256 = "466b66b99475f12845828eb9a7c48fd3c21269933fc8354b5e458be580c5314f";
    private static final int SIZE = 1634835;
    private static boolean installing;

    public interface Callback {
        void onResult(boolean ok, String error);
    }

    private BundledCustomProfile() {}

    public static boolean isInstalled(PluginsController controller) {
        return controller.getPlugin(ID) != null
                || new File(controller.getPluginsDir(), ID + ".py").exists()
                || new File(controller.getPluginsDir(), ID + ".plugin").exists();
    }

    /** No DEX extraction or plugin execution takes place before explicit consent. */
    public static void install(Context context, Callback callback) {
        PluginsController controller = PluginsController.getInstance();
        if (isInstalled(controller)) {
            callback.onResult(false, "Custom Profile is already installed; open it in Plugins");
            return;
        }
        synchronized (BundledCustomProfile.class) {
            if (installing) {
                callback.onResult(false, "Custom Profile installation is already in progress");
                return;
            }
            installing = true;
        }
        Context app = context.getApplicationContext();
        Utilities.globalQueue.postRunnable(() -> {
            File staged = null;
            try {
                staged = File.createTempFile("custom-profile-bundled-", ".py", app.getCacheDir());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                int length = 0;
                try (InputStream input = app.getAssets().open(ASSET);
                     FileOutputStream output = new FileOutputStream(staged)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = input.read(buf)) != -1) {
                        length += n;
                        if (length > SIZE) {
                            throw new SecurityException("Custom Profile asset size mismatch");
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
                    throw new SecurityException("Custom Profile asset digest mismatch");
                }
                File finalStaged = staged;
                // Install disabled: grant permissions only after confirmation, before on_plugin_load.
                controller.installPlugin(staged, false, (ok, error, plugin) -> {
                    finalStaged.delete();
                    synchronized (BundledCustomProfile.class) {
                        installing = false;
                    }
                    callback.onResult(ok, error);
                });
            } catch (Exception e) {
                if (staged != null) staged.delete();
                synchronized (BundledCustomProfile.class) {
                    installing = false;
                }
                String message = e.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onResult(false, message));
            }
        });
    }
}
