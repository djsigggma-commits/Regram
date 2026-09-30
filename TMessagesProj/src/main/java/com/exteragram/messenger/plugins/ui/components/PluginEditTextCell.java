package com.exteragram.messenger.plugins.ui.components;

import android.content.Context;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;

/**
 * То же основание, что и у {@link Plugin}: пакет
 * {@code com.exteragram.messenger.plugins.ui.components} — настоящий Java-пакет
 * (в нём уже лежит {@code InstallPluginBottomSheet}), поэтому форма
 * «from com.exteragram... import PluginEditTextCell» идёт через механизм импорта
 * Chaquopy, а не через подстановку имён в class_aliases. Значит класс обязан
 * существовать по этому имени, а не только по нашему.
 */
public class PluginEditTextCell extends app.regram.plugins.ui.components.PluginEditTextCell {

    public PluginEditTextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    public static class Factory extends app.regram.plugins.ui.components.PluginEditTextCell.Factory {
        static {
            setup(new Factory());
        }

        @Override
        public app.regram.plugins.ui.components.PluginEditTextCell createView(
                Context context, org.telegram.ui.Components.RecyclerListView listView,
                int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PluginEditTextCell(context, resourcesProvider);
        }

        /**
         * Плагин приносит плагин по имени эталона: наш {@code Plugin} наследуется
         * от него, поэтому базового типа достаточно. Настройки же приходят уже
         * наши — пакет {@code plugins.models} настоящим классом не занят, и там
         * работает подстановка имён.
         */
        public static UItem of(com.exteragram.messenger.plugins.Plugin plugin,
                               app.regram.plugins.models.EditTextSetting setting) {
            UItem item = UItem.ofFactory(Factory.class);
            item.object = plugin;
            item.object2 = setting;
            return item;
        }
    }
}
