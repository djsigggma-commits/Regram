# Обновление основы re:gram до exteraless 13.0.2

## Источник и резервная копия

- Пользовательский архив: `/home/atb/Загрузки/exteraless-1302.zip`.
- SHA-256: `c72d81f0170686c9fbdf61ea6aaaa9a5025de7c13823e2f87b52091b50555449`.
- Комментарий ZIP: upstream commit `1ad6a60a6834ffca722b1a22f4fba27ecb29a061`.
- Прежняя база для трёхстороннего слияния: exteraless 12.10.6-beta14.
- До переноса сохранён `../Regram-before-1302.tar.gz`: исходники, настройки сборки,
  локальные подмодули; без generated build/cache/artifacts. Копия может содержать
  локальные credentials — не публиковать её как архив исходников.

## Выполнено

Не просто изменён номер версии: перенесён upstream delta из 341 файла.
`tools/merge_base_1302.py` подготавливает трёхстороннее слияние без изменения checkout:
сначала нормализует namespace `app.exteraless` → `app.regram` в обеих upstream
версиях, затем сливает с существующими локальными правками. Бинарные данные не
переименовываются. Итог: 307 upstream-копий/добавлений, 32 автоматических слияния
и 2 вручную разрешённых конфликта:

- `gradle.properties`: база **13.0.2 / 7175**, пакет **app.regram.android**;
  после завершения обновления по отдельному запросу собственная версия re:gram
  повышена до **1.3 / 1030000** для релизной сборки.
- `ChatActivity`: сохранены одновременно собственный S Pen routing и новый
  `findWalletSendLanding()`; методы разделены, ни один блок не потерян.

Удалены 272 прежних upstream-файла, отсутствующих в новом снимке, **только после
побайтной проверки** с нормализованной старой базой. Среди них прежний
`org.telegram.messenger.CodeHighlighting` (заменён пакетом `utils.code.highlight`)
и перенесённые в submodule файлы tgcalls. Локально изменённые файлы не удалялись.
ZIP без подмодулей не трактуется как команда удалить восстановленные gitlinks.

Перенесены:
- TL-схемы, API **layer 231**, изменения Messages/Media/Notifications/Send helpers;
- изменения чатов, списка диалогов, профиля, MainTabs, редактора и камеры;
- JNI/TgNet и download budgeting;
- новая Prisma-подсветка кода, JNI bridge и поставляемые upstream static archives;
- upstream Gram Wallet / TON Connect, Java/C++ bridge, ресурсы и локальное
  защищённое хранение; зависимость `nv-websocket-client:2.14`;
- upstream ограничения plugin hooks на Wallet/KeyStore и sealing reflection
  policy в новой Chaquopy. Это не гарантия полной изоляции произвольного доверенного
  кода (в частности, явно разрешённого Termux in-process executor).

Сохранены re:gram branding/package, существующие настройки и интеграции:
MentionAvatars, AllLeft, MonoColor, Termux Bridge, Doc Viewer, S Pen, native
Weex/Compact/Chips и остальные правки. Плеер Weex не добавляется. ExitFy не
возвращается. Пользовательские аккаунты/БД/preferences не очищаются; engine и
установленные плагины не удаляются. Фактическое обновление данных на устройстве
ещё не проверялось.

## Подмодули и native provenance

ZIP не содержит содержимого gitlinks. Их SHA получены через GitHub git-trees API
**для того же commit**, а не с произвольных HEAD. Полный manifest с путями delta,
удалениями и всеми gitlinks: [upstream/base-13.0.2.json](upstream/base-13.0.2.json).
`tools/restore_submodules.sh` обновлён. Изменённые/новые зависимости:

| Подмодуль | SHA |
|---|---|
| Chaquopy | `d47b4a1e1817b4590b9c56dfd4ee5fcfdd6f0cbd` |
| tlottie | `31f1b542f88e7b4be9a01e749920d857535fc715` |
| tgcalls | `ccbb57965020768c94f5b205ea08547a7e0fe49d` |
| wallet-engine | `5c65e47cb8d68727c20d89382d0bc51292486b9a` |
| boost-regex | `4cbcd3078e6ae10d05124379623a1bf03fcb9350` |
| libprisma | `31a5d6fbbef8136e4a2b3adaf7b95e2ccaa7436d` |

Существующая локальная Chaquopy build-правка
`-DCMAKE_POLICY_VERSION_MINIMUM=3.5` сохранена. Все остальные закреплённые
подмодули прошли проверку restore script. Старое CMake-дерево в сборочной копии
инвалидировано по source fingerprint (включая содержимое `.a`), чтобы не повторить
смешение Java/JNI из прежнего startup crash.

`prebuild/lib/*/libwallet_engine.a` и `libprisma.a` взяты из предоставленного архива.
Исходники/пины восстановлены, но воспроизводимая пересборка этих Rust/Prisma
архивов и аудит криптографии здесь **не заявляются**. Полная работоспособность
кошелька/операций с реальными средствами не подтверждена; для проверки использовать
только тестовые сценарии, не реальные активы.

## Проверки

- Debug/release Java/Kotlin и release resources: **BUILD SUCCESSFUL in 3m 22s**.
  Первый offline запуск выявил отсутствие новой websocket dependency; она разрешена
  обычным Gradle online запуском. Заглушками библиотека не заменялась.
- Обновлены host-тесты версии/pinned runtime и idempotent/sealed reflection policy.
- Новые `tests/test_base_1302.py`: namespace-aware merge на маленьком реальном
  fixture, сохранение двух сторон конфликтов, identity, layer, native wiring,
  gitlink pins, wallet policy, отсутствие старого highlighter.
- Targeted suite: **24 passed**. Итоговый полный suite:
  **412 passed, 31 skipped, 27 subtests passed**.
- `externalNativeBuildRelease` и `buildChaquopyRuntimeArm64V8a` завершили сборку
  arm64-v8a. На stripped копии новой `.so` проверено точное совпадение всех
  **43 TgNet JNI registration names** с актуальной Java/C++ основой; найдено
  27 Wallet JNI exports и 5 Prisma JNI exports. Это не проверка загрузки на Android
  и не подтверждение всех JNI-сигнатур. SHA-256 stripped native:
  `e741d0539a5da4666d3b28c658ead364a619512d8219444339f0f3e44410ec2d`.
  Native check log: `/tmp/regram-1302-native-check.txt`.
- Итоговая native/Chaquopy/R8 проверка: **BUILD SUCCESSFUL in 7m 42s**.
  Сохранились прежние compiler/resource/Gradle warnings и предупреждение R8 о
  `META-INF/services/javax.script.ScriptEngineFactory`.

Логи:
- `/tmp/regram-1302-compile.log`;
- `/tmp/regram-1302-submodules.log`;
- `/tmp/regram-1302-tests-final.log`;
- `/tmp/regram-1302-native-r8.log`.

После завершения обновления по отдельному запросу собран **re:gram 1.3** на этой
базе под прежней подписью; manifest/ABI/JNI проверены. [APK и отчёт](BUILD_RELEASE_1.3.md).
Прежний APK 1.1.6.3 содержит старую базу; не выдавать его за 13.0.2.
На устройстве новый APK не устанавливался. Предстоит device validation:
startup, вход/переключение аккаунтов, отправка/чтение/загрузка файлов, звонки,
камера/кружки, подсветка кода, папки/навигация, встроенные функции и lifecycle.
Для Wallet — также auth/passcode/background/ключи/backup/TON Connect в тестовой
среде. Компиляция, host tests и R8 не заменяют эти проверки.
