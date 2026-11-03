package app.regram.settings;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import app.regram.appearance.FoldersPreviewCell;
import app.regram.appearance.PluginStyleRules;
import app.regram.appearance.PluginStyles;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import java.util.ArrayList;
import java.util.Locale;

/** Folder-chip controls, reached from Built-in appearance (or a folder long press). */
public final class ChipFoldersActivity extends BaseNekoSettingsActivity {
    private static final int BOOL = 0, INT = 1, CHOICE = 2, COLOR = 3, ACTION = 4, PREVIEW = 5, INFO = 6;
    private final ArrayList<Row> entries = new ArrayList<>();

    private static final class Row {
        final String key, title;
        final int kind, fallback, min, max, position;
        final String[] choices;
        Row(String key, String title, int kind, int fallback, int min, int max, int position, String[] choices) {
            this.key = key; this.title = title; this.kind = kind; this.fallback = fallback;
            this.min = min; this.max = max; this.position = position; this.choices = choices;
        }
    }

    private static String t(String en, String ru) {
        return "ru".equals(LocaleController.getInstance().getCurrentLocale().getLanguage()) ? ru : en;
    }
    private void row(String key, String en, String ru, int kind, int fallback, int min, int max, String... choices) {
        entries.add(new Row(key, t(en, ru), kind, fallback, min, max, addRow("native_style_" + key), choices));
    }
    private void bool(String key, String en, String ru, boolean value) { row(key, en, ru, BOOL, value ? 1 : 0, 0, 1); }
    private void integer(String key, String en, String ru, int value, int min, int max) { row(key, en, ru, INT, value, min, max); }

    @Override protected void updateRows() {
        super.updateRows();
        entries.clear();
        bool("chips", "Enable folder chips", "Включить чипсы папок", false);
        row("chip_preview", "Preview", "Предпросмотр", PREVIEW, 0, 0, 0);
        row("chip_style", "Chip style", "Стиль чипсов", CHOICE, 0, 0, 2, t("Filled", "Заливка"), t("Outlined", "Контур"), t("Ghost", "Призрачный"));
        bool("chip_md3", "Material 3 chip colors", "Цвета чипсов Material 3", true);
        row("chip_active", "Active chip color (0 = theme)", "Цвет активного чипса (0 = тема)", COLOR, 0, 0, 0);
        row("chip_inactive", "Inactive chip color (0 = theme)", "Цвет неактивного чипса (0 = тема)", COLOR, 0, 0, 0);
        row("text_active", "Active text color (0 = theme)", "Активный текст (0 = тема)", COLOR, 0, 0, 0);
        row("text_inactive", "Inactive text color (0 = theme)", "Неактивный текст (0 = тема)", COLOR, 0, 0, 0);
        row("chip_shape", "Chip shape", "Форма чипсов", CHOICE, 5, 0, 7, t("Capsule", "Капсула"), t("Rounded", "Скруглённая"), t("Soft", "Мягкая"), t("Square", "Квадрат"), t("Dynamic", "Динамическая"), t("Expressive", "Экспрессивная"), t("Alternative", "Альтернативная"), t("Custom", "Своя"));
        integer("radius_active", "Expressive: active radius", "Expressive: активное скругление", 20, 0, 24);
        integer("radius_outer", "Expressive: outer radius", "Expressive: внешнее скругление", 20, 0, 24);
        integer("radius_inner", "Expressive: inner radius", "Expressive: внутреннее скругление", 8, 0, 24);
        integer("radius_alt_active", "Alternative: active radius", "Alternative: активное скругление", 20, 0, 24);
        integer("radius_alt_outer", "Alternative: outer radius", "Alternative: внешнее скругление", 20, 0, 24);
        integer("radius_custom_active", "Custom: active radius", "Своя форма: активное скругление", 11, 0, 24);
        integer("radius_inactive", "Custom: inactive radius", "Своя форма: неактивное скругление", 20, 0, 24);
        row("chip_animation", "Chip animation", "Анимация чипсов", CHOICE, 0, 0, 4, t("None", "Нет"), t("Jelly", "Желе"), t("Squeeze", "Сжатие"), t("Press", "Прожатие"), t("Spring", "Пружина"));
        integer("chip_speed", "Animation speed (%)", "Скорость анимации (%)", 100, 50, 200);
        row("chip_size", "Folder bar size", "Размер панели папок", CHOICE, 1, 0, 3, t("Compact", "Компактный"), t("Default", "Обычный"), t("Large", "Крупный"), t("Custom", "Свой"));
        integer("chip_height", "Custom bar height (dp)", "Своя высота панели (dp)", 48, 36, 64);
        row("chip_spacing", "Chip spacing", "Промежутки чипсов", CHOICE, 1, 0, 3, t("Tight", "Плотно"), t("Default", "Обычно"), t("Wide", "Свободно"), t("Custom", "Свои"));
        integer("chip_gap", "Custom spacing (dp)", "Свои промежутки (dp)", 6, 0, 24);
        integer("chip_bottom", "Folder bar bottom padding (dp)", "Отступ под папками (dp)", 0, 0, 24);
        integer("chip_list_gap", "Chat list offset (dp)", "Отступ списка чатов (dp)", 5, 0, 24);
        bool("chip_divider", "Divider when the list is scrolled", "Разделитель при прокрутке", true);
        row("folder_settings", "Other folder settings", "Остальные настройки папок", ACTION, 0, 0, 0);
        row("info", "Chip Folders 1.0.2 · @RnPlugins. Disabled by default; native rendering without plugins or hooks.", "Папки-чипсы 1.0.2 · @RnPlugins. По умолчанию выключены; встроенная отрисовка без плагинов и хуков.", INFO, 0, 0, 0);
    }

    @Override protected String getActionBarTitle() { return LocaleController.getString(R.string.RegramChipFolders); }
    @Override protected String getKey() { return "regram_chip_folders"; }
    @Override public int getSearchGuid() { return 23050; }
    @Override public int getSearchIcon() { return R.drawable.msg_theme; }
    @Override public String getSearchPrefix() { return "RegramChipFolders"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new Adapter(context); }
    private Row at(int position) { for (Row row : entries) if (row.position == position) return row; return null; }
    private int value(Row row) { return PluginStyles.number(row.key, row.fallback, row.min, row.max); }
    private int color(Row row) { return PluginStyles.number(row.key, 0, Integer.MIN_VALUE, Integer.MAX_VALUE); }

    private void refresh() {
        PluginStyles.invalidate();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false);
        if (parentLayout != null) parentLayout.rebuildAllFragmentViews(false, false);
        updateRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }
    private void save(Row row, int value) {
        PluginStyles.preferences().edit().putInt(row.key, value).apply();
        refresh();
    }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        Row row = at(position);
        if (row == null || getParentActivity() == null) return;
        if (row.kind == BOOL) {
            PluginStyles.preferences().edit().putBoolean(row.key, !PluginStyles.flag(row.key, row.fallback != 0)).apply();
            refresh();
        } else if (row.kind == CHOICE) {
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(row.title)
                    .setItems(row.choices, (dialog, which) -> save(row, which)).create());
        } else if (row.kind == COLOR) {
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(row.title)
                    .setItems(new String[]{t("Color picker", "Палитра"), "HEX", t("Reset to theme", "Цвет темы")},
                            (dialog, which) -> { if (which == 0) pickColor(row); else if (which == 1) input(row); else save(row, 0); }).create());
        } else if (row.kind == INT) input(row);
        else if (row.kind == ACTION) presentFragment(new OpenExteraAppearanceActivity());
    }
    private void pickColor(Row row) {
        org.telegram.ui.Components.Paint.ColorPickerBottomSheet picker = new org.telegram.ui.Components.Paint.ColorPickerBottomSheet(getParentActivity(), getResourceProvider());
        picker.setPipetteDelegate(new org.telegram.ui.Components.Paint.ColorPickerBottomSheet.PipetteDelegate() {
            public void onStartColorPipette() {} public void onStopColorPipette() {}
            public ViewGroup getContainerView() { return null; } public View getSnapshotDrawingView() { return null; }
            public void onDrawImageOverCanvas(android.graphics.Bitmap bitmap, android.graphics.Canvas canvas) {}
            public boolean isPipetteVisible() { return false; } public boolean isPipetteAvailable() { return false; }
            public void onColorSelected(int color) {}
        });
        int color = color(row);
        picker.setColor(color == 0 ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, getResourceProvider()) : color);
        picker.setColorListener(value -> save(row, value));
        showDialog(picker);
    }
    private void input(Row row) {
        EditText input = new EditText(getParentActivity());
        input.setSingleLine(true);
        input.setText(row.kind == COLOR ? String.format(Locale.US, "#%08X", color(row)) : "" + value(row));
        if (row.kind == INT) input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(row.title + (row.kind == INT ? " (" + row.min + "–" + row.max + ")" : "")).setView(input)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                    try {
                        String text = input.getText().toString().trim();
                        if (row.kind == INT) save(row, PluginStyleRules.clamp(Integer.parseInt(text), row.min, row.max));
                        else if (text.equals("0") || text.equals("#00000000")) save(row, 0);
                        else if (text.matches("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) save(row, Color.parseColor(text.startsWith("#") ? text : "#" + text));
                        else throw new IllegalArgumentException();
                    } catch (Exception error) {
                        Toast.makeText(getParentActivity(), t("Invalid value", "Неверное значение"), Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }
    private final class Adapter extends BaseListAdapter {
        Adapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) {
            Row row = at(position);
            return row.kind == BOOL ? TYPE_CHECK : row.kind == INFO ? TYPE_INFO_PRIVACY : row.kind == PREVIEW ? 100 : TYPE_TEXT;
        }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 100) return new RecyclerListView.Holder(new FoldersPreviewCell(parent.getContext(), getResourceProvider()));
            return super.onCreateViewHolder(parent, type);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            Row row = at(position);
            if (row.kind == INFO) { ((TextInfoPrivacyCell) holder.itemView).setText(row.title); return; }
            if (row.kind == PREVIEW) { ((FoldersPreviewCell) holder.itemView).refreshNativeStyle(); return; }
            if (row.kind == BOOL) { ((TextCheckCell) holder.itemView).setTextAndCheck(row.title, PluginStyles.flag(row.key, row.fallback != 0), true); return; }
            String value = row.kind == CHOICE ? row.choices[value(row)] : row.kind == INT ? "" + value(row)
                    : row.kind == COLOR ? String.format(Locale.US, "#%08X", color(row)) : "";
            ((TextCell) holder.itemView).setTextAndValue(row.title, value, true);
        }
    }
}
