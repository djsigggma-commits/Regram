# re:gram 1.1.6.3 — исправление загрузки JNI после обновления

## Подтверждённая причина

На Pixel 8 / Android 17 версия 1.1.6.2 завершалась в `ApplicationLoader.onCreate()` с `can't load native libraries arm64-v8a`. Прочитан пользовательский crash-лог; затем через ADB получен основной logcat, где обнаружена исходная ошибка:

```
Failed to register native method org.telegram.tgnet.ConnectionsManager.native_setDeviceMetadata(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V
No implementation found for void org.telegram.tgnet.ConnectionsManager.native_setJava(boolean)
```

Скомпилированный `.so` содержал устаревший `native_setDeviceMetadata`, которого нет ни в текущем Java-коде, ни в текущей таблице `TgNetWrapper.cpp`. Сборочная копия использовала `.cxx` от другого снимка исходников: восстановленные из архива исходники имели mtime 1 октября, а объект `TgNetWrapper.cpp.o` — 12 октября. Ninja счёл объект свежим. Ошибка регистрации прервала загрузку библиотеки; ошибка `native_setJava` была следствием, а не отсутствием arm64-поддержки.

## Исправление

- Полностью удалено старое CMake-дерево в сборочной копии, native-код пересобран из текущих исходников без Gradle build cache.
- `tools/build.sh` теперь синхронизирует файлы через `rsync --checksum`, чтобы обнаруживать изменения с одинаковыми размерами/mtime.
- `tools/native_source_fingerprint.py` считает содержимое и относительные имена native-файлов, игнорируя generated build/cache-каталоги. Неизвестное или изменившееся происхождение кеша очищает `.cxx` и `build/intermediates/cxx` перед Gradle. Неизменённый проверенный снимок сохраняет возможность инкрементальной сборки.
- `tools/verify_release_apk.py` сверяет имена TgNet JNI-регистраций непосредственно в упакованной stripped-библиотеке с текущей исходной таблицей. APK 1.1.6.2 теперь отклоняется с `unexpected=['native_setDeviceMetadata']`. Это проверка имен таблицы, не доказательство полной корректности всех JNI-сигнатур; отдельная проверка FFmpeg DEX-сигнатур сохранена.
- `ApplicationLoader` сохраняет исходный `UnsatisfiedLinkError` и накопленные ошибки загрузчика в исключении вместо потери диагностической информации.
- Версия повышена до **1.1.6.3 (1010603)**. ExitFy по-прежнему отсутствует; пользовательские данные, настройки и установленные пользователем плагины не удаляются.

## Проверки

- JDK 21, `python3 -m pytest -q`: **315 passed, 31 skipped, 27 subtests passed**. Лог: `/tmp/regram-1163-tests-final.log`.
- Новые регрессионные проверки: одинаковые размер и mtime при разных исходниках; изменения заголовков/имён; отсутствие влияния generated-кеша; отклонение лишнего и отсутствующего JNI-метода; сохранение причины startup-исключения.
- Первичная полная native-сборка завершила компиляцию, но APK не был собран из-за сетевой ошибки задачи отправки mapping в Firebase (`NoHttpResponseException`). Повторная сборка исключила только эту задачу:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --no-build-cache -Dorg.gradle.jvmargs=-Xmx10g \
  :TMessagesProj:assembleRelease -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

- **BUILD SUCCESSFUL in 3m 11s** после полной native-пересборки. Логи: `/tmp/regram-release-1163.log` и `/tmp/regram-release-1163-final.log`. Crashlytics runtime не отключён; mapping этой сборки не загружен.
- `verify_release_apk.py --abi arm64-v8a` прошёл: пакет, отсутствие debuggable, ABI, Python engine, FFmpeg JNI, TgNet native snapshot и подпись.
- В новой библиотеке 43 актуальных JNI-регистрации. SHA-256 `libtmessages.49.so`: `c6914615cdc88462185a2edf1e663e0a88125ffc55c1810dcc529c0edf9028fc`; библиотека побайтно совпадает с рабочим APK 1.1.6.1.
- APK не содержит имён/строк ExitFy (проверены распакованные записи, DEX, ресурсы; ASCII и UTF-16LE).
- Manifest: `app.regram.android`, **1.1.6.3 / 1010603**, arm64-v8a.
- Сертификат SHA-256 сохранён: `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba` — совпадает с 1.1.6.1.

## APK

- `artifacts/regram-v1.1.6.3(1010603)-release-arm64-v8a.apk`
- Копия для установки: `/home/atb/Загрузки/regram-1.1.6.3-arm64-v8a.apk`
- Размер: **77 160 238 байт**.
- SHA-256: `8aa1bc681d12bd8e5f55471bc4d855758c28c121c2f46ef2912664022419151a`.
- Файлы `.sha256` лежат рядом; проверка обеих копий прошла.

Устанавливать как обновление, без удаления приложения и очистки данных. 1.1.6.2 не использовать. Новый APK не устанавливался на телефон: к окончанию сборки устройство было отключено от ADB. Подтверждение запуска и функций на Pixel 8 ещё требуется; независимые системные/плагинные падения из старой истории logcat этим исправлением не объявляются устранёнными. APK не публиковался.
