package app.regram.plugins.ui.components.templates;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.Utilities;

public final class UniversalView extends View {

    public interface UniversalViewDelegate {

        default void onAttachedToWindow() {
        }

        default void onDetachedFromWindow() {
        }

        default void onDraw(Canvas canvas, Utilities.Callback<Canvas> originalMethod) {
            originalMethod.run(canvas);
        }

        default void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info,
                                                       Utilities.Callback<AccessibilityNodeInfo> originalMethod) {
            originalMethod.run(info);
        }

        default void onMeasure(int widthMeasureSpec, int heightMeasureSpec,
                               Utilities.Callback2<Integer, Integer> originalMethod) {
            originalMethod.run(widthMeasureSpec, heightMeasureSpec);
        }

        default boolean onTouchEvent(MotionEvent event,
                                     Utilities.CallbackReturn<MotionEvent, Boolean> originalMethod) {
            return originalMethod.run(event);
        }
    }

    private UniversalViewDelegate delegate;

    public UniversalView(Context context) {
        this(context, null);
    }

    public UniversalView(Context context, UniversalViewDelegate delegate) {
        super(context);
        this.delegate = delegate;
    }

    public UniversalViewDelegate getDelegate() {
        return delegate;
    }

    public void setDelegate(UniversalViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void callSuperOnDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void callSuperOnInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
    }

    public void callSuperOnMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public boolean callSuperOnTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final UniversalViewDelegate current = delegate;
        if (current != null) {
            current.onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        final UniversalViewDelegate current = delegate;
        if (current != null) {
            current.onDetachedFromWindow();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final UniversalViewDelegate current = delegate;
        if (current != null) {
            current.onDraw(canvas, this::callSuperOnDraw);
        } else {
            super.onDraw(canvas);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        final UniversalViewDelegate current = delegate;
        if (current != null) {
            current.onInitializeAccessibilityNodeInfo(info,
                    this::callSuperOnInitializeAccessibilityNodeInfo);
        } else {
            super.onInitializeAccessibilityNodeInfo(info);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final UniversalViewDelegate current = delegate;
        if (current != null) {
            current.onMeasure(widthMeasureSpec, heightMeasureSpec,
                    (width, height) -> callSuperOnMeasure(width, height));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        final UniversalViewDelegate current = delegate;
        return current != null
                ? current.onTouchEvent(event, this::callSuperOnTouchEvent)
                : super.onTouchEvent(event);
    }
}
