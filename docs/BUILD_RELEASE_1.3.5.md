# re:gram 1.3.5 — release APK arm64-v8a

## Версия и изменения

- Пакет: `app.regram.android`.
- `versionName=1.3.5`, `versionCode=1030500`; Telegram-основа остаётся 13.0.2.
- Включены текущие исходники, в том числе раздел «Функционал re:gram», изменения производительности и исправления обновлятора: совместимый выбор ABI и проверка размера загрузки. Подробности: [UPDATER_AUDIT.md](UPDATER_AUDIT.md).
- Подпись прежняя, SHA-256 сертификата:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.

## Проверки

- Тесты с JDK 21: **430 passed, 31 skipped, 27 subtests passed**. Ожидания версии в двух тестах обновлены до 1.3.5.
- Release compilation/R8/packaging: **BUILD SUCCESSFUL in 3m 50s**.
- `tools/verify_release_apk.py --abi arm64-v8a`: успешно. Проверены package, release flag, ABI, Python engine, FFmpeg DEX JNI contract, TgNet native snapshot и подпись.
- `aapt dump badging`: подтверждены `1.3.5 / 1030500`, minSdk 24, targetSdk 36.
- Существующие resource/compiler/Gradle warnings и предупреждение R8 о `javax.script.ScriptEngineFactory` остаются.

Команда сборки:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --console=plain --no-build-cache \
  -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Сборочная копия: `/home/atb/projects/regram`. Отправка mapping в Crashlytics исключена; подпись и native-сборка не отключались.

Логи: `/tmp/regram-release-1.3.5.log`, `/tmp/regram-release-1.3.5-tests.log`, `/tmp/regram-release-1.3.5-verify.log`.

## APK

- `/home/atb/Загрузки/regram-1.3.5-arm64-v8a.apk`
- `artifacts/regram-v1.3.5(1030500)-release-arm64-v8a.apk`
- Размер: **79 962 424 байта**.
- SHA-256: `640af5daf98130e50468e1d471a7c1c77cf5f38d8366c319e404e0dcb5f1378c`.
- Обе копии совпадают с результатом сборки; `.sha256` сохранены рядом.

**На устройстве не проверен.** APK является подписанным релизным кандидатом, не опубликованным проверенным релизом. Устанавливать поверх прежнего re:gram без удаления приложения и очистки данных; затем пройти [проверки на устройстве](INTEGRATION.md#проверка-на-устройстве-после-успешной-сборки). Публикация не выполнялась.
