package app.regram.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import tw.nekomimi.nekogram.config.ConfigItem;

/** Portable defaults, not an account backup or an upgrade migration. */
public final class BundledDefaults {
    private static final String ASSET = "regram/default-settings.json";
    private static final String[] SECTIONS = {"userconfing", "mainconfig", "themeconfig", "nkmrcfg"};
    private static JSONObject profile;

    private BundledDefaults() {
    }

    private static synchronized JSONObject profile(Context context) {
        if (profile != null || context == null) {
            return profile;
        }
        try (InputStream input = context.getAssets().open(ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            profile = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return profile;
    }

    /** Runs before SharedConfig, NekoConfig and Theme can cache their preferences. */
    public static synchronized void initializeForFreshInstall(Context context) {
        if (context == null) {
            return;
        }
        // Existing installations (including restored backups) are never seeded.
        for (String section : SECTIONS) {
            if (!context.getSharedPreferences(section, Context.MODE_PRIVATE).getAll().isEmpty()) {
                return;
            }
        }
        JSONObject defaults = profile(context);
        if (defaults == null) {
            return;
        }
        try {
            for (String section : SECTIONS) {
                JSONObject values = defaults.optJSONObject(section);
                if (values == null) {
                    continue;
                }
                SharedPreferences preferences = context.getSharedPreferences(section, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                Iterator<String> keys = values.keys();
                while (keys.hasNext()) {
                    String exportedKey = keys.next();
                    String key = actualKey(exportedKey);
                    if (!preferences.contains(key)) {
                        Object value = values.get(exportedKey);
                        // Monet themes are registered only on Android 12+.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                                && ("mainconfig".equals(section) || "themeconfig".equals(section))) {
                            if ("Monet Dark".equals(value)) {
                                value = "Night";
                            } else if ("Monet Light".equals(value)) {
                                value = "Blue";
                            }
                        }
                        put(editor, exportedKey, value);
                    }
                }
                if (!editor.commit()) {
                    FileLog.d("Unable to persist bundled defaults for " + section);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** ConfigItem uses the same profile for unset values and 'Reset to defaults'. */
    public static Object configDefault(String key, int type, Object fallback) {
        JSONObject defaults = profile(ApplicationLoader.applicationContext);
        JSONObject values = defaults == null ? null : defaults.optJSONObject("nkmrcfg");
        if (values == null) {
            return fallback;
        }
        String exportedKey = key;
        if (type == ConfigItem.configTypeFloat) {
            exportedKey += "_float";
        } else if (type == ConfigItem.configTypeLong) {
            exportedKey += "_long";
        }
        Object value = values.opt(exportedKey);
        switch (type) {
            case ConfigItem.configTypeBool:
                return value instanceof Boolean ? value : fallback;
            case ConfigItem.configTypeInt:
                return value instanceof Number ? ((Number) value).intValue() : fallback;
            case ConfigItem.configTypeLong:
                return value instanceof Number ? ((Number) value).longValue() : fallback;
            case ConfigItem.configTypeFloat:
                return value instanceof Number ? ((Number) value).floatValue() : fallback;
            case ConfigItem.configTypeString:
                return value instanceof String ? value : fallback;
            default:
                return fallback;
        }
    }

    private static String actualKey(String key) {
        if (key.endsWith("_float")) {
            return key.substring(0, key.length() - 6);
        }
        if (key.endsWith("_long")) {
            return key.substring(0, key.length() - 5);
        }
        return key;
    }

    private static void put(SharedPreferences.Editor editor, String exportedKey, Object value) {
        String key = actualKey(exportedKey);
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Number) {
            Number number = (Number) value;
            if (exportedKey.endsWith("_float")) {
                editor.putFloat(key, number.floatValue());
            } else if (exportedKey.endsWith("_long")) {
                editor.putLong(key, number.longValue());
            } else {
                editor.putInt(key, number.intValue());
            }
        }
    }
}
