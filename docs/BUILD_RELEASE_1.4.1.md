# re:gram 1.4.1 — release APK arm64-v8a

## Версия и изменения

- Пакет: `app.regram.android`.
- `versionName=1.4.1`, `versionCode=1040100`; Telegram-основа остаётся 13.0.2.
- В «Быструю настройку темы» добавлен переключатель «Убрать подложку за навигацией». Он скрывает общий фон за нижними папками и навигацией, сохраняя фон самих панелей, отступы прокрутки и защиту от нажатий по строкам за панелями.
- Настройка отображается в предпросмотре, сохраняется в пресетах и сбрасывается вместе с настройками темы. Старые пресеты без этого поля загружаются с исходной подложкой.
- Подпись совпадает с APK 1.4, SHA-256 сертификата:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.

## Проверки

- Финальный полный прогон с JDK 21: **433 passed, 31 skipped, 27 subtests passed**.
- В предыдущем прогоне тест Termux Bridge завершился `ConnectException` при переподключении после смены токена. Отдельный повтор и финальный полный прогон успешны; код Termux не менялся. Это не проверка Termux на устройстве.
- Обновлены ожидания версии и список самостоятельных настроек темы в интеграционном тесте.
- Release compilation/R8/packaging: **BUILD SUCCESSFUL in 4m 46s**.
- `tools/verify_release_apk.py --abi arm64-v8a`: успешно; package, release flag, ABI, Python engine, FFmpeg DEX JNI contract, TgNet native snapshot и подпись.
- `aapt dump badging`: `1.4.1 / 1040100`, minSdk 24, targetSdk 36, ABI arm64-v8a.
- Существующие resource/compiler/Gradle warnings и предупреждение R8 о `javax.script.ScriptEngineFactory` остаются.

Команда сборки:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --console=plain --no-build-cache \
  -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Сборочная копия: `/home/atb/projects/regram`. Отправка mapping в Crashlytics исключена; подпись и native-сборка не отключались.

Логи: `/tmp/regram-release-1.4.1.log`, `/tmp/regram-release-1.4.1-tests-verified.log`, `/tmp/regram-release-1.4.1-verify.log`.

## APK

- `/home/atb/Загрузки/regram-1.4.1-arm64-v8a.apk`
- `artifacts/regram-v1.4.1(1040100)-release-arm64-v8a.apk`
- Размер: **79 982 964 байта**.
- SHA-256: `873da833e9e563e8cb01bf0c356f86d54c55ac8f78569a1aa5fa9635be8afb13`.
- Обе копии побайтово совпадают с результатом сборки; `.sha256` сохранены рядом.

**На устройстве не проверен.** APK — подписанный релизный кандидат; публикация не выполнялась. Для обновления устанавливать поверх прежнего re:gram без удаления приложения/очистки данных, затем пройти [проверки на устройстве](INTEGRATION.md#проверка-на-устройстве-после-успешной-сборки).
