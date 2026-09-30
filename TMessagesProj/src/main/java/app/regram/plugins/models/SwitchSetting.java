package app.regram.plugins.models;

import com.chaquo.python.PyObject;

public class SwitchSetting extends SettingItem {

    private String key;
    private String text;
    private boolean defaultValue;
    private String subtext;
    private PyObject onChangeCallback;

    public SwitchSetting(String key, String text, boolean defaultValue, String subtext,
                         String icon, PyObject onChangeCallback, PyObject onLongClickCallback,
                         String linkAlias) {
        super("switch", icon, onLongClickCallback, linkAlias);
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

    public boolean getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(boolean defaultValue) {
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
