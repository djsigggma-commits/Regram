# re:gram 13.1.1 — Bluetooth HD, краткая строка версии и Vaporeon preview

## APK

- `/home/atb/Загрузки/regram-13.1.1-arm64-v8a-bluetooth-hd.apk`
- `artifacts/regram-v13.1.1(13010100)-release-arm64-v8a-bluetooth-hd.apk`
- Пакет `app.regram.android`, `versionName=13.1.1`, `versionCode=13010100`.
- Архитектура **arm64-v8a**; размер **80 741 309 байт**.
- SHA-256: **`e2816f3d401243780af6afe1accf7cb62e173b2759abb7c1ef51665b0c296b79`**.
- Рядом с обеими копиями сохранены `.sha256`. Предыдущие APK не перезаписаны.

Подписан прежним release-ключом. SHA-256 сертификата:
`d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.
Версия и Android-код оставлены прежними: автообновлятор не обязан считать
этот APK новым. Для проверки устанавливать вручную поверх текущего приложения,
**не удаляя данные**. Фактическая установка на устройство не проверена.

## Состав

- [HD звук звонков по Bluetooth](BLUETOOTH_HD_CALLS.md), по умолчанию выключен.
  Путь: Настройки re:gram → Функционал re:gram → HD звук звонков по Bluetooth.
  Если A2DP-наушники подключены при старте службы звонка, используется media output
  и микрофон телефона. Есть ручной выбор выхода и обработка отключения устройств.
- В шапке настроек и updater label — только `13.1.1`, без основы/старого номера/commit ID.
- Предпросмотр размера стикеров загружает третий документ из
  `vaporeon_by_atb_by_fStikBot` через Telegram с кэшем; встроенный котик не используется.
  Первая загрузка требует сети, установка пака не обязательна.
- Сохранена [защита доставки страниц истории](HISTORY_PAGE_DELIVERY.md) при
  зависшей загрузке дополнительных цитат/stories. Устранение конкретного
  пользовательского случая пропусков ещё требует проверки на устройстве.

## Проверки

- Полный suite, JDK 21: **494 passed, 31 skipped, 27 subtests passed**.
- Release Java/Kotlin/resources compile-check: **BUILD SUCCESSFUL in 2m 2s**.
- Полная сборка arm64, без пропуска native-задач: **BUILD SUCCESSFUL in 3m 46s**,
  289 tasks: 14 executed, 275 up-to-date. Выполнены R8, resource shrinking,
  упаковка и подпись. Неизменённые native outputs использованы повторно.
- `tools/verify_release_apk.py --abi arm64-v8a`: package/release flag/ABI,
  Python, FFmpeg DEX/JNI, TgNet snapshot и APK signature — успешно.
- `aapt dump badging`: package, versionName/versionCode и arm64-v8a подтверждены.
- `apksigner verify --verbose --print-certs`: v2-подпись действительна,
  сертификат совпадает с предыдущими APK.
- ZIP CRC — успешно. В готовом DEX обнаружены ключ настройки,
  `BluetoothMediaAudio`, `HistoryPageDelivery` и имя Vaporeon-пака.
- Все 20 выбранных production/version файлов совпадают по SHA-256 между
  исходным и сборочным деревьями.

Команда:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --console=plain --no-build-cache \
  -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Логи: `reports/bluetooth-hd-calls/`: `environment.log`, `release.log`,
`compile-final-source.log`, `tests-final.log`, `verification.log`, `signature.log`,
`badging.log`, `apk-content.json`, `source-consistency.json`.
Остались известные предупреждения upstream-ресурсов, deprecated API/Gradle и R8
о `META-INF/services/javax.script.ScriptEngineFactory`.

## Ограничения

**На физическом устройстве и реальной Bluetooth-гарнитуре не проверено.**
Host-тесты и сборка не доказывают фактический routed device, отсутствие утечки
звука при внезапном отключении наушников, задержку A2DP или поведение прошивки.
Не меняется битрейт Telegram-кодека; название HD относится к обходу ограничения
SCO/HFP. Для LE-only/HFP-only устройств HD-режим не активируется.
Публикация на внешних сервисах не выполнялась. Перед распространением пройти
аппаратный чек-лист из [описания функции](BLUETOOTH_HD_CALLS.md).
