package com.exteragram.messenger.plugins;

import java.util.List;

/**
 * Основание модели плагина под именем exteraGram.
 *
 * Существует ради dex-модулей: они получают объект плагина рефлексией и
 * проверяют его тип через `isAssignableFrom`, поэтому наш `Plugin` обязан
 * быть наследником именно этого класса, а не просто одноимённым.
 *
 * Вызовы у них скомпилированы по типу `com.exteragram.messenger.plugins.Plugin`,
 * поэтому объявлено всё, что публично у модели эталона и есть у нас: метода,
 * объявленного только у наследника, такой вызов не находит.
 */
public abstract class Plugin {

    public abstract String getId();

    public abstract String getName();

    public abstract String getAuthor();

    public abstract String getVersion();

    public abstract String getDescription();

    public abstract String getIcon();

    public abstract String getEngine();

    public abstract String getAppVersion();

    public abstract String getSdkVersion();

    public abstract List<String> getRequirements();

    public abstract String getPack();

    public abstract int getIndex();

    public abstract boolean isEnabled();

    public abstract void setEnabled(boolean enabled);

    public abstract boolean hasError();

    public abstract boolean isNotResponding();

    public boolean getIsNotResponding() {
        return isNotResponding();
    }
}
