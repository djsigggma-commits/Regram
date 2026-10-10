# Подписанный release 1.0.7

Нативные Folders Below и Plugins Tab: [NATIVE_BOTTOM_NAVIGATION.md](NATIVE_BOTTOM_NAVIGATION.md).

- APK: `/home/atb/Загрузки/reggram/regram-v1.0.7-arm64-release.apk`.
- Package `app.regram.android`, versionName `1.0.7`, versionCode `10007`, ABI `arm64-v8a`.
- Размер: 77 278 845 байт.
- SHA-256: `a10838d98210c8fd7a9aa6ea2dd84108aab814c2645d056f7f4cb53234d24e50`.
- Подписанный release, не debug; release-verifier проверил пакет, ABI, подпись, Python-движок и FFmpeg JNI-контракт.
- Сертификат SHA-256: `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`, совпадает с APK 1.0.6.
- `zipalign -c -P 16 4` успешен.
- Четыре изменённых Java-файла и `gradle.properties` побайтно совпадают со сборочной копией.
- `libtmessages.49.so` побайтно совпадает с APK 1.0.6; native-исправления звонков сохранены.
- Дескрипторы `PluginsNavBridge` и `com/pessdes/bottomfolders` отсутствуют в DEX APK: payload-ы этих плагинов не добавлены.
- Автотесты: 260 passed, 39 skipped, 27 subtests passed.

В подмодуле Chaquopy применена локальная совместимость с CMake 4: `-DCMAKE_POLICY_VERSION_MINIMUM=3.5`. Для воспроизведения после восстановления подмодулей выполнить `git -C TMessagesProj/chaquopy apply ../../tools/patches/chaquopy-cmake-policy.patch`. Патч сохранён отдельно, поскольку gitlink указывает на оригинальный upstream-коммит.

Сборочная копия: `/home/atb/projects/regram-beta14-release`.
Команда: `REGRAM_BUILD_DIR=/home/atb/projects/regram-beta14-release NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/home/atb/.local/share/pythons/python/bin/python3.11 ./tools/build.sh -Dorg.gradle.jvmargs='-Xmx10g -XX:+UseG1GC -Dfile.encoding=UTF-8' --max-workers=6 :TMessagesProj:assembleRelease`.
Финальный результат: `BUILD SUCCESSFUL in 4m 52s`. Логи: `/tmp/regram-1.0.7-release-build.log`, `/tmp/regram-1.0.7-release-verify.log`, `/tmp/regram-native-tests.log`.

APK не установлен автоматически. Отрисовка, жесты и реальные звонки на устройстве не проверены. Перед использованием отключить исходные `folders_below` и `plugstonav`. Обновлять поверх 1.0.6 без удаления приложения/данных.
