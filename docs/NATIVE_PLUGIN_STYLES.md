# Папки-чипсы — сохранённая часть встроенного оформления

## Текущий статус

Группа **«MD3extra, компактная панель и чипсы папок»** удалена по запросу
пользователя. Её Weex/MD3extra-палитра, seed-пресеты, импорт TTF/OTF и font
invalidation, MD3 preset, чипсы действий профиля, Expressive navigation/FAB и
Compact Bottom Nav удалены из исходников вместе с местами подключения.
`WeexColors`, `WeexFont`, `NativeStylePresets`, `NativeFabStyle`,
`NativePluginStylesActivity`, JSON mapping и palette-import tool отсутствуют.
Старые значения этих флагов в preferences больше не влияют на приложение.
Настройки аккаунтов, плагины и другие возможности клиента не удаляются.
Штатные Material 3/iOS/Glass и обычные настройки шрифтов клиента сохраняются.
Ранее применённые общие настройки тем/радиусов не сбрасываются вслепую.

## Где включить папки-чипсы

**Настройки → Настройки re:gram → Функционал re:gram → Встроенное оформление → Папки-чипсы**

Также доступен переход по долгому нажатию основной папки.
`ChipFoldersActivity` содержит только параметры Chip Folders.
По умолчанию выключены. Существующие `regram_native_styles` store и ключи
чипсов сохранены — миграция не теряет включение, цвета или геометрию.
`PluginStyles` теперь хранит только конфигурацию чипсов;
`PluginStyleRules` — только их geometry/animation math.

## Сохранённые возможности

- Filled / outlined / ghost; Material 3 цвета текущей темы или четыре custom
  ARGB цвета, native color picker, HEX и reset-to-theme.
- Capsule / rounded / soft / square / dynamic / expressive / alternative / custom.
  Радиусы активных/неактивных чипсов и соседних углов, RTL.
- None / jelly / squeeze / press / spring; скорость 50–200%.
- Три размера или custom 36–64 dp, три spacing или custom 0–24 dp,
  отступы под панелью и перед списком, divider при прокрутке.
- Реальный `FoldersPreviewCell`, прежние unread counters, folder IDs,
  drag/reorder и lock/premium policy.
- Path/ripple/геометрия переиспользуются, wave для X/Y считается однократно;
  дополнительных idle animation tickers не добавлено.
- Настройки быстрой темы для папок продолжают действовать.

Источник дизайна: **ChipFolders.plugin 1.0.2**, @RnPlugins.
SHA-256 исходного предоставленного файла:
`2e4852ba0af378bdc32601f99e8f2c11d47ae8f3e4fc2987d38b003fd36e50d1`.
Плагин не запускается; native renderer не загружает его DEX и не ставит хуки.
Это не заявление о pixel-identical копии или проверке на Android.

## Проверки и резервная копия

- `tests/test_native_plugin_styles.py`: реальные Java config/math/MainTabsHelper
  на host API doubles, совместимость старых chip keys, игнорирование удалённых
  флагов, snapshot cache, clamping, shapes/animations и отсутствие удалённых классов.
- `tests/test_native_style_performance.py`: настоящий renderer, 10 000 idle draws,
  ограниченная перестройка Path и reuse ripple; read-only APK audit.
- `tests/test_bottom_dock_visuals.py`: штатная geometry M3/Glass/iOS без удалённой
  отдельной кнопки поиска; сохранение bottom-folder clip/background/touch guards.
- `tests/test_regram_features_hub.py`: новая вложенность и отсутствие старого пункта.

Резервная копия исходников перед удалением:
`../Regram-before-style-removal.tar.gz`; логи: `../reports/style-removal/`.
Проверки после удаления:
- общий suite с JDK 21 в PATH: **432 passed, 31 skipped, 27 subtests passed**
  за 69 секунд (`tests-final.log`);
- debug/release Java/Kotlin и release resource linking:
  **BUILD SUCCESSFUL in 56s** (`build-final.log`), 282 actionable tasks;
- отдельная повторная проверка chip renderer после очистки test scaffolding:
  **3 passed**; удалённые font/palette/Expressive тесты заменены тестами
  отсутствия реализации и игнорирования прежних флагов;
- `changed-files.json` содержит список изменения production source/tool файлов:
  18 изменённых, 9 удалённых, 3 новых. Ссылок на удалённые классы в production
  исходниках, ProGuard или инструментах больше нет.

При самом удалении группы APK не собирался и версия не менялась. По следующему
запросу собран [подписанный release APK 1.4.5 / 1040500](BUILD_RELEASE_1.4.5.md):
R8/упаковка/подпись/JNI проверены, удалённые классы отсутствуют в DEX.
Device validation не выполнялась.
Проверить на устройстве: старые включённые стили больше не применяются;
настройки чипсов сохранились; top/bottom folders, preview, RTL, unread, reorder,
пикер, navigation/insets и остальные разделы hub работают как прежде.
