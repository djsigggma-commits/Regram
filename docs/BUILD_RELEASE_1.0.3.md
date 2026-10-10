# Подписанный release 1.0.3

Исправление подтверждённой причины вылета: [CALL_FIX_1.0.3.md](CALL_FIX_1.0.3.md).

- APK: `/home/atb/Загрузки/reggram/regram-v1.0.3-arm64-release.apk`.
- SHA-256: `7158fdfd7259a70795ab01186c38b5ec4672ecc0df37f244e318206116c86c0c`.
- Размер: 76 840 721 байт.
- Package: `app.regram.android`; versionName `1.0.3`; versionCode `10003`.
- Основа: exteraless 12.10.6-beta14; ABI: `arm64-v8a`.
- Не debug: `application-debuggable` отсутствует; подпись v2 валидна, прежний release-сертификат сохранён.
- SHA-256 сертификата: `0f47547f8fb5a5c3f1199339cf7b725a19dc030bd90891ce6e97cd364fea5f1d`.
- DEX после R8 соответствует обеим FFmpeg JNI-таблицам и Java-колбэку `growOutputBuffer`; старый APK 1.0.2 эту проверку не проходит.
- `zipalign -c -P 16 -v 4` успешен.
- Пересобранная native-библиотека: Build ID `548aca97bf5f8e792eb78fb05bb451af425cab2c`. Совпадение Build ID packaged и сохранённых unstripped символов проверено.
- Media-зависимость действительно восстановлена из Git, HEAD `c430d207677071b1873f9f18266d55ec45722180`; FFmpeg Java-исходники не изменены относительно этой ревизии.
- Исходники: 254 passed, 31 skipped, 27 subtests passed.

Build-каталог: `/home/atb/projects/regram-beta14-release`. `run-release.sh` выбирает APK из текущего `output-metadata.json`, а не первый старый APK в каталоге. `release-build.exit` и `release-verify.exit` равны 0. Native-символы сохранены локально в `/home/atb/projects/regram-release-symbols/v1.0.3-arm64-v8a/`.

На телефон APK автоматически не установлен. Обновлять поверх существующего re:gram, без удаления приложения и его данных. Успешный звонок, работа медиадекодеров и скорость запуска новой версии ещё требуют проверки на телефоне.
