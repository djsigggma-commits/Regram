# Подписанный release-кандидат 1.0.8

Включены исправление ширины нижней панели на экранах высокой плотности, настройка «Папки сверху» и дополнительный проход оптимизации / Debloat из [PERFORMANCE.md](PERFORMANCE.md).

- APK: `/home/atb/Загрузки/reggram/regram-v1.0.8-arm64-release.apk`.
- Package: `app.regram.android`; versionName: `1.0.8`; versionCode: `10008`; ABI: `arm64-v8a`.
- Размер: 77 281 341 байт.
- SHA-256: `0787e9d875efb2e69d263b166ca635f000c2c901670182065acf38eff5706666`.
- Сертификат SHA-256: `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`, совпадает с APK 1.0.7.
- Release-verifier успешно проверил пакет, ABI, отсутствие debuggable, подпись, наличие Python-движка и FFmpeg JNI-контракт в post-R8 DEX.
- `zipalign -c -P 16 4` успешен.
- `libtmessages.49.so` побайтно совпадает с APK 1.0.7; native-исправления звонков сохранены.
- Автотесты: 268 passed, 39 skipped, 27 subtests passed.

Сборочная копия: `/home/atb/projects/regram-beta14-release`.
Команда: `REGRAM_BUILD_DIR=/home/atb/projects/regram-beta14-release NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/home/atb/.local/share/pythons/python/bin/python3.11 bash tools/build.sh --offline -Dorg.gradle.jvmargs='-Xmx10g -XX:+UseG1GC -Dfile.encoding=UTF-8' --max-workers=6 :TMessagesProj:assembleRelease`.
Результат: `BUILD SUCCESSFUL in 5m 5s`. Лог: `/tmp/regram-1.0.8-release-build.log`.

APK не установлен автоматически и не опубликован. Отрисовка, переключение положения папок, обновление поверх предыдущей версии, уведомления и реальные звонки на устройстве ещё не проверены. Подпись и версия позволяют обновление поверх 1.0.7 без удаления приложения; работу обновления нужно подтвердить на устройстве.
