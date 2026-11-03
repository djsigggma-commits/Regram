# AllLeft 2.0.11 — обновление нативной реализации

## Проверенный источник

- Плагин: `/home/atb/Загрузки/Telegram Desktop/allleft (4).plugin`.
- Автор: **@limeplug**, версия **2.0.11**.
- SHA-256 файла: `f8fd17e64dd2e2c546e4ba1089c792087f3a42163df83e97ad89f1903ef027c9`.
- Репозиторий: https://gitlab.com/limezsh/allleft.
- Проверенный commit: `7bdbeef90eac9432955d0fe2b8ed7294a90ca6ea`.
- `allleft.plugin` из этого commit **побайтно совпадает** с предоставленным файлом.
  Исходники `src/AllLeftBridge.java`, Python wrapper и build script прочитаны;
  лицензия репозитория — **GPL v3**, атрибуция автора сохранена.
- Вложенный DEX только декодирован и разобран `dexdump`: 16016 байт,
  SHA-256 `cc6e95228d9f0a77bd8e0143a15b323aacec8ca568c948c2767fe42f17c17914`.
  В нём девять классов: bridge и восемь обработчиков, соответствующих структуре
  опубликованного Java-файла. Обфусцированного дополнительного runtime не найдено.
  Это не заявление о воспроизводимой побайтовой сборке DEX из Java.

**Плагин, DEX и build script не запускались.** В клиент не добавлены ни DEX,
ни Python wrapper, ни Xposed/hooks, reflection, ThreadLocal-подмена ownership
или опрос настроек через plugin engine. Native-код использует прямые вызовы.

## Что обновлено относительно встроенного AllLeft 2.0.6

### Время и статусы

- Отметки на фото/стикерах/видео привязаны к координатам действительно нарисованного
  timestamp. Для media checkmarks используется нижнее выравнивание
  `textTop + textHeight - iconHeight - dp(1.2)`, а не старое центрирование.
- Для текста сохранён резерв **20 dp**, но во время отрисовки он исключается из
  локального параметра ширины. Timestamp с views/replies/pin больше не сдвигается
  ещё на один резерв; правый край статуса учитывает всю измеренную строку.
  Поле `cell.timeWidth` не уменьшается/восстанавливается временно, как в хуке.
- Медиа-проход использует переданный снимок ширины, а не поле текущей ячейки:
  это важно для анимации изменения размера/раскладки времени.
- Координаты timestamp обновляются и при edited crossfade; не используются
  устаревшие координаты предыдущего кадра или переиспользованной ячейки.
- В cloud-чатах двойная галочка подтверждается **только** account-local
  `dialogs_read_outbox_max >= messageId`. Отсутствие записи, локальный/нулевой ID
  и scheduled дают одну галочку. `unread=false` больше не превращает отсутствие
  cloud read receipt в ложное подтверждение прочтения.
- Сохранено полезное отличие прежнего native-кода: секретные сообщения с
  отрицательными локальными ID используют собственный protocol read flag.
  В исходном плагине такие ID всегда давали одну галочку.
- Сохранены часы sending/editing и ошибка. Incoming-ветка metadata не рисует второй
  экземпляр часов/ошибки поверх собственного статуса AllLeft.
- Часы рисуются отдельным лениво созданным `MsgClockDrawable`: его `setColorFilter`
  в клиенте не окрашивает paint, поэтому применяется `setColor`, без изменения
  глобальных часов Theme. У общих check/error drawables восстанавливаются bounds,
  alpha и filter в `finally`, включая исключение при рисовании.
- Если intrinsic width/height drawable отсутствуют, используется fallback 11 dp,
  как в 2.0.11. Скрытое время, реклама и phone-call layout не получают статус.

### Пузыри, аватары и анимации

- Внесена недостающая layout-only обработка `TextMessageEnterTransition`:
  clipping пузыря, reply colors и todo controls используют `isOutOwnerForLayout()`.
  Отправляемый пузырь и его содержимое не возвращаются к правостороннему оформлению
  в ходе enter-анимации.
- Для активного исходящего AllLeft эффективный pinned-bottom progress равен 1,
  как в новой версии источника. Все соответствующие расчёты фона, хвоста,
  границ и clipping читают единый presentation accessor.
  Реальное значение animator в `TransitionParams` не перезаписывается.
- Существующие native-аватары/имена, группировка и album layout сохраняются.
  Отступ под аватар в private/secret chat рассчитывается штатной геометрией
  **один раз**, без повторного hook-based вычитания gutter из готовой ширины.
- `updateCurrentUserAndChat()` продолжает видеть настоящего автора. Настоящие
  `isOutOwner()`, `messageOwner.out`, sender/peer IDs, read receipts, отправка,
  права редактирования/удаления и модель сообщения не изменяются.

## Настройки и совместимость

Путь прежний: **Оформление → MonoColor, AllLeft и упоминания → AllLeft**.
Сохранены ключи и значения `regramAllLeft`, `regramAllLeftPrivate`,
`regramAllLeftStatus` в `NekoConfig`. Миграции или сброса пользовательских настроек
нет. Master по умолчанию **выключен**, в отличие от включённого plugin default.

Избранное определяется по аккаунту сообщения и остаётся без изменений.
Private-only включает обычные личные и секретные чаты; группы/каналы не меняются.
Существующие исключения preview, sponsored и quick replies сохранены.

Если исходный AllLeft plugin установлен, его нужно отключить вручную, чтобы
не накладывать hooks на native rendering. Общий plugin engine и чужие установки
не удаляются. Режим не обещает визуальную совместимость с произвольными хуками
сторонних плагинов.

Основные изменения:
- `TMessagesProj/src/main/java/app/regram/chats/AllLeft.java`;
- `org/telegram/ui/Cells/ChatMessageCell.java`;
- `org/telegram/ui/TextMessageEnterTransition.java`;
- EN/RU `regram_native_visual.xml` (версия источника в описании).

## Проверки и статус APK

- `tests/test_allleft_2011.py`: **8 новых проверок**, исполняющих настоящий Java
  policy/renderer на host-модели Android API, плюс source contracts.
  Проверены account isolation/read state, negative secret IDs, scheduled/pending/
  error, настройки/Избранное/private-only, pinned progress, media/text geometry,
  double checkmarks, alpha, private clock, drawable fallback и восстановление
  общего drawable при исключении.
- Вместе с `test_native_visual_settings.py`: **13 passed**.
- Полный suite: **378 passed, 31 skipped, 27 subtests passed**.
- Debug/release Java/Kotlin и release resources: **BUILD SUCCESSFUL**.
- Логи: `/tmp/regram-allleft2011-tests.log`, `/tmp/regram-allleft2011-full-tests.log`,
  `/tmp/regram-allleft2011-compile.log`.

**Новый APK не собирался.** Версия клиента остаётся 1.1.6.3; опубликованный APK
не обновлён. Компиляция и host-тесты не подтверждают визуальное поведение на телефоне.
Для этой итерации R8 не запускался.

Нужны device-проверки: длинные строки с views/replies/pin, разные шрифты/RTL,
редактирование и time crossfade, фото/видео/стикеры/кружки с подписями и без,
round-video transcription, альбомы, смена pinned grouping, enter-анимация с reply/
todo, sending/error/read transitions, secret chats, несколько аккаунтов, тёмная/
светлая тема, включение/выключение AllLeft и отдельной опции статусов.
