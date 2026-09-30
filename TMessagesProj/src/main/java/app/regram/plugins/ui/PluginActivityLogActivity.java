package app.regram.plugins.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import app.regram.plugins.Plugin;
import app.regram.plugins.PluginAuditJournal;
import app.regram.plugins.PluginPermissions;
import app.regram.plugins.PluginsController;
import app.regram.plugins.PythonPluginsEngine;

/**
 * Что плагин делал на самом деле: журнал наблюдений вместо обещаний манифеста.
 *
 * Смысл экрана в расхождении. Манифест — это то, что плагин попросил; здесь
 * видно, к чему он обращался и что ему запретили. Если плагин просил сеть, но
 * ни разу в неё не пошёл, разрешение можно отозвать; если лезет туда, чего не
 * просил, это видно строкой «отказано».
 *
 * Источников два: {@link PluginAuditJournal} (Java-стоки) и кольцевой буфер
 * Python-гейта; {@link PluginAuditJournal#merged} сливает их по времени.
 */
public class PluginActivityLogActivity extends BaseFragment {

    private static final int ID_CLEAR = 1;
    /** id строк журнала: база плюс порядковый номер записи. */
    private static final int ID_ENTRY_BASE = 100;
    /** Больше на экране не нужно: журнал показывает поведение, а не аудит. */
    private static final int MAX_ROWS = 120;

    private final String pluginId;
    private UniversalRecyclerView listView;
    /**
     * Развёрнутые строки — по индексу записи в текущем списке.
     *
     * Подробность в строку не влезает: путь к файлу, адрес с портом, имя
     * класса. Обрезать её насовсем — значит спрятать ровно то, ради чего
     * журнал и открывают, поэтому строка раскрывается по нажатию.
     */
    private final Set<Integer> expanded = new HashSet<>();

    public PluginActivityLogActivity(String pluginId) {
        this.pluginId = pluginId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.PluginActivityLog));
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        if (plugin != null) {
            actionBar.setSubtitle(plugin.getDisplayName());
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        fragmentView = contentView;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        List<PluginAuditJournal.Entry> entries = PluginAuditJournal.merged(pluginId);

        items.add(UItem.asHeader(getString(R.string.PluginActivityProfile)));
        Map<String, int[]> profile = collectProfile(entries);
        if (profile.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.PluginActivityNothing)));
        } else {
            for (Map.Entry<String, int[]> row : profile.entrySet()) {
                int allowed = row.getValue()[0];
                int denied = row.getValue()[1];
                CharSequence value = denied == 0
                        ? String.valueOf(allowed)
                        : LocaleController.formatString(R.string.PluginActivityCounts, allowed, denied);
                items.add(UItem.asButton(0, categoryTitle(row.getKey()), value));
            }
            items.add(UItem.asShadow(getString(R.string.PluginActivityProfileInfo)));
        }

        items.add(UItem.asHeader(getString(R.string.PluginActivityRecent)));
        if (entries.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.PluginActivityNothing)));
        } else {
            int from = Math.max(0, entries.size() - MAX_ROWS);
            CharSequence previousTitle = null;
            int repeats = 0;
            int lastIndex = -1;
            for (int i = entries.size() - 1; i >= from; i--) {
                PluginAuditJournal.Entry entry = entries.get(i);
                CharSequence title = describe(entry);
                // Разные события могут читаться одной фразой (exec и compile —
                // оба «собрал код на ходу»). Показывать их двумя одинаковыми
                // строками незачем.
                if (previousTitle != null && previousTitle.toString().equals(title.toString())
                        && !expanded.contains(i + 1)) {
                    repeats += Math.max(1, entry.count);
                    UItem last = items.get(items.size() - 1);
                    last.textValue = countAndTime(repeats, entry.ts);
                    continue;
                }
                previousTitle = title;
                repeats = Math.max(1, entry.count);
                lastIndex = i;
                UItem row = UItem.asButton(ID_ENTRY_BASE + i, title, countAndTime(repeats, entry.ts));
                if (!entry.allowed) {
                    row.red();
                }
                items.add(row);
                if (expanded.contains(i)) {
                    items.add(UItem.asShadow(fullDetail(entry)));
                    previousTitle = null;
                }
            }
        }

        items.add(UItem.asButton(ID_CLEAR, getString(R.string.PluginActivityClear)).red());
        items.add(UItem.asShadow(getString(R.string.PluginActivityInfo)));
    }

    /**
     * Строка журнала человеческим языком.
     *
     * Раньше событие и его деталь шли двумя строками («import», а под ней
     * «org.telegram»), и список из десятка импортов выглядел мусором. Здесь
     * одно действие — одна строка, деталь внутри неё.
     */
    private CharSequence describe(PluginAuditJournal.Entry entry) {
        String detail = entry.detail == null ? "" : entry.detail;
        String category = entry.category == null ? "misc" : entry.category;
        switch (category) {
            case "imports":
                return LocaleController.formatString(R.string.PluginActivityDidImport, detail);
            case "network":
                return LocaleController.formatString(R.string.PluginActivityDidNetwork,
                        detail.isEmpty() ? entry.event : detail);
            case "files":
                return LocaleController.formatString(R.string.PluginActivityDidFile,
                        detail.isEmpty() ? entry.event : shortPath(detail));
            case "reflection":
                return LocaleController.formatString(R.string.PluginActivityDidReach, detail);
            case "process":
                return getString(R.string.PluginActivityDidProcess);
            case "native":
                return LocaleController.formatString(R.string.PluginActivityDidNative,
                        detail.isEmpty() ? entry.event : shortPath(detail));
            case "introspection":
                return getString(R.string.PluginActivityDidIntrospect);
            case "code":
                return getString(R.string.PluginActivityDidCode);
            default:
                return detail.isEmpty() ? entry.event : entry.event + " · " + detail;
        }
    }

    /**
     * Путь в строку списка целиком не влезает и обрывается посередине, где
     * ничего не видно. Показываем последние два сегмента — по ним понятно,
     * куда полез плагин.
     */
    private static String shortPath(String path) {
        String cleaned = path;
        int space = cleaned.indexOf(' ');
        String suffix = "";
        if (space > 0) {
            suffix = cleaned.substring(space);
            cleaned = cleaned.substring(0, space);
        }
        int last = cleaned.lastIndexOf('/');
        if (last <= 0) {
            return path;
        }
        int previous = cleaned.lastIndexOf('/', last - 1);
        String tail = previous < 0 ? cleaned.substring(last) : cleaned.substring(previous);
        return "…" + tail + suffix;
    }

    /** Время, а при повторах — сколько раз. */
    /** Полный вид записи: событие, подробность целиком и точное время. */
    private CharSequence fullDetail(PluginAuditJournal.Entry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.event);
        if (!entry.allowed) {
            sb.append(" — ").append(getString(R.string.PluginActivityDenied));
        }
        if (entry.detail != null && !entry.detail.isEmpty()) {
            sb.append('\n').append(entry.detail);
        }
        sb.append('\n').append(LocaleController.getInstance().getFormatterDayWithSeconds()
                .format(new Date(entry.ts)));
        if (entry.stack != null && !entry.stack.isEmpty()) {
            sb.append('\n').append('\n').append(entry.stack);
        }
        return sb.toString();
    }

    private CharSequence countAndTime(int count, long ts) {
        String time = LocaleController.getInstance().getFormatterDay().format(new Date(ts));
        if (count > 1) {
            return LocaleController.formatString(R.string.PluginActivityRepeat, count) + "  " + time;
        }
        return time;
    }

    /**
     * Счётчики считаем по показанным записям, а не по счётчикам движка: журнал
     * кольцевой, и старые записи из него уже вытеснены — иначе цифра в шапке
     * не сошлась бы со списком под ней.
     */
    private Map<String, int[]> collectProfile(List<PluginAuditJournal.Entry> entries) {
        Map<String, int[]> profile = new TreeMap<>();
        for (PluginAuditJournal.Entry entry : entries) {
            String category = entry.category == null ? "misc" : entry.category;
            int[] counters = profile.get(category);
            if (counters == null) {
                counters = new int[2];
                profile.put(category, counters);
            }
            counters[entry.allowed ? 0 : 1] += Math.max(1, entry.count);
        }
        return profile;
    }

    private CharSequence categoryTitle(String category) {
        if (category == null) {
            return "";
        }
        switch (category) {
            case "network": return PluginPermissionsActivity.titleOf(PluginPermissions.NETWORK);
            case "files": return PluginPermissionsActivity.titleOf(PluginPermissions.FILES);
            case "imports": return getString(R.string.PluginActivityImports);
            case "process": return getString(R.string.PluginActivityProcess);
            case "native": return getString(R.string.PluginActivityNative);
            case "reflection": return getString(R.string.PluginActivityReflection);
            case "introspection": return getString(R.string.PluginActivityIntrospection);
            case "code": return getString(R.string.PluginActivityCode);
            default: return getString(R.string.PluginActivityOther);
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < ID_ENTRY_BASE) {
            return false;
        }
        List<PluginAuditJournal.Entry> entries = PluginAuditJournal.merged(pluginId);
        int index = item.id - ID_ENTRY_BASE;
        if (index < 0 || index >= entries.size()) {
            return false;
        }
        AndroidUtilities.addToClipboard(fullDetail(entries.get(index)).toString());
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.copy, getString(R.string.TextCopied))
                .show();
        return true;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_ENTRY_BASE) {
            int index = item.id - ID_ENTRY_BASE;
            if (!expanded.remove(index)) {
                expanded.add(index);
            }
            if (listView != null) {
                listView.adapter.update(true);
            }
            return;
        }
        if (item.id != ID_CLEAR) {
            return;
        }
        PythonPluginsEngine.getInstance().forgetAudit(pluginId);
        if (listView != null) {
            listView.adapter.update(true);
        }
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.done, getString(R.string.PluginActivityCleared))
                .show();
    }
}
