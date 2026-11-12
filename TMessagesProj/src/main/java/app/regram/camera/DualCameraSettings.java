package app.regram.camera;

import android.content.Context;

import org.telegram.messenger.MessagesController;

/** Opt-in pre-opening of front/back cameras for round-video switching, not story picture-in-picture. */
public final class DualCameraSettings {
    // Keep the existing preference so the old seamless-switching control stays in sync.
    private static final String KEY = "rounddual_available";

    private DualCameraSettings() {}

    public static boolean isEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(KEY, false);
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(KEY, enabled).apply();
    }

    public static boolean isSupported(Context context) {
        return context != null && CameraXSession.isSeamlessSwitchingAvailable(context);
    }

    public static boolean canUse(Context context) {
        return isEnabled() && isSupported(context);
    }
}
