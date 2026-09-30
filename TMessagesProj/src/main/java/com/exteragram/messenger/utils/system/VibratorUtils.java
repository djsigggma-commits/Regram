package com.exteragram.messenger.utils.system;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.ViewGroup;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/** Шим {@code com.exteragram.messenger.utils.system.VibratorUtils}. */
public final class VibratorUtils {

    private VibratorUtils() {
    }

    public static void disableHapticFeedback(View view) {
        if (view == null) {
            return;
        }
        view.setHapticFeedbackEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disableHapticFeedback(group.getChildAt(i));
            }
        }
    }

    public static int getType(int type) {
        return ExteraConfig.inAppVibration() ? type : -1;
    }

    public static void vibrate() {
        vibrate(200L);
    }

    public static void vibrate(long milliseconds) {
        Vibrator vibrator = vibrator();
        if (vibrator == null) {
            return;
        }
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(
                    milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable t) {
            FileLog.e("VibratorUtils shim: vibrate failed", t);
        }
    }

    public static void vibrateEffect(VibrationEffect effect) {
        Vibrator vibrator = vibrator();
        if (vibrator == null || effect == null) {
            return;
        }
        try {
            vibrator.cancel();
        } catch (Throwable t) {
            FileLog.e("VibratorUtils shim: cancel failed", t);
        }
        try {
            vibrator.vibrate(effect);
        } catch (Throwable t) {
            FileLog.e("VibratorUtils shim: vibrateEffect failed", t);
        }
    }

    private static Vibrator vibrator() {
        if (!ExteraConfig.inAppVibration()) {
            return null;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return null;
            }
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager =
                        (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = manager != null ? manager.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            return vibrator != null && vibrator.hasVibrator() ? vibrator : null;
        } catch (Throwable t) {
            FileLog.e("VibratorUtils shim: no vibrator", t);
            return null;
        }
    }
}
