# Тесты каталога плагинов

Статические проверки поверх корпуса плагинов exteraGram. Джавы и устройства не требуют,
гоняются обычным CPython.

```
python3 -m pytest tests/ -q
```

Полный проход — около пяти минут, почти всё время уходит на разбор `mapping.txt` R8
и на AST восьмисот плагинов.

## Bounded-work / native appearance performance

`test_native_style_performance.py` выполняет настоящий Java NativeChipFolders
с host API doubles: 10 000 idle draws и reuse Path/Ripple. Проверяет read-only
APK size audit. `test_native_plugin_styles.py` проверяет 100 000 обращений к
snapshot настроек чипсов и equivalence общей wave для X/Y. Тесты удалённых
Weex-палитры/шрифтов/Expressive заменены контрактами отсутствия этой реализации.
Это не Android frame-time/memory/battery benchmarks.
[Отчёт](../docs/PERFORMANCE_CURRENT.md).

## Общий раздел «Функционал re:gram»

`test_regram_features_hub.py` проверяет единственный вход из настроек re:gram
(без дублирования в Appearance), сохранённые
переходы и путь «Встроенное оформление → Папки-чипсы», отсутствие старой группы
MD3extra/Compact и изменения backend preferences,
RU/EN ресурсы и регистрацию в поиске. Это source/navigation contract tests,
не Android UI tests. См. [описание](../docs/REGRAM_FEATURES_HUB.md).

## Нижний док папок / штатная навигация

`test_bottom_dock_visuals.py` компилирует настоящий `MainTabsUiHelper` на host API
модели: общий reserve/radius/blur/FAB для штатных Glass/M3/iOS и отсутствие
удалённой отдельной кнопки поиска/масштабирования. Source guards проверяют clip/собственную
подложку/отклонение новых taps по скрытым строкам. Это не screenshot/device tests.
[Сборка и ограничения](../docs/BUILD_RELEASE_1.3.1.md).

## Основа 13.0.2

`test_base_1302.py`: namespace-aware трёхстороннее слияние на fixture, версия
основы отдельно от identity re:gram, API layer 231, pinned submodules, native
Prisma/Wallet wiring, сохранение S Pen и Wallet методов/lifecycle, wallet hook
policy и sealing reflection runtime. `test_beta14_update.py` сохраняет legacy
регрессии, но теперь проверяет актуальные 13.0.2 version/pins.
`test_reflection_gate.py` проверяет idempotent install и sealing нового runtime.
Host/source проверки не подтверждают работу звонков/кошелька на устройстве.

## Папки-чипсы (Chip Folders)

`test_native_plugin_styles.py` исполняет реальные Java math/config/MainTabsHelper
на host API-модели: default-off, совместимость старых chip preferences,
игнорирование удалённых style-флагов, штатные маршруты, границы настроек и
radii/animations. Source guards проверяют отсутствие удалённых классов и
сохранение renderer/preview/всех настроек чипсов. Нужен JDK в PATH;
это не pixel/device тесты. [Отчёт](../docs/NATIVE_PLUGIN_STYLES.md).

## AllLeft 2.0.11

`test_allleft_2011.py` исполняет настоящие Java policy/renderer с Android-заглушками:
read receipts и account isolation, negative secret IDs, настройки/private-only,
pinned progress, геометрию и восстановление drawable при исключении. Source
contracts проверяют layout-only enter animation и отсутствие временной мутации
`cell.timeWidth`. Нужен JDK в PATH; это не device-тест отрисовки/анимации.
Отчёт: [NATIVE_ALLLEFT.md](../docs/NATIVE_ALLLEFT.md).

## Нативный S Pen

`test_native_spen.py` проверяет реальные Java состояния кнопки, hover и двойного
касания, нативные маршруты и защитные условия. `test_native_spen_input.py` проверяет
реальный Java router на минимальной модели Android API: отдельные окна,
блокировку/фон/фокус, клонирование и recycling событий, отмену после закрытия окна.
`test_native_spen_selection_cleanup.py` исполняет реальные методы clear/finish
на host-модели lifecycle и проверяет отмену text autoscroll/magnifier.
Нужен JDK в PATH. Это не эмуляция Android input dispatcher и не проверка Samsung.
Подробности: [NATIVE_SPEN.md](../docs/NATIVE_SPEN.md).

## Нативный Doc Viewer

`test_native_doc_viewer.py` проверяет реальные Java parser/helper классы на host JVM:
Office, EPUB/FB2, RTF/EML, текст/таблицы/Jupyter, ZIP/TAR, лимиты, CRC, XML/XXE,
Markdown/CSP и размеры bitmap. Нужны JDK в PATH и кэшированные зависимости Gradle.
Это не device-тест PdfRenderer, WebView или жестов.
Подробности: [NATIVE_DOC_VIEWER.md](../docs/NATIVE_DOC_VIEWER.md).

## Нативный Termux Bridge

`test_termux_bridge.py` и `test_termux_ui.py` проверяют встроенный сервер, RPC,
одноразовое подключение, CLI/подтверждение ИИ, Python executor и настоящий Java UI
на host-модели Android view. Для Java-проверок нужны JDK в PATH и кэшированный
`org.json:json` в Gradle cache; остальные проверки работают на CPython.
Это не тесты на устройстве. Подробности: [NATIVE_TERMUX_BRIDGE.md](../docs/NATIVE_TERMUX_BRIDGE.md).

## Корпус

Корпус в репозиторий не входит (77 МБ). По умолчанию тесты ищут его в
`/home/coral/openExtera/plugin-corpus`, путь переопределяется переменными окружения:

| переменная | что содержит |
|---|---|
| `EXTERALESS_PLUGIN_CORPUS` | каталог с `*.plugin` — по одному Python-файлу на плагин |
| `EXTERALESS_PLUGIN_ARCHIVES` | каталог с `*.eaf` — ZIP-архивы многофайловых плагинов |

Если каталога нет, тесты скипаются, а не падают.

Собрать корпус заново: выгрузки каталогов плагинов из Telegram (папки `ChatExport_*`)
плюс `git clone https://github.com/KangelPlugins/Plugins-Store`, дедупликация по sha256
содержимого. ZIP-архивы (`.eaf`) кладутся отдельно от исходников — это не Python.

## Базовые списки

`tests/baselines/*.json` — известные дыры на момент записи. Тест падает только на том,
чего в списке нет, иначе на 104 непортированных Java-классах он был бы красным всегда.
Когда дыру закрывают, запись из списка убирают: тест показывает такие остатки в тексте
ошибки, но из-за них не падает.

## R8

Проверки шринкинга читают `TMessagesProj/build/outputs/mapping/release/`. Если этих
артефактов нет или они старше `proguard-rules.pro`, проверки скипаются с указанием
правила, которого не было в том билде, — зелёный результат на протухшем маппинге ничего
не значит.
