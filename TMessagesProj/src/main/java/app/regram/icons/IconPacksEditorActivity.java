package app.regram.icons;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Map;

import app.regram.icons.picker.IconPickerController;
import app.regram.icons.picker.ReplaceIconBottomSheet;
import app.regram.icons.ui.NewIconPackBottomSheet;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.icons.IconsResources;

/**
 * Экран-редактор пака: список <b>всех</b> иконок приложения с заменой по тапу
 * (порт {@code com.exteragram.messenger.icons.ui.IconPacksEditorActivity}, 447 строк).
 *
 * <p>Что перенесено из exteraGram:
 * <ul>
 *   <li>поиск в шапке с дебаунсом 200 мс ({@code IconPacksEditorActivity.java:47});</li>
 *   <li>три фильтра — все / заменённые / незаменённые ({@code :186–188});</li>
 *   <li>статический кэш списка иконок на процесс ({@code cachedIconItems}, {@code isIconsLoaded},
 *       {@code isLoading}) — список строится один раз;</li>
 *   <li>строка показывает обе иконки: оригинал слева, заменённая справа
 *       ({@code EditorIconCell.Factory.bindView}).</li>
 * </ul>
 *
 * <p>Отличия:
 * <ul>
 *   <li>exteraGram живёт на {@code BasePreferencesActivity} + {@code UniversalRecyclerView};
 *       у нас база — {@link BaseNekoSettingsActivity}, поэтому ячейка своя
 *       ({@code TextCell.setTextAndIconAndValueDrawable} в форке нет), тип строки — 100;</li>
 *   <li>«сохранить и выйти» ({@code :195}) сделан пунктом меню, как в exteraGram, но завершает
 *       редактирование через {@link IconPickerController#finishEditing()};</li>
 *   <li>пункт «Edit» открывает {@link NewIconPackBottomSheet} в режиме правки метаданных, как
 *       в exteraGram ({@code :317}); список обновляется через колбэк, потому что
 *       {@code NotificationCenter.iconPackUpdated} в форке нет;</li>
 *   <li>подписи фильтров ждут строк {@code IconPickerAllIcons} / {@code IconPickerReplacedIcons} /
 *       {@code IconPickerNotReplacedIcons} / {@code IconPickerFilter} / {@code IconPickerSaveAndExit}:
 *       строки правит только координатор, поэтому резолвим их по имени и до появления
 *       показываем английский текст exteraGram (см. {@link #localized(String, String)}).</li>
 * </ul>
 */
public class IconPacksEditorActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_ICON = 100;

    private static final int MENU_SEARCH = 0;
    private static final int MENU_OTHER = 1;
    private static final int MENU_SAVE_AND_EXIT = 2;
    private static final int MENU_EDIT_INFO = 3;
    private static final int MENU_FILTER_BASE = 10;

    /** Фильтры списка иконок. */
    private static final int FILTER_ALL = 0;
    private static final int FILTER_REPLACED = 1;
    private static final int FILTER_NOT_REPLACED = 2;

    /** Список иконок приложения строится один раз на процесс — как {@code cachedIconItems}. */
    private static final ArrayList<IconEntry> cachedIcons = new ArrayList<>();
    private static volatile boolean iconsLoaded;
    private static volatile boolean loading;

    private final String packId;
    private IconPack pack;

    private final ArrayList<IconEntry> visible = new ArrayList<>();
    private final ActionBarMenuSubItem[] filterItems = new ActionBarMenuSubItem[3];
    private int iconFilter = FILTER_ALL;

    private ActionBarMenuItem otherItem;
    private String query;
    private boolean searching;
    private Runnable searchRunnable;

    public IconPacksEditorActivity(String packId) {
        this.packId = packId;
    }

    // ---- жизненный цикл ----

    @Override
    public boolean onFragmentCreate() {
        IconPacksConfig.init();
        pack = IconPackStorage.findPackById(packId);
        loadIconsAsync();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        pack = IconPackStorage.findPackById(packId);
        updateRows();
        super.onResume();
    }

    @Override
    protected String getActionBarTitle() {
        return pack == null ? getString(R.string.IconPacks) : pack.getName();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search);
        searchItem.setIsSearchField(true);
        searchItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                if (otherItem != null) {
                    otherItem.setVisibility(View.GONE);
                }
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                query = null;
                if (otherItem != null) {
                    otherItem.setVisibility(View.VISIBLE);
                }
                updateList();
            }

            @Override
            public void onTextChanged(EditText editText) {
                // дебаунс 200 мс
                if (searchRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                }
                searchRunnable = () -> {
                    query = editText.getText().toString();
                    updateList();
                };
                AndroidUtilities.runOnUIThread(searchRunnable, 200);
            }
        });
        searchItem.setSearchFieldHint(getString(R.string.Search));

        otherItem = menu.addItem(MENU_OTHER, R.drawable.ic_ab_other);
        filterItems[FILTER_ALL] = otherItem.addSubItem(MENU_FILTER_BASE + FILTER_ALL, R.drawable.msg_select,
                localized("IconPickerAllIcons", "All icons"), true);
        filterItems[FILTER_REPLACED] = otherItem.addSubItem(MENU_FILTER_BASE + FILTER_REPLACED, R.drawable.msg_select,
                localized("IconPickerReplacedIcons", "Replaced icons"), true);
        filterItems[FILTER_NOT_REPLACED] = otherItem.addSubItem(MENU_FILTER_BASE + FILTER_NOT_REPLACED, R.drawable.msg_select,
                localized("IconPickerNotReplacedIcons", "Not replaced icons"), true);
        updateFilterChecks();
        otherItem.addSubItem(MENU_EDIT_INFO, R.drawable.msg_edit, getString(R.string.Edit));
        if (IconPacksConfig.isEditing()) {
            ActionBarMenuSubItem saveItem = otherItem.addSubItem(MENU_SAVE_AND_EXIT, R.drawable.ic_ab_done,
                    localized("IconPickerSaveAndExit", "Save and exit"));
            // exteraGram красит пункт в key_featuredStickers_addButtonPressed
            // (IconPacksEditorActivity.java:196); в форке у ActionBarMenuSubItem нет setColors(int,int)
            int accent = getThemedColor(Theme.key_featuredStickers_addButtonPressed);
            saveItem.setTextColor(accent);
            saveItem.setIconColor(accent);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SAVE_AND_EXIT) {
                    IconPickerController.finishEditing();
                    finishFragment();
                } else if (id == MENU_EDIT_INFO) {
                    showPackInfoSheet();
                } else if (id >= MENU_FILTER_BASE) {
                    setIconFilter(id - MENU_FILTER_BASE);
                }
            }
        });
        return view;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (!searching) {
            return super.onBackPressed(invoked);
        }
        if (!invoked) {
            return false;
        }
        actionBar.closeSearchField();
        return false;
    }

    /** Пункт «Edit» из меню: та же шторка, что создаёт паки, но в режиме правки метаданных. */
    private void showPackInfoSheet() {
        Context context = getParentActivity();
        if (context == null || pack == null) {
            return;
        }
        NewIconPackBottomSheet sheet = new NewIconPackBottomSheet(this, context, pack);
        sheet.setOnDone(() -> {
            pack = IconPackStorage.findPackById(packId);
            actionBar.setTitle(getActionBarTitle());
            updateList();
        });
        showDialog(sheet);
    }

    // ---- данные ----

    /** Один ресурс приложения: id и имя. Аналог {@code UItem} с {@code id}/{@code text} у exteraGram. */
    private static class IconEntry {
        final int id;
        final String name;

        IconEntry(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /**
     * Разовое построение списка иконок в фоне (порт {@code loadIconsAsync}).
     * Список статический, поэтому повторный вход на экран мгновенный.
     */
    private void loadIconsAsync() {
        if (loading || (iconsLoaded && !cachedIcons.isEmpty())) {
            return;
        }
        loading = true;
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<IconEntry> collected = new ArrayList<>(1500);
            try {
                Map<String, Integer> icons = IconPackManager.getInstance().getSystemIcons();
                for (Map.Entry<String, Integer> entry : icons.entrySet()) {
                    collected.add(new IconEntry(entry.getValue(), entry.getKey()));
                }
            } catch (Throwable t) {
                FileLog.e("openExtera: cannot build icon list", t);
            }
            AndroidUtilities.runOnUIThread(() -> {
                cachedIcons.clear();
                cachedIcons.addAll(collected);
                iconsLoaded = !collected.isEmpty();
                loading = false;
                updateList();
            });
        });
    }

    private void setIconFilter(int filter) {
        if (iconFilter == filter) {
            return;
        }
        iconFilter = filter;
        updateFilterChecks();
        updateList();
    }

    private void updateFilterChecks() {
        for (int i = 0; i < filterItems.length; i++) {
            if (filterItems[i] != null) {
                filterItems[i].setChecked(iconFilter == i);
            }
        }
    }

    private void updateList() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        visible.clear();
        String lower = searching && !TextUtils.isEmpty(query)
                ? query.toLowerCase(java.util.Locale.ROOT) : null;
        for (int i = 0; i < cachedIcons.size(); i++) {
            IconEntry entry = cachedIcons.get(i);
            if (lower != null && !entry.name.toLowerCase(java.util.Locale.ROOT).contains(lower)) {
                continue;
            }
            boolean replaced = isReplaced(entry.name);
            if (iconFilter == FILTER_REPLACED && !replaced) {
                continue;
            }
            if (iconFilter == FILTER_NOT_REPLACED && replaced) {
                continue;
            }
            visible.add(entry);
        }
        rowCount = visible.size();
    }

    private boolean isReplaced(String name) {
        return pack != null && pack.getIcons().containsKey(name);
    }

    // ---- клик ----

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position < 0 || position >= visible.size() || getParentActivity() == null) {
            return;
        }
        if (pack == null) {
            return;
        }
        IconEntry entry = visible.get(position);
        // saveCustomIcon/resetCustomIcon сами чистят кэши и перечитывают паки,
        // здесь остаётся перечитать метаданные и перерисовать список
        showDialog(new ReplaceIconBottomSheet(getParentActivity(), packId, entry.id, () -> {
            pack = IconPackStorage.findPackById(packId);
            updateList();
        }));
    }

    // ---- ячейка ----

    /**
     * Строка списка: слева оригинал, справа замена из пака (порт {@code EditorIconCell},
     * {@code IconPacksEditorActivity.java:63}; в exteraGram это {@code TextCell} с
     * {@code setTextAndIconAndValueDrawable} + {@code setOffsetFromImage(68)}).
     */
    private static class EditorIconCell extends FrameLayout {

        private final ImageView originalView;
        private final ImageView replacedView;
        private final TextView textView;
        private boolean needDivider;

        EditorIconCell(Context context) {
            super(context);
            setWillNotDraw(false);

            boolean rtl = LocaleController.isRTL;

            originalView = new ImageView(context);
            originalView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(originalView, LayoutHelper.createFrame(24, 24f,
                    (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,
                    rtl ? 0 : 20, 0, rtl ? 20 : 0, 0));

            replacedView = new ImageView(context);
            replacedView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(replacedView, LayoutHelper.createFrame(24, 24f,
                    (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                    rtl ? 20 : 0, 0, rtl ? 0 : 20, 0));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            // 68 dp от края — offsetFromImage exteraGram
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,
                    rtl ? 56 : 68, 0, rtl ? 68 : 56, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        void set(String name, Drawable original, Drawable replaced, boolean divider) {
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setText(name);
            if (original != null) {
                original = original.mutate();
                original.setColorFilter(new PorterDuffColorFilter(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            }
            originalView.setImageDrawable(original);
            replacedView.setImageDrawable(replaced);
            replacedView.setVisibility(replaced == null ? INVISIBLE : VISIBLE);
            needDivider = divider;
            requestLayout();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!needDivider) {
                return;
            }
            boolean rtl = LocaleController.isRTL;
            canvas.drawLine(rtl ? 0 : dp(68), getMeasuredHeight() - 1,
                    getMeasuredWidth() - (rtl ? dp(68) : 0), getMeasuredHeight() - 1,
                    Theme.dividerPaint);
        }
    }

    // ---- адаптер ----

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            return TYPE_ICON;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_ICON || super.isEnabled(holder);
        }

        /** Строки иконок собираются в карточку-секцию наравне со штатными ячейками. */
        @Override
        protected boolean isSectionContent(int viewType) {
            return viewType == TYPE_ICON || super.isSectionContent(viewType);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_ICON) {
                EditorIconCell cell = new EditorIconCell(mContext);
                cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(cell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder.getItemViewType() != TYPE_ICON || position < 0 || position >= visible.size()) {
                return;
            }
            IconEntry entry = visible.get(position);
            Drawable original = originalDrawable(entry.id);
            Drawable replaced = pack == null ? null
                    : IconPackManager.getInstance().getPackIconDrawable(pack, entry.id);
            ((EditorIconCell) holder.itemView).set(entry.name, original, replaced, position != visible.size() - 1);
        }
    }

    /**
     * Оригинальная иконка без пользовательских паков — аналог
     * {@code ExteraResources.getOriginalDrawable(id)} ({@code ExteraResources.java:38}).
     * Ресурсы приложения не обёрнуты: подмену ставит только {@code LaunchActivity.getResources()}
     * ({@code LaunchActivity.java:419}), поэтому обычно сюда попадает стоковый drawable.
     * Проверка на {@link IconsResources} оставлена на случай, если обёртка переедет выше:
     * {@code getOriginalDrawableForDensity} в паки не ходит и рекурсии не даёт.
     */
    private static Drawable originalDrawable(int resId) {
        try {
            Resources resources = ApplicationLoader.applicationContext.getResources();
            if (resources instanceof IconsResources) {
                return ((IconsResources) resources).getOriginalDrawableForDensity(resId, 0, null);
            }
            return resources.getDrawable(resId);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Строка по имени ресурса: строки подсистемы иконок заводит координатор, и пока их нет,
     * экран не должен ломать сборку. Как только ключ появится в {@code res/values/strings_oe_icons.xml},
     * подпись подхватится автоматически.
     */
    private static String localized(String key, String fallback) {
        try {
            Context context = ApplicationLoader.applicationContext;
            int id = context.getResources().getIdentifier(key, "string", context.getPackageName());
            return id == 0 ? fallback : LocaleController.getString(id);
        } catch (Throwable t) {
            return fallback;
        }
    }

}
