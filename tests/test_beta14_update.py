from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'TMessagesProj/src/main'


def test_beta14_version_and_chaquopy_revision():
    props = (ROOT / 'gradle.properties').read_text()
    assert 'APP_VERSION_NAME=12.10.6\n' in props
    assert 'APP_VERSION_CODE=7112\n' in props
    assert 'REGRAM_VERSION=1.0.8\n' in props
    restore = (ROOT / 'tools/restore_submodules.sh').read_text()
    assert 'v12.10.6-beta14' in restore
    assert 'c217deac1c2df5ab879b9844d911e1c90e59a4c1' in restore


def test_new_kotlin_code_uses_fork_namespace_without_duplicate_configs():
    kotlin = MAIN / 'kotlin/app'
    assert not list((kotlin / 'exteraless').rglob('*.kt'))
    for name in ('LinkCleaner', 'UrlCleaner', 'AdGuardRule'):
        source = (kotlin / 'regram/links' / f'{name}.kt').read_text()
        assert 'package app.regram.links' in source
    config = (kotlin / 'regram/chats/ChatsConfig.kt').read_text()
    assert 'val stripTrackingOnOpen = addConfig(' in config
    assert 'val stripTrackingOnPaste = addConfig(' in config
    assert 'val hideChannelSearchButton = addConfig(' in config


def test_release_branding_and_sticker_rows_use_current_symbols():
    java = MAIN / 'java/app/regram'
    icons = (java / 'appicons/AppIcons.java').read_text()
    updater = (java / 'updater/UpdaterSheet.java').read_text()
    replacement = (java / 'plugins/xposed/PyMethodReplacement.java').read_text()
    chats = (java / 'settings/OpenExteraChatsActivity.java').read_text()
    assert 'return LauncherIcon.REGRAM;' in icons
    assert 'LauncherIcon.EXTERALESS' not in icons
    assert 'R.drawable.regram_icon' in updater
    assert 'R.drawable.exteraless_icon' not in updater
    assert 'import org.telegram.messenger.FileLog;' in replacement
    assert 'hideTimeOnStickersRow' not in chats
    assert 'stickerTimeRow = addRow("stickerTime", "hideTimeOnStickers")' in chats


def test_fork_search_options_survive_settings_reorganization():
    source = (MAIN / 'java/app/regram/settings/OpenExteraChatsActivity.java').read_text()
    for field in ('regramKeepPeerSearch', 'regramHideHashtagSuggestions',
                  'regramDisableGeneralTopicSwipe', 'regramSortAlbumsBySize'):
        assert f'addRow(NekoConfig.{field}.key)' in source
