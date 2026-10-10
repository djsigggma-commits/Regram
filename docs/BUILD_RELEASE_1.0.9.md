# Подписанный release-кандидат 1.0.9

Изменения относительно 1.0.8:
- Небезопасный режим плагинов включён по умолчанию при отсутствии сохранённой настройки. Явное отключение пользователем сохраняется; при недоступном хранилище режим остаётся выключенным. Режим снимает ограничения доступа плагинов, поэтому устанавливать следует только доверенные плагины.
- MentionAvatars сохраняет цвет и форматирование ссылки в заменённом участке текста, включая первое кириллическое слово; общая краска текста не изменяется.
- Окно обновлений использует светлый/тёмный логотип из `logo`; значок раздела настроек и монохромный значок заменены самолётиком re:gram.

APK: `/home/atb/Загрузки/reggram/regram-v1.0.9-arm64-release.apk`.
Package: `app.regram.android`; versionName: `1.0.9`; versionCode: `10009`; ABI: `arm64-v8a`.
Размер: 77 296 891 байт.
SHA-256: `c1992baa7a5b23a3b0c0af016dbd616b74b8d894b8ffdc74230ab10ea357266b`.
Сертификат SHA-256: `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`, совпадает с 1.0.8.

Проверки:
- 279 passed, 31 skipped, 27 subtests passed (`pytest`, JDK 17 в PATH).
- Release-verifier: пакет, ABI, отсутствие debuggable, подпись, Python-движок и FFmpeg JNI-контракт в post-R8 DEX.
- `zipalign -c -P 16 4` успешен.
- `libtmessages.49.so` побайтно совпадает с 1.0.8; native-исправления звонков сохранены.

Команда: `REGRAM_BUILD_DIR=/home/atb/projects/regram-beta14-release NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/home/atb/.local/share/pythons/python/bin/python3.11 bash tools/build.sh --offline -Dorg.gradle.jvmargs='-Xmx10g -XX:+UseG1GC -Dfile.encoding=UTF-8' --max-workers=6 :TMessagesProj:assembleRelease`.
Результат: `BUILD SUCCESSFUL in 5m 20s`. Лог: `/tmp/regram-1.0.9-release-build.log`.

APK не установлен автоматически и не опубликован на GitHub. Подпись и увеличенный versionCode позволяют установку поверх 1.0.8 без удаления данных; обновление и новые изменения требуют проверки на устройстве.
