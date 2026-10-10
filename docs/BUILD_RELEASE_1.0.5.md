# Подписанный release 1.0.5

Встроенные аватары упоминаний: [NATIVE_MENTION_AVATARS.md](NATIVE_MENTION_AVATARS.md). MonoColor UI, AllLeft, существующий движок плагинов и исправления звонков сохранены.

- APK: `/home/atb/Загрузки/reggram/regram-v1.0.5-arm64-release.apk`.
- Размер: 76 857 721 байт.
- SHA-256: `3633c67ae935f2d796cc0c82b8294bd79351c9d60d5c4cdc9919108626955f1c`.
- Package: `app.regram.android`; versionName `1.0.5`; versionCode `10005`.
- Основа exteraless 12.10.6-beta14; ABI `arm64-v8a`.
- Подписанный release, не debug. Подпись v2 проверена; прежний сертификат SHA-256 `0f47547f8fb5a5c3f1199339cf7b725a19dc030bd90891ce6e97cd364fea5f1d`.
- FFmpeg JNI-контракт в post-R8 DEX, package, ABI и отсутствие application-debuggable проверены release-verifier.
- `zipalign -c -P 16 -v 4` успешен.
- MentionAvatars, MentionAvatarSpan, MonoColor, AllLeft и NativeVisualSettingsActivity присутствуют как определения классов в готовом DEX. Дескриптор MentionAvatarsBridge плагина отсутствует.
- Все 12 изменённых файлов, участвующих в сборке, совпадают с текущими исходниками.
- `libtmessages.49.so` побайтно совпадает с проверенным APK 1.0.4: native-исправления звонков не изменены.
- Автоматические тесты: 262 passed, 31 skipped, 27 subtests passed. Android-отрисовку и реальные звонки эти тесты не проверяют.

Build-каталог: `/home/atb/projects/regram-beta14-release`. Финальная сборка успешна; `release-build.exit` и `release-verify.exit` равны 0. Файл контрольной суммы находится рядом с APK.

Включение: **re:gram → Оформление → MonoColor, AllLeft и упоминания → MentionAvatars**. Режим по умолчанию выключен; перед включением отключить исходный плагин `mention_avatars`. Изображения берутся для пользователей/чатов, уже известных текущему аккаунту; дополнительные запросы разрешения username не выполняются.

APK не установлен автоматически и не опубликован. Обновлять поверх существующего re:gram без удаления приложения/данных. Визуальная корректность, производительность и звонки на устройстве ещё требуют проверки.
