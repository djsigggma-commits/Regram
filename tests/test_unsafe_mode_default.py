"""Default changes must preserve explicit opt-out and fail-closed startup."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_unsafe_mode_defaults_on_only_when_store_is_available():
    code = (ROOT / 'TMessagesProj/src/main/java/app/regram/plugins/PluginsController.java').read_text()
    getter = code.split('public boolean isUnsafeMode() {', 1)[1].split('public void setUnsafeMode', 1)[0]
    assert 'store.getBoolean(PluginsConstants.KEY_UNSAFE_MODE, true)' in getter
    assert 'if (store == null) {\n            return false;' in getter
    # Do not overwrite a saved false value or persist a forced default.
    assert '.putBoolean(' not in getter
    assert 'if (cached != null)' in getter


def test_user_can_still_disable_unsafe_mode():
    code = (ROOT / 'TMessagesProj/src/main/java/app/regram/plugins/PluginsController.java').read_text()
    setter = code.split('public void setUnsafeMode(boolean value) {', 1)[1].split('private volatile Boolean unsafeMode', 1)[0]
    assert 'unsafeMode = value;' in setter
    assert 'putBoolean(PluginsConstants.KEY_UNSAFE_MODE, value)' in setter
    assert 'PluginSinkGate.refuseFromPlugin("setUnsafeMode", false)' in setter
    assert 'PythonPluginsEngine.getInstance().setUnsafeMode(value)' in setter
