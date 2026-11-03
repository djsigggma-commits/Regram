# Ссылки настроек re:gram

Канонический префикс категорий re:gram изменён с `exteraless` на **`regramoff`**.
Например, ссылка на строку плагинов главного экрана:

`https://t.me/regramoff/settings?r=plugins`

`SettingsHelper.linkPathFor()` используется всеми существующими copy/share
точками BaseNekoSettingsActivity, BaseNekoXSettingsActivity и native Navigation.
Параметры `r`/`v`, row IDs и внутренние ключи категорий (`exteraless_*`) не меняются.
Это URL branding, а не миграция пользовательских preferences.

Обработка входящих ссылок принимает и новый `regramoff`, и старый `exteraless`
для прежнего ограниченного набора известных экранов. Неизвестные экраны,
похожие username и просто `t.me/regramoff` не перехватываются как настройки.
Совместимость `nasettings` и его существующий fallback для прочих настроек
сохранены; произвольные ссылки Telegram не переписываются.

Регрессии: `tests/test_settings_link_branding.py` исполняет реальные pure
path-methods SettingsHelper в отдельном Java harness, проверяя генерацию путей,
старые aliases, plugins example и отклонение неизвестных путей. Отдельно проверены
точки копирования и входящий router. Это не тест Android открытия URL.

Проверки: **420 passed, 31 skipped, 27 subtests passed**;
debug/release Java/Kotlin и release resources: **BUILD SUCCESSFUL in 1m**.

Новый APK пока не собирался; изменение находится в исходниках вместе с новым
общим разделом «Функционал re:gram». Последний APK 1.3.2 старый префикс ещё содержит.
Логи: `/tmp/regram-settings-link-tests.log`, `/tmp/regram-settings-link-compile.log`.
