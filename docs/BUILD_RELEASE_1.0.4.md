# Подписанный release 1.0.4

Новые встроенные функции: [NATIVE_VISUAL_SETTINGS.md](NATIVE_VISUAL_SETTINGS.md).

- APK: `/home/atb/Загрузки/reggram/regram-v1.0.4-arm64-release.apk`.
- Размер: 76 849 833 байт.
- SHA-256: `880174b8883e2cc578beb068419cbae2c82dfcad5a69d701f753b70bc64f83d7`.
- Package: `app.regram.android`; versionName `1.0.4`; versionCode `10004`.
- Основа exteraless 12.10.6-beta14, ABI `arm64-v8a`.
- Release, не debug. Подпись v2 валидна, сертификат прежний: SHA-256 `0f47547f8fb5a5c3f1199339cf7b725a19dc030bd90891ce6e97cd364fea5f1d`.
- JNI-контракт FFmpeg в post-R8 DEX, package, ABI и отсутствие `application-debuggable` проверены release-verifier.
- `zipalign -c -P 16 -v 4` успешен.
- Классы MonoColor, AllLeft, NativeVisualSettingsActivity действительно присутствуют в DEX готового APK.
- Все 17 изменённых файлов сборки совпадают с текущими исходниками.
- Тесты исходников: 259 passed, 31 skipped, 27 subtests passed.

Build-каталог `/home/atb/projects/regram-beta14-release`; `release-build.exit` и `release-verify.exit` равны 0. Исправление звонков из 1.0.3 сохранено; media-подмодуль остаётся в проверенной beta14 ревизии, JNI-проверка не отключена.

Установка на телефон и визуальные тесты не выполнялись. Обновлять поверх существующего клиента, не удаляя приложение/данные. Перед включением встроенных режимов отключить одноимённые исходные плагины, чтобы их хуки не конфликтовали с native Java-отрисовкой.
