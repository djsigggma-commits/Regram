"""Exercise the actual Java router with minimal Android API doubles, not a device."""
from pathlib import Path
import shutil
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def test_actual_java_router_ownership_gating_and_window_lifecycle(tmp_path):
    if not shutil.which('javac') or not shutil.which('java'):
        pytest.skip('JDK unavailable')
    sources = {
        'android/content/SharedPreferences.java': '''package android.content;
            public interface SharedPreferences { boolean getBoolean(String key, boolean fallback); }''',
        'android/content/Context.java': '''package android.content;
            public class Context {
                public java.util.Map<String, Boolean> settings = new java.util.HashMap<>();
                public SharedPreferences getSharedPreferences(String name, int mode) {
                    assert name.equals("regram_spen"); return (key, fallback) -> settings.getOrDefault(key, fallback);
                }
            }''',
        'android/os/SystemClock.java': '''package android.os;
            public class SystemClock { public static long uptimeMillis() { return 1000; } }''',
        'android/view/InputDevice.java': '''package android.view;
            public class InputDevice { public static final int SOURCE_TOUCHSCREEN=0x1002; }''',
        'android/view/MotionEvent.java': '''package android.view;
            public class MotionEvent {
                public static final int TOOL_TYPE_STYLUS=2, ACTION_DOWN=0, ACTION_UP=1, ACTION_MOVE=2,
                    ACTION_CANCEL=3, ACTION_POINTER_DOWN=5, ACTION_POINTER_UP=6, ACTION_BUTTON_PRESS=11, ACTION_BUTTON_RELEASE=12;
                public int action, buttons, button, tool=2, count=1, source;
                public float x, y; public long time; public boolean recycled;
                public static java.util.List<MotionEvent> created = new java.util.ArrayList<>();
                public MotionEvent(int a, int b, int ab, float rx, float ry) { action=a; buttons=b; button=ab; x=rx; y=ry; }
                public static MotionEvent obtain(MotionEvent e) {
                    MotionEvent c=new MotionEvent(e.action,e.buttons,e.button,e.x,e.y);
                    c.tool=e.tool; c.count=e.count; c.time=e.time; c.source=e.source; created.add(c); return c;
                }
                public static MotionEvent obtain(long down, long time, int action, float x, float y, int meta) {
                    MotionEvent c=new MotionEvent(action,0,0,x,y); c.time=time; c.tool=0; created.add(c); return c;
                }
                public int getPointerCount() { return count; }
                public int getToolType(int p) { return tool; }
                public int getActionMasked() { return action; }
                public int getButtonState() { return buttons; }
                public int getActionButton() { return button; }
                public float getRawX() { return x; } public float getRawY() { return y; }
                public void setAction(int a) { action=a; }
                public void setSource(int s) { source=s; }
                public void recycle() { assert !recycled; recycled=true; }
            }''',
        'android/view/View.java': '''package android.view;
            public class View {
                public boolean attached=true, shown=true, focused=true;
                public int left=180, top=260, width=1000, height=1000;
                private Object tag;
                public Object getTag(int key) { return tag; }
                public void setTag(int key, Object value) { tag=value; }
                public boolean isAttachedToWindow() { return attached; }
                public boolean isShown() { return shown; }
                public boolean hasWindowFocus() { return focused; }
                public int getWidth() { return width; } public int getHeight() { return height; }
                public void getLocationOnScreen(int[] a) { a[0]=left; a[1]=top; }
                public boolean dispatchTouchEvent(MotionEvent e) { return false; }
            }''',
        'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
            public class ApplicationLoader {
                public static android.content.Context applicationContext=new android.content.Context();
                public static boolean mainInterfacePaused;
            }''',
        'org/telegram/messenger/SharedConfig.java': '''package org.telegram.messenger;
            public class SharedConfig { public static boolean appLocked, isWaitingForPasscodeEnter; }''',
        'org/telegram/messenger/R.java': '''package org.telegram.messenger;
            public class R { public static class id { public static final int regram_spen_hover_state=1; } }''',
        'RouterTest.java': '''
            import app.regram.input.SPenInput;
            import android.view.*;
            import org.telegram.messenger.*;
            public class RouterTest {
                static class Root extends View {
                    int downs, ups, cancels; boolean vanish, reject, fail;
                    @Override public boolean dispatchTouchEvent(MotionEvent e) {
                        assert SPenInput.isHoverTap();
                        assert e.source==InputDevice.SOURCE_TOUCHSCREEN && e.tool!=MotionEvent.TOOL_TYPE_STYLUS;
                        assert e.x==20 && e.y==40;
                        SPenInput.contact(this,e);
                        if (e.action==0) { downs++; if (vanish) attached=false; if (fail) throw new IllegalStateException(); }
                        if (e.action==1) { ups++; assert e.time==1040; }
                        if (e.action==3) cancels++;
                        return !reject;
                    }
                }
                static MotionEvent e(int action,int buttons,int button) { return new MotionEvent(action,buttons,button,200,300); }
                static void click(Root r) {
                    assert SPenInput.hover(r,e(11,32,32));
                    assert SPenInput.hover(r,e(12,0,32));
                }
                public static void main(String[] args) {
                    Root root=new Root();
                    assert !SPenInput.hover(root,e(11,32,32)); // default disabled
                    ApplicationLoader.applicationContext.settings.put("enabled",true);
                    click(root); assert root.downs==1 && root.ups==1;
                    SPenInput.hover(root,e(12,0,32)); assert root.downs==1; // repeated release
                    assert !SPenInput.hover(root,e(7,0,0)); // ordinary hover preserved
                    Root other=new Root();
                    SPenInput.hover(root,e(11,32,32)); SPenInput.hover(other,e(12,0,32));
                    assert other.downs==0; // per-window state
                    SPenInput.resetHover(root); SPenInput.hover(root,e(12,0,32)); assert root.downs==1;
                    SPenInput.hover(root,e(11,32,32)); root.focused=false;
                    assert !SPenInput.hover(root,e(12,0,32)); root.focused=true;
                    SPenInput.hover(root,e(12,0,32)); assert root.downs==1;
                    SharedConfig.appLocked=true; clickDisabled(root); SharedConfig.appLocked=false;
                    SharedConfig.isWaitingForPasscodeEnter=true; clickDisabled(root); SharedConfig.isWaitingForPasscodeEnter=false;
                    ApplicationLoader.mainInterfacePaused=true; clickDisabled(root); ApplicationLoader.mainInterfacePaused=false;
                    ApplicationLoader.applicationContext.settings.put("hover_click_menu",false);
                    clickDisabled(root); ApplicationLoader.applicationContext.settings.put("hover_click_menu",true);
                    MotionEvent mouse=e(11,32,32); mouse.tool=3; assert !SPenInput.hover(root,mouse);
                    MotionEvent multi=e(11,32,32); multi.count=2; assert !SPenInput.hover(root,multi);
                    SPenInput.contact(root,e(0,0,0)); click(root); assert root.downs==1;
                    SPenInput.contact(root,e(1,0,0));
                    MotionEvent original=e(211,0,0); MotionEvent normalized=SPenInput.normalizedCopy(original);
                    assert normalized!=null && normalized.action==0 && original.action==211 && !original.recycled;
                    normalized.recycle(); assert SPenInput.normalizedCopy(e(0,32,0))==null;
                    Root toolbar=new Root(); toolbar.focused=false;
                    assert SPenInput.hover(toolbar,e(11,32,32),root);
                    assert SPenInput.hover(toolbar,e(12,0,32),root);
                    assert toolbar.downs==1 && toolbar.ups==1;
                    SPenInput.hover(toolbar,e(11,32,32),root);
                    SPenInput.invalidateHoverWindows();
                    SPenInput.hover(toolbar,e(12,0,32),root); assert toolbar.downs==1;
                    root.focused=false;
                    assert !SPenInput.hover(toolbar,e(11,32,32),root); root.focused=true;
                    Root gone=new Root(); gone.vanish=true; click(gone);
                    assert gone.downs==1 && gone.ups==0 && gone.cancels==1;
                    Root rejected=new Root(); rejected.reject=true; click(rejected);
                    assert rejected.downs==1 && rejected.ups==0 && rejected.cancels==1;
                    rejected.reject=false; click(rejected); assert rejected.ups==1;
                    Root outside=new Root(); outside.width=10; click(outside); assert outside.downs==0;
                    Root failure=new Root(); failure.fail=true;
                    try { click(failure); throw new AssertionError(); } catch(IllegalStateException expected) {}
                    assert !SPenInput.isHoverTap();
                    for(MotionEvent created: MotionEvent.created) assert created.recycled;
                }
                static void clickDisabled(Root root) {
                    assert !SPenInput.hover(root,e(11,32,32)); assert !SPenInput.hover(root,e(12,0,32));
                    assert root.downs==1;
                }
            }
        ''',
    }
    for name in ('SPenState', 'SPenInput'):
        sources[f'app/regram/input/{name}.java'] = (JAVA / f'app/regram/input/{name}.java').read_text()
    paths = []
    for name, text in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        paths.append(str(path))
    subprocess.run(['javac', '-d', str(tmp_path), *paths], check=True, capture_output=True, text=True)
    subprocess.run(['java', '-ea', '-cp', str(tmp_path), 'RouterTest'], check=True, capture_output=True, text=True)
