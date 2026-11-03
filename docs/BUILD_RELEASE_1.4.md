# re:gram 1.4 — release APK arm64-v8a

## Версия и изменения

- Пакет: `app.regram.android`.
- `versionName=1.4`, `versionCode=1040000`; Telegram-основа остаётся 13.0.2.
- Расширена «Быстрая настройка темы»: отдельная прозрачность фона папок и навигации, цвета этих панелей, отдельная прозрачность и цвета входящих/исходящих сообщений, скругления сообщений/папок/навигации, интенсивность размытия поддерживаемых панелей Android 12+.
- Добавлены готовые пресеты «Стекло», «Мягкий», «Яркий» и три именованных слота сохранения/загрузки/удаления собственных настроек. Сброс не удаляет сохранённые слоты.
- Подпись совпадает с APK 1.3.5, SHA-256 сертификата:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.

## Проверки

- Полный набор тестов с JDK 21: **433 passed, 31 skipped, 27 subtests passed**.
- Обновлены ожидания версии и host doubles рендера папок для новых настроек.
- Release compilation/R8/packaging: **BUILD SUCCESSFUL in 4m 40s**.
- `tools/verify_release_apk.py --abi arm64-v8a`: успешно; package, release flag, ABI, Python engine, FFmpeg DEX JNI contract, TgNet native snapshot и подпись.
- `aapt dump badging`: подтверждены `1.4 / 1040000`, minSdk 24, targetSdk 36, ABI arm64-v8a.
- Существующие resource/compiler/Gradle warnings и предупреждение R8 о `javax.script.ScriptEngineFactory` остаются.

Команда сборки:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --console=plain --no-build-cache \
  -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Сборочная копия: `/home/atb/projects/regram`. Отправка mapping в Crashlytics исключена; подпись и native-сборка не отключались.

Логи: `/tmp/regram-release-1.4.log`, `/tmp/regram-release-1.4-tests-final.log`, `/tmp/regram-release-1.4-verify.log`.

## APK

- `/home/atb/Загрузки/regram-1.4-arm64-v8a.apk`
- `artifacts/regram-v1.4(1040000)-release-arm64-v8a.apk`
- Размер: **79 975 132 байта**.
- SHA-256: `035b3b60670e6efd330286c40a3f07616e91e0305f988eab14787b4804bd7fd3`.
- Обе копии побайтово совпадают с результатом сборки; `.sha256` сохранены рядом.

**На устройстве не проверен.** APK — подписанный релизный кандидат; публикация не выполнялась. Для обновления устанавливать поверх прежнего re:gram без удаления приложения/очистки данных, затем пройти [проверки на устройстве](INTEGRATION.md#проверка-на-устройстве-после-успешной-сборки).
