# Подписанная release-сборка re:gram 1.0.2

Собран **release**, не debug. Выполнена только локальная сборка; APK не опубликован, не установлен и не проверен на телефоне.

## Артефакт

- APK: `/home/atb/Загрузки/reggram/regram-v1.0.2-arm64-release.apk`
- SHA-256: `fe11d9cfbdef719cb6d077b8c5c5ffc108bb94e91c472165a336379b4429fa95`
- Размер: 76 831 429 байт (~73.3 MiB).
- Пакет: `app.regram.android`.
- Версия: `1.0.2`, `versionCode=10002`.
- ABI: только `arm64-v8a`.
- Сертификат SHA-256: `0f47547f8fb5a5c3f1199339cf7b725a19dc030bd90891ce6e97cd364fea5f1d` — совпадает с существующим подписанным `regram-v1.0.1(10001).apk`.

## Сборка и проверки

- Отдельный каталог: `/home/atb/projects/regram-beta14-release`.
- Gradle 9.7.1, JDK 21, Android SDK, NDK 27.2.12479018, CMake 3.31.6, build Python 3.11.
- Задача `:TMessagesProj:assembleRelease`, `NATIVE_TARGET=arm64-v8a`, Gradle heap 10 GiB.
- `REGRAM_COMPILE_CHECK` отключён; использованы существующие приватные API/Firebase-настройки и release-keystore от предыдущего релиза. Секреты не копировались в это дерево исходников и не включены в отчёт.
- Chaquopy собран из закреплённого форка `c217deac1c2df5ab879b9844d911e1c90e59a4c1`. Явно задан `CMAKE=$ANDROID_HOME/cmake/3.31.6/bin/cmake`: системный CMake 4.x несовместим с upstream CMakeLists Chaquopy.
- Native-библиотека пересобрана в новом каталоге; упакованная `libtmessages.49.so` побайтно соответствует свежему stripped release-выходу. Старый APK/native binary не использовался вместо новых изменений.
- Исправлены выявленные javac несовместимости после обновления: enum/icon re:gram, импорт FileLog, оставшаяся старая ветка hideTimeOnStickers. Настройка времени стикеров остаётся в новом `stickerTimeRow`.
- `BUILD SUCCESSFUL`; R8 и resource shrinking выполнены.
- `tools/verify_release_apk.py`: корректный пакет/ABI, Python engine присутствует, `application-debuggable` отсутствует, подпись валидна.
- `apksigner verify`: APK Signature Scheme v2 подтверждена; сертификат совпадает с предыдущим релизом.
- `zipalign -c -P 16 4`: успешно.
- Тесты исходников с JDK в PATH: **247 passed, 32 skipped, 27 subtests passed**.

Подробные локальные логи и метаданные: `release-build.log`, `release-verification.log`, `release-signature.log`, `release-badging.log` в каталоге сборки. Приватные `local.properties`, `google-services.json` и keystore не следует передавать вместе с логами.

## Ограничения

Это подписанный релизный кандидат, а не подтверждение исправности звонков, push, плагинов или быстрого запуска. Нужна проверка обновления с 1.0.1 и сценариев из [CALL_STARTUP_FIX.md](CALL_STARTUP_FIX.md) и [INTEGRATION.md](INTEGRATION.md). На компьютере нет подключённого adb-устройства. Установку и публикацию автоматически не выполняли.
