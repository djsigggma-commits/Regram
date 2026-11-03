# «Функционал re:gram» — общий раздел настроек

**Настройки → Настройки re:gram → Функционал re:gram**

В текущих исходниках остаются:
1. **Встроенное оформление** — `NativeVisualSettingsActivity`;
2. **Быстрая настройка темы** — `QuickThemeActivity`;
3. **Termux Bridge** — `TermuxBridgeActivity`;
4. **Doc Viewer** — `NativeDocumentSettingsActivity`;
5. **S Pen** — `NativeSPenSettingsActivity`;
6. **Создатели и источники** — `RegramCreditsActivity`.

По запросу пользователя отмеченная на скриншоте группа
**«MD3extra, компактная панель и чипсы папок»** удалена. Удалён не только её пункт:
Weex-палитра и mapping, импорт шрифтов и font overrides, MD3 preset,
profile chips, Expressive navigation/FAB и Compact Bottom Nav больше не
подключены и не имеют своей реализации в исходниках.

Единственное сохранённое исключение — **папки-чипсы**. Новый путь:

**Функционал re:gram → Встроенное оформление → Папки-чипсы**

Все параметры папок доступны через `ChipFoldersActivity`: предпросмотр,
формы, цвета, анимации, размеры, промежутки и отступы. Долгое нажатие основной
папки тоже открывает этот экран, а не удалённую группу. Предыдущие preference
store/keys чипсов сохранены; существующие значения не сбрасываются.

Остальные экраны, штатные Material 3/iOS/Glass настройки панели и плеер клиента
не удаляются. Общий hub остаётся навигационным экраном без изменения preferences.
Регистрация поиска удалённого экрана заменена регистрацией `ChipFoldersActivity`.
Старые `appearance?r=nativeVisual` и `appearance?r=quickTheme` перенаправляются
в общий раздел; alias `nativePluginStyles` больше не перенаправляется к удалённой
группе. Другие перенаправления Termux/Doc Viewer/S Pen/credits сохранены.

Проверки: `tests/test_regram_features_hub.py`, `test_native_plugin_styles.py`,
`test_bottom_dock_visuals.py`, `test_native_style_performance.py` и общий suite.
Это host/source contracts, не Android UI validation. Изменения включены в
[release APK 1.4.5](BUILD_RELEASE_1.4.5.md); установленные старые APK продолжат
показывать прежнюю группу до обновления. На устройстве APK не проверен.
