package app.regram.plugins.models;

import com.chaquo.python.PyObject;

/**
 * Базовая модель строки настроек плагина из SDK exteraGram.
 *
 * У нас строки описываются питоновскими датаклассами из `ui/settings.py`, но плагины
 * каталога наследуют джавовые модели напрямую (`@java_subclass(CustomSetting.Factory)`),
 * поэтому классы обязаны существовать с теми же именами и полями.
 */
public abstract class SettingItem {

    private String type;
    private String icon;
    private PyObject onLongClickCallback;
    private String linkAlias;

    protected SettingItem(String type, String icon, PyObject onLongClickCallback, String linkAlias) {
        this.type = type;
        this.icon = icon;
        this.onLongClickCallback = onLongClickCallback;
        this.linkAlias = linkAlias;
    }

    public void cleanup() {
        closeCallback(onLongClickCallback);
        onLongClickCallback = null;
    }

    protected final void closeCallback(PyObject callback) {
        if (callback != null) {
            try {
                callback.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Глубокая ссылка на строку настроек; null, если у строки нет псевдонима. */
    public final String getLink(String pluginId, String prefix) {
        if (linkAlias == null || linkAlias.isEmpty() || pluginId == null || pluginId.isEmpty()) {
            return null;
        }
        final String alias = prefix == null ? linkAlias : prefix + ':' + linkAlias;
        return "https://t.me/exteraSettings?p=" + pluginId + "&s=" + alias;
    }

    public final String getType() {
        return type;
    }

    public final void setType(String type) {
        this.type = type;
    }

    public final String getIcon() {
        return icon;
    }

    public final void setIcon(String icon) {
        this.icon = icon;
    }

    public final PyObject getOnLongClickCallback() {
        return onLongClickCallback;
    }

    public final void setOnLongClickCallback(PyObject callback) {
        this.onLongClickCallback = callback;
    }

    public final String getLinkAlias() {
        return linkAlias;
    }

    public final void setLinkAlias(String linkAlias) {
        this.linkAlias = linkAlias;
    }
}
