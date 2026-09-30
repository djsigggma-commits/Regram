package app.regram.plugins;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Что плагин может делать по его исходнику — и хранение этого разбора.
 *
 * Разбор делает Python-сторона ({@code extera_utils/capability_scan.py}): AST
 * и поиск по тексту, без запуска кода. Здесь только вызов, разбор JSON и
 * запись рядом с разрешениями.
 *
 * Хранить обязательно: разбор нужен не только в момент установки, но и потом
 * на экране разрешений — там человек спрашивает «почему приложение решило, что
 * плагину нужна сеть», и ответ должен быть под рукой, а не после переустановки.
 * Перечитывать файл заново каждый раз нельзя: это чтение с диска и запуск
 * Python на открытии экрана.
 */
public final class PluginCapabilityScan {

    private static final String PREFIX = "plugin_scan_";

    /** Ключ разбора, который не является разрешением: код плагина нечитаем. */
    public static final String KEY_OBFUSCATION = "obfuscation";

    private PluginCapabilityScan() {
    }

    /** Статический разбор исходника; пустая карта, если движок молчит. */
    public static Map<String, List<String>> scan(File file) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (file == null) {
            return result;
        }
        try {
            String json = PythonPluginsEngine.getInstance()
                    .scanCapabilitiesJson(file.getAbsolutePath());
            return parse(json);
        } catch (Throwable t) {
            FileLog.e("PluginCapabilityScan: scan failed", t);
            return result;
        }
    }

    private static Map<String, List<String>> parse(String json) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (TextUtils.isEmpty(json)) {
            return result;
        }
        try {
            JSONObject parsed = new JSONObject(json);
            for (Iterator<String> keys = parsed.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!PluginPermissions.isKnown(key) && !KEY_OBFUSCATION.equals(key)) {
                    continue;  // "error" и всё незнакомое
                }
                JSONArray array = parsed.optJSONArray(key);
                List<String> evidence = new ArrayList<>();
                for (int i = 0; array != null && i < array.length(); i++) {
                    String item = JsonUtils.optStringOrNull(array, i);
                    if (item != null && !evidence.contains(item)) {
                        evidence.add(item);
                    }
                }
                result.put(key, evidence);
            }
        } catch (Throwable t) {
            FileLog.e("PluginCapabilityScan: bad json", t);
        }
        return result;
    }

    public static void store(String pluginId, Map<String, List<String>> capabilities) {
        if (TextUtils.isEmpty(pluginId) || capabilities == null) {
            return;
        }
        SharedPreferences prefs = PluginsController.getInstance().getPreferences();
        if (prefs == null) {
            return;
        }
        try {
            JSONObject object = new JSONObject();
            for (Map.Entry<String, List<String>> entry : capabilities.entrySet()) {
                object.put(entry.getKey(), new JSONArray(entry.getValue()));
            }
            prefs.edit().putString(PREFIX + pluginId, object.toString()).apply();
        } catch (Throwable t) {
            FileLog.e("PluginCapabilityScan: store failed", t);
        }
    }

    /** Что нашлось при установке; пустая карта для плагинов, поставленных раньше. */
    public static Map<String, List<String>> load(String pluginId) {
        if (TextUtils.isEmpty(pluginId)) {
            return new LinkedHashMap<>();
        }
        SharedPreferences prefs = PluginsController.getInstance().getPreferences();
        if (prefs == null) {
            return new LinkedHashMap<>();
        }
        return parse(prefs.getString(PREFIX + pluginId, null));
    }

    /**
     * Разобрать плагин, если разбора ещё нет.
     *
     * Плагины, поставленные до появления разбора (и все, что стояли раньше
     * этой версии), записи не имеют — и раскрывать на экране разрешений было
     * бы нечего. Разбор идёт на фоновом потоке: это чтение файла и Python.
     *
     * @param onReady выполнится на UI-потоке, только если что-то нашлось.
     */
    public static void ensureScanned(Plugin plugin, Runnable onReady) {
        if (plugin == null || TextUtils.isEmpty(plugin.id) || TextUtils.isEmpty(plugin.path)) {
            return;
        }
        SharedPreferences prefs = PluginsController.getInstance().getPreferences();
        if (prefs != null && prefs.contains(PREFIX + plugin.id)) {
            return;
        }
        final String id = plugin.id;
        final File file = new File(plugin.path);
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            Map<String, List<String>> capabilities = scan(file);
            store(id, capabilities);
            if (!capabilities.isEmpty() && onReady != null) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(onReady);
            }
        });
    }

    /** Улики нечитаемости кода; пустой список, если разбор ничего не нашёл. */
    public static List<String> obfuscationEvidence(Map<String, List<String>> capabilities) {
        return evidenceOf(capabilities, KEY_OBFUSCATION);
    }

    public static boolean isObfuscated(Map<String, List<String>> capabilities) {
        return !obfuscationEvidence(capabilities).isEmpty();
    }

    /** Улики одного разрешения; пустой список, если разбора нет. */
    public static List<String> evidenceOf(Map<String, List<String>> capabilities, String permission) {
        List<String> evidence = capabilities == null ? null : capabilities.get(permission);
        return evidence == null ? new ArrayList<>() : evidence;
    }

    public static void clear(String pluginId) {
        if (TextUtils.isEmpty(pluginId)) {
            return;
        }
        SharedPreferences prefs = PluginsController.getInstance().getPreferences();
        if (prefs != null) {
            prefs.edit().remove(PREFIX + pluginId).apply();
        }
    }

    /** Разрешения из разбора в порядке экрана плагина. */
    public static List<String> ordered(Map<String, List<String>> capabilities) {
        List<String> out = new ArrayList<>();
        if (capabilities == null) {
            return out;
        }
        for (String permission : PluginPermissions.REQUESTABLE) {
            if (capabilities.containsKey(permission)) {
                out.add(permission);
            }
        }
        return out;
    }
}
