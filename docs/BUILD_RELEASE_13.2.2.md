# re:gram 13.2.2 — подписанный release APK

## Файл

- APK: `artifacts/regram-v13.2.2(13020200)-release-arm64-v8a.apk`.
- Полный путь: `/home/lime/projects/Regram/artifacts/regram-v13.2.2(13020200)-release-arm64-v8a.apk`.
- Размер: **86 732 148 байт** (82.7 MiB).
- Пакет: **app.regram.android**.
- `versionName=13.2.2`, `versionCode=13020200`; предыдущий выпуск — `13010101`.
- ABI: **arm64-v8a**. minSdk **24**, targetSdk **36**, compileSdk **37**.
- Release, `debuggable=false`, R8 включён.

SHA-256 APK:

```text
e9427ec51b40ee38a08240f323c16ca4617b578024e0b1b28cfebd0500c5e27f
```

Рядом сохранены `.apk.sha256` и `artifacts/regram-13.2.2-apk-audit.json`.
Это локальный релизный кандидат: APK не публиковался в GitHub Releases и не проверялся на устройстве.

## Основа и сборка

Основа — [exteraless 13.2.2 / Actions 43392419710](BASE_43392419710.md),
commit `31a35e4683fb420ee782cdd32f36aea812bf756c`, Telegram layer **233**.
Сохранены изменения re:gram, включая встроенный WebSocket-транспорт.

- Gradle **9.7.1**, JDK **21**, NDK **27.2.12479018**.
- Python **3.11**, Chaquopy runtime и plugin SDK собраны для arm64-v8a.
- Все 18 верхнеуровневых подмодулей восстановлены по закреплённым SHA.
- Дополнительно восстановлен вложенный QuickJS `ca3594dae5fd0dd6d667be74a322e8e935a2d217`
  из gitlink `widget-sandbox`. Этот шаг добавлен в `tools/restore_submodules.sh`.
- Native-кэш от прежней основы инвалидирован. Пересобраны основная JNI-библиотека,
  tgnet, звонки, Prisma и новая `libtwidget.so`. Неизменённые prebuilt-архивы
  wallet/tde2e и прочие штатные бинарные зависимости использованы из дерева проекта.
- Подготовительный release Java/Kotlin/resources/R8: **BUILD SUCCESSFUL in 4m 53s**,
  233 tasks.
- Итоговый `:TMessagesProj:assembleRelease`: **BUILD SUCCESSFUL in 7m 13s**,
  291 tasks (65 executed, 226 up-to-date). Этот проход включает native-сборку,
  Chaquopy runtime, окончательный R8, resource shrinking, упаковку и подпись.
- Сохранились предупреждения native/Java deprecated API, ресурсов, Gradle deprecations
  и R8 о `javax.script.ScriptEngineFactory`; сборка и последующий аудит APK успешны.
- Штатная задача `uploadCrashlyticsMappingFileRelease` также выполнена.

Сборочный каталог: `/home/lime/projects/regram`.
Логи и аудит: `/tmp/regram-release-1322/` (временные рабочие файлы).

## Ключи и подпись

По запросу владельца использованы release keystore, Telegram API credentials
и Firebase-конфигурация из `/home/lime/projects/suursiii.zip`.
Compile-check placeholders **не использовались**.

Сертификат SHA-256:

```text
d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba
```

Сертификат совпадает с `regram-v13.1.1.1(13010101)-release-arm64-v8a.apk`
из того же архива. APK подписан схемой **v2**, подходящей для minSdk 24;
`apksigner verify` успешно. Совпадение подписи и больший versionCode позволяют
обновление предыдущего APK; runtime-совместимость требует проверки на устройстве.

Пароли/значения API-ключей в документацию и Git не добавлялись.
Временные копии keystore и Firebase-файла после сборки удалены из дерева исходников.
Закрытые рабочие материалы остаются в защищённом каталоге
`/home/lime/.local/share/regram-signing-1322` (каталог 700, секретные файлы 600).

## Проверки самого APK

- `tools/verify_release_apk.py --abi arm64-v8a`: успешно.
- Package/version/versionCode/ABI проверены через APK и `aapt`.
- `debuggable=false`.
- Подпись проверена `apksigner`, сертификат сравнен с предыдущим APK.
- `zipalign -c -P 16 4`: успешно.
- ELF LOAD segments `libtmessages.49.so` и `libtwidget.so`: выравнивание не менее **16 KiB**.
- Все `.so` в APK — только **arm64-v8a**.
- `libtmessages.49.so`: **22 859 704 байта**, `libtwidget.so`: **5 465 048 байт**.
- Python engine, `libpython3.11.so` и `assets/WidgetSandbox/manifest.txt` присутствуют.
- Post-R8 DEX: проверены декларации FFmpeg JNI; native registration table TgNet
  совпадает с текущими исходниками.
- Проверены наличие WebSocket transport, warm pool и widget service в DEX,
  а также использование Telegram API hash из архива (без вывода значения).
- Пять regression/provenance guards 13.2.2 после изменения восстановления QuickJS: **5 passed**.
- Полный host suite перед release-сборкой: **508 passed, 52 skipped, 27 subtests passed**.

## Что ещё проверить

**На Android APK не запускался.** Перед публичным выпуском нужны запуск/обновление
с сохранением аккаунта, миграция БД, плагины, виджеты и изолированный процесс, PiP,
звонки/Bluetooth, камера, круглые видео, плеер/lyrics, карты, AllLeft/компактный режим,
темы, S Pen, папки и WebSocket на реальной сети.
