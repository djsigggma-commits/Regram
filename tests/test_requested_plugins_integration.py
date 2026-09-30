"""Regression guards for the reviewed native replacements for external plugins."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'TMessagesProj/src/main/java'


def source(relative):
    return (ROOT / relative).read_text(encoding='utf-8')


def test_bottom_plugin_tab_is_not_a_pager_page():
    tabs = source('org/telegram/ui/MainTabsActivity.java')
    assert 'TabAnimation.PLUGINS, R.string.OpenExteraPlugins' in tabs
    assert 'new app.regram.plugins.ui.PluginsActivity()' in tabs
    assert 'tabsView.addTabToIgnoreClick(pluginsTab)' in tabs
    assert 'tabsView.addView(pluginsTab)' in tabs
    assert 'tabs[INDEX_PLUGINS]' not in tabs


def test_auto_read_opt_in_and_preserves_other_unread_messages():
    feature = source('app/regram/ui/ChannelPostAutoRead.java')
    assert 'prefs(account).getBoolean(Long.toString(dialogId), false)' in feature
    assert 'chat == null || !chat.megagroup' in feature
    assert 'message.fwd_from.from_id.channel_id == message.from_id.channel_id' in feature
    assert 'withComments.contains(object.getDialogId())' in feature
    assert 'batchCounts.get(object.getDialogId()) != 1' in feature
    assert 'dialog.unread_count != 1' in feature
    assert 'controller.markDialogAsRead(' in feature
    assert 'ChannelPostAutoRead.filterNotifications(currentAccount, messageObjects)' in source(
        'org/telegram/messenger/NotificationsController.java')

