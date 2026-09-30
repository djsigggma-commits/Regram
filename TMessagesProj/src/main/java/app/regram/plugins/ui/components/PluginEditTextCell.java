package app.regram.plugins.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;

import com.chaquo.python.PyObject;

import app.regram.plugins.Plugin;
import app.regram.plugins.PluginsController;
import app.regram.plugins.models.EditTextSetting;
import app.regram.plugins.models.SettingItem;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Строка текста в настройках плагина: значение уходит в хранилище не по кнопке,
 * а через короткий debounce, чтобы не писать на каждый нажатый символ.
 */
@SuppressLint("ViewConstructor")
public class PluginEditTextCell extends EditTextCell {

    private static final int SAVE_DEBOUNCE_MS = 750;

    private EditTextSetting currentSetting;
    private Runnable pendingSaveRunnable;
    private String pluginId;
    private String valueToSave;
    private final TextWatcher saveTextWatcher;

    public PluginEditTextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, false, false, -1, resourcesProvider);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        saveTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                valueToSave = s != null ? s.toString() : "";
                scheduleSave();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        editText.addTextChangedListener(saveTextWatcher);
    }

    private void updateMultiline(boolean multiline) {
        editText.setSingleLine(!multiline);
        editText.setMaxLines(multiline ? 5 : 1);
        editText.setMinLines(multiline ? 3 : 1);
        editText.setGravity((LocaleController.isRTL ? android.view.Gravity.RIGHT : android.view.Gravity.LEFT)
                | (multiline ? android.view.Gravity.TOP : android.view.Gravity.CENTER_VERTICAL));
    }

    public void bind(String pluginId, EditTextSetting setting) {
        EditTextSetting previous = currentSetting;
        boolean sameSetting = previous != null && TextUtils.equals(previous.getKey(), setting.getKey());
        if (!sameSetting) {
            flushPendingSave();
        }
        this.pluginId = pluginId;
        this.currentSetting = setting;

        setWatchersEnabled(false);
        boolean multiline = setting.getMultiline();
        updateMultiline(multiline);
        int inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (multiline) {
            inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
        editText.setInputType(inputType);

        final int maxLength = setting.getMaxLength();
        ArrayList<InputFilter> filters = new ArrayList<>();
        InputFilter regexFilter = createInputFilter(setting.getMask());
        if (regexFilter != null) {
            filters.add(regexFilter);
        }
        if (maxLength > 0) {
            setShowLimitWhenNear(maxLength / Math.min(maxLength, 4));
            filters.add(new InputFilter.LengthFilter(maxLength));
        } else {
            setShowLimitWhenNear(-1);
        }
        editText.setFilters(filters.toArray(new InputFilter[0]));
        if (setting.getHint() != null) {
            editText.setHint(setting.getHint());
        }

        String value = PluginsController.getInstance()
                .getPluginSettingString(pluginId, setting.getKey(), setting.getDefaultValue());
        if (!TextUtils.equals(value, editText.getText().toString())) {
            if (editText.hasFocus() && sameSetting) {
                setWatchersEnabled(true);
                return;
            }
            setText(value);
        }
        valueToSave = value;
        setWatchersEnabled(true);
    }

    private void setWatchersEnabled(boolean enabled) {
        editText.removeTextChangedListener(saveTextWatcher);
        if (enabled) {
            editText.addTextChangedListener(saveTextWatcher);
        }
    }

    private void scheduleSave() {
        cancelPendingSave();
        final EditTextSetting setting = currentSetting;
        final String key = setting != null ? setting.getKey() : null;
        final String id = this.pluginId;
        final String value = valueToSave;
        if (setting == null || value == null || TextUtils.isEmpty(key) || TextUtils.isEmpty(id)) {
            return;
        }
        pendingSaveRunnable = () -> {
            pendingSaveRunnable = null;
            PluginsController.getInstance().runOnPluginsQueue(() -> {
                String finalValue = value;
                int maxLength = setting.getMaxLength();
                if (maxLength > 0 && finalValue.length() > maxLength) {
                    finalValue = finalValue.substring(0, maxLength);
                }
                PluginsController.getInstance().setPluginSetting(id, key, finalValue);
                triggerOnChange(setting.getOnChangeCallback(), key, finalValue);
            });
        };
        AndroidUtilities.runOnUIThread(pendingSaveRunnable, SAVE_DEBOUNCE_MS);
    }

    private void cancelPendingSave() {
        if (pendingSaveRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(pendingSaveRunnable);
            pendingSaveRunnable = null;
        }
    }

    /** Недописанное значение сохраняют сворачивание экрана и потеря фокуса. */
    private void flushPendingSave() {
        Runnable pending = pendingSaveRunnable;
        if (pending == null) {
            return;
        }
        cancelPendingSave();
        pending.run();
    }

    @Override
    protected void onFocusChanged(boolean focused) {
        super.onFocusChanged(focused);
        if (!focused) {
            flushPendingSave();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        flushPendingSave();
    }

    private void triggerOnChange(final PyObject callback, final String key, final Object value) {
        if (callback == null) {
            return;
        }
        PluginsController.getInstance().runOnPluginsQueue(() -> {
            try {
                callback.call(value);
            } catch (Exception e) {
                FileLog.e("Error executing on_change callback for " + pluginId + "/" + key, e);
            }
        });
    }

    private InputFilter createInputFilter(String mask) {
        if (TextUtils.isEmpty(mask)) {
            return null;
        }
        try {
            final Pattern pattern = Pattern.compile(mask);
            return (source, start, end, dest, dstart, dend) -> {
                StringBuilder builder = new StringBuilder(end - start);
                boolean unchanged = true;
                for (int i = start; i < end; i++) {
                    char ch = source.charAt(i);
                    if (pattern.matcher(String.valueOf(ch)).matches()) {
                        builder.append(ch);
                    } else {
                        unchanged = false;
                    }
                }
                return unchanged ? null : builder.toString();
            };
        } catch (PatternSyntaxException e) {
            FileLog.e("Invalid mask for EditText: " + mask, e);
            return null;
        }
    }

    /** Не final: под этим же именем живёт shim для плагинов exteraGram. */
    public static class Factory extends UItem.UItemFactory<PluginEditTextCell> {
        static {
            setup(new Factory());
        }

        @Override
        public boolean isClickable() {
            return false;
        }

        @Override
        public PluginEditTextCell createView(Context context, org.telegram.ui.Components.RecyclerListView listView,
                                            int currentAccount, int classGuid,
                                            Theme.ResourcesProvider resourcesProvider) {
            return new PluginEditTextCell(context, resourcesProvider);
        }

        @Override
        public void bindView(android.view.View view, UItem item, boolean divider,
                             UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (!(view instanceof PluginEditTextCell)) {
                return;
            }
            Plugin plugin = item.object instanceof Plugin ? (Plugin) item.object : null;
            SettingItem settingItem = item.object2 instanceof SettingItem ? (SettingItem) item.object2 : null;
            if (plugin != null && settingItem instanceof EditTextSetting) {
                PluginEditTextCell cell = (PluginEditTextCell) view;
                cell.bind(plugin.getId(), (EditTextSetting) settingItem);
                cell.setDivider(divider);
            }
        }

        public static UItem of(Plugin plugin, EditTextSetting setting) {
            UItem item = UItem.ofFactory(Factory.class);
            item.object = plugin;
            item.object2 = setting;
            return item;
        }
    }
}
