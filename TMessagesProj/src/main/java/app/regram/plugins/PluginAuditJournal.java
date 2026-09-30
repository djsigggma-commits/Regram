package app.regram.plugins;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Журнал наблюдений за плагинами: что каждый реально делал, а не что обещал
 * в {@code __permissions__}.
 *
 * Здесь копятся только события Java-стороны ({@link PluginSinkGate}); события
 * Python-гейта живут в его собственном кольцевом буфере и подмешиваются при
 * показе (extera_utils/audit_gate.py:get_journal). Так на горячем пути нет ни
 * JNI-перехода, ни блокировки.
 *
 * Всё в памяти: журнал — инструмент «посмотреть, что этот плагин делает»,
 * а не аудиторская запись. Переживать перезапуск ему незачем, а запись на
 * диск с горячего пути — та самая ошибка, из-за которой пришлось переписывать
 * PluginsWatchdog.
 */
public final class PluginAuditJournal {

    /** Хватает, чтобы увидеть поведение плагина; больше — только память. */
    private static final int LIMIT = 200;

    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>(LIMIT);
    private static final Map<String, Map<String, int[]>> PROFILE = new HashMap<>();

    private PluginAuditJournal() {
    }

    public static final class Entry {
        public final long ts;
        public final String pluginId;
        public final String event;
        public final String category;
        public final String detail;
        public final boolean allowed;
        public final String stack;
        /** Сколько раз подряд повторилось одно и то же обращение. */
        public int count = 1;

        Entry(String pluginId, String event, String category, String detail, boolean allowed) {
            this(System.currentTimeMillis(), pluginId, event, category, detail, allowed, null);
        }

        Entry(long ts, String pluginId, String event, String category, String detail,
              boolean allowed) {
            this(ts, pluginId, event, category, detail, allowed, null);
        }

        Entry(long ts, String pluginId, String event, String category, String detail,
              boolean allowed, String stack) {
            this.ts = ts;
            this.pluginId = pluginId;
            this.event = event;
            this.category = category;
            this.detail = detail;
            this.allowed = allowed;
            this.stack = stack;
        }
    }

    public static void record(String pluginId, String event, String category,
                              String detail, boolean allowed) {
        record(pluginId, event, category, detail, allowed, null);
    }

    public static void record(String pluginId, String event, String category,
                              String detail, boolean allowed, String stack) {
        if (pluginId == null) {
            return;
        }
        String trimmed = detail == null ? "" : (detail.length() > 200
                ? detail.substring(0, 200) : detail);
        Entry entry = new Entry(System.currentTimeMillis(), pluginId, event, category,
                trimmed, allowed, stack);
        synchronized (ENTRIES) {
            Entry last = ENTRIES.peekLast();
            if (last != null && last.pluginId.equals(pluginId) && last.event.equals(event)
                    && last.detail.equals(trimmed) && last.allowed == allowed) {
                // Одно и то же обращение подряд — счётчик, а не новая строка.
                last.count++;
            } else {
                if (ENTRIES.size() >= LIMIT) {
                    ENTRIES.pollFirst();
                }
                ENTRIES.addLast(entry);
            }
            Map<String, int[]> perPlugin = PROFILE.get(pluginId);
            if (perPlugin == null) {
                perPlugin = new HashMap<>();
                PROFILE.put(pluginId, perPlugin);
            }
            int[] counters = perPlugin.get(category);
            if (counters == null) {
                counters = new int[2];
                perPlugin.put(category, counters);
            }
            counters[allowed ? 0 : 1]++;
        }
    }

    /** Записи по плагину (или все, если {@code pluginId == null}), новые последними. */
    public static List<Entry> entries(String pluginId) {
        List<Entry> out = new ArrayList<>();
        synchronized (ENTRIES) {
            for (Entry entry : ENTRIES) {
                if (pluginId == null || pluginId.equals(entry.pluginId)) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    /** Категория -> {разрешено, отказано}. */
    public static Map<String, int[]> profile(String pluginId) {
        synchronized (ENTRIES) {
            Map<String, int[]> perPlugin = PROFILE.get(pluginId);
            if (perPlugin == null) {
                return Collections.emptyMap();
            }
            Map<String, int[]> copy = new HashMap<>();
            for (Map.Entry<String, int[]> e : perPlugin.entrySet()) {
                copy.put(e.getKey(), new int[]{e.getValue()[0], e.getValue()[1]});
            }
            return copy;
        }
    }

    public static void forget(String pluginId) {
        synchronized (ENTRIES) {
            PROFILE.remove(pluginId);
            ENTRIES.removeIf(entry -> entry.pluginId.equals(pluginId));
        }
    }

    /**
     * Java-события + события Python-гейта одним списком, отсортированным по времени.
     * Python-часть берётся через мост; если движок не поднят — только Java-часть.
     */
    public static List<Entry> merged(String pluginId) {
        List<Entry> out = entries(pluginId);
        try {
            String json = PythonPluginsEngine.getInstance().getAuditJournalJson(pluginId);
            if (json != null && !json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    Entry entry = new Entry(
                            item.optLong("ts", System.currentTimeMillis()),
                            JsonUtils.optStringOrNull(item, "plugin"),
                            JsonUtils.optStringOrNull(item, "event"),
                            JsonUtils.optStringOrNull(item, "category"),
                            JsonUtils.optStringOrNull(item, "detail"),
                            item.optBoolean("allowed", true));
                    if (entry.pluginId != null) {
                        entry.count = Math.max(1, item.optInt("count", 1));
                        out.add(entry);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Журнал не должен ронять экран: показываем то, что есть на Java.
        }
        Collections.sort(out, (a, b) -> Long.compare(a.ts, b.ts));
        return out;
    }
}
