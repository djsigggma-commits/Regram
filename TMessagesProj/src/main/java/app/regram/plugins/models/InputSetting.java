package app.regram.plugins.models;

import com.chaquo.python.PyObject;

public class InputSetting extends SettingItem {

    private String key;
    private String text;
    private String defaultValue;
    private String subtext;
    private PyObject onChangeCallback;

    public InputSetting(String key, String text, String defaultValue, String subtext,
                        String icon, PyObject onChangeCallback, PyObject onLongClickCallback,
                        String linkAlias) {
        super("input", icon, onLongClickCallback, linkAlias);
        this.key = key;
        this.text = text;
        this.defaultValue = defaultValue;
        this.subtext = subtext;
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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getSubtext() {
        return subtext;
    }

    public void setSubtext(String subtext) {
        this.subtext = subtext;
    }

    public PyObject getOnChangeCallback() {
        return onChangeCallback;
    }

    public void setOnChangeCallback(PyObject onChangeCallback) {
        this.onChangeCallback = onChangeCallback;
    }
}
