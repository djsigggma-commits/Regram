"""The round-video zoom control is optional; opting out must not disable zoom gestures."""
from pathlib import Path
import xml.etree.ElementTree as ET

from test_native_visual_settings import compile_run, method

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def test_real_toggle_visibility_and_touch_contract(tmp_path):
    slider = (JAVA / 'app/regram/camera/InstantCameraZoomSlider.java').read_text()
    settings = (JAVA / 'app/regram/settings/RoundVideoZoomSettingsActivity.java').read_text()
    show = method(slider, '    private void showAnimated()')
    touch = method(slider, '    public boolean onTouchEvent(MotionEvent event)')
    attach = method(slider, '    protected void onAttachedToWindow()')
    click = method(settings, '    @Override protected void onItemClick(').replace('@Override ', '')
    compile_run(tmp_path, {'ZoomToggleTest.java': '''
import java.util.function.Consumer;
class ChatsConfig {
 static final Item zoomSlider=new Item();
 static class Item {
  boolean enabled=true;
  boolean Bool(){return enabled;}
  void toggleConfigBool(){enabled=!enabled;}
 }
}
class View {}
class TextCheckCell extends View {boolean checked;void setChecked(boolean value){checked=value;}}
class MotionEvent {}
class Animator {}
class AnimatorListenerAdapter {public void onAnimationEnd(Animator animation){}}
class ValueAnimator extends Animator {
 static int created;
 static ValueAnimator ofFloat(float a,float b){created++;return new ValueAnimator();}
 void addUpdateListener(Consumer<ValueAnimator> callback){}
 Object getAnimatedValue(){return 1f;}
 void addListener(AnimatorListenerAdapter listener){}
 void setDuration(long value){}
 void setInterpolator(Object value){}
 void start(){}
}
class CubicBezierInterpolator {static final Object DEFAULT=new Object();}
class Base {
 int touches;
 public boolean onTouchEvent(MotionEvent e){touches++;return true;}
 protected void onAttachedToWindow(){}
}
public class ZoomToggleTest extends Base {
 static final int VISIBLE=0,GONE=8;
 static final long APPEAR_DURATION=180;
 enum Backend {NONE,CAMERA_X}
 Backend backend=Backend.NONE;
 int visibility=VISIBLE,hides,bindings,observers,bindRetries,enabledRow=1;
 boolean enabled=true,animateNextConfiguration; float alpha=1,appearProgress=1;
 ValueAnimator appearAnimator;
 int getVisibility(){return visibility;}
 void setVisibility(int value){visibility=value;}
 boolean isEnabled(){return enabled;}
 void setEnabled(boolean value){enabled=value;}
 float getAlpha(){return alpha;}
 void hideImmediately(){hides++;visibility=GONE;appearProgress=0;appearAnimator=null;}
 void setAppearProgress(float value){appearProgress=value;}
 void tryBind(){bindings++;}
 void attachCameraXZoomObserver(){observers++;}
''' + show + '\n' + touch + '\n' + attach + '\n' + click + '''
 public static void main(String[] args){
  ZoomToggleTest control=new ZoomToggleTest(); TextCheckCell toggle=new TextCheckCell();
  control.onItemClick(toggle,1,0,0); // disable using the real settings handler
  assert !ChatsConfig.zoomSlider.Bool() && !toggle.checked;
  assert !control.onTouchEvent(new MotionEvent()) && control.touches==0;
  control.showAnimated();
  assert control.visibility==GONE && control.hides==1 && ValueAnimator.created==0;
  control.visibility=VISIBLE; control.appearProgress=1; control.backend=Backend.CAMERA_X;
  control.onAttachedToWindow();
  assert control.visibility==GONE && control.bindings==1; // binding retained for gesture zoom mapping
  control.onItemClick(toggle,1,0,0); assert ChatsConfig.zoomSlider.Bool() && toggle.checked;
  control.showAnimated(); assert control.visibility==VISIBLE && ValueAnimator.created==1;
  control.appearProgress=1; control.alpha=1;
  assert control.onTouchEvent(new MotionEvent()) && control.touches==1;
  control.visibility=GONE; assert !control.onTouchEvent(new MotionEvent());
  control.visibility=VISIBLE; control.alpha=0.1f; assert !control.onTouchEvent(new MotionEvent());
  control.alpha=1; control.enabled=false; assert !control.onTouchEvent(new MotionEvent());
  control.onItemClick(toggle,2,0,0); assert ChatsConfig.zoomSlider.Bool(); // unrelated row
 }
}
'''}, 'ZoomToggleTest')


def test_one_existing_preference_and_all_camera_backends_keep_zoom_support():
    config = (ROOT / 'TMessagesProj/src/main/kotlin/app/regram/chats/ChatsConfig.kt').read_text()
    assert 'val zoomSlider = addConfig("OEChatsZoomSlider", ConfigItem.configTypeBool, true)' in config
    settings = (JAVA / 'app/regram/settings/RoundVideoZoomSettingsActivity.java').read_text()
    assert 'ChatsConfig.zoomSlider.toggleConfigBool()' in settings
    chat = (JAVA / 'app/regram/settings/OpenExteraChatsActivity.java').read_text()
    assert 'if (position == zoomSliderRow) return ChatsConfig.zoomSlider;' in chat
    assert 'zoomSliderRow = addRow("zoomSlider", "RegramRoundVideoZoomTitle")' in chat
    facade = (JAVA / 'com/exteragram/messenger/ExteraConfig.java').read_text()
    assert 'ChatsConfig.zoomSlider.Bool()' in method(facade, 'public static boolean getZoomSlider()')
    slider = (JAVA / 'app/regram/camera/InstantCameraZoomSlider.java').read_text()
    bind = method(slider, 'private void tryBind()')
    for backend in ('CAMERA_1', 'CAMERA_2', 'CAMERA_X'):
        assert 'case ' + backend in bind
    assert 'maxRatio <= minRatio' in bind and 'hideImmediately()' in bind
    assert 'setZoomConfiguration(minRatio, maxRatio' in bind
    recorder = (JAVA / 'org/telegram/ui/Components/InstantCameraView.java').read_text()
    assert 'zoomSlider.setOnCameraZoomChangeListener(this::onZoomSliderChanged)' in recorder
    # The preference hides the control, not the camera/gesture logic.
    assert 'zoomSlider.Bool()' not in recorder
    assert 'zoomSlider.beginPinchZoomGesture()' in recorder
    assert 'cameraSession.setZoom(zoom)' in recorder
    assert 'session.setZoomRatio(zoom)' in recorder
    assert 'zoomSlider::unbindSession' in method(recorder, 'public void destroy(boolean async)')


def test_round_video_zoom_is_navigable_and_explains_opt_out():
    hub = (JAVA / 'app/regram/settings/RegramFeaturesActivity.java').read_text()
    assert 'presentFragment(new RoundVideoZoomSettingsActivity())' in hub
    registry = (JAVA / 'app/regram/settings/utils/SettingsRegistry.java').read_text()
    assert 'icons.put(app.regram.settings.RoundVideoZoomSettingsActivity.class' in registry
    keys=[]
    for locale in ('values','values-ru'):
        tree = ET.parse(ROOT / f'TMessagesProj/src/main/res/{locale}/regram_round_video_zoom.xml').getroot()
        strings = {node.get('name'):node.text for node in tree}
        keys.append(set(strings))
        assert 'Camera1' in strings['RegramRoundVideoZoomInfo']
        assert 'Camera2' in strings['RegramRoundVideoZoomInfo']
        assert 'CameraX' in strings['RegramRoundVideoZoomInfo']
    assert keys[0] == keys[1]
