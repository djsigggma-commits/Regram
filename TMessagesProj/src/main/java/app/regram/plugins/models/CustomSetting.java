package app.regram.plugins.models;

import android.view.View;

import com.chaquo.python.PyObject;

import app.regram.plugins.Plugin;

import org.telegram.ui.Components.UItem;

/**
 * Произвольная строка настроек: либо готовый {@link UItem}, либо вью, либо фабрика.
 *
 * Фабрику плагины наследуют из Python через `@java_subclass(CustomSetting.Factory)`,
 * поэтому она обязана оставаться абстрактным джавовым классом поверх
 * {@link UItem.UItemFactory}.
 */
public final class CustomSetting extends SettingItem {

    private PyObject onClickCallback;
    private PyObject createSubFragmentCallback;
    private PyObject factoryArgs;
    private Factory<?> factory;
    private UItem item;

    public static abstract class Factory<V extends View> extends UItem.UItemFactory<V> {

        private boolean isShadowValue;
        private boolean isClickableValue = true;

        public UItem create(Plugin plugin, CustomSetting setting, PyObject args) {
            return null;
        }

        public void onClick(Plugin plugin, UItem item, View view) {
        }

        public void onLongClick(Plugin plugin, UItem item, View view) {
        }

        @Override
        public boolean isShadow() {
            return isShadowValue;
        }

        @Override
        public boolean isClickable() {
            return isClickableValue;
        }

        public final boolean isShadowValue() {
            return isShadowValue;
        }

        public final void setShadowValue(boolean value) {
            this.isShadowValue = value;
        }

        public final boolean isClickableValue() {
            return isClickableValue;
        }

        public final void setClickableValue(boolean value) {
            this.isClickableValue = value;
        }
    }

    private CustomSetting(PyObject onClickCallback, PyObject createSubFragmentCallback,
                          PyObject onLongClickCallback, String linkAlias) {
        super("custom", null, onLongClickCallback, linkAlias);
        this.onClickCallback = onClickCallback;
        this.createSubFragmentCallback = createSubFragmentCallback;
    }

    public CustomSetting(UItem item, PyObject onClickCallback, PyObject createSubFragmentCallback,
                         PyObject onLongClickCallback, String linkAlias) {
        this(onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias);
        this.item = item;
        item.settingItem = this;
    }

    public CustomSetting(View view, PyObject onClickCallback, PyObject createSubFragmentCallback,
                         PyObject onLongClickCallback, String linkAlias) {
        this(UItem.asCustom(view), onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias);
    }

    public CustomSetting(Factory<?> factory, PyObject onClickCallback, PyObject createSubFragmentCallback,
                         PyObject onLongClickCallback, String linkAlias) {
        this(onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias);
        this.factory = factory;
    }

    public CustomSetting(Factory<?> factory, PyObject factoryArgs, PyObject onClickCallback,
                         PyObject createSubFragmentCallback, PyObject onLongClickCallback, String linkAlias) {
        this(factory, onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias);
        this.factoryArgs = factoryArgs;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        closeCallback(onClickCallback);
        closeCallback(createSubFragmentCallback);
        closeCallback(factoryArgs);
        onClickCallback = null;
        createSubFragmentCallback = null;
        factoryArgs = null;
    }

    public PyObject getOnClickCallback() {
        return onClickCallback;
    }

    public void setOnClickCallback(PyObject callback) {
        this.onClickCallback = callback;
    }

    public PyObject getCreateSubFragmentCallback() {
        return createSubFragmentCallback;
    }

    public void setCreateSubFragmentCallback(PyObject callback) {
        this.createSubFragmentCallback = callback;
    }

    public PyObject getFactoryArgs() {
        return factoryArgs;
    }

    public void setFactoryArgs(PyObject args) {
        this.factoryArgs = args;
    }

    public Factory<?> getFactory() {
        return factory;
    }

    public void setFactory(Factory<?> factory) {
        this.factory = factory;
    }

    public UItem getItem() {
        return item;
    }

    public void setItem(UItem item) {
        this.item = item;
    }
}
