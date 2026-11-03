# re:gram 1.3.1 — нижние папки и компактная навигация

Основание: предоставленный пользователем скриншот
`artifacts/photo_2026-10-16_21-37-56.jpg`. Изображение/личные названия чатов в отчёт
не копируются. Скриншот показывает читаемый текст списка между нижними папками и
панелью, а также отдельный поиск, выступающий выше компактной панели.
Заголовок `Plugins` может быть названием выбранной папки при включённом
`folderNameAsTitle`; этот режим сохранён и ошибкой маршрутизации не объявляется.

## Исправления

- `DialogsActivity`: при видимых нижних папках основная и соседняя страницы
  списка ограничены по верхней границе folder dock. Внизу рисуется собственная
  тематическая подложка, так что строки чатов не проступают в зазорах чипсов.
  Общие Theme Paint не мутируются. Top-folders и отсутствие папок сохраняют
  прежнюю область рисования; штатный blur capture остаётся отдельным путём.
- Новые DOWN-события по скрытым строкам под доком отклоняются, с переводом
  координат RecyclerView в систему fragment root. Уже начатая прокрутка сохраняет
  MOVE/UP/CANCEL; нижний padding списка по-прежнему позволяет доскроллить до конца.
- `MainTabsUiHelper`: separate search сохраняет touch target 48dp, а Glass panel
  учитывает его высоту даже в compact режиме. Высота view, reserve списка/FAB,
  скругление и blur bounds используют одну геометрию; без separate search прежние
  размеры сохранены, Material 3 остаётся отдельной веткой.
- `MainTabsActivity`: центр поиска выровнен по центру панели при разных размерах.
- `MainTabsLayout`: при отдельном поиске вкладки занимают выделенную им ширину,
  вместо дополнительного сжатия до legacy max-width и лишнего пустого промежутка.

## Проверки

- Новые host tests `tests/test_bottom_dock_visuals.py`: настоящий Java
  `MainTabsUiHelper` на API doubles, размеры 27/36/45/48/56/70dp, margins 4/8dp,
  search on/off, radius/blur/FAB/reserve/центрирование, сохранение M3/insets;
  source guards области отрисовки/подложки/touch routing.
- Targeted suite: **7 passed**; полный suite: **414 passed, 31 skipped,
  27 subtests passed**. Это не Android instrumentation или screenshot test.
- Debug compilation и release packaging/R8:
  **BUILD SUCCESSFUL in 5m 38s**.
- `verify_release_apk.py --abi arm64-v8a` прошёл: package, release flag,
  ABI, Python engine, FFmpeg JNI contract, TgNet JNI snapshot и подпись.
- Manifest: **app.regram.android**, версия **1.3.1 / 1030100**.
  Основа прежняя: **13.0.2 / 7175**.
- Сертификат прежний:
  `d1e77b65db864980440f552dcfd6dc686c749c55150ae1da9699c40ef63c13ba`.
- Пропущена только Crashlytics mapping upload, не runtime. Прежние compiler/
  resource/Gradle warnings и R8 ScriptEngineFactory warning сохранились.

Команда:

```sh
NATIVE_TARGET=arm64-v8a CHAQUOPY_BUILD_PYTHON=/usr/local/bin/python3.11 \
  ./tools/build.sh --offline --no-build-cache -Dorg.gradle.jvmargs=-Xmx10g \
  :TMessagesProj:compileDebugJavaWithJavac :TMessagesProj:assembleRelease \
  -x :TMessagesProj:uploadCrashlyticsMappingFileRelease
```

Первичная проверка выявила обращение к локальному `contentView` вне его scope;
исправлено на `fragmentView`, после чего итоговая сборка выше прошла.
Логи: `/tmp/regram-release-1.3.1.log`, `/tmp/regram-release-1.3.1-tests.log`,
`/tmp/regram-release-1.3.1-verify.log`, `/tmp/regram-release-1.3.1-cert.log`.

## APK

- `/home/atb/Загрузки/regram-1.3.1-arm64-v8a.apk`.
- `artifacts/regram-v1.3.1(1030100)-release-arm64-v8a.apk`.
- Размер: **79 959 852 байта**.
- SHA-256:
  `1279b18d4f4c89bffb2a59eb991681a47818ecdce1f2818383bf2e076a83d7b3`.
- Обе копии проверены по SHA-256, рядом `.sha256`.

Устанавливать поверх 1.3 без удаления приложения/очистки данных. Пользовательские
настройки папок, форм, подписей, размеров и фоновой темы не сбрасываются.
**На устройстве APK не проверен**: ожидаемый визуальный эффект требует подтверждения
на том же экране/настройках. Дополнительно проверить top/bottom folders, compact/
обычную панель и M3, search on/off, RTL, rotation/insets, появление/скрытие панели,
прокрутку до последнего чата, swipe папок и action mode. Другие отличия оформления
(например, форма аватаров/карточек, заданная настройками) этой правкой не меняются.
