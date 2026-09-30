package app.regram.plugins.models;

import com.chaquo.python.PyObject;

public class EditTextSetting extends SettingItem {

    // Публичные поля — форма, под которую написан API плагинов: они читают и
    // пишут setting.key напрямую. Геттеры оставлены: часть плагинов ходит через них.
    public String key;
    public String hint;
    public String defaultValue;
    public boolean multiline;
    public int maxLength;
    public String mask;
    public PyObject onChangeCallback;

    public EditTextSetting(String key, String hint, String defaultValue, boolean multiline,
                           int maxLength, String mask, PyObject onChangeCallback) {
        super("edit_text", null, null, null);
        this.key = key;
        this.hint = hint;
        this.defaultValue = defaultValue;
        this.multiline = multiline;
        this.maxLength = maxLength;
        this.mask = mask;
        this.onChangeCallback = onChangeCallback;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        closeCallback(onChangeCallback);
        onChangeCallback = null;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean getMultiline() {
        return multiline;
    }

    public void setMultiline(boolean multiline) {
        this.multiline = multiline;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public PyObject getOnChangeCallback() {
        return onChangeCallback;
    }

    public void setOnChangeCallback(PyObject onChangeCallback) {
        this.onChangeCallback = onChangeCallback;
    }
}
