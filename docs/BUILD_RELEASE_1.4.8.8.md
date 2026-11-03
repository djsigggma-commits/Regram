# re:gram 1.4.8.8 — release APK arm64-v8a

## Версия и состав

- Пакет: `app.regram.android`.
- `versionName=1.4.8.8`, `versionCode=1040808` (выше прежнего `1040500`).
- Основа: **exteraless 13.1.1 / 7237**, API layer 232,
  commit `4771c553fee84618f8fa8dfc034c9e85317c2c72`
  из [Actions 41526234112](https://github.com/exteraless/exteraless/actions/runs/41526234112).
  [Отчёт об обновлении](BASE_41526234112.md).
- Включены [оптимизации без удаления функций](PERFORMANCE_13.1.1.md):
  очередь плеера, метки времени, gradient/lifecycle текста песен и equalizer,
  освобождение копий обложек профиля и отдельная очередь анализа,
  parser упоминаний, nullable MonoColor overrides и конфигурация теней карточек.
- AllLeft, MonoColor, MentionAvatars, папки-чипсы, S Pen, Doc Viewer,
  Termux Bridge, плагины, настройки, темы и локализации сохранены.
- Last.fm и 3D-карта Wallet удалены **upstream при обновлении основы**, не в ходе
  текущей оптимизации. Music card и новая 2D-карта Wallet остаются.

## Сборка и проверки

`tools/check_build_env.py --release`: успешно, compile-check режим не используется.

Предварительная проверка оптимизаций: debug/release Java/Kotlin, release resources
и R8 — **BUILD SUCCESSFUL in 4m 50s**, 338 tasks. Эта проверка предшествовала
последней правке lifecycle копий bitmap в карточке профиля; итоговая release-сборка
ниже должна включать её и версию 1.4.8.8.

Команда итоговой сборки:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --console=plain --no-build-cache \
  -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Отправка mapping в Crashlytics исключена; native-сборка, runtime Crashlytics
и release-подпись не отключались. Используется существующий release-keystore,
новый ключ не создаётся. Сборочная копия: `/home/atb/projects/regram`.

### Итоговые результаты

- Полный suite с JDK 21: **463 passed, 31 skipped, 27 subtests passed**
  за 80.34 секунды. Skipped corpus/device-зависимые проверки не выдаются за пройденные.
- Итоговый `assembleRelease`: **BUILD SUCCESSFUL in 4m 15s**, 289 tasks
  (35 executed, 254 up-to-date). Последняя правка profile palette включена.
- `tools/verify_release_apk.py --abi arm64-v8a`: успешно — package,
  отсутствие debug flag, ABI, Python engine, FFmpeg DEX/JNI signatures,
  TgNet native snapshot и APK signature.
- `aapt dump badging`: `1.4.8.8 / 1040808`, `app.regram.android`, arm64-v8a.
- `apksigner verify --verbose --print-certs`: успешно; сертификат **совпадает
  с APK 1.4.5**. SHA-256 сертификата:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.
- Все 30 непосредственно упакованных `.so` проверены как ELF64/AArch64.
  Asset `chaquopy.so` побайтово совпадает с собранным runtime и содержит freeze
  bridge markers. Это проверка состава, не runtime/security аудит Wallet.
- Обе сохранённые копии APK совпадают с результатом сборки; SHA-256 проверен.
- Сохранились upstream-предупреждения ресурсов/Java/Gradle и известное R8
  предупреждение `META-INF/services/javax.script.ScriptEngineFactory`.

Логи: `../reports/release-1.4.8.8/`: `environment.log`, `build.log`,
`verification.log`, `badging.log`, `signature.log`, `previous-signature.log`,
`artifact.json`, `apk-size.json`, `tests.log`, `source-consistency.json`.
Проверено совпадение 11 исходных/сборочных файлов с использованной build-копией.
Изменения исходников: `../reports/performance-1311/source-changes.json`.

## APK

- `/home/atb/Загрузки/regram-1.4.8.8-arm64-v8a.apk`
- `artifacts/regram-v1.4.8.8(1040808)-release-arm64-v8a.apk`
- Размер: **80 696 805 байт** (около 80.7 MB / 77.0 MiB).
- SHA-256: `349d015ca431d2e04ac94d7c51a818f8a2be552f68cb0e7a029ab353ad4fdd67`.
- Файлы `.sha256` сохранены рядом с обеими копиями.

Прежний APK 1.4.5 имел размер 80 084 927 байт и другую основу (13.0.2).
Общий размер вырос на 611 878 байт вместе с обновлением основы; это не
изолированное сравнение текущих оптимизаций. Ресурсы/функции ради размера не вырезались.

## Ограничения

**На устройстве не проверен.** APK — подписанный релизный кандидат, не
подтверждённый устройствами публичный релиз. Публикация не выполняется.
Не обещается конкретное повышение FPS или устранение всех лагов без измерений.

Проверить установку поверх прежнего re:gram без удаления приложения/данных,
сохранение настроек, запуск и аккаунты, сообщения/rich quotes/AllLeft, push,
плагины и их установку, QR, аудио/круглые видео, звонки, bot privacy,
темы/плеер/текст песен/очередь и возврат из background. Wallet тестировать
без реальных активов. Полный список: [INTEGRATION.md](INTEGRATION.md#проверка-на-устройстве-после-успешной-сборки).
