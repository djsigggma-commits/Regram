# re:gram 1.4.8.9 — release APK с разделом «Обход блокировки»

## Версия и состав

- Пакет: `app.regram.android`, архитектура **arm64-v8a**.
- `versionName=1.4.8.9`, `versionCode=1040809` — **сохранены такими же, как у первой сборки 1.4.8.9**, по запросу владельца. Отличайте APK по SHA-256, а не по номеру версии. Автообновлятор может не предложить эту сборку как более новую.
- Основа остаётся exteraless 13.1.1 / 7237, layer 232.
- Включён [встроенный Telegram WebSocket-транспорт](NATIVE_TELEGRAM_WEBSOCKET.md) с настоящим пулом заранее открытых соединений. **Размер пула по умолчанию — 4 на недавно использованный маршрут**, варианты в настройках — 0/2/4/8.
- **Cloudflare-маршруты по умолчанию включены** внутри WS-транспорта. Явно сохранённое пользователем выключение Cloudflare не перезаписывается.
- Раздел переименован в **«Обход блокировки»**, переключатель — **«Использовать обход»**; длинное описание под настройками удалено. Диагностика и ссылка на исходники/авторов сохранены.
- Сам WS-транспорт и прямой TCP-резерв по умолчанию выключены. Настройки: **Настройки → Настройки re:gram → Функционал re:gram → Обход блокировки**.
- Есть пополнение пула, TTL, backoff, остановка неактивного прогрева, приоритет внешнего прокси и диагностика target/idle/pending. Использованные MTProto-соединения в пул не возвращаются.
- Не требуется отдельное приложение, foreground service или локальный MTProxy-порт. TLS-сертификаты и hostname проверяются. Звонки, Telegram CDN и bootstrap-соединения сохраняют прежний транспорт; это не VPN.

## Сборка

`tools/check_build_env.py --release`: успешно. Использованы существующие API/Firebase-конфигурация и release-keystore; новый ключ не создавался. Compile-check и пропуск native-сборки не использовались.

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --console=plain --no-build-cache \
  -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Сборочная копия: `/home/atb/projects/regram`.

**BUILD SUCCESSFUL in 3m 45s** — 289 tasks, 19 executed, 270 up-to-date. Выполнены release Java/Kotlin, R8, resource shrinking, упаковка и подпись. Native-задачи не отключались; неизменённые native outputs использованы повторно после проверки provenance. Отправка mapping в Crashlytics исключена, runtime Crashlytics не отключался. Сохранились upstream-предупреждения ресурсов, Java/Gradle и известное предупреждение R8 о `META-INF/services/javax.script.ScriptEngineFactory`.

## Проверки

- Полный suite: **469 passed, 31 skipped, 27 subtests passed** за 78.38 секунды. Skipped проверки не выдаются за пройденные.
- `tools/verify_release_apk.py --abi arm64-v8a`: успешно — package, release/debug flag, ABI, Python engine, FFmpeg DEX/JNI, TgNet native snapshot и APK signature.
- `aapt dump badging`: `app.regram.android`, `1.4.8.9`, `1040809`, arm64-v8a.
- `apksigner verify --verbose --print-certs`: успешно. SHA-256 сертификата совпадает с первой сборкой 1.4.8.9 и с 1.4.8.8:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.
- В фактическом post-R8 DEX проверены `DEFAULT_POOL_SIZE=4`, `DEFAULT_CLOUDFLARE=true`, наличие `WebSocketWarmPool.take/prepare/maintain/reset`, `poolSize/defaultBoolean`, WSS callbacks и методы framing.
- В ресурсах готового APK проверены русские названия **«Обход блокировки»**, **«Использовать обход»** и английские эквиваленты. В DEX отсутствует маркер строки описания `ws_info`, но присутствуют `ws_source`, `ws_enabled` и `ws_pool_size`.
- Callback-маркеры и сигнатура `(IZZLjava/lang/String;I)I` присутствуют в упакованной `libtmessages.49.so`.
- ZIP CRC проверены. Все **30** непосредственно упакованных `.so` (включая Python bootstrap assets) — ELF64/AArch64; их набор совпадает с первой сборкой 1.4.8.9.
- Все 16 выбранных исходных/сборочных файлов совпали по SHA-256, включая новый `WebSocketWarmPool.java`, транспорт, настройки, локализации и version properties.
- Native source fingerprint исходного и сборочного деревьев совпадает:
  `d95247639ca41191a18cd9be0f081f0e63bd43faea1a7f94beff6ed06ff961fc`.
- Обе сохранённые копии APK совпадают по SHA-256 с результатом сборки; `.sha256` проверены из соответствующих каталогов.

Логи и проверки этой пересборки: `../reports/release-1.4.8.9-bypass-ui/` — `environment.log`, `build.log`, `tests.log`, `verification.log`, `badging.log`, `signature.log`, `previous-signature.log`, `websocket.json`, `artifact.json`, `source-consistency.json`, `native-provenance.json`. Скрипт post-R8/UI проверки — `verify_websocket.py` в том же каталоге. При первом чтении полного aapt-дампа вспомогательный скрипт встретил невалидный UTF-8 в посторонних строках; исправлена только обработка дампа, повторная проверка названий успешна. Исходники приложения из-за этого не менялись; лог первой попытки сохранён отдельно.

## Новый APK

- `/home/atb/Загрузки/regram-1.4.8.9-arm64-v8a.apk`
- `artifacts/regram-v1.4.8.9(1040809)-release-arm64-v8a.apk`
- Размер: **80 731 385 байт** (80.73 MB / 76.99 MiB).
- **SHA-256: `070081c10c29a7e2865215b1ad5403ba08ec7454aa08f8121b133c4dfdd03eea`.**
- Рядом с обеими копиями обновлены файлы `.sha256`.

### Старая сборка сохранена

Обе предыдущие сборки сохранены:

- До переименования и удаления описания: `artifacts/archive/regram-v1.4.8.9(1040809)-release-arm64-v8a-before-bypass-ui.apk`, SHA-256 `0b9a58b8a01d400b0e70a658698193c42bab178b507e6a7c09a1018d0882a024`. Прежние логи — `../reports/release-1.4.8.9-ws-pool/`; предыдущий отчёт скопирован в `../reports/release-1.4.8.9-bypass-ui/previous-build-report.md`.
- До добавления пула: `artifacts/archive/regram-v1.4.8.9(1040809)-release-arm64-v8a-before-ws-pool.apk`, SHA-256 `37dd5f031517eb7107aa9f796f30f6ba8aa87521115dff53ee5de2d3a83e1a8b`. Его логи — `../reports/release-1.4.8.9/`.

## Ограничения

**На устройстве и реальной сети не проверен.** Это подписанный релизный кандидат; публикация и загрузка на внешние сервисы не выполнялись. Подпись совместима с прежними APK, но установка поверх первой сборки 1.4.8.9, миграция настроек и OEM-поведение требуют проверки на устройстве.

Перед выпуском проверить [общие сценарии](INTEGRATION.md#проверка-на-устройстве-после-успешной-сборки) и [WSS-сценарии](NATIVE_TELEGRAM_WEBSOCKET.md#проверки), особенно pool hit/refill, значения 0/2/4/8, Cloudflare и сохранённое выключение, ошибки TLS/HTTP 429, несколько аккаунтов, смену сети и остановку прогрева при внешнем прокси. WebSocket-функция остаётся экспериментальной.
