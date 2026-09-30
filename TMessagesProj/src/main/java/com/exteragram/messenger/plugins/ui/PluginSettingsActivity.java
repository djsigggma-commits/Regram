package com.exteragram.messenger.plugins.ui;

/**
 * Шим {@code com.exteragram.messenger.plugins.ui.PluginSettingsActivity} —
 * второй по востребованности класс каталога (21 плагин).
 *
 * Идиома у всех одна: взять свой Plugin из реестра и открыть его экран
 * настроек, {@code presentFragment(PluginSettingsActivity(java_plugin))}.
 * Поэтому конструктор принимает и объект плагина, и его id.
 */
public class PluginSettingsActivity extends app.regram.plugins.ui.PluginSettingsActivity {

    public PluginSettingsActivity(app.regram.plugins.Plugin plugin) {
        super(plugin != null ? plugin.id : null);
    }

    public PluginSettingsActivity(String pluginId) {
        super(pluginId);
    }
}
