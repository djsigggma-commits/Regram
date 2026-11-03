"""Native S Pen protocol host checks; these do not emulate a Samsung device."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def run_java(tmp_path, body):
    if not shutil.which('javac') or not shutil.which('java'):
        pytest.skip('JDK unavailable')
    harness = tmp_path / 'PenTest.java'
    harness.write_text('import app.regram.input.SPenState; public class PenTest { public static void main(String[] args) {' + body + '}}')
    subprocess.run(['javac', '-d', str(tmp_path), str(JAVA / 'app/regram/input/SPenState.java'), str(harness)], check=True, capture_output=True, text=True)
    subprocess.run(['java', '-ea', '-cp', str(tmp_path), 'PenTest'], check=True, capture_output=True, text=True)


@pytest.mark.parametrize('button', [2, 32, 34])
def test_hover_release_once_and_only_after_press(tmp_path, button):
    run_java(tmp_path, f'''
        SPenState.Hover h = new SPenState.Hover();
        assert !h.step(12, 0, {button});
        assert !h.step(11, {button}, {button});
        assert !h.step(7, {button}, 0);
        assert h.step(12, 0, {button});
        assert !h.step(12, 0, {button});
        assert !h.step(7, 0, 0);
    ''')


def test_hover_legacy_state_transitions_and_zero_action_button(tmp_path):
    run_java(tmp_path, '''
        SPenState.Hover h = new SPenState.Hover();
        assert !h.step(7, 32, 0);
        assert h.step(7, 0, 0);
        assert !h.step(11, 32, 0);
        assert h.step(12, 0, 0);
    ''')


@pytest.mark.parametrize('boundary', [9, 10, 3])
def test_hover_does_not_click_across_boundaries(tmp_path, boundary):
    run_java(tmp_path, f'''
        SPenState.Hover h = new SPenState.Hover();
        assert !h.step(11, 32, 32);
        assert !h.step({boundary}, 32, 0);
        assert !h.step(12, 0, 32);
    ''')


def test_contacts_focus_reset_and_other_buttons_do_not_click(tmp_path):
    run_java(tmp_path, '''
        SPenState.Hover h = new SPenState.Hover();
        assert !h.step(11, 64, 64);
        assert !h.step(12, 0, 64);
        h.contact(0, 0);
        assert !h.step(11, 32, 32);
        assert !h.step(12, 0, 32);
        h.contact(1, 0);
        assert !h.step(11, 32, 32);
        h.reset();
        assert !h.step(12, 0, 32);
        assert !h.step(11, 32, 32);
        h.contact(211, 32);
        h.contact(212, 32);
        assert !h.step(12, 0, 32);
    ''')


@pytest.mark.parametrize('first,second,expected', [(100, 499, True), (100, 500, False), (100, 501, False), (100, 100, False), (100, 99, False)])
def test_double_tap_monotonic_time(tmp_path, first, second, expected):
    run_java(tmp_path, f'''
        SPenState.DoubleTap d = new SPenState.DoubleTap();
        assert !d.down("0:10:1", {first}, 0, 0);
        assert d.down("0:10:1", {second}, 0, 0) == {str(expected).lower()};
    ''')


def test_double_tap_identity_distance_reset_and_no_triple_reuse(tmp_path):
    run_java(tmp_path, '''
        SPenState.DoubleTap d = new SPenState.DoubleTap();
        assert !d.down("0:10:1", 100, 0, 0);
        assert !d.down("1:10:1", 200, 0, 0);
        assert !d.down("1:11:1", 250, 0, 0);
        assert !d.down("1:11:2", 300, 0, 0);
        assert !d.down("1:11:2", 350, 100, 0);
        assert d.down("1:11:2", 400, 100, 0);
        assert !d.down("1:11:2", 450, 100, 0);
        d.reset();
        assert !d.down("1:11:2", 500, 100, 0);
        assert !d.down(null, 550, 100, 0);
    ''')


def test_samsung_normalization_leaves_other_actions_intact(tmp_path):
    run_java(tmp_path, '''
        for (int i=211; i<=214; i++) assert SPenState.normalize(i) == i-211;
        for (int i : new int[]{0,1,2,3,5,6,7,9,10,11,12,210,215}) assert SPenState.normalize(i) == i;
    ''')


def test_native_routes_security_and_no_plugin_loading():
    source = (JAVA / 'app/regram/input/SPenInput.java').read_text()
    assert 'getBoolean("enabled", false)' in source
    assert 'SharedConfig.appLocked' in source and 'SharedConfig.isWaitingForPasscodeEnter' in source
    assert 'event.getPointerCount() == 1' in source and 'TOOL_TYPE_STYLUS' in source
    assert 'MotionEvent.obtain(event)' in source and 'copy.setAction(normal)' in source
    assert 'SOURCE_TOUCHSCREEN' in source and 'now + 40' in source
    assert 'down.recycle()' in source and 'up.recycle()' in source
    for path in (JAVA / 'app/regram/input').glob('*.java'):
        text = path.read_text()
        for forbidden in ('java.lang.reflect', 'MethodHook', 'get_private_field', 'DexClassLoader', 'PythonPluginsEngine', 'PluginsController'):
            assert forbidden not in text
    for name in ('org/telegram/ui/LaunchActivity', 'org/telegram/ui/ActionBar/AlertDialog', 'org/telegram/ui/ActionBar/BottomSheet', 'org/telegram/ui/ActionBar/ActionBarPopupWindow', 'org/telegram/ui/ActionBar/FloatingToolbar', 'app/regram/documents/DocumentViewerDialog'):
        text = (JAVA / (name + '.java')).read_text()
        assert 'SPenInput.hover(' in text and 'SPenInput.contact(' in text and 'SPenInput.resetHover(' in text
    cell = (JAVA / 'org/telegram/ui/Cells/ChatMessageCell.java').read_text()
    region = cell[cell.index('public boolean startTextSelectionForPen'):cell.index('private void updateSelectionTextPosition')]
    for guard in ('messageOwner.noforwards', 'needDrawBluredPreview()', 'isPeerNoForwards(', 'isMenuEmpty()', 'delegate.hasSelectedMessages()'):
        assert guard in region
    assert 'drawSelectionBackground =' not in region
    chat = (JAVA / 'org/telegram/ui/ChatActivity.java').read_text()
    assert 'currentAccount + ":" + message.getDialogId() + ":" + message.getId()' in chat
    assert 'onItemLongClickListener.onItemClick' in chat and 'chatListView.cancelClickRunnables(false)' in chat
    assert 'if (inside) return false' in chat and 'getGlobalVisibleRect' in chat
    assert 'resetPenInput();\n        super.onPause();' in chat
    assert 'penGesture = -1' in chat
    helper = (JAVA / 'org/telegram/ui/Cells/TextSelectionHelper.java').read_text()
    assert 'select(selectedView, 0, text.length())' in helper
    assert 'finishOneTouchSelection();' in helper


def test_settings_and_translations():
    en = ET.parse(ROOT / 'TMessagesProj/src/main/res/values/regram_spen.xml').getroot()
    ru = ET.parse(ROOT / 'TMessagesProj/src/main/res/values-ru/regram_spen.xml').getroot()
    assert {s.get('name') for s in en if s.tag == 'string'} == {s.get('name') for s in ru}
    settings = (JAVA / 'app/regram/settings/NativeSPenSettingsActivity.java').read_text()
    for key in ('enabled', 'instant_selection', 'double_tap_select_all', 'message_selection', 'hover_click_menu'):
        assert '"' + key + '"' in settings
    assert 'presentFragment(new NativeSPenSettingsActivity())' in (JAVA / 'app/regram/settings/RegramFeaturesActivity.java').read_text()
