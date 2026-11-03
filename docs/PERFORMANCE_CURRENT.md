# Аудит debloat и bounded-work после базы 13.0.2

**Актуальный проход на основе 13.1.1:** [PERFORMANCE_13.1.1.md](PERFORMANCE_13.1.1.md).

> Исторический отчёт: после него по запросу пользователя удалены
> MD3extra/Weex, Expressive и Compact Bottom Nav. Описанные ниже кэши их
> маршрутов/палитры больше не входят в исходники. Сохранены chip renderer
> bounded-work и read-only APK audit. [Текущий статус](NATIVE_PLUGIN_STYLES.md).

Это целевой безопасный проход по текущим исходникам, **не утверждение, что весь
клиент максимально оптимизирован**. Без профиля устройства нельзя доказать
улучшение FPS, времени старта или батареи. Функции, аккаунты, сообщения, темы,
emoji, настройки, общий plugin engine и пользовательские плагины не удалялись.

Резервная копия: `../Regram-before-performance-current.tar.gz`, без generated
build/cache/artifacts/.git. Она может содержать локальные credentials и keystore;
**не публиковать** этот архив.

## Убрана повторная работа

### Навигация

`PluginStyles` хранит одну immutable запись конфигурации разделов, привязанную к
сырому значению настройки. Нормализация, split, проверка обязательных Chats/Settings
и построение display order делаются только при изменении этого значения.
`position()` и `sectionCount()` повторно не разбирают строки. Переходы ViewPager,
его счётчик страниц и проверки положения используют один и тот же кэш.

Массив порядка наружу возвращается копией, так что UI/caller не может испортить
кэш. Размер кэша — **одна запись**, без накопления вариантов. Старая логика
ArrayList/boxing/contains на каждом вызове displayOrder убрана.

### Палитра MD3extra

- Декларативный JSON mapping читается/проверяется один раз за процесс при первом
  включении, а не заново при каждой теме и для каждой light/dark стороны.
- В кэше только известные theme key/role/alpha; пользовательские файлы/контексты/
  View не удерживаются. Отключённая функция не запускает загрузку.
- Light и dark делят одну матрицу из 505 тонов; изменения seed/source и штатная
  theme invalidation очищают зависящую от настроек матрицу.
- Если Android Monet доступен, локальные 505 seed-тонов **не вычисляются вообще**.
  Раньше они рассчитывались и сразу заменялись системной палитрой.
- Invalidate сериализован с build: завершившийся старый build не публикует
  устаревшую палитру после возврата invalidate.
- Лимит JSON 32 KiB, исключение player mappings, неизвестные ключи и fallback на
  seed сохранены. Повреждённый immutable mapping даёт пустой override без частичной
  палитры; повторный цикл чтения/логирования на каждую смену темы не запускается.

### Рендер папок

- Path/радиусы чипса перестраиваются только при изменении фактической геометрии:
  размеров, density, формы, соседей, progress или пользовательских радиусов.
  Цвета по-прежнему получаются из актуального Theme/ResourcesProvider.
- Existing Path/RippleDrawable/маска переиспользуются; idle redraw не вызывает
  `Path.addRoundRect` заново. Fade/shape transitions и RTL не заморожены.
- Индекс вкладки берётся из adapter position за O(1); при drag/reorder несовпадении
  сохранён безопасный fallback `indexOf`, а не устаревшее обращение к соседям.
- Для scale X/Y используется одна wave/sin оценка вместо двух одинаковых.
  Старые публичные scale методы сохранены и дают идентичный результат.
- Custom color читается из snapshot один раз вместо двух map lookup.

Не добавлены animation tickers, фоновые worker/polling задачи или network calls.

## Аудит объёма APK

Новый read-only инструмент:

```sh
python3 tools/audit_apk_size.py path/to/release.apk --output /tmp/apk-size.json
```

Он считает stored/expanded размеры по категориям, крупнейшие entries и exact
SHA-256 duplicates, читая данные chunks по 64 KiB. Ничего не извлекает и не удаляет.

Baseline — **предыдущий APK 1.3.2**, 79 959 912 байт; он не содержит последних
перестановок настроек/переименования/оптимизаций. Отчёт:
`/tmp/regram-apk-size-before.json`.

| Категория | Байты содержимого в ZIP (compressed/stored) |
|---|---:|
| DEX (7 entries) | 23 712 375 |
| arm64 native (18 entries) | 18 152 046 |
| res | 8 600 369 |
| metadata/other (включая resources.arsc) | 5 968 749 |
| Python requirements-common.imy | 5 088 536 |
| Python stdlib-common.imy | 4 819 241 |

Это размеры contents, не сумма APK с ZIP/signing/alignment overhead.
Найдено 209 групп exact duplicates (499 entries); дубли сверх первой копии
занимают 390 449 **expanded** байт. Это не обещание такого же уменьшения APK.
Разные имена ресурсов/ABI/runtime lookup могут требовать одинакового содержимого.
Бездоказательно удалять их нельзя.

`grammars.dat` не мусор: его загружает новая Prisma-подсветка. Python runtime,
JNI/VoIP, bundled emoji/fonts/themes и reflection API плагинов также не являются
ненужными только из-за размера. Широкие keep rules нельзя вслепую ослаблять:
Python, JNI и Xposed используют имена, которые статический shrinker не видит.
Release code/resource shrinking уже включены. Не отключались проверки защиты,
кошелёк, Crashlytics runtime или другие возможности ради цифры размера.

## Проверки

- Настоящие Java PluginStyles/MainTabsHelper/rules: 100 000 повторных запросов
  возвращают тот же normalized String, без повторного parsing; изменение settings
  инвалидирует route value; наружный массив не может испортить display order.
- Настоящий NativeChipFolders на host Android doubles: 10 000 idle draw → **1**
  Path build / **1** RippleDrawable; изменение размера/radius/density перестраивает
  путь, переустановка ripple не пересоздаёт объект.
- Настоящий WeexColors с настоящим JSON parser и mapping asset: 10 000 обращений,
  light/dark и invalidate; mapping читается один раз, system palette делится между
  light/dark, seed conversion пропускается при system Monet.
- Детерминированный concurrent test блокирует palette build и проверяет
  сериализацию invalidate, отсутствие deadlock и rebuild после invalidate.
- APK audit fixture подтверждает отсутствие записи/удаления и поиск duplicates.

Host doubles проверяют счётчики работы и правила, **не Android rasterization,
реальный HSL/Monet framework, frame timing или энергию**.

Итоговый full suite: **428 passed, 31 skipped, 27 subtests passed** (62.36s).
Debug/release resources/Java/Kotlin и R8: **BUILD SUCCESSFUL in 5m 33s**.
Сохранились прежние resource/compiler/Gradle warnings и предупреждение R8 о
`META-INF/services/javax.script.ScriptEngineFactory`.

В одном промежуточном параллельном прогоне существующий Termux transport test
получил localhost SocketException (connection reset). Его отдельный повтор:
**10 passed**; итоговый полный повтор после завершения R8 прошёл выше.
Termux transport в этом проходе не менялся; причина разового сбоя не установлена.

Логи: `/tmp/regram-style-performance-tests.log`,
`/tmp/regram-style-performance-concurrent.log`, `/tmp/regram-performance-current-tests.log`,
`/tmp/regram-performance-current-tests-final.log` (промежуточный сбой),
`/tmp/regram-performance-termux-recheck.log`,
`/tmp/regram-performance-current-tests-recheck.log` (итоговый полный прогон),
`/tmp/regram-performance-current-compile.log`.

**Новый APK в этом проходе не собран.** Сравнения итогового размера APK/производительности
с baseline пока нет. Для следующего шага нужны Perfetto CPU/frame trace, cold/warm
startup, memory/GC и background battery measurements с фактическими включёнными
режимами и плагинами. На устройстве проверить смену темы/seed/system mode, RTL,
reorder папок, динамические цвета, быстрые переключения настройки и аккаунтов.
