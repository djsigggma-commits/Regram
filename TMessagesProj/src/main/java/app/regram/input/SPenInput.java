package app.regram.input;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;

/** Direct input routing at windows owned by the client, never framework hooks. */
public final class SPenInput {
    // Window callbacks/input dispatch are UI-thread owned. No collection of windows.
    private static long hoverWindowEpoch;
    private static int hoverTapDepth;
    public static boolean isHoverTap() { return hoverTapDepth != 0; }
    public static void invalidateHoverWindows() { hoverWindowEpoch++; }
    public static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("regram_spen", 0);
    }

    public static boolean enabled() {
        return ApplicationLoader.applicationContext != null && preferences().getBoolean("enabled", false);
    }

    public static boolean option(String key) { return enabled() && preferences().getBoolean(key, true); }
    public static boolean unlocked() {
        return !ApplicationLoader.mainInterfacePaused && !SharedConfig.appLocked && !SharedConfig.isWaitingForPasscodeEnter;
    }

    public static boolean stylus(MotionEvent event) {
        return event.getPointerCount() == 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS;
    }

    /** Caller owns/recycles the optional copy. Never change the incoming event. */
    public static MotionEvent normalizedCopy(MotionEvent event) {
        if (!enabled() || !unlocked() || !stylus(event)) return null;
        int action = event.getActionMasked();
        int normal = SPenState.normalize(action);
        if (normal == action) return null;
        MotionEvent copy = MotionEvent.obtain(event);
        copy.setAction(normal);
        return copy;
    }

    public static void contact(View root, MotionEvent event) {
        if (root == null) return;
        SPenState.Hover state = (SPenState.Hover) root.getTag(R.id.regram_spen_hover_state);
        if (!option("hover_click_menu") || !unlocked()) {
            if (state != null) state.reset();
            return;
        }
        if (state == null) {
            state = new SPenState.Hover();
            root.setTag(R.id.regram_spen_hover_state, state);
        }
        state.windowEpoch(hoverWindowEpoch);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) action = MotionEvent.ACTION_MOVE;
        state.contact(action, event.getButtonState());
    }

    public static void resetHover(View root) {
        if (root == null) return;
        SPenState.Hover state = (SPenState.Hover) root.getTag(R.id.regram_spen_hover_state);
        if (state != null) state.reset();
    }

    public static boolean hover(View root, MotionEvent event) {
        return hover(root, event, null);
    }

    private static boolean activeWindow(View root, View focusOwner) {
        return root.hasWindowFocus() || (focusOwner != null && focusOwner.isAttachedToWindow()
                && focusOwner.isShown() && focusOwner.hasWindowFocus());
    }

    /** Explicit focus owner for the client's non-focusable floating toolbar. */
    public static boolean hover(View root, MotionEvent event, View focusOwner) {
        if (root == null) return false;
        if (!option("hover_click_menu") || !unlocked() || !root.isAttachedToWindow()
                || !root.isShown() || !activeWindow(root, focusOwner)) {
            resetHover(root);
            return false;
        }
        if (!stylus(event)) {
            resetHover(root);
            return false;
        }
        SPenState.Hover state = (SPenState.Hover) root.getTag(R.id.regram_spen_hover_state);
        if (state == null) {
            state = new SPenState.Hover();
            root.setTag(R.id.regram_spen_hover_state, state);
        }
        state.windowEpoch(hoverWindowEpoch);
        int action = event.getActionMasked();
        boolean click = state.step(action, event.getButtonState(), event.getActionButton());
        if (click) tap(root, event.getRawX(), event.getRawY(), focusOwner);
        // Preserve ordinary hover effects; own only the enabled pen button protocol.
        return click || ((action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE)
                && (event.getActionButton() == 0 || (event.getActionButton() & SPenState.BUTTONS) != 0));
    }

    private static void tap(View root, float rawX, float rawY, View focusOwner) {
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        float x = rawX - location[0], y = rawY - location[1];
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || y < 0
                || x >= root.getWidth() || y >= root.getHeight()) return;
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        hoverTapDepth++;
        try {
            boolean handled = root.dispatchTouchEvent(down);
            if (!handled || !unlocked() || !root.isAttachedToWindow() || !root.isShown() || !activeWindow(root, focusOwner)) {
                up.setAction(MotionEvent.ACTION_CANCEL);
            }
            root.dispatchTouchEvent(up);
        } finally {
            hoverTapDepth--;
            resetHover(root);
            down.recycle();
            up.recycle();
        }
    }

    private SPenInput() {}
}
