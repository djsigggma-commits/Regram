# re:gram 1.4.5 — release APK arm64-v8a

## Версия и изменения

- Пакет: `app.regram.android`.
- `versionName=1.4.5`, `versionCode=1040500`; minSdk 24, targetSdk 36.
- Основа exteraless 13.0.2 / 7175, API layer 231, точный commit
  `8b7ec40ecada6ab3cfcc24e9f3d8ae262c02de63` из Actions 39628177709.
  [Отчёт об обновлении](BASE_39628177709.md).
- Удалена группа «MD3extra, компактная панель и чипсы папок» с реализацией
  Weex-палитры/импорта шрифтов/MD3 preset/profile chips/Expressive/FAB/Compact.
- Сохранены все настройки папок-чипсов, прежние ключи preferences и preview;
  новый путь: **Функционал re:gram → Встроенное оформление → Папки-чипсы**.
  Другие разделы и штатное оформление клиента сохранены.
- Подпись совпадает с предыдущим APK 1.4.1, SHA-256 сертификата:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.

## Проверки

- `tools/check_build_env.py --release`: успешно, без compile-check режима.
- Полный suite с JDK 21: **432 passed, 31 skipped, 27 subtests passed** за 69 секунд.
- Release compilation / native / R8 / упаковка / подпись:
  **BUILD SUCCESSFUL in 3m 55s**, 289 actionable tasks.
- `tools/verify_release_apk.py --abi arm64-v8a`: успешно — package,
  release flag, ABI, Python engine, FFmpeg DEX JNI contract, TgNet native
  snapshot и APK signature.
- `aapt dump badging`: `1.4.5 / 1040500`, `app.regram.android`, arm64-v8a.
- `apksigner verify --verbose --print-certs`: APK Signature Scheme v2,
  сертификат совпадает с прежней сборкой.
- В итоговом DEX есть `ChipFoldersActivity`; отсутствуют
  `NativePluginStylesActivity`, `WeexColors`, `WeexFont`, `NativeFabStyle`,
  `NativeStylePresets`.
- Все packaged `.so` — ELF64/AArch64. Asset `chaquopy.so` побайтово совпадает
  с новым собранным runtime, содержит freeze bridge markers.
  Это проверка состава, не runtime/security аудит Wallet.
- Обе сохранённые копии APK побайтово совпадают с результатом сборки.
- Остались предупреждения ресурсов/Java/Gradle и известное R8 предупреждение
  о `META-INF/services/javax.script.ScriptEngineFactory`.

Команда сборки из исходного каталога:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --console=plain --no-build-cache \
  -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Сборочная копия: `/home/atb/projects/regram`.
Отправка mapping в Crashlytics исключена; native-сборка и release-подпись
не отключались. Логи и manifest: `../reports/release-1.4.5/`:
`environment.log`, `tests.log`, `build.log`, `verification.log`, `badging.log`,
`signature.log`, `previous-signature.log`, `artifact.json`.

## APK

- `/home/atb/Загрузки/regram-1.4.5-arm64-v8a.apk`
- `artifacts/regram-v1.4.5(1040500)-release-arm64-v8a.apk`
- Размер: **80 084 927 байт** (около 80,1 MB / 76,4 MiB).
- SHA-256: `9857129c2b17e94c674df7f18996c33c812c3b5ba1b54e8bc4121168ca403cde`.
- Файлы `.sha256` сохранены рядом с обеими копиями.

**На устройстве не проверен.** Это подписанный релизный кандидат;
публикация не выполнялась. Для сохранения данных устанавливать поверх прежнего
re:gram с той же подписью, без удаления приложения и очистки данных. Проверить
обновление, сохранение настроек чипсов, отсутствие старых эффектов,
[остальные сценарии на устройстве](INTEGRATION.md#проверка-на-устройстве-после-успешной-сборки).
