# Нативный S Pen — по Samsung S-Pen Support 1.6.0

## Источник и статус

- Источник: `/home/atb/Загрузки/Telegram Desktop/spen_support.plugin`.
- Автор: **@huecoder**, версия **1.6.0**, идентификатор `spen_support`.
- SHA-256: `c215b47868720dfc1367ed82d252c51b96a7c6e3aee58d0ddd789d7dbaa7ee17`.
- Файл полностью прочитан, **не запускался**. Закрытых DEX/библиотек, загрузчиков,
  сетевого runtime или другого скрытого исполняемого компонента в нём не найдено.
  Явная лицензия и адрес репозитория в файле не указаны.
- Реализация встроена в Java клиента. Нет хуков, reflection, подмены private полей,
  загрузки `.plugin`, Python или DEX. Новых зависимостей/Android permissions нет.
- Настройки: **openExtera → Other → S Pen**. Главный переключатель **выключен**
  по умолчанию; четыре отдельные опции имеют исходные значения `true`.
- Новый APK **не собран**, версия 1.1.6.3 не изменена. Проверка Samsung не выполнена.
  Опубликованный APK этих изменений не содержит.

## Возможности и прямые точки интеграции

1. **Текст с зажатой кнопкой:** `ChatMessageCell.startTextSelectionForPen()`
   использует существующий hit-test текста/подписи/description/fact-check/rich layout.
   Явный параметр проверки заменяет временную подмену `drawSelectionBackground`.
   `TextSelectionHelper.startSelectionImmediatelyForPen()` запускает уже проверенный
   capture; обычный overlay клиента продолжает расширять выделение при движении.
2. **Двойное касание:** меньше 400 мс, расстояние по каждой оси меньше 100 физических
   пикселей, как в источнике. Дополнительно требуется то же сообщение, чат и аккаунт;
   время строго возрастает. Выделяется полный текст выбранного layout по Java UTF-16
   `text.length()`, без изменения текста/entities. Используются штатные `select()`
   и `finishOneTouchSelection()`, а не private поля движущихся handles.
3. **Выбор сообщений по фону:** штатный long-click/select путь `ChatActivity`
   и `RecyclerListView.startMultiselect`, с существующими лимитами, группами и
   проверками. На первом событии задаётся точка отсчёта свайпа. Отпускание/отмена
   передают `ACTION_CANCEL` в list, прекращая multiselect/autoscroll без клика/fling.
4. **Hover-нажатие:** прямые `dispatchGenericMotionEvent()` в `LaunchActivity`,
   общих `AlertDialog`, `BottomSheet`, `ActionBarPopupWindowLayout`, `FloatingToolbar` и основном окне
   нативного Doc Viewer. Нажатие и отпускание кнопки преобразуются в пару обычных
   touchscreen-событий в координатах **того же окна**, с интервалом event time 40 мс.
   Это не системная инъекция ввода. Обычный hover продолжается штатным способом.

Стандартный stylus tool type — 2; маска кнопки — `BUTTON_SECONDARY (2)` и
`BUTTON_STYLUS_PRIMARY (32)`. В root чата поддержаны Samsung actions 211–214
как DOWN/UP/MOVE/CANCEL на **копии** события. Исходное событие не модифицируется
новой нормализацией; все созданные MotionEvent освобождаются в `finally`.

Классы протокола и оконной маршрутизации:
`TMessagesProj/src/main/java/app/regram/input/{SPenState,SPenInput}.java`.
UI: `app/regram/settings/NativeSPenSettingsActivity.java`, EN/RU `regram_spen.xml`.

## Безопасность и отличия от глобальных хуков

- Мышь, eraser и обычные касания не запускают жесты пера. Multitouch отменяет
  активный жест; перехваченный отменённый stream поглощается до UP/CANCEL,
  чтобы не отдавать обычному view события без исходного DOWN. Прерванное перьевое
  выделение текста очищается; clear/finish отменяют scroll runnable и magnifier.
- Текст `noforwards`, protected peer, скрытое медиа, sponsored и preview не обходят
  ограничения. Проверка пустого selection menu и существующая политика групп
  сохранены. Штатные callback действий продолжают проверять копирование/пересылку.
- Для сообщений выделение запускается **на фоне**, а не на media/link/button
  внутри пузыря. Action bar, composer и строки других типов не превращаются
  в выбор ближайшего сообщения. Поиск, report/preview и embedded chat исключены.
  Это более консервативно, чем fallback исходного плагина.
- Hover-состояние хранится отдельно на каждом корневом view (три boolean и epoch, без
  ссылки на Activity). Явный parent focus owner поддерживает собственный
  non-focusable floating toolbar. Общий epoch меняется при потере фокуса окна или
  паузе Activity и сбрасывает старый arm даже после возвращения фокуса.
  Выход/вход hover, контакт, потеря фокуса, detach popup и
  пауза главной Activity сбрасывают arm. Блокировка/ожидание пароля/фон запрещают
  новый синтетический клик. После DOWN закрытое/скрытое/неактивное окно получает
  CANCEL вместо UP; непринятый DOWN также завершается CANCEL, а исключение
  сбрасывает hover-состояние в `finally`. FloatingToolbar не передаёт синтетический
  hover-tap в окна за ним через свой штатный fallback; обычные касания не изменены.
- Нет отложенных кликов и сохранённых MotionEvent, нет фонового сервера, JNI,
  Samsung SDK, Accessibility service или разрешения на поверхностные/system окна.
- Не перехватываются **все** Android View/Window: внешние приложения, IME, системные
  диалоги, произвольные окна SDK/плагинов, самостоятельные WindowManager overlays
  и немодифицированные `android.app.AlertDialog` (включая дочерние диалоги Doc Viewer)
  не гарантируют hover-click. Поддержаны перечисленные собственные окна клиента.
  Legacy actions 211–214 нормализуются в чате, а не глобально во всех Activity.
- Bluetooth **Air Actions**, удалённая камера, жесты вне экрана, письмо от руки и
  pressure drawing не реализованы: их не было в предоставленном исходнике.
- Новый private SharedPreferences `regram_spen` независим от plugin settings.
  Настройки/данные аккаунтов и общий plugin engine не удаляются и не мигрируются.
  Старый S Pen plugin нужно **отключить вручную**, иначе два обработчика могут
  дублировать действия. Полная совместимость с одновременно активными hooks
  сторонних плагинов не обещается.

## Проверки

Host-тесты: `tests/test_native_spen.py`, `tests/test_native_spen_input.py` и
`tests/test_native_spen_selection_cleanup.py`.
19 проверок реальных Java классов и source contracts: кнопки/границы hover,
contact/reset, monotonic double tap, account/chat/message isolation, Samsung
normalization, settings/переводы, разные окна, locked/background/focus gating,
клонирование, recycling при исключении, закрытие окна после DOWN и отсутствие
повторного клика. Реальные методы `clear()` и `finishOneTouchSelection()` также
проверены на отмену text autoscroll и закрытие magnifier, включая interruption
и отсутствие выбранного view. Android API doubles **не эмулируют input dispatcher**.

Полный suite: **370 passed, 31 skipped, 27 subtests passed**.
Debug/release Java/Kotlin и release resources успешно скомпилированы; APK и
устройство не проверялись. Логи:
`/tmp/regram-spen-tests.log`, `/tmp/regram-spen-full-tests.log`,
`/tmp/regram-spen-compile-final.log`.

## Обязательная проверка на Samsung

1. Включить master, отключить старый plugin. Проверить обычный палец/мышь и перо
   без кнопки; отдельно стандартные и Samsung legacy button events на устройстве.
2. Выделять слово и двигать endpoint по тексту, caption, ссылкам, rich layout,
   RTL/emoji, альбомам. Проверить double tap, копирование и реальные offsets.
3. Свайпать рядом с пузырями вверх/вниз с autoscroll; отпускать, отменять,
   добавлять второй палец, сворачивать приложение во время жеста. Не должно быть
   самопроизвольного клика, fling или продолжающегося автоскролла.
4. Проверить protected чаты/сообщения, spoiled media, несколько аккаунтов,
   preview/search/report и composer/action bar. Ограничения не обходятся.
5. Hover кнопкой: главное окно, меню, общие диалоги, BottomSheet, Doc Viewer.
   Проверить long hold, выход пера за границу, нажатие во время физического контакта,
   смену окна, закрытие окна на DOWN, background/passcode и возврат после unlock.
6. Проверить изменения каждой опции и отсутствие побочных эффектов при master off.

Компиляция, R8 предыдущих интеграций и host-тесты не подтверждают работу S Pen
на конкретной модели Samsung/прошивке.
