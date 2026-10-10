"""Source regression guards; actual Android rendering requires device testing."""
from pathlib import Path

JAVA = Path(__file__).resolve().parents[1] / 'TMessagesProj/src/main/java'


def source(path):
    return (JAVA / path).read_text()


def test_folders_reserve_space_at_selected_edge():
    dialogs = source('org/telegram/ui/DialogsActivity.java')
    assert 'final float filterTabsVisibility = AppearanceConfig.foldersAtTop()' in dialogs
    assert '? getFilterTabsVisibilityFactor(true) : 0f;' in dialogs
    assert 'return AppearanceConfig.foldersAtTop() ? 0 : getVisibleFoldersHeight();' in dialogs
    assert 'return AppearanceConfig.foldersAtTop() ? getVisibleFoldersHeight() : 0;' in dialogs
    assert 'filterTabsView.setTranslationY(totalOffset - searchOffset);' in dialogs
    assert 'totalOffset - searchOffset + getTopFoldersHeight()' in dialogs
    assert 'h += getTopFoldersHeight();' in dialogs
    assert 'return calculateBaseListViewPaddingBottom() + getBottomFoldersHeight();' in dialogs
    assert '- getBottomFoldersPadding() - filterTabsView.getTop()' in dialogs
    assert '- floatingButtonPanOffset - getBottomFoldersHeight()' in dialogs
    assert 'fragmentView.getMeasuredHeight() - getBottomFoldersPadding()' in dialogs
    assert 'topPanelsHeight + getTopFoldersHeight() - s' in dialogs
    assert 'getBottomTabsVisibility()' in dialogs


def test_folder_position_setting_is_persisted_and_rebuilds_views():
    root = JAVA.parents[2]
    config = (root / 'src/main/kotlin/app/regram/appearance/AppearanceConfig.kt').read_text()
    assert 'addConfig("OEAppearanceFoldersAtTop", ConfigItem.configTypeBool, false)' in config
    assert 'fun foldersAtTop(): Boolean' in config
    settings = source('app/regram/settings/OpenExteraAppearanceActivity.java')
    assert 'foldersAtTopRow = addRow("foldersAtTop");' in settings
    click = settings.split('} else if (position == foldersAtTopRow) {', 1)[1].split('} else if', 1)[0]
    assert 'setConfigBool(!AppearanceConfig.foldersAtTop())' in click
    assert 'rebuildAll();' in click
    assert 'R.string.OEAppearanceFoldersAtTop' in settings
    for locale in ('values', 'values-ru'):
        assert 'name="OEAppearanceFoldersAtTop"' in (root / f'src/main/res/{locale}/regram_folders.xml').read_text()


def test_plugins_page_is_a_real_pager_page():
    page = source('app/regram/plugins/ui/PluginsActivity.java')
    assert 'implements org.telegram.ui.MainTabsActivity.TabFragmentDelegate' in page
    assert 'this(false);' in page  # Settings entry keeps the ordinary back button.
    assert 'if (!hasMainTabs)' in page
    assert 'public boolean canParentTabsSlide(' in page
    assert 'listView.smoothScrollToPosition(0)' in page
    assert 'bottomInset + tabsPadding + miniPlayerPadding' in page
    tabs = source('org/telegram/ui/MainTabsActivity.java')
    assert 'INDEX_PROFILE, INDEX_PLUGINS};' in tabs
    assert 'for (int pos = 1; pos < TABS_COUNT; pos++)' in tabs
    assert 'MainTabsUiHelper.applyTabsLayoutStyle(tabsView, dp(MainTabsHelper.getTabsViewWidth()))' in tabs
    icons = source('org/telegram/ui/Components/glass/GlassTabView.java')
    assert 'PLUGINS(0, R.drawable.plugins_filled, R.drawable.msg_plugins, -1, -1)' in icons


def test_bottom_navigation_max_width_uses_pixels():
    helper = source('app/regram/appearance/MainTabsUiHelper.java')
    assert 'int legacyMaxWidthPx' in helper
    assert 'layout.setMaxWidth(isMaterial3NavigationBar() || isIosNavigationBar() ? 0 : legacyMaxWidthPx)' in helper
    tabs = source('org/telegram/ui/MainTabsActivity.java')
    # LayoutHelper takes dp, but View measurement / setMaxWidth takes pixels.
    assert 'LayoutHelper.createFrame(MainTabsUiHelper.getTabsViewWidth(),' in tabs
    assert 'applyTabsLayoutStyle(tabsView, dp(MainTabsHelper.getTabsViewWidth()))' in tabs
    assert 'applyTabsLayoutStyle(tabsView, MainTabsHelper.getTabsViewWidth())' not in tabs
    statistics = source('org/telegram/ui/StatisticActivity.java')
    assert 'applyTabsLayoutStyle(tabsView, dp(328 + DialogsActivity.MAIN_TABS_MARGIN * 2))' in statistics


def test_native_replacements_do_not_load_or_hook_plugin_payloads():
    for path in ('org/telegram/ui/MainTabsActivity.java',
                 'org/telegram/ui/DialogsActivity.java',
                 'app/regram/plugins/ui/PluginsActivity.java'):
        text = source(path)
        for forbidden in ('InMemoryDexClassLoader', 'PluginsNavBridge',
                          'com.pessdes.bottomfolders', 'XposedBridge.hook',
                          'XC_MethodHook'):
            assert forbidden not in text
