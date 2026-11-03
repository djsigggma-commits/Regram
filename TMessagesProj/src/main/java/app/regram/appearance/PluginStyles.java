package app.regram.appearance;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import java.util.Collections;
import java.util.Map;

/** Folder-chip settings only. Keep the original store and keys for existing users. */
public final class PluginStyles {
    private static volatile Map<String, ?> snapshot;
    private static final int[] HEIGHTS = {44, 48, 52}, GAPS = {2, 6, 10};

    public static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("regram_native_styles", 0);
    }

    private static Map<String, ?> values() {
        if (ApplicationLoader.applicationContext == null) return Collections.emptyMap();
        Map<String, ?> value = snapshot;
        if (value == null) snapshot = value = preferences().getAll();
        return value;
    }

    public static boolean flag(String key, boolean fallback) {
        Object value = values().get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public static int number(String key, int fallback, int min, int max) {
        Object value = values().get(key);
        return PluginStyleRules.clamp(value instanceof Integer ? (Integer) value : fallback, min, max);
    }

    public static void invalidate() { snapshot = null; }
    public static boolean chips() { return flag("chips", false); }

    public static int barHeight() {
        if (!chips()) return 44;
        return number("chip_size", 1, 0, 3) == 3 ? number("chip_height", 48, 36, 64)
                : HEIGHTS[number("chip_size", 1, 0, 2)];
    }

    public static int gap() {
        return number("chip_spacing", 1, 0, 3) == 3 ? number("chip_gap", 6, 0, 24)
                : GAPS[number("chip_spacing", 1, 0, 2)];
    }

    private PluginStyles() {}
}
