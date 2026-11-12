# re:gram 13.1.1.1 — шрифты, две камеры, ползунок зума

## Готовый релиз

- APK: `/home/atb/Загрузки/regram-13.1.1.1-arm64-v8a.apk`
- Копия: `artifacts/regram-v13.1.1.1(13010101)-release-arm64-v8a.apk`
- Пакет: `app.regram.android`.
- `versionName=13.1.1.1`, `versionCode=13010101` — выше прежнего `13010100`.
- Telegram base остаётся `APP_VERSION_NAME=13.1.1`, `APP_VERSION_CODE=7237`.
- Архитектура: **arm64-v8a**, release/non-debuggable.
- Размер: **80 767 654 байта**.
- SHA-256: `e5b5644e342e1b4c22d11b13c1091a33f1c7c220591187a683b136bc7dda4d5d`.
- Подпись: прежний release-ключ, SHA-256 сертификата:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.
- Рядом с обеими копиями лежат `.sha256`. Прежние APK не перезаписаны.

Устанавливать **поверх** существующего приложения, без удаления приложения или
данных. Сохранение данных опирается на тот же package/signing identity; сама
установка на устройстве не выполнялась. Публикация или загрузка APK на внешние
сервисы не выполнялась.

## Состав

- [Шрифты](APPLICATION_FONTS.md), по пользовательскому образцу:
  - Оформление → Шрифты; также Функционал re:gram → Шрифты;
  - выбор импортированного шрифта интерфейса или «По умолчанию»;
  - перезапуск для согласованного применения UI font/cache;
  - TTF/OTF/TTC через SAF, до 20 МиБ/файл и 32 импортов;
  - список системных семей медиаредактора, preview, drag/reorder, глаз, удаление,
    сброс порядка; скрытие не меняет шрифт сохранённых entities;
  - код/моноширинные и декоративные шрифты, медиатекст, web/system UI не подменяются.
- [Две камеры](DUAL_CAMERA.md): opt-in одновременного открытия front/back для
  кружков Camera2/CameraX, общий прежний ключ `rounddual_available`, default off,
  предупреждение о батарее/нагреве и проверки совместимости.
- [Ползунок зума для кружков](ROUND_VIDEO_ZOOM_SLIDER.md): явная настройка,
  синхронизированная с прежней `ChatsConfig.zoomSlider`; поддержка Camera1/2/X,
  выключенный/невидимый контрол не принимает касания, жесты остаются доступны.
- Сохранены [Bluetooth HD](BLUETOOTH_HD_CALLS.md), [history page delivery guard](HISTORY_PAGE_DELIVERY.md),
  Vaporeon sticker-size preview и краткая строка версии. Шапка показывает только
  **13.1.1.1**, без base/legacy/commit suffix.

## Сборка и проверка

Исходники: `/home/atb/Загрузки/re151gram/Regram-main`.
Отдельное сборочное дерево: `/home/atb/projects/regram`.

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --console=plain --no-build-cache \
  -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Финальный запуск: **BUILD SUCCESSFUL in 3m 43s**, 289 tasks,
16 executed, 273 up-to-date. Пройдены Java compile, R8, resource shrinking,
packaging/signing; native-задачи не исключались, неизменённые результаты повторно
использованы по Gradle up-to-date checks.

Полный suite после завершения сборки, JDK 21:
**507 passed, 31 skipped, 27 subtests passed** за 88.96 s.
Во время предварительного параллельного запуска был единичный сбой существующего
Termux transport test на проверке закрытия socket после stop. В полном итоговом
повторном запуске он прошёл; Termux production code и этот тест не изменялись.
Ранний compile-check выявил старый RecyclerView API (нет getBindingAdapterPosition),
исправлено использованием совместимого getAdapterPosition до финальной сборки.

Проверено на конечном APK:

- `tools/verify_release_apk.py --abi arm64-v8a`: identity/non-debuggable/ABI,
  Chaquopy assets, post-R8 FFmpeg DEX/JNI contract, TgNet native snapshot, подпись;
- `aapt dump badging`: точные package/versionName/versionCode;
- `apksigner verify --verbose --print-certs`: действительная подпись и прежний сертификат;
- ZIP CRC всех entries;
- DEX markers: font library/import prefix, dual-camera и zoom settings,
  Bluetooth HD и Vaporeon pack;
- bundled media editor font assets;
- SHA-256 **52 production/version/resource файлов** совпадает между исходным
  деревом и финальным сборочным деревом;
- SHA-256 обеих копий APK совпадает с исходным build output.

Логи и машинные отчёты: `reports/release-13.1.1.1/`:
`release-final.log`, `tests-final.log`, `verification.log`, `signature.log`,
`badging.log`, `apk-content.json`, `source-consistency.json`, `release.json`.
`verify-final.py` воспроизводит проверку и безопасное копирование артефакта.
Предварительные логи также сохранены.

## Что ещё требует устройства

ADB-устройств нет. Не проверены реальная отрисовка импортированного шрифта и
покрытие специализированных custom-drawn экранов, Android native font decoding
на разных API, метрики/кириллица/emoji, фактическая concurrent camera поддержка,
скорость/непрерывность переключения, touch dispatch ползунка, расход батареи и
Bluetooth routing. Исполняемые host-тесты используют Android doubles, не подменяют
аппаратные проверки. Причина исходных пропусков групповой истории по-прежнему
не подтверждена; guard не является доказанным исправлением этого случая.
