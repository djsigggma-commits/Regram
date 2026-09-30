package app.regram.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Модель разрешений плагинов: набор ключей, хранение выданного, проверка в точках вызова.
 *
 * Модель разрешений плагинов. У exteraGram exteraGram 12.9.0 модели разрешений
 * нет вовсе (там только «источник доверенный/неизвестный»), так что это не перенос —
 * ссылаться на файл exteraGram не на что.
 *
 * Честная граница: плагины исполняются в том же процессе и через Chaquopy достают Java
 * напрямую ({@code from java.lang import ...}), поэтому враждебный код обойдёт любую
 * проверку мимо SDK. Задача модели — сделать намерения плагина видимыми и ограничить
 * случайный вред, а не построить границу против атакующего.
 */
public final class PluginPermissions {

    private PluginPermissions() {
    }

    // ---------- набор ключей (таблица «Набор разрешений» спецификации) ----------

    /** Меню, диалоги, бюллетени, экран настроек плагина. Выдаётся всегда, не спрашивается. */
    public static final String UI = "ui";
    /** Хуки апдейтов, чтение диалогов и сообщений, post-request хуки. */
    public static final String MESSAGES_READ = "messages.read";
    /** Отправка, редактирование, удаление сообщений; отмена исходящих. */
    public static final String MESSAGES_SEND = "messages.send";
    /** Сетевые запросы из кода плагина. */
    public static final String NETWORK = "network";
    /** Чтение и запись вне своего каталога, перехват открытия файлов. */
    public static final String FILES = "files";
    /** Перехват ссылок и интентов. */
    public static final String INTENTS = "intents";
    /** Чтение и запись настроек приложения вне своих. */
    public static final String SETTINGS = "settings";
    /** Xposed-хуки, Class Proxy, деоптимизация, allocateInstance. */
    public static final String HOOKS = "hooks";
    /** Загрузка нативных библиотек через ctypes и работа с их памятью. */
    public static final String NATIVE = "native";

    /** Все ключи в порядке спецификации; первый — {@link #UI}. */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            UI, MESSAGES_READ, MESSAGES_SEND, NETWORK, FILES, INTENTS, SETTINGS, HOOKS, NATIVE));

    /** Ключи, которые спрашиваются у пользователя (всё, кроме {@link #UI}). */
    public static final List<String> REQUESTABLE = Collections.unmodifiableList(
            new ArrayList<>(ALL.subList(1, ALL.size())));

    private static final Set<String> KNOWN = Collections.unmodifiableSet(
            new LinkedHashSet<>(ALL));

    /**
     * Отказы логируются один раз на пару (plugin, perm): проверки стоят на пути
     * регистрации хуков, но hookMethod плагин может звать в цикле — иначе лог зальёт.
     */
    private static final Set<String> LOGGED_DENIALS = ConcurrentHashMap.newKeySet();
    /** Один раз на плагин пишем, что он работает в режиме совместимости. */
    private static final Set<String> LOGGED_LEGACY = ConcurrentHashMap.newKeySet();
    /**
     * Эффективные разрешения пересчитываются только после изменения прав.
     * {@link #getEffective} стоит на каждом вызове плагина из Java-гейта, а один
     * пересчёт — это два чтения prefs, разбивка строки по запятой и несколько
     * копий списков. Сбрасываем карту целиком при любой записи: права меняются
     * только с экрана разрешений, и выборочный сброс дороже самого пересчёта.
     */
    private static final Map<String, List<String>> EFFECTIVE_CACHE = new ConcurrentHashMap<>();

    public static boolean isKnown(String perm) {
        return perm != null && KNOWN.contains(perm);
    }

    /**
     * Корневое разрешение: обладая {@code hooks}, плагин технически может всё остальное.
     * Помечается в интерфейсе особо (отдельное предупреждение в диалоге установки).
     */
    public static boolean isDangerous(String perm) {
        return HOOKS.equals(perm) || NATIVE.equals(perm);
    }

    /** Короткий английский текст для логов и как fallback, если строки локали ещё нет. */
    public static String describe(String perm) {
        if (perm == null) {
            return "unknown";
        }
        switch (perm) {
            case UI: return "user interface";
            case MESSAGES_READ: return "read messages and updates";
            case MESSAGES_SEND: return "send, edit and delete messages";
            case NETWORK: return "network access";
            case FILES: return "files outside its own directory";
            case INTENTS: return "intercept links and intents";
            case SETTINGS: return "app settings";
            case HOOKS: return "Java hooks (full control)";
            case NATIVE: return "load native libraries (full control)";
            default: return perm;
        }
    }

    /** Отбросить неизвестные ключи и дубли, порядок — как в {@link #ALL}. */
    public static List<String> sanitize(Collection<String> perms) {
        List<String> out = new ArrayList<>();
        if (perms == null) {
            return out;
        }
        for (String key : ALL) {
            if (perms.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    // ---------- хранилище ----------

    /**
     * Полный ключ выданных разрешений плагина: {@code plugin_perms_<id>},
     * значение — ключи через запятую. Пустая строка — выдан только {@link #UI}.
     */
    public static String prefsKey(String pluginId) {
        return PluginsConstants.KEY_PLUGIN_PERMS_PREFIX + pluginId;
    }

    private static SharedPreferences prefs() {
        SharedPreferences p = PluginsController.getInstance().getPreferences();
        if (p != null) {
            return p;
        }
        // PluginPermissions могут дёрнуть до PluginsController.init (например из
        // ранней инициализации движка) — тогда открываем тот же файл сами.
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return null;
        }
        return ctx.getSharedPreferences(PluginsConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Есть ли в prefs запись о согласии. Отличать «нет записи» (плагин установлен ДО
     * появления модели — режим совместимости) от пустой записи (пользователь снял всё).
     */
    public static boolean hasRecord(String pluginId) {
        SharedPreferences p = prefs();
        return p != null && pluginId != null && p.contains(prefsKey(pluginId));
    }

    /** Разрешения, записанные в prefs. null — записи нет вовсе. */
    public static List<String> getStored(String pluginId) {
        SharedPreferences p = prefs();
        if (p == null || pluginId == null) {
            return null;
        }
        String raw = p.getString(prefsKey(pluginId), null);
        if (raw == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String key = part.trim();
            if (!key.isEmpty() && isKnown(key) && !out.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * Записать согласие пользователя. Запись появляется всегда, даже пустая — именно
     * её наличие отличает установленный при модели плагин от старого.
     */
    public static void setGranted(String pluginId, Collection<String> perms) {
        SharedPreferences p = prefs();
        if (p == null || pluginId == null) {
            return;
        }
        List<String> clean = sanitize(perms);
        clean.remove(UI); // ui подразумевается, в строке не храним
        p.edit().putString(prefsKey(pluginId), String.join(",", clean)).apply();
        LOGGED_DENIALS.removeIf(k -> k.startsWith(pluginId + "|"));
        LOGGED_LEGACY.remove(pluginId);
        PluginDenialNotice.reset(pluginId);
        invalidateEffectiveCache();
    }

    public static void grant(String pluginId, String perm) {
        if (!isKnown(perm)) {
            return;
        }
        Set<String> current = new LinkedHashSet<>(getEffective(pluginId));
        current.add(perm);
        setGranted(pluginId, current);
        PluginDenialNotice.reset(pluginId);
    }

    public static void revoke(String pluginId, String perm) {
        if (UI.equals(perm)) {
            return; // ui не отзывается
        }
        Set<String> current = new LinkedHashSet<>(getEffective(pluginId));
        current.remove(perm);
        setGranted(pluginId, current);
        PluginDenialNotice.reset(pluginId);
    }

    /** Стереть запись (вызывается при удалении плагина). */
    public static void clear(String pluginId) {
        SharedPreferences p = prefs();
        if (p == null || pluginId == null) {
            return;
        }
        p.edit().remove(prefsKey(pluginId)).apply();
        LOGGED_DENIALS.removeIf(k -> k.startsWith(pluginId + "|"));
        LOGGED_LEGACY.remove(pluginId);
        PluginDenialNotice.reset(pluginId);
        invalidateEffectiveCache();
    }

    // ---------- эффективные разрешения ----------

    /**
     * Что плагин имеет на самом деле, с учётом режима совместимости.
     *
     * Три случая:
     *  1. запись в prefs есть — она и есть ответ (плюс {@link #UI});
     *  2. записи нет, плагин объявил {@code __permissions__} — считаем объявленное
     *     согласованным (плагин лежал в каталоге до появления модели);
     *  3. записи нет и объявления нет — старый плагин, написанный до модели вовсе.
     *     Даём всё: иначе обновление приложения молча ломает то, что работало.
     *     Свежая установка сюда не попадает — диалог согласия пишет запись всегда.
     */
    public static List<String> getEffective(String pluginId) {
        if (pluginId == null) {
            return effectiveFor(null);
        }
        List<String> cached = EFFECTIVE_CACHE.get(pluginId);
        if (cached == null) {
            cached = effectiveFor(pluginId);
            EFFECTIVE_CACHE.put(pluginId, cached);
        }
        return cached;
    }

    /** Сбросить кэш после изменения прав, уровня или метаданных плагина. */
    public static void invalidateEffectiveCache() {
        EFFECTIVE_CACHE.clear();
    }

    private static List<String> effectiveFor(String pluginId) {
        List<String> raw = getEffectiveRaw(pluginId);
        int level = PluginTrustLevel.getLevel(pluginId);
        if (level == PluginTrustLevel.ISOLATED) {
            return Collections.singletonList(UI);
        }
        if (level != PluginTrustLevel.TRUSTED) {
            raw.remove(HOOKS);
            raw.remove(NATIVE);
        }
        return raw.isEmpty() ? Collections.<String>emptyList()
                : Collections.unmodifiableList(raw);
    }

    /**
     * То же, но без учёта уровня доступа. Нужен самому
     * {@link PluginTrustLevel} при выводе уровня для плагина, установленного до
     * появления рычага, — иначе вычисление ходило бы по кругу.
     */
    public static List<String> getEffectiveRaw(String pluginId) {
        List<String> stored = getStored(pluginId);
        if (stored != null) {
            if (!stored.contains(UI)) {
                stored.add(0, UI);
            }
            return stored;
        }
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        if (plugin == null) {
            // Такого плагина в реестре нет. Раньше сюда попадала ветка
            // совместимости и выдавала ALL — а значит, проверку обходил любой
            // вызов с выдуманным id: hookMethod("nosuch", ...) получал hooks.
            // Незнакомому id не даём ничего сверх ui.
            if (pluginId != null && LOGGED_LEGACY.add("?" + pluginId)) {
                FileLog.w("PluginPermissions: unknown plugin id " + pluginId
                        + ", granting nothing but ui");
            }
            return new ArrayList<>(Collections.singletonList(UI));
        }
        if (plugin.permissionsDeclared) {
            Set<String> out = new LinkedHashSet<>();
            out.add(UI);
            out.addAll(sanitize(plugin.permissions));
            return new ArrayList<>(out);
        }
        if (pluginId != null && LOGGED_LEGACY.add(pluginId)) {
            FileLog.d("PluginPermissions: " + pluginId
                    + " installed before the permission model, granting all (legacy compat)");
        }
        return new ArrayList<>(ALL);
    }

    /** Разрешения, о которых плагин просит при установке (объявленные минус {@link #UI}). */
    public static List<String> getRequested(Plugin plugin) {
        List<String> out = plugin == null ? new ArrayList<>() : sanitize(plugin.permissions);
        out.remove(UI);
        return out;
    }

    // ---------- проверка ----------

    /** Тихая проверка (для UI: нарисовать состояние тумблера). Ничего не пишет в лог. */
    public static boolean has(String pluginId, String perm) {
        if (UI.equals(perm)) {
            return true;
        }
        if (isUnsafeMode()) {
            return true;
        }
        if (pluginId == null || !isKnown(perm)) {
            return false;
        }
        return getEffective(pluginId).contains(perm);
    }

    public static boolean isUnsafeMode() {
        try {
            return PluginsController.getInstance().isUnsafeMode();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Проверка в точке вызова. Отказ не роняет плагин: вызывающий возвращает безопасное
     * значение (null / false / пустой список), а сюда пишется, кому и в чём отказано.
     */
    public static boolean check(String pluginId, String perm) {
        if (has(pluginId, perm)) {
            return true;
        }
        String mark = pluginId + "|" + perm;
        if (LOGGED_DENIALS.add(mark)) {
            FileLog.w("PluginPermissions: denied '" + perm + "' (" + describe(perm)
                    + ") to plugin " + pluginId + " — not granted");
        }
        PluginDenialNotice.note(pluginId, perm);
        return false;
    }

    /** То же, но с указанием места отказа — так в логе видно, что именно плагин пытался сделать. */
    public static boolean check(String pluginId, String perm, String what) {
        if (has(pluginId, perm)) {
            return true;
        }
        String mark = pluginId + "|" + perm + "|" + what;
        if (LOGGED_DENIALS.add(mark)) {
            FileLog.w("PluginPermissions: denied " + what + " to plugin " + pluginId
                    + " — missing '" + perm + "' (" + describe(perm) + ")");
        }
        PluginDenialNotice.note(pluginId, perm);
        return false;
    }
}
