"""Pinned upstream provenance and integration guards, not device validation."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "TMessagesProj/src/main"
SHA = "4771c553fee84618f8fa8dfc034c9e85317c2c72"


def source(path):
    return (MAIN / path).read_text()


def test_exact_actions_snapshot_and_pins():
    manifest = json.loads((ROOT / "docs/upstream/base-41526234112.json").read_text())
    assert manifest["actions_run"] == "https://github.com/exteraless/exteraless/actions/runs/41526234112"
    assert manifest["upstream_commit"] == SHA
    assert manifest["previous_commit"] == "8b7ec40ecada6ab3cfcc24e9f3d8ae262c02de63"
    assert manifest["archive_sha256"] == "e1a1888bcf862dba45aab01f623b667ee94675377f45f0d744069384e659ad02"
    assert manifest["base_version"] == "13.1.1"
    assert manifest["base_version_code"] == 7237
    assert manifest["api_layer"] == 232
    assert len(manifest["delta"]) == 322
    assert len(manifest["deletions"]) == 31
    restore = (ROOT / "tools/restore_submodules.sh").read_text()
    assert len(manifest["gitlinks"]) == 16
    for path, sha in manifest["gitlinks"].items():
        assert sha in restore
        if (ROOT / path / ".git").exists():
            actual = subprocess.check_output(["git", "-C", str(ROOT / path), "rev-parse", "HEAD"], text=True).strip()
            assert actual == sha, path
    for item in manifest["delta"]:
        path = ROOT / item["path"]
        assert path.is_file(), item["path"]
        if path.suffix in (".java", ".kt", ".py", ".xml", ".gradle", ".properties", ".attheme"):
            assert b"<<<<<<< " not in path.read_bytes(), item["path"]
    for item in manifest["deletions"]:
        assert not (ROOT / item["path"]).exists(), item["path"]


def test_bot_privacy_and_local_notification_setting_coexist():
    config = source("kotlin/app/regram/general/GeneralConfig.kt")
    for field in ("botPrivacyEnabled", "botPrivacyAlwaysAsk", "botPrivacyUserId", "disableNotificationDelay"):
        assert f"val {field} = addConfig(" in config
    assert 'ConfigItem.configTypeLong, 0L)' in config
    assert '"OEGeneralBotPrivacyEnabled", ConfigItem.configTypeBool, false)' in config
    assert '"OEGeneralBotPrivacyAlwaysAsk", ConfigItem.configTypeBool, false)' in config


def test_recorder_and_local_glyph_hooks_survive():
    media = source("java/org/telegram/messenger/MediaController.java")
    stop = media.split("public void stopRecording(final int send,", 1)[1].split("if (recordStartRunnable", 1)[0]
    assert "app.regram.glyph.GlyphController.getInstance().onRecordingStopped();" in stop
    assert "stopRecordingV2(send, notify, scheduleDate, once, payStars);" in stop
    assert "app.exteraless" not in media
    assert (MAIN / "java/org/telegram/utils/recorder/round/RoundVideoSession.java").is_file()
    app = source("java/org/telegram/messenger/ApplicationLoader.java")
    assert "CrashLog::reportOutOfMemory" in app
    assert "app.regram.debug.EnergyProfiler.resumeIfEnabled" in app
    assert "Theme.reloadMonetThemes()" in app


def test_rich_quote_highlights_follow_allleft_layout():
    cell = source("java/org/telegram/ui/Cells/ChatMessageCell.java")
    highlights = [line for line in cell.splitlines() if "quoteHighlight.paint.setColor(" in line]
    assert len(highlights) >= 3
    assert all("isOutOwnerForLayout()" in line for line in highlights)


def test_plugin_guard_install_sheet_compatibility_fields():
    shim = source("java/com/exteragram/messenger/plugins/ui/components/InstallPluginBottomSheet.java")
    for field in ("protected View button;", "protected LinearLayout bottomContainer;",
                  "protected boolean isUpdate;", "protected PluginInstallParams params;"):
        assert field in shim
    assert "public void applyButtonState(boolean animated)" in shim
    assert "public void onInstallClick()" in shim
    sheet = source("java/app/regram/plugins/ui/PluginInstallBottomSheet.java")
    assert "extends com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet" in sheet
    assert "protected void performInstall()" in sheet


def test_new_qr_screen_replaces_old_activity():
    assert not (MAIN / "java/org/telegram/ui/QrActivity.java").exists()
    assert (MAIN / "java/org/telegram/ui/QrShare/QrShareActivity.java").is_file()
    for name in ("ProfileActivity", "SettingsActivity"):
        text = source(f"java/org/telegram/ui/{name}.java")
        assert "QrShareActivity" in text
        assert "new QrActivity(" not in text
