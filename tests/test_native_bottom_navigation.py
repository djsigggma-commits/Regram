"""Source regression guards; actual Android rendering requires device testing."""
from pathlib import Path

JAVA = Path(__file__).resolve().parents[1] / 'TMessagesProj/src/main/java'


def source(path):
    return (JAVA / path).read_text()


def test_folders_reserve_bottom_not_header_space():
    dialogs = source('org/telegram/ui/DialogsActivity.java')
    assert 'final float filterTabsVisibility = 0f;' in dialogs
    assert 'return calculateBaseListViewPaddingBottom() + getBottomFoldersHeight();' in dialogs
    assert '- getBottomFoldersPadding() - filterTabsView.getTop()' in dialogs
    assert '- floatingButtonPanOffset - getBottomFoldersHeight()' in dialogs
    assert 'fragmentView.getMeasuredHeight() - getBottomFoldersPadding()' in dialogs
    assert 'topPanelsHeight - s' in dialogs
    assert 'getBottomTabsVisibility()' in dialogs


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
    assert 'MainTabsUiHelper.applyTabsLayoutStyle(tabsView, MainTabsHelper.getTabsViewWidth())' in tabs
    icons = source('org/telegram/ui/Components/glass/GlassTabView.java')
    assert 'PLUGINS(0, R.drawable.plugins_filled, R.drawable.msg_plugins, -1, -1)' in icons


def test_native_replacements_do_not_load_or_hook_plugin_payloads():
    for path in ('org/telegram/ui/MainTabsActivity.java',
                 'org/telegram/ui/DialogsActivity.java',
                 'app/regram/plugins/ui/PluginsActivity.java'):
        text = source(path)
        for forbidden in ('InMemoryDexClassLoader', 'PluginsNavBridge',
                          'com.pessdes.bottomfolders', 'XposedBridge.hook',
                          'XC_MethodHook'):
            assert forbidden not in text
