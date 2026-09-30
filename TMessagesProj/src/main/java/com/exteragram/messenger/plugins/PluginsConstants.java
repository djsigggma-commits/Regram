package com.exteragram.messenger.plugins;

/**
 * Шим строковых констант exteraGram. Значения — те же, что в
 * {@code com.exteragram.messenger.plugins.PluginsConstants} 12.9.0, а не наши
 * внутренние: плагин сравнивает их с тем, что приходит из SDK.
 */
public final class PluginsConstants {

    public static final String PYTHON = app.regram.plugins.PluginsConstants.PYTHON;
    public static final String SEND_MESSAGE_HOOK = app.regram.plugins.PluginsConstants.SEND_MESSAGE_HOOK;
    public static final String STRATEGY = app.regram.plugins.PluginsConstants.STRATEGY;
    public static final String PARAMS = app.regram.plugins.PluginsConstants.PARAMS;
    public static final String UPDATE = app.regram.plugins.PluginsConstants.UPDATE;
    public static final String UPDATES = app.regram.plugins.PluginsConstants.UPDATES;
    public static final String REQUEST = app.regram.plugins.PluginsConstants.REQUEST;
    public static final String RESPONSE = app.regram.plugins.PluginsConstants.RESPONSE;
    public static final String ERROR = app.regram.plugins.PluginsConstants.ERROR;
    public static final String PLUGINS = app.regram.plugins.PluginsConstants.PLUGINS;
    public static final String PLUGINS_EXT = app.regram.plugins.PluginsConstants.PLUGINS_EXT;
    public static final String PLUGINS_SDK = app.regram.plugins.PluginsConstants.PLUGINS_SDK;
    public static final String CREATE_SETTINGS = app.regram.plugins.PluginsConstants.CREATE_SETTINGS;
    public static final String APP_START = app.regram.plugins.PluginsConstants.APP_START;
    public static final String APP_STOP = app.regram.plugins.PluginsConstants.APP_STOP;
    public static final String APP_PAUSE = app.regram.plugins.PluginsConstants.APP_PAUSE;
    public static final String APP_RESUME = app.regram.plugins.PluginsConstants.APP_RESUME;
    public static final String ON_APP_EVENT = app.regram.plugins.PluginsConstants.ON_APP_EVENT;
    public static final String ON_PLUGIN_LOAD = app.regram.plugins.PluginsConstants.ON_PLUGIN_LOAD;
    public static final String ON_PLUGIN_UNLOAD = app.regram.plugins.PluginsConstants.ON_PLUGIN_UNLOAD;

    private PluginsConstants() {
    }

    public static final class Settings {
        public static final String TYPE = app.regram.plugins.PluginsConstants.Settings.TYPE;
        public static final String KEY = app.regram.plugins.PluginsConstants.Settings.KEY;
        public static final String TEXT = app.regram.plugins.PluginsConstants.Settings.TEXT;
        public static final String SUBTEXT = app.regram.plugins.PluginsConstants.Settings.SUBTEXT;
        public static final String ICON = app.regram.plugins.PluginsConstants.Settings.ICON;
        public static final String ACCENT = app.regram.plugins.PluginsConstants.Settings.ACCENT;
        public static final String RED = app.regram.plugins.PluginsConstants.Settings.RED;
        public static final String ON_CLICK = app.regram.plugins.PluginsConstants.Settings.ON_CLICK;
        public static final String DEFAULT = app.regram.plugins.PluginsConstants.Settings.DEFAULT;
        public static final String ITEMS = app.regram.plugins.PluginsConstants.Settings.ITEMS;
        public static final String HINT = app.regram.plugins.PluginsConstants.Settings.HINT;
        public static final String MULTILINE = app.regram.plugins.PluginsConstants.Settings.MULTILINE;
        public static final String MAX_LENGTH = app.regram.plugins.PluginsConstants.Settings.MAX_LENGTH;
        public static final String MASK = app.regram.plugins.PluginsConstants.Settings.MASK;
        public static final String ON_CHANGE = app.regram.plugins.PluginsConstants.Settings.ON_CHANGE;
        public static final String TYPE_SWITCH = app.regram.plugins.PluginsConstants.Settings.TYPE_SWITCH;
        public static final String TYPE_INPUT = app.regram.plugins.PluginsConstants.Settings.TYPE_INPUT;
        public static final String TYPE_SELECTOR = app.regram.plugins.PluginsConstants.Settings.TYPE_SELECTOR;
        public static final String TYPE_HEADER = app.regram.plugins.PluginsConstants.Settings.TYPE_HEADER;
        public static final String TYPE_DIVIDER = app.regram.plugins.PluginsConstants.Settings.TYPE_DIVIDER;
        public static final String TYPE_TEXT = app.regram.plugins.PluginsConstants.Settings.TYPE_TEXT;
        public static final String TYPE_EDIT_TEXT = app.regram.plugins.PluginsConstants.Settings.TYPE_EDIT_TEXT;
        public static final String TYPE_CUSTOM = app.regram.plugins.PluginsConstants.Settings.TYPE_CUSTOM;
        public static final String VIEW = app.regram.plugins.PluginsConstants.Settings.VIEW;
        public static final String ITEM = app.regram.plugins.PluginsConstants.Settings.ITEM;
        public static final String FACTORY = app.regram.plugins.PluginsConstants.Settings.FACTORY;
        public static final String FACTORY_ARGS = app.regram.plugins.PluginsConstants.Settings.FACTORY_ARGS;
        public static final String CREATE_SUB_FRAGMENT = app.regram.plugins.PluginsConstants.Settings.CREATE_SUB_FRAGMENT;
        public static final String ON_LONG_CLICK = app.regram.plugins.PluginsConstants.Settings.ON_LONG_CLICK;
        public static final String LINK_ALIAS = app.regram.plugins.PluginsConstants.Settings.LINK_ALIAS;

        private Settings() {
        }
    }

    public static final class Strategy {
        public static final String MODIFY = app.regram.plugins.PluginsConstants.Strategy.MODIFY;
        public static final String CANCEL = app.regram.plugins.PluginsConstants.Strategy.CANCEL;
        public static final String DEFAULT = app.regram.plugins.PluginsConstants.Strategy.DEFAULT;
        public static final String MODIFY_FINAL = app.regram.plugins.PluginsConstants.Strategy.MODIFY_FINAL;

        private Strategy() {
        }
    }

    public static final class Xposed {
        public static final String REPLACE_HOOKED_METHOD = app.regram.plugins.PluginsConstants.Xposed.REPLACE_HOOKED_METHOD;
        public static final String BEFORE_HOOKED_METHOD = app.regram.plugins.PluginsConstants.Xposed.BEFORE_HOOKED_METHOD;
        public static final String AFTER_HOOKED_METHOD = app.regram.plugins.PluginsConstants.Xposed.AFTER_HOOKED_METHOD;
        public static final String HOOK_FILTERS = app.regram.plugins.PluginsConstants.Xposed.HOOK_FILTERS;

        private Xposed() {
        }
    }

    public static final class DevServer {
        public static final String MODULE = app.regram.plugins.PluginsConstants.DevServer.MODULE;
        public static final String CLASS = app.regram.plugins.PluginsConstants.DevServer.CLASS;
        public static final String START_SERVER = app.regram.plugins.PluginsConstants.DevServer.START_SERVER;
        public static final String STOP_SERVER = app.regram.plugins.PluginsConstants.DevServer.STOP_SERVER;

        private DevServer() {
        }
    }

    public static final class MenuItemTypes {
        public static final String MESSAGE_CONTEXT_MENU = app.regram.plugins.PluginsConstants.MenuItemTypes.MESSAGE_CONTEXT_MENU;
        public static final String DRAWER_MENU = app.regram.plugins.PluginsConstants.MenuItemTypes.DRAWER_MENU;
        public static final String MAIN_MENU = app.regram.plugins.PluginsConstants.MenuItemTypes.MAIN_MENU;
        public static final String CHAT_ACTION_MENU = app.regram.plugins.PluginsConstants.MenuItemTypes.CHAT_ACTION_MENU;
        public static final String PROFILE_ACTION_MENU = app.regram.plugins.PluginsConstants.MenuItemTypes.PROFILE_ACTION_MENU;

        private MenuItemTypes() {
        }
    }

    public static final class MenuItemProperties {
        public static final String MENU_TYPE = app.regram.plugins.PluginsConstants.MenuItemProperties.MENU_TYPE;
        public static final String ITEM_ID = app.regram.plugins.PluginsConstants.MenuItemProperties.ITEM_ID;
        public static final String TEXT = app.regram.plugins.PluginsConstants.MenuItemProperties.TEXT;
        public static final String SUBTEXT = app.regram.plugins.PluginsConstants.MenuItemProperties.SUBTEXT;
        public static final String ICON = app.regram.plugins.PluginsConstants.MenuItemProperties.ICON;
        public static final String ON_CLICK = app.regram.plugins.PluginsConstants.MenuItemProperties.ON_CLICK;
        public static final String CONDITION = app.regram.plugins.PluginsConstants.MenuItemProperties.CONDITION;
        public static final String PRIORITY = app.regram.plugins.PluginsConstants.MenuItemProperties.PRIORITY;

        private MenuItemProperties() {
        }
    }

    public static final class HookFilterTypes {
        public static final String RESULT_IS_NULL = app.regram.plugins.PluginsConstants.HookFilterTypes.RESULT_IS_NULL;
        public static final String RESULT_IS_TRUE = app.regram.plugins.PluginsConstants.HookFilterTypes.RESULT_IS_TRUE;
        public static final String RESULT_IS_FALSE = app.regram.plugins.PluginsConstants.HookFilterTypes.RESULT_IS_FALSE;
        public static final String RESULT_NOT_NULL = app.regram.plugins.PluginsConstants.HookFilterTypes.RESULT_NOT_NULL;
        public static final String RESULT_IS_INSTANCE_OF = app.regram.plugins.PluginsConstants.HookFilterTypes.RESULT_IS_INSTANCE_OF;
        public static final String RESULT_EQUAL = app.regram.plugins.PluginsConstants.HookFilterTypes.RESULT_EQUAL;
        public static final String RESULT_NOT_EQUAL = app.regram.plugins.PluginsConstants.HookFilterTypes.RESULT_NOT_EQUAL;
        public static final String ARGUMENT_IS_NULL = app.regram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_IS_NULL;
        public static final String ARGUMENT_IS_TRUE = app.regram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_IS_TRUE;
        public static final String ARGUMENT_IS_FALSE = app.regram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_IS_FALSE;
        public static final String ARGUMENT_NOT_NULL = app.regram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_NOT_NULL;
        public static final String ARGUMENT_IS_INSTANCE_OF = app.regram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_IS_INSTANCE_OF;
        public static final String ARGUMENT_EQUAL = app.regram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_EQUAL;
        public static final String ARGUMENT_NOT_EQUAL = app.regram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_NOT_EQUAL;
        public static final String CONDITION = app.regram.plugins.PluginsConstants.HookFilterTypes.CONDITION;
        public static final String OR = app.regram.plugins.PluginsConstants.HookFilterTypes.OR;

        private HookFilterTypes() {
        }
    }
}
