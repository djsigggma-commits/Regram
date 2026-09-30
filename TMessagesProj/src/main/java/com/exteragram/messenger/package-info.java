/**
 * Шим публичного Java-API exteraGram.
 *
 * <p>Опубликованные плагины — это обычные Python-модули, и часть из них
 * обращается к классам приложения напрямую: {@code from
 * com.exteragram.messenger.plugins import PluginsController}. В этом форке те
 * же классы живут в {@code app.regram.*}, поэтому такой импорт падает на
 * загрузке плагина, а не деградирует какую-то одну фичу.
 *
 * <p>По экспорту публичного каталога (361 плагин) так делают 40 штук, и
 * поверхность у них узкая: {@code PluginsController.getInstance().plugins.get(id)}
 * с последующим {@code PluginSettingsActivity(plugin)} — то есть идиома
 * «открой мой собственный экран настроек» — плюс горстка статики. Классы здесь
 * ничего не реализуют сами: это тонкие делегаты в реальные
 * {@code app.regram.*}, повторяющие имена, сигнатуры и типы exteraGram.
 *
 * <p>Важно: эти классы недостижимы из Java-кода приложения — на них ссылается
 * только Python через рефлексию. Правила {@code -keep} для них лежат в
 * proguard-rules.pro; без них релизная сборка их вырежет.
 *
 * @see app.regram.plugins.PluginsController
 */
package com.exteragram.messenger;
