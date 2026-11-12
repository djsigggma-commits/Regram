package app.regram.fonts;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.TextView;
import org.telegram.messenger.R;

/** Covers framework TextViews without reflection, frame polling, or retaining global window references. */
public final class FontWindows {
    private FontWindows() {}
    public static void register(Application application) {
        if (ApplicationFonts.ui(400, false) == null) return;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle state) { install(a.getWindow()); }
            @Override public void onActivityResumed(Activity a) { install(a.getWindow()); }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle state) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
    public static void install(Window window) {
        if (window != null && ApplicationFonts.ui(400, false) != null) install(window.getDecorView());
    }
    public static void install(View root) {
        if (root == null || ApplicationFonts.ui(400, false) == null) return;
        Object tag = root.getTag(R.id.regram_font_window);
        if (tag instanceof Binding) { ((Binding) tag).bind(); return; }
        Binding binding = new Binding(root);
        root.setTag(R.id.regram_font_window, binding);
        root.addOnAttachStateChangeListener(binding);
        binding.bind();
    }
    private static final class Binding implements View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        final View root;
        ViewTreeObserver observer;
        Binding(View root) { this.root = root; }
        void bind() {
            ViewTreeObserver current = root.getViewTreeObserver();
            if (observer != current) {
                unbind();
                observer = current;
                // A floating observer can have transferred this listener during attachment.
                observer.removeOnGlobalLayoutListener(this);
                observer.addOnGlobalLayoutListener(this);
            }
            apply(root);
        }
        void unbind() {
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            observer = null;
        }
        @Override public void onViewAttachedToWindow(View view) { bind(); }
        @Override public void onViewDetachedFromWindow(View view) { unbind(); }
        @Override public void onGlobalLayout() { apply(root); }
    }
    private static void apply(View view) {
        if (view == null || Boolean.TRUE.equals(view.getTag(R.id.regram_font_preview))) return;
        String name = view.getClass().getName();
        if (name.contains("PaintTypeface") || name.endsWith("EditTextOutline") || name.endsWith("PaintTextOptionsView")) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            android.graphics.Typeface font = ApplicationFonts.replacement(text.getTypeface());
            if (font != null) text.setTypeface(font);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
        }
    }
}
