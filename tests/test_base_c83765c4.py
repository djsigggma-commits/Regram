"""Guards for the main snapshot after 4771c553; host tests, not Android validation."""
import ast
import contextlib
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import types

import pytest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "TMessagesProj/src/main"
PYTHON = MAIN / "python"


def source(path):
    return (MAIN / path).read_text()


def load_host_module(name, filename, monkeypatch):
    spec = importlib.util.spec_from_file_location(name, PYTHON / filename)
    module = importlib.util.module_from_spec(spec)
    monkeypatch.setitem(sys.modules, name, module)
    spec.loader.exec_module(module)
    return module


def test_pinned_main_snapshot_and_unchanged_native_dependencies():
    manifest = json.loads((ROOT / "docs/upstream/base-c83765c4.json").read_text())
    previous = json.loads((ROOT / "docs/upstream/base-41526234112.json").read_text())
    assert manifest["upstream_commit"] == "c83765c4151d50ba514325ddf0a3262769729dc6"
    assert manifest["previous_commit"] == previous["upstream_commit"]
    assert manifest["gitlinks"] == previous["gitlinks"]
    assert manifest["base_version"] == "13.1.1"
    assert manifest["base_version_code"] == 7237
    assert manifest["api_layer"] == 232
    assert len(manifest["delta"]) == 23
    assert manifest["deletions"] == []
    for item in manifest["delta"]:
        data = (ROOT / item["path"]).read_bytes()
        assert b"<<<<<<< " not in data, item["path"]
        assert b"app.exteraless" not in data, item["path"]
    doc = manifest["documentation"]
    assert hashlib.sha256((ROOT / doc["path"]).read_bytes()).hexdigest() == doc["sha256"]
    properties = (ROOT / "gradle.properties").read_text()
    assert "APP_VERSION_NAME=13.1.1" in properties
    assert "APP_PACKAGE=app.regram.android" in properties


@pytest.mark.parametrize("owner", [None, "example_plugin"])
def test_callback_error_keeps_original_exception_and_exits_mark(monkeypatch, owner):
    module = load_host_module("regram_android_utils_update_test", "android_utils.py", monkeypatch)
    events = []

    @contextlib.contextmanager
    def mark(plugin_id):
        events.append(("enter", plugin_id))
        try:
            yield
        finally:
            events.append(("exit", plugin_id))

    loader = types.SimpleNamespace(owner_of_function=lambda fn: owner, java_runtime_mark=mark)
    monkeypatch.setattr(module, "_internal", lambda name: loader)
    error = ValueError("original callback error")
    with pytest.raises(ValueError) as caught:
        with module._plugin_mark(lambda: None):
            raise error
    assert caught.value is error
    assert events == ([] if owner is None else [("enter", owner), ("exit", owner)])
    logs = []
    monkeypatch.setattr(module, "log", logs.append)

    def callback():
        raise error

    assert module.safe_call(callback) is None
    assert len(logs) == 1
    assert "original callback error" in logs[0]
    assert "generator didn't stop" not in logs[0]


@pytest.mark.parametrize("before,priority", [(True, 0), (False, 9)])
def test_handler_info_preserves_callback_and_merges_explicit_filters(monkeypatch, before, priority):
    module = load_host_module("regram_intents_update_test", "intents.py", monkeypatch)
    package = types.ModuleType("extera_utils")
    package.plugin_loader = types.SimpleNamespace(current_plugin_id=lambda: "p")
    monkeypatch.setitem(sys.modules, "extera_utils", package)
    calls = []

    def register(*args):
        calls.append(args)
        return "handler-1"

    monkeypatch.setattr(module, "_plugin_services", lambda: types.SimpleNamespace(registerIntentHandler=register))
    manager = module.IntentsManager
    callback = lambda host: host == "new"
    info = manager.HandlerInfo(callback, priority=4, scheme="tg", host="old")
    method = manager.new_global_before_handler if before else manager.new_global_after_handler
    handle = method(info, priority=priority, host="new")
    assert handle.plugin_id == "p"
    plugin_id, is_before, raw_filters, actual_priority, wrapped = calls[0]
    assert plugin_id == "p"
    assert is_before is before
    assert json.loads(raw_filters) == {"schemes": ["tg"], "hosts": ["new"]}
    assert actual_priority == (priority or 4)
    assert wrapped({"host": "new"}) is (True if before else None)
    assert info.filters == {"scheme": "tg", "host": "old"}


@pytest.mark.parametrize("value", [2, 0.25, "label", True])
def test_dual_java_member_is_readable_callable_and_writable(value):
    # Exercise the actual descriptor without importing the Android plugin runtime.
    tree = ast.parse((PYTHON / "extera_utils/plugin_loader.py").read_text())
    names = {"_CallableInt", "_CallableFloat", "_CallableStr", "_DualMember"}
    nodes = [node for node in tree.body if isinstance(node, ast.ClassDef) and node.name in names]
    field = {"value": value}
    namespace = {
        "_get_dual_field": lambda ref, obj: ref["value"],
        "_set_dual_field": lambda ref, obj, new: ref.__setitem__("value", new),
    }
    exec(compile(ast.Module(body=nodes, type_ignores=[]), "dual_member_test", "exec"), namespace)

    class Page:
        def progress(self, delta):
            return delta + 10

    original_method = Page.progress
    Page.progress = namespace["_DualMember"]("progress", field, original_method)
    page = Page()
    assert page.progress == value
    assert page.progress(3) == 13
    assert Page.progress is original_method
    page.progress = 5
    assert field["value"] == 5
    assert page.progress == 5


def test_download_toggle_supports_deep_links_and_refresh():
    navigation = source("java/app/regram/settings/OpenExteraAppNavigationActivity.java")
    assert 'UItem.asCheck(ID_ALWAYS_DOWNLOADS, getString(R.string.AlwaysShowDownloadIcon))' in navigation
    handler = navigation.split("if (id == ID_ALWAYS_DOWNLOADS)", 1)[1].split("return;", 1)[0]
    assert "downloads.setConfigBool(!downloads.Bool())" in handler
    assert "NotificationCenter.onDownloadingFilesChanged" in handler
    assert '"immersiveDrawerAnimation", "AlwaysShowDownloadIcon"' in navigation
    helper = source("java/tw/nekomimi/nekogram/helpers/SettingsHelper.java")
    assert 'NAGRAM_ROWS.put("AlwaysShowDownloadIcon", "navigation")' in helper


def test_lottie_deletion_is_restricted_to_canonical_telegram_paths():
    text = source("java/org/telegram/ui/Components/RLottieDrawable.java")
    assert text.count("deleteBrokenCacheFile(file);") == 2
    assert text.count("file.delete();") == 1
    guard = text.split("private static void deleteBrokenCacheFile", 1)[1]
    assert "file.getCanonicalPath()" in guard
    assert "path.startsWith(dir.getCanonicalPath() + File.separator)" in guard
    assert "FileLoader.MEDIA_DIR_CACHE" in guard


def test_crash_reports_tag_hooks_instead_of_dropping_them():
    util = source("java/tw/nekomimi/nekogram/utils/AndroidUtil.java")
    report_filter = util.split("boolean shouldReportCrashToCrashlytics", 1)[1].split("boolean hasHookFrames", 1)[0]
    assert "containsHookSignature" not in report_filter
    assert "containsHookSignature" in util.split("boolean hasHookFrames", 1)[1]
    crash = source("java/app/regram/crash/CrashLog.java")
    assert 'setCustomKey("hooked", AndroidUtil.hasHookFrames(error))' in crash
    assert 'setCustomKey("plugins", ids.toString())' in crash
    assert "CrashLog.putCrashlyticsKeys(error)" in source("java/org/telegram/messenger/ApplicationLoader.java")


def test_round_video_uses_public_encoder_renderer_hooks():
    camera = source("java/org/telegram/ui/Components/InstantCameraView.java")
    assert "public static class FrameSnapshot" in camera
    assert "public class EncoderRenderer" in camera
    assert "renderer.onEncoderSurfaceCreated(videoWidth, videoHeight)" in camera
    assert "renderer.onDrawEncoderFrame(alphaDt, frameSnapshot)" in camera
    assert camera.count("renderer.onEncoderSurfaceDestroyed();") == 2


def test_plugin_empty_settings_and_android_null_guards():
    loader = (PYTHON / "extera_utils/plugin_loader.py").read_text()
    empty_settings = loader.split("def get_settings_json(", 1)[1].split("pending = replace(", 1)[0]
    assert 'return "[]"' in empty_settings
    settings = source("java/app/regram/plugins/ui/PluginSettingsActivity.java")
    assert "loaded ? R.string.PluginsNoSettings : R.string.PluginsNotLoaded" in settings
    for locale in ("values", "values-ru-rRU"):
        assert 'name="PluginsNoSettings"' in source(f"res/{locale}/strings_oe_plugins.xml")
    assert "public void updateRowsIds()" in source("java/org/telegram/ui/ProfileActivity.java")
    assert "threadMessageObject.messageOwner.replies != null && obj.isReply()" in source("java/org/telegram/ui/ChatActivity.java")
    assert "contextView == null" in source("java/org/telegram/ui/Cells/TextSelectionHelper.java")
    for name in ("CircularProgressDrawable", "RadialProgressView"):
        assert "Theme_Material3_DayNight_NoActionBar" in source(f"java/org/telegram/ui/Components/{name}.java")
    assert "android.permission.MANAGE_EXTERNAL_STORAGE" in source("AndroidManifest.xml")
