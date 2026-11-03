# re:gram 1.3 — release arm64-v8a на основе 13.0.2

## Версии и состав

- Android package: **app.regram.android**.
- Версия клиента: **1.3**, versionCode **1030000** (выше 1010603).
- Telegram/exteraless-основа: **13.0.2 / 7175**, API layer **231**.
- Источник основы, хеш архива, слияние и native pins: [BASE_13.0.2.md](BASE_13.0.2.md).
- Сборка включает накопленные native MentionAvatars 1.1.3, AllLeft 2.0.11,
  Termux Bridge, Doc Viewer, S Pen и WeexMD3/Compact Bottom Nav/Chip Folders.
  **Плеер Weex не перенесён.** Новые opt-in режимы не включаются принудительно.
- ExitFy отсутствует; общий plugin engine, стандартные прокси и существующие
  настройки сохранены. Приложение не удалялось и данные на устройстве не очищались.

## Сборка и проверки

Проверка release prerequisites прошла без вывода credentials. Использован прежний
release-keystore. Команда:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --no-build-cache -Dorg.gradle.jvmargs=-Xmx10g \
  :TMessagesProj:assembleRelease -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

**BUILD SUCCESSFUL in 4m**. До упаковки обновлённая native-база и Chaquopy
пересобраны из актуального снимка с invalidation CMake cache по fingerprint.
`--no-build-cache` не означает принудительную пересборку неизменённых задач:
валидные up-to-date результаты этого же снимка использованы повторно.
Crashlytics runtime сохранён; пропущена только отправка mapping.

- Полный host suite: **412 passed, 31 skipped, 27 subtests passed**.
- `verify_release_apk.py --abi arm64-v8a` прошёл: package, release/debuggable,
  ABI, наличие Python engine, FFmpeg JNI DEX signatures, TgNet JNI snapshot,
  криптографическая проверка подписи.
- AAPT manifest: **1.3 / 1030000**, minSdk **24**, targetSdk **36**.
- Сертификат SHA-256:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.
  Совпадает с ранее выпущенным 1.1.6.3; apksigner подтвердил v2 signature.
- В упакованной `libtmessages.49.so` ровно 43 актуальных имени TgNet JNI
  registrations. SHA-256 совпадает с проверенной stripped копией новой сборки:
  `e741d0539a5da4666d3b28c658ead364a619512d8219444339f0f3e44410ec2d`.
- По распакованным записям APK проверено отсутствие ExitFy (имена, ASCII,
  UTF-16LE). Наличие native-style palette и Termux assets подтверждено.
- Сохранились прежние compiler/resource/Gradle warnings, предупреждение R8
  `META-INF/services/javax.script.ScriptEngineFactory`; они не остановили сборку.

Логи:
- `/tmp/regram-release-1.3.log`;
- `/tmp/regram-release-1.3-tests.log`;
- `/tmp/regram-release-1.3-verify.log`;
- `/tmp/regram-release-1.3-badging.txt`;
- `/tmp/regram-release-1.3-cert.txt`.

## Готовый APK

- Для установки: **`/home/atb/Загрузки/regram-1.3-arm64-v8a.apk`**.
- В проекте: `artifacts/regram-v1.3(1030000)-release-arm64-v8a.apk`.
- Размер: **79 959 744 байта**.
- SHA-256:
  **`90c804c404a67ee309359efaf11abd13b5cd15b1e9f424361994fd7d95e536a1`**.
- Обе копии побайтно проверены по SHA-256; рядом сохранены `.sha256`.

Устанавливать **поверх текущего re:gram**, без удаления приложения/очистки данных.
Клиенты с другим package или сертификатом этим APK не обновляются.
APK локально собран, не опубликован и **не установлен/не проверен на устройстве**.
Host tests, native compilation и R8 не гарантируют startup, работу звонков,
камеры, криптографического кошелька или отсутствие UI-регрессий. Device checklist
остаётся в отчёте основы. Для Wallet не использовать реальные средства до
отдельной проверки; воспроизводимость поставленных upstream Rust static archives
здесь не заявляется.
