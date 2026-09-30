package org.telegram.ui.Components;

import android.content.Context;
import android.os.Build;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import xyz.nextalone.nagram.NaConfig;

public class SlideChooseView extends View {

    private final SeekBarAccessibilityDelegate accessibilityDelegate;

    private Paint paint;
    private Paint linePaint;
    public TextPaint textPaint;
    private int lastDash;

    public int circleSize;
    public int gapSize;
    public int sideSide;
    public int lineSize;

    public int dashedFrom = -1;

    public boolean moving;
    private boolean startMoving;
    private float xTouchDown;
    private float yTouchDown;

    private int startMovingPreset;

    public String[] optionsStr;
    public int[] optionsSizes;
    public Drawable[] leftDrawables;

    private int selectedIndex;
    public int minIndex = Integer.MIN_VALUE;
    private float selectedIndexTouch;
    public AnimatedFloat selectedIndexAnimatedHolder = new AnimatedFloat(this, 120, CubicBezierInterpolator.DEFAULT);
    public AnimatedFloat movingAnimatedHolder = new AnimatedFloat(this, 150, CubicBezierInterpolator.DEFAULT);

    private Callback callback;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean touchWasClose = false;

    private Path materialPath;
    private RectF materialRect;

    public SlideChooseView(Context context) {
        this(context, null);
    }

    public SlideChooseView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStrokeWidth(AndroidUtilities.dp(2));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextSize(AndroidUtilities.dp(13));

        accessibilityDelegate = new IntSeekBarAccessibilityDelegate() {
            @Override
            protected int getProgress() {
                return selectedIndex;
            }

            @Override
            protected void setProgress(int progress) {
                setOption(progress);
            }

            @Override
            protected int getMaxValue() {
                return optionsStr.length - 1;
            }

            @Override
            protected CharSequence getContentDescription(View host) {
                return selectedIndex < optionsStr.length ? optionsStr[selectedIndex] : null;
            }
        };
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setOptions(int selected, String... options) {
        setOptions(selected, null, options);
    }

    public void setOptions(int selected, Drawable[] leftDrawables, String... options) {
        this.optionsStr = options;
        this.leftDrawables = leftDrawables;
        selectedIndex = selected;
        optionsSizes = new int[optionsStr.length];
        for (int i = 0; i < optionsStr.length; i++) {
            optionsSizes[i] = (int) Math.ceil(textPaint.measureText(optionsStr[i]));
        }
        if (this.leftDrawables != null) {
            for (Drawable drawable : this.leftDrawables) {
                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            }
        }
        requestLayout();
    }

    public void setMinAllowedIndex(int index) {
        if (index != -1 && optionsStr != null) {
            index = Math.min(index, optionsStr.length - 1);
        }
        if (minIndex != index) {
            minIndex = index;
            if (selectedIndex < index) {
                selectedIndex = index;
            }
            invalidate();
        }
    }

    public void setDashedFrom(int from) {
        dashedFrom = from;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float indexTouch = MathUtils.clamp((x - sideSide + circleSize / 2f) / (lineSize + gapSize * 2 + circleSize), 0, optionsStr.length - 1);
        boolean isClose = Math.abs(indexTouch - Math.round(indexTouch)) < .35f;
        if (isClose) {
            indexTouch = Math.round(indexTouch);
        }
        if (minIndex != Integer.MIN_VALUE) {
            indexTouch = Math.max(indexTouch, minIndex);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            xTouchDown = x;
            yTouchDown = y;
            selectedIndexTouch = indexTouch;
            startMovingPreset = selectedIndex;
            startMoving = true;
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!moving) {
                if (Math.abs(xTouchDown - x) > Math.abs(yTouchDown - y)) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            if (startMoving) {
                if (Math.abs(xTouchDown - x) >= AndroidUtilities.touchSlop) {
                    moving = true;
                    startMoving = false;
                }
            }
            if (moving) {
                selectedIndexTouch = indexTouch;
                invalidate();
                if (Math.round(selectedIndexTouch) != selectedIndex && isClose) {
                    setOption(Math.round(selectedIndexTouch));
                }
            }
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (!moving) {
                selectedIndexTouch = indexTouch;
                if (event.getAction() == MotionEvent.ACTION_UP && Math.round(selectedIndexTouch) != selectedIndex) {
                    setOption(Math.round(selectedIndexTouch));
                }
            } else {
                if (selectedIndex != startMovingPreset) {
                    setOption(selectedIndex);
                }
            }
            if (callback != null) {
                callback.onTouchEnd();
            }
            startMoving = false;
            moving = false;
            invalidate();
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }

    private void setOption(int index) {
        if (selectedIndex != index) {
            AndroidUtilities.vibrateCursor(this);
        }
        selectedIndex = index;
        if (callback != null) {
            callback.onOptionSelected(index);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(74), MeasureSpec.EXACTLY));
        circleSize = AndroidUtilities.dp(6);
        gapSize = AndroidUtilities.dp(2);
        sideSide = AndroidUtilities.dp(22);
        lineSize = (getMeasuredWidth() - circleSize * optionsStr.length - gapSize * 2 * (optionsStr.length - 1) - sideSide * 2) / Math.max(1, optionsStr.length - 1);
        // См. SeekBarView.onMeasure: у краёв экрана системный жест «назад»
        // забирает касание себе, и ползунок вместо перетаскивания закрывает экран.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            gestureExclusionRects.clear();
            gestureExclusionRects.add(new Rect(0, 0, AndroidUtilities.dp(80), getMeasuredHeight()));
            gestureExclusionRects.add(new Rect(getMeasuredWidth() - AndroidUtilities.dp(80), 0, getMeasuredWidth(), getMeasuredHeight()));
            setSystemGestureExclusionRects(gestureExclusionRects);
        }
    }

    private final java.util.ArrayList<android.graphics.Rect> gestureExclusionRects = new java.util.ArrayList<>();

    /**
     * Material-слайдер включается только для
     * непрерывной шкалы без пунктира и без ограничения снизу.
     * У NagramX вместо булева флага три стиля (NaConfig.sliderStyle), MD3 — второй.
     */
    private boolean canUseMaterialSlider() {
        return NaConfig.INSTANCE.getSliderStyle().Int() == SeekBarView.SLIDER_STYLE_MD3
                && optionsStr != null && optionsStr.length > 1
                && dashedFrom < 0 && minIndex <= 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (app.regram.ui.M3SliderHelper.drawSlideChoose(this, canvas)) {
            return;
        }
        final boolean material = canUseMaterialSlider();
        float selectedIndexAnimated = selectedIndexAnimatedHolder.set(selectedIndex);
        float movingAnimated = movingAnimatedHolder.set(moving ? 1 : 0);
        int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(11);

        for (int a = 0; a < optionsStr.length; a++) {
            int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
            float t = Math.max(0, 1f - Math.abs(a - selectedIndexAnimated));
            float ut = MathUtils.clamp(selectedIndexAnimated - a + 1f, 0, 1);
            int color = ColorUtils.blendARGB(getThemedColor(Theme.key_switchTrack), Theme.multAlpha(getThemedColor(Theme.key_switchTrackChecked), minIndex != Integer.MIN_VALUE && a <= minIndex ? .50f : 1.0f), ut);
            paint.setColor(color);
            linePaint.setColor(color);
            if (!material) {
                canvas.drawCircle(cx, cy, AndroidUtilities.lerp(circleSize / 2, AndroidUtilities.dp(6), t), paint);
                if (a != 0) {
                    int x = cx - circleSize / 2 - gapSize - lineSize;
                    int width = lineSize;
                    if (dashedFrom != -1 && a - 1 >= dashedFrom) {
                        x += AndroidUtilities.dp(3);
                        width -= AndroidUtilities.dp(3);
                        int dash = width / AndroidUtilities.dp(13);
                        if (lastDash != dash) {
                            float gap = (width - dash * AndroidUtilities.dp(8)) / (float) (dash - 1);
                            linePaint.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(6), gap}, 0));
                            lastDash = dash;
                        }
                        canvas.drawLine(x + AndroidUtilities.dp(1), cy, x + width - AndroidUtilities.dp(1), cy, linePaint);
                    } else {
                        float nt = MathUtils.clamp(1f - Math.abs(a - selectedIndexAnimated - 1), 0, 1);
                        float nct = MathUtils.clamp(1f - Math.min(Math.abs(a - selectedIndexAnimated), Math.abs(a - selectedIndexAnimated - 1)), 0, 1);
                        width -= AndroidUtilities.dp(3) * nct;
                        x += AndroidUtilities.dp(3) * nt;
                        canvas.drawRect(x, cy - AndroidUtilities.dp(1), x + width, cy + AndroidUtilities.dp(1), paint);
                    }
                }
            }
            int size = optionsSizes[a];
            String text = optionsStr[a];
            textPaint.setColor(ColorUtils.blendARGB(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), getThemedColor(Theme.key_windowBackgroundWhiteBlueText), t));

            if (leftDrawables != null) {
                canvas.save();
                if (a == 0) {
                    canvas.translate(AndroidUtilities.dp(12), AndroidUtilities.dp(15.5f));
                } else if (a == optionsStr.length - 1) {
                    canvas.translate(getMeasuredWidth() - size - AndroidUtilities.dp(22) - AndroidUtilities.dp(10), AndroidUtilities.dp(28) - AndroidUtilities.dp(12.5f));
                } else {
                    canvas.translate(cx - size / 2 - AndroidUtilities.dp(10), AndroidUtilities.dp(28) - AndroidUtilities.dp(12.5f));
                }
                leftDrawables[a].setColorFilter(textPaint.getColor(), PorterDuff.Mode.MULTIPLY);
                leftDrawables[a].draw(canvas);
                canvas.restore();
                canvas.save();
                canvas.translate((leftDrawables[a].getIntrinsicWidth() / 2f) - AndroidUtilities.dp(a == 0 ? 3 : 2), 0);
            }

            if (a == 0) {
                canvas.drawText(text, AndroidUtilities.dp(22), AndroidUtilities.dp(28), textPaint);
            } else if (a == optionsStr.length - 1) {
                canvas.drawText(text, getMeasuredWidth() - size - AndroidUtilities.dp(22), AndroidUtilities.dp(28), textPaint);
            } else {
                canvas.drawText(text, cx - size / 2, AndroidUtilities.dp(28), textPaint);
            }

            if (leftDrawables != null) {
                canvas.restore();
            }
        }

        float cx = sideSide + (lineSize + gapSize * 2 + circleSize) * selectedIndexAnimated + circleSize / 2;
        if (material) {
            drawMaterialSlider(canvas, cx, cy);
            return;
        }
        paint.setColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_switchTrackChecked), 80));
        canvas.drawCircle(cx, cy, AndroidUtilities.dp(12 * movingAnimated), paint);
        paint.setColor(getThemedColor(Theme.key_switchTrackChecked));
        canvas.drawCircle(cx, cy, AndroidUtilities.dp(6), paint);
    }

    /**
     * MD3-слайдер с засечками. exteraGram встраивает com.google.android.material.slider.Slider
     * (MaterialSliderUiHelper.java:45-69): trackHeight 8dp, thumb 3x24dp, засечки радиусом 2dp,
     * haloRadius 0; зазор 6dp и внутренний угол 2dp — из темы Widget.Material3.Slider
     * (res/values/styles.xml:8902). Зависимости material в дереве нет, рисуем теми же числами.
     * Цвета — SlideChooseView.java:168-171: активная дорожка key_switchTrackChecked,
     * неактивная key_switchTrack, засечки key_windowBackgroundWhite.
     * Метод drawContent exteraGram не декомпилирован (831 инструкция), поэтому подписи
     * оставлены на прежних местах.
     */
    private void drawMaterialSlider(Canvas canvas, float thumbCx, int cy) {
        final float trackHeight = AndroidUtilities.dp(8);
        final float thumbWidth = AndroidUtilities.dp(3);
        final float thumbHeight = AndroidUtilities.dp(24);
        final float gap = AndroidUtilities.dp(6);
        final float insideCorner = AndroidUtilities.dp(2);
        final float radius = trackHeight / 2f;
        final float tickRadius = AndroidUtilities.dp(2);

        final float left = sideSide + circleSize / 2f;
        final float right = getMeasuredWidth() - sideSide - circleSize / 2f;
        final float top = cy - trackHeight / 2f;
        final float bottom = cy + trackHeight / 2f;
        final float activeRight = thumbCx - thumbWidth / 2f - gap;
        final float inactiveLeft = thumbCx + thumbWidth / 2f + gap;

        if (materialPath == null) {
            materialPath = new Path();
        }
        if (materialRect == null) {
            materialRect = new RectF();
        }

        paint.setColor(getThemedColor(Theme.key_switchTrackChecked));
        if (activeRight > left) {
            materialRect.set(left, top, activeRight, bottom);
            if (materialRect.width() < radius + insideCorner) {
                canvas.drawRoundRect(materialRect, insideCorner, insideCorner, paint);
            } else {
                updateMaterialPath(materialRect, radius, insideCorner);
                canvas.drawPath(materialPath, paint);
            }
        }

        paint.setColor(getThemedColor(Theme.key_switchTrack));
        if (inactiveLeft < right) {
            materialRect.set(inactiveLeft, top, right, bottom);
            if (materialRect.width() < radius + insideCorner) {
                canvas.drawRoundRect(materialRect, insideCorner, insideCorner, paint);
            } else {
                updateMaterialPath(materialRect, insideCorner, radius);
                canvas.drawPath(materialPath, paint);
            }
        }

        paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        for (int a = 0; a < optionsStr.length; a++) {
            final float tickCx = left + (right - left) * a / (float) (optionsStr.length - 1);
            if (tickCx > activeRight && tickCx < inactiveLeft) {
                continue;
            }
            canvas.drawCircle(tickCx, cy, tickRadius, paint);
        }

        paint.setColor(getThemedColor(Theme.key_switchTrackChecked));
        materialRect.set(thumbCx - thumbWidth / 2f, cy - thumbHeight / 2f, thumbCx + thumbWidth / 2f, cy + thumbHeight / 2f);
        canvas.drawRoundRect(materialRect, thumbWidth / 2f, thumbWidth / 2f, paint);
    }

    private void updateMaterialPath(RectF rect, float radiusLeft, float radiusRight) {
        materialPath.reset();
        materialPath.moveTo(rect.left + radiusLeft, rect.top);
        materialPath.lineTo(rect.right - radiusRight, rect.top);
        materialPath.quadTo(rect.right, rect.top, rect.right, rect.top + radiusRight);
        materialPath.lineTo(rect.right, rect.bottom - radiusRight);
        materialPath.quadTo(rect.right, rect.bottom, rect.right - radiusRight, rect.bottom);
        materialPath.lineTo(rect.left + radiusLeft, rect.bottom);
        materialPath.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - radiusLeft);
        materialPath.lineTo(rect.left, rect.top + radiusLeft);
        materialPath.quadTo(rect.left, rect.top, rect.left + radiusLeft, rect.top);
        materialPath.close();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        accessibilityDelegate.onInitializeAccessibilityNodeInfoInternal(this, info);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return super.performAccessibilityAction(action, arguments) || accessibilityDelegate.performAccessibilityActionInternal(this, action, arguments);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }


    public interface Callback {
        void onOptionSelected(int index);

        default void onTouchEnd() {

        }
    }
}
