package app.regram.plugins.models;

import com.chaquo.python.PyObject;

public class SelectorSetting extends SettingItem {

    private String key;
    private String text;
    private int defaultValue;
    private String[] items;
    private PyObject onChangeCallback;

    public SelectorSetting(String key, String text, int defaultValue, String[] items,
                           String icon, PyObject onChangeCallback, PyObject onLongClickCallback,
                           String linkAlias) {
        super("selector", icon, onLongClickCallback, linkAlias);
        this.key = key;
        this.text = text;
        this.defaultValue = defaultValue;
        this.items = items;
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

    public int getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String[] getItems() {
        return items;
    }

    public void setItems(String[] items) {
        this.items = items;
    }

    public PyObject getOnChangeCallback() {
        return onChangeCallback;
    }

    public void setOnChangeCallback(PyObject onChangeCallback) {
        this.onChangeCallback = onChangeCallback;
    }
}
