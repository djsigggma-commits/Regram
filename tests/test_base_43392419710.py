"""13.2.2 provenance and merge guards; these are not Android runtime tests."""
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "TMessagesProj/src/main"
JAVA = MAIN / "java"


def text(path):
    return (ROOT / path).read_text()


def test_pinned_actions_snapshot():
    manifest = json.loads(text("docs/upstream/base-43392419710.json"))
    assert manifest["actions_run"].endswith("/43392419710")
    assert manifest["upstream_commit"] == "31a35e4683fb420ee782cdd32f36aea812bf756c"
    assert manifest["previous_commit"] == "c83765c4151d50ba514325ddf0a3262769729dc6"
    assert (manifest["base_version"], manifest["base_version_code"], manifest["api_layer"]) == ("13.2.2", 7303, 233)
    assert len(manifest["delta"]) == 1147
    assert len(manifest["deletions"]) == 55
    for item in manifest["delta"]:
        data = (ROOT / item["path"]).read_bytes()
        assert hashlib.sha256(data).hexdigest() == item["applied_sha256"], item["path"]
        assert not re.search(rb"^<<<<<<< |^>>>>>>> ", data, re.M), item["path"]
    for item in manifest["deletions"]:
        assert not (ROOT / item["path"]).exists(), item["path"]
    assert len(manifest["gitlinks"]) == 18
    pins = dict(re.findall(r"^fetch_one\s+(\S+)\s+\S+\s+([0-9a-f]{40})", text("tools/restore_submodules.sh"), re.M))
    assert pins == manifest["gitlinks"]
    restore = text("tools/restore_submodules.sh")
    assert "submodule update --init --depth 1 --" in restore
    assert "packages/ios/Sources/CQuickJS/quickjs" in restore


def test_widget_service_remains_isolated_and_native_library_separate():
    android = "{http://schemas.android.com/apk/res/android}"
    manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
    service = next(s for s in manifest.findall("application/service") if s.get(android + "name") == "org.telegram.utils.twidget.WidgetEngineService")
    assert service.get(android + "isolatedProcess") == "true"
    assert service.get(android + "exported") == "false"
    assert service.get(android + "process") == ":widgets"
    cmake = text("TMessagesProj/jni/CMakeLists.txt")
    assert "add_library(twidget SHARED" in cmake
    assert "twidget_client_sources(${NATIVE_LIB})" in cmake
    assert "add_library(prisma STATIC IMPORTED)" not in cmake
    gradle = text("TMessagesProj/build.gradle")
    assert "aidl true" in gradle
    assert "'DEBUG_PRIVATE_VERSION', 'false'" in gradle
    assert (MAIN / "aidl/org/telegram/utils/twidget/IWidgetEngine.aidl").is_file()


def test_allleft_follows_extracted_presentation_helpers_but_not_authorization():
    cells = JAVA / "org/telegram/ui/Cells"
    for name in ("Audio", "BotButtons", "Contact", "FactCheck", "LinkPreview", "Location", "PhoneCall", "Poll", "TransitionParams"):
        source = (cells / f"ChatMessage{name}.java").read_text()
        assert ".isOutOwnerForLayout()" in source, name
    poll = (cells / "ChatMessagePoll.java").read_text()
    assert "!cell.currentMessageObject.isOutOwner() && cell.currentMessageObject.getDialogId() >= 0 && !media.todo.others_can_complete" in poll
    assert "quoteHighlight.paint.setColor" in poll
    assert "cell.currentMessageObject.isOutOwnerForLayout() && !cell.currentMessageObject.preview" in poll
    model = (JAVA / "org/telegram/messenger/MessageObject.java").read_text()
    assert "compactIn = generatedWithCompactMode && !isOutOwnerForLayout()" in model
    assert "compactCode = isCompactMode() && !isOutOwnerForLayout()" in model


def test_single_profile_title_keeps_local_badge_lifecycle():
    profile = (JAVA / "org/telegram/ui/ProfileActivity.java").read_text()
    assert "private SimpleTextView nameTextView;" in profile
    assert "nameTextView[a]" not in profile
    clear = profile.index("SupporterBadges.clear(nameTextView)")
    decorate = profile.index("SupporterBadges.decorate(nameTextView, user.id, currentAccount)")
    assert clear < decorate
    assert "addWeakObserver(this, NotificationCenter.pluginMenuItemsUpdated)" in profile


def test_new_and_local_native_transports_coexist():
    cmake = text("TMessagesProj/jni/CMakeLists.txt")
    assert "twidget" in cmake
    transport = (JAVA / "org/telegram/tgnet/TelegramWebSocketTransport.java").read_text()
    assert "createSocketPair()" in transport
    assert "DEFAULT_CLOUDFLARE = true" in transport
    assert "webSocket" in text("TMessagesProj/jni/tgnet/ConnectionSocket.cpp") or "WebSocket" in text("TMessagesProj/jni/tgnet/ConnectionSocket.cpp")
    native = text("TMessagesProj/jni/tgnet/ConnectionsManager.cpp")
    assert "MAX_CAPTCHA_CHECKS = 5" in native
    assert "isMessageIdTimeValid(messageId)" in native
    assert (JAVA / "org/telegram/utils/pip").is_dir()
    assert not (JAVA / "org/telegram/messenger/pip/PipSource.java").exists()
