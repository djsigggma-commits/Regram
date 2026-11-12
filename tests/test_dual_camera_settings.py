"""Opt-in round-video camera prewarming. Host tests do not prove hardware concurrency."""
from pathlib import Path
import xml.etree.ElementTree as ET

from test_native_visual_settings import compile_run, method

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def policy_sources():
    return {
        'app/regram/camera/DualCameraSettings.java': (JAVA / 'app/regram/camera/DualCameraSettings.java').read_text(),
        'android/content/Context.java': 'package android.content; public class Context {}',
        'app/regram/camera/CameraXSession.java': '''package app.regram.camera;
public class CameraXSession {
 public static boolean supported; public static int queries;
 public static boolean isSeamlessSwitchingAvailable(android.content.Context c){queries++;return supported;}
}
''',
        'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger;
import java.util.*;
public class MessagesController {
 public static final Prefs prefs=new Prefs();
 public static Prefs getGlobalMainSettings(){return prefs;}
 public static class Prefs {
  public Map<String,Boolean> values=new HashMap<>();
  public boolean getBoolean(String key,boolean def){return values.getOrDefault(key,def);}
  public Prefs edit(){return this;}
  public Prefs putBoolean(String key,boolean value){values.put(key,value);return this;}
  public void apply(){}
 }
}
''',
    }


def test_real_opt_in_policy_preserves_legacy_preference_and_gates_hardware(tmp_path):
    compile_run(tmp_path, {**policy_sources(), 'PolicyTest.java': '''
import app.regram.camera.*;
import android.content.Context;
import org.telegram.messenger.MessagesController;
public class PolicyTest {
 public static void main(String[] args){
  Context c=new Context(); CameraXSession.supported=true;
  assert !DualCameraSettings.isEnabled();
  assert !DualCameraSettings.canUse(c); assert CameraXSession.queries==0;
  assert DualCameraSettings.isSupported(c);
  DualCameraSettings.setEnabled(true);
  assert MessagesController.prefs.getBoolean("rounddual_available",false);
  assert DualCameraSettings.canUse(c);
  CameraXSession.supported=false;
  assert !DualCameraSettings.canUse(c);
  assert DualCameraSettings.isEnabled(); // capability checks never erase the user's preference
  assert !DualCameraSettings.canUse(null);
  CameraXSession.supported=true; assert DualCameraSettings.canUse(c);
  DualCameraSettings.setEnabled(false); assert !DualCameraSettings.canUse(c);
  MessagesController.prefs.values.put("rounddual_available",true); // old seamless-switching opt-in
  assert DualCameraSettings.isEnabled() && DualCameraSettings.canUse(c);
 }
}
'''}, 'PolicyTest')


def test_real_toggle_cannot_enable_on_unsupported_device_but_can_disable(tmp_path):
    source = (JAVA / 'app/regram/settings/DualCameraSettingsActivity.java').read_text()
    click = method(source, '    @Override protected void onItemClick(').replace('@Override ', '')
    compile_run(tmp_path, {**policy_sources(), 'ToggleTest.java': '''
import app.regram.camera.DualCameraSettings;
class View {}
public class ToggleTest {
 int enabledRow=1; boolean available;
 static class Adapter {int updates; void notifyDataSetChanged(){updates++;}}
 Adapter listAdapter=new Adapter();
 boolean supported(){return available;}
''' + click + '''
 public static void main(String[] args){
  ToggleTest t=new ToggleTest();
  t.onItemClick(new View(),1,0,0); assert !DualCameraSettings.isEnabled();
  assert t.listAdapter.updates==0;
  t.available=true; t.onItemClick(new View(),1,0,0);
  assert DualCameraSettings.isEnabled() && t.listAdapter.updates==1;
  t.available=false; t.onItemClick(new View(),1,0,0);
  assert !DualCameraSettings.isEnabled() && t.listAdapter.updates==2;
  t.available=true; t.onItemClick(new View(),2,0,0); assert !DualCameraSettings.isEnabled();
 }
}
'''}, 'ToggleTest')


def test_both_round_camera_engines_share_policy_without_changing_story_dual():
    camera_x = (JAVA / 'app/regram/camera/CameraXSession.java').read_text()
    legacy = (JAVA / 'org/telegram/ui/Stories/recorder/DualCameraView.java').read_text()
    assert 'DualCameraSettings.canUse(context)' in method(camera_x, 'public static boolean isRoundDualAvailable(')
    assert 'DualCameraSettings.canUse(context)' in method(legacy, 'public static boolean roundDualAvailableStatic(')
    # Story picture-in-picture remains independent of round video prewarming.
    story = method(legacy, 'public static boolean dualAvailableStatic(')
    assert '"dual_available"' in story and 'DualCameraSettings' not in story
    capabilities = method(camera_x, 'private static boolean hasConcurrentFrontBackPair(')
    assert 'getConcurrentCameraIds()' in capabilities
    assert 'LENS_FACING_FRONT' in capabilities and 'LENS_FACING_BACK' in capabilities
    fallback = method(camera_x, 'private void bindDualUseCases()')
    assert 'isDualMode = false' in fallback and 'bindSingleUseCases()' in fallback
    recorder = (JAVA / 'org/telegram/ui/Components/InstantCameraView.java').read_text()
    assert 'bothCameras = app.regram.camera.CameraXSession.isRoundDualAvailable(getContext())' in recorder
    assert 'bothCameras = DualCameraView.roundDualAvailableStatic(getContext())' in recorder
    cleanup = method(recorder, 'public void destroy(boolean async)')
    assert 'releaseCameraXSession()' in cleanup
    assert 'session.closeCamera()' in method(recorder, 'private void releaseCameraXSession()')
    assert 'camera2Sessions[a].destroy(async)' in cleanup
    settings = (JAVA / 'app/regram/settings/OpenExteraChatsActivity.java').read_text()
    assert 'DualCameraSettings.isEnabled()' in method(settings, 'private static boolean isSeamlessSwitchingEnabled()')
    assert 'DualCameraSettings.setEnabled(value)' in method(settings, 'private static void setSeamlessSwitching(')
    assert 'setSeamlessSwitching(false)' not in method(settings, 'private void updateMediaRows()')


def test_dual_camera_navigation_and_battery_warning_in_both_languages():
    hub = (JAVA / 'app/regram/settings/RegramFeaturesActivity.java').read_text()
    assert 'presentFragment(new DualCameraSettingsActivity())' in hub
    settings = (JAVA / 'app/regram/settings/DualCameraSettingsActivity.java').read_text()
    assert 'RegramDualCameraUnavailable' in settings
    assert 'cell.setEnabled(available || enabled)' in settings
    registry = (JAVA / 'app/regram/settings/utils/SettingsRegistry.java').read_text()
    assert 'icons.put(app.regram.settings.DualCameraSettingsActivity.class' in registry
    keys=[]
    for locale, battery in (('values','battery'),('values-ru','батареи')):
        tree=ET.parse(ROOT / f'TMessagesProj/src/main/res/{locale}/regram_dual_camera.xml').getroot()
        strings={node.get('name'):node.text for node in tree}
        keys.append(set(strings))
        assert battery in strings['RegramDualCameraSummary']
        assert 'Camera2' in strings['RegramDualCameraInfo'] and 'CameraX' in strings['RegramDualCameraInfo']
    assert keys[0]==keys[1]
