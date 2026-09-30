package app.regram.plugins;

import com.chaquo.python.PyObject;

/**
 * Пункт меню, добавленный плагином. Аналог hooks/MenuItemRecord.java exteraGram.
 * Колбэк хранится как PyObject и зовётся напрямую с Map-контекстом.
 */
public class MenuItemRecord {

    public enum MenuType {
        MESSAGE_CONTEXT_MENU,
        DRAWER_MENU,
        MAIN_MENU,
        CHAT_ACTION_MENU,
        PROFILE_ACTION_MENU;

        public static MenuType fromString(String s) {
            try {
                return s == null ? MESSAGE_CONTEXT_MENU : valueOf(s.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return MESSAGE_CONTEXT_MENU;
            }
        }
    }

    public final String pluginId;
    public final String itemId;
    public final MenuType menuType;
    public final String text;
    public final String subtext;
    public final String icon;
    /** MVEL-выражение видимости (вычисляется при построении меню; null = всегда виден). */
    public final String condition;
    public final int priority;
    /** Python-колбэк on_click(context_map). */
    public final transient PyObject onClick;

    public MenuItemRecord(String pluginId, String itemId, MenuType menuType, String text,
                          String subtext, String icon, String condition, int priority,
                          PyObject onClick) {
        this.pluginId = pluginId;
        this.itemId = itemId;
        this.menuType = menuType;
        this.text = text;
        this.subtext = subtext;
        this.icon = icon;
        this.condition = condition;
        this.priority = priority;
        this.onClick = onClick;
    }
}
