# re:gram 1.3.2 — обрезанная компактная панель с поиском

## Причина и исправление

По предоставленному скриншоту при compact navigation первая иконка обрезана слева.
В `MainTabsActivity` панель с separate search получала `MATCH_PARENT` и правый
margin 60dp для кнопки, но наследовала `Gravity.CENTER_HORIZONTAL`.

При измеренной ширине `W - 60` FrameLayout центрировал её с дополнительной
поправкой `leftMargin - rightMargin`: `left = 30 - 60 = -30dp`.
Это ошибка размещения контейнера, а не масштаба иконки или списка папок.

В ветке separate Glass search теперь явно устанавливаются:
- `Gravity.BOTTOM | Gravity.LEFT`;
- `MATCH_PARENT`, left margin 4dp и right margin 60dp.

Физическая привязка соответствует уже имеющейся физической `Gravity.RIGHT`
у кнопки поиска. Остальные режимы панели не меняются; размеры и центрирование,
исправленные в 1.3.1, сохранены. Настройки пользователя не сбрасываются.

## Проверки и сборка

- `test_bottom_dock_visuals.py`: добавлен source guard gravity и модель
  FrameLayout для ширин 240–840dp, плотностей 1–4 и асимметричных системных inset.
  Проверяется, что панель в пределах родителя и не пересекается с поиском;
  прежняя центрированная геометрия действительно уходит за левый край.
  Это host-модель, не выполнение Android FrameLayout и не pixel test.
- Полный suite: **415 passed, 31 skipped, 27 subtests passed**.
- Debug compilation и release packaging/R8: **BUILD SUCCESSFUL in 5m 40s**.
- Release APK verifier прошёл: package/release flag/arm64-v8a/Python engine,
  FFmpeg DEX JNI contract, TgNet native snapshot и подпись.
- Manifest: `app.regram.android`, **1.3.2 / 1030200**. Основа: **13.0.2**.
- Сертификат сохранён:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.
- Команда аналогична 1.3.1: `compileDebugJavaWithJavac assembleRelease`, offline,
  no-build-cache, исключена только Crashlytics mapping upload. Прежние compiler,
  resource, Gradle и R8 ScriptEngineFactory warnings остаются.

Логи: `/tmp/regram-release-1.3.2.log`, `/tmp/regram-release-1.3.2-tests.log`,
`/tmp/regram-release-1.3.2-verify.log`, `/tmp/regram-release-1.3.2-cert.log`.

## APK

- `/home/atb/Загрузки/regram-1.3.2-arm64-v8a.apk`.
- `artifacts/regram-v1.3.2(1030200)-release-arm64-v8a.apk`.
- Размер: **79 959 912 байт**.
- SHA-256: `8215916dbc79d65b81d3628e0d12cec364e3545fa9cb6d9cf77d171e96bfdd46`.
- Обе копии проверены, `.sha256` рядом.

Устанавливать поверх текущего re:gram без удаления приложения/очистки данных.
**На устройстве не проверен.** После установки проверить compact+separate search,
обычную панель, search off/M3, обе ориентации, RTL и разные размеры панели.
