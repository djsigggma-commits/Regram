# Выпуск re:gram: релизный кандидат ≠ опубликованный релиз

До установки на реальные устройства и проверки списка из [INTEGRATION.md](INTEGRATION.md#проверка-на-устройстве-после-успешной-сборки) публикация запрещена. Автоматической публикации или канала обновлений пока нет; собранный APK — только кандидат. Полное объединение Inugram и Nagram XF также ещё не закончено.

## Данные, которые должен предоставить владелец проекта

- Отдельная пара `TELEGRAM_APP_ID`/`TELEGRAM_APP_HASH` с my.telegram.org, не компиляционные значения `9999999` / 32 нуля.
- Настоящий `TMessagesProj/google-services.json` Firebase с клиентом `app.regram.android`. Debug-заглушка и `regram-compile-check` не допускаются; проверьте push/Crashlytics на устройстве.
- Собственный `TMessagesProj/release.keystore` и `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`; храните оригинал ключа и резервную копию вне CI. Потеря ключа делает обновление APK с тем же пакетом невозможным.
- `MAPS_API_KEY`, ограниченный пакетом и сертификатом подписи, если нужны Google Maps; `GLYPH_API_KEY` — если нужен Nothing Glyph. Отсутствие ключей не блокирует сборку, но работу этих интеграций нужно проверить отдельно.
- Утверждённый канал распространения и обновлений, стратегия перехода со старого пакета. `app.regram.android` не заменяет старые установки и сам не переносит их данные.

Файлы `local.properties`, `TMessagesProj/google-services.json` и `TMessagesProj/release.keystore` исключены из git. Никогда не прикладывайте их к issue или артефактам CI.

## Локальная сборка кандидата

1. Установите компоненты из [README](../README.md#подготовка-сборки), восстановите подмодули `./tools/restore_submodules.sh .`, создайте `local.properties` на основе примера. В текущем пути с `:` обязательно используйте `tools/build.sh` — он переносит исходники в безопасный путь без двоеточия.
2. Выполните `python3 -m pytest tests -q` и `python3 tools/check_build_env.py --release` (из исходного каталога проверка сообщит о `:`; реальная сборка выполняется в копии). `REGRAM_COMPILE_CHECK=1` запрещён.
3. `NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/path/to/python3.11 ./tools/build.sh -Dorg.gradle.jvmargs=-Xmx10g :TMessagesProj:assembleRelease`.
4. В сборочной копии (`~/projects/regram` по умолчанию) проверьте APK: `python3 tools/verify_release_apk.py --abi arm64-v8a TMessagesProj/build/outputs/apk/release/*.apk`. Для x86_64 используйте `NATIVE_TARGET=x86_64`; для двух ABI одним APK — `NATIVE_TARGET=universal`. Проверка проверяет пакет, ABI, Python engine и подпись, но **не** корректность ключа, серверных настроек и приложения в работе.
5. Сохраните контрольную сумму SHA-256, версию, сертификат подписи и список проверенных устройств. Версия re:gram задаётся `REGRAM_VERSION` в `gradle.properties` независимо от версии Telegram-основы: `1.0` → `versionCode=10000`, `1.0.1` → `10001`, `1.1` → `10100`. Перед следующим выпуском увеличьте версию re:gram; код вычисляется автоматически. Не распространяйте debug или `NATIVE_TARGET=SKIP` APK.

## GitHub Actions

`checks.yml` запускает тесты исходников на push/PR. `release-candidate.yml` запускается **только вручную** через Actions → Build release candidate и кладёт подписанный APK в приватный артефакт на 1 день. GitHub Actions и Android сборка в нём ещё не проверялись на живом runner. Нужен доверенный **self-hosted Linux x64 runner** с меткой `regram-android`, JDK/SDK устанавливаются workflow; выделите более 10 GiB RAM для Gradle и достаточно диска для NDK, подмодулей и APK. Обычный GitHub-hosted runner с 7 GiB RAM для полного dex не годится. До запуска защитите GitHub environment `release` обязательным ревьюером, исключите недоверенных пользователей с правом workflow_dispatch и настройте секреты environment:

| Секрет | Содержимое (base64 одной строкой) |
| --- | --- |
| `LOCAL_PROPERTIES_B64` | `local.properties` с API ID/hash и параметрами подписи (можно без `sdk.dir`: CI берёт SDK из `ANDROID_HOME`) |
| `REGRAM_GOOGLE_SERVICES_B64` | настоящий `google-services.json` |
| `REGRAM_KEYSTORE_B64` | настоящий `release.keystore` |

Не публикуйте артефакт автоматически: после сборки нужны установка APK, вход с реальными credentials, push, плагины, доступность внешних сервисов, все 15 сценариев из INTEGRATION.md, проверка обновления с предыдущего подписанного APK и ручное решение о выпуске. Для первого выпуска отдельно определите канал обновлений и способ обратной связи о сбоях.
