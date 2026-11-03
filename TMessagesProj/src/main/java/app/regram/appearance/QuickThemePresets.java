package app.regram.appearance;

import org.json.JSONObject;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;

/** Three named local slots. Snapshots contain only quick-theme settings, never theme files. */
public final class QuickThemePresets {
    private QuickThemePresets() {}
    public static ConfigItem[] items() {
        return new ConfigItem[]{NekoConfig.regramQuickTransparency, NekoConfig.regramQuickContrast,
                NekoConfig.regramQuickSaturation, NekoConfig.regramQuickBrightness,
                NekoConfig.regramQuickFoldersTransparency, NekoConfig.regramQuickNavigationTransparency,
                NekoConfig.regramQuickIncomingTransparency, NekoConfig.regramQuickOutgoingTransparency,
                NekoConfig.regramQuickBubbleRadius, NekoConfig.regramQuickFolderRadius,
                NekoConfig.regramQuickNavigationRadius, NekoConfig.regramQuickBlur,
                NekoConfig.regramQuickFolderColor, NekoConfig.regramQuickNavigationColor,
                NekoConfig.regramQuickIncomingColor, NekoConfig.regramQuickOutgoingColor};
    }
    private static String key(int slot) {
        if (slot < 0 || slot >= 3) throw new IllegalArgumentException("slot");
        return "RegramQuickPreset" + slot;
    }
    public static String name(int slot) {
        return NekoConfig.getPreferences().getString(key(slot) + "Name", "");
    }
    public static boolean save(int slot, String name) {
        try {
            JSONObject json = new JSONObject();
            for (ConfigItem item : items()) json.put(item.key, item.Int());
            json.put(NekoConfig.regramQuickHideNavigationBackdrop.key, NekoConfig.regramQuickHideNavigationBackdrop.Bool());
            NekoConfig.getPreferences().edit().putString(key(slot), json.toString())
                    .putString(key(slot) + "Name", name.trim()).apply();
            return true;
        } catch (Exception e) { return false; }
    }
    public static boolean load(int slot) {
        try {
            String saved = NekoConfig.getPreferences().getString(key(slot), "");
            JSONObject json = new JSONObject(saved);
            ConfigItem[] items = items();
            int[] values = new int[items.length];
            // Validate the complete snapshot before changing any preferences.
            for (int i = 0; i < items.length; i++) {
                Object value = json.get(items[i].key);
                if (!(value instanceof Number)) return false;
                values[i] = ((Number) value).intValue();
            }
            boolean hideBackdrop = json.optBoolean(NekoConfig.regramQuickHideNavigationBackdrop.key, false);
            for (int i = 0; i < items.length; i++) items[i].setConfigInt(values[i]);
            NekoConfig.regramQuickHideNavigationBackdrop.setConfigBool(hideBackdrop);
            NekoConfig.regramQuickTheme.setConfigBool(true);
            return true;
        } catch (Exception e) { return false; }
    }
    public static void delete(int slot) {
        NekoConfig.getPreferences().edit().remove(key(slot)).remove(key(slot) + "Name").apply();
    }
    public static void builtIn(int index) {
        if (index < 0 || index > 2) return;
        QuickTheme.reset();
        NekoConfig.regramQuickTheme.setConfigBool(true);
        if (index == 0) { // Glass
            NekoConfig.regramQuickTransparency.setConfigInt(20);
            NekoConfig.regramQuickFoldersTransparency.setConfigInt(30);
            NekoConfig.regramQuickNavigationTransparency.setConfigInt(35);
            NekoConfig.regramQuickBlur.setConfigInt(140);
            NekoConfig.regramQuickBubbleRadius.setConfigInt(20);
        } else if (index == 1) { // Soft
            NekoConfig.regramQuickContrast.setConfigInt(95);
            NekoConfig.regramQuickSaturation.setConfigInt(75);
            NekoConfig.regramQuickBubbleRadius.setConfigInt(24);
            NekoConfig.regramQuickFolderRadius.setConfigInt(24);
        } else { // Vivid
            NekoConfig.regramQuickContrast.setConfigInt(110);
            NekoConfig.regramQuickSaturation.setConfigInt(135);
            NekoConfig.regramQuickBubbleRadius.setConfigInt(12);
            NekoConfig.regramQuickFolderRadius.setConfigInt(12);
        }
    }
}
