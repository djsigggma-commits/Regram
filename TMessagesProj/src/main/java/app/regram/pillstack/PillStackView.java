package app.regram.pillstack;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.List;

import app.regram.pillstack.pills.BasePill;

/**
 * Полоса пилюль: показывает одну пилюлю, вертикальный свайп переключает на соседнюю.
 *
 * Переписано по мотивам exteraGram — обработчик касаний в декомпиляте не восстановился,
 * поэтому логика жестов написана заново.
 */
public class PillStackView extends FrameLayout {

    private final List<BasePill> pills = new ArrayList<>();
    private final float touchSlop;

    private int currentIndex;
    private ValueAnimator currentAnimator;
    private float currentSwipeProgress;
    private boolean isSwiping;
    private boolean isSwipingUp;
    private boolean maybeClick;
    private boolean longClickPerformed;
    private float startX;
    private float startY;
    private float visibilityFactor = -1f;
    private boolean stackOnScreen = true;

    private final Runnable longPressRunnable = () -> {
        if (!maybeClick || isSwiping || pills.isEmpty()) {
            return;
        }
        longClickPerformed = pills.get(currentIndex).onPillLongClicked();
        if (longClickPerformed) {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }
    };

    public PillStackView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
    }

    public int getPillsCount() {
        return pills.size();
    }

    public void addPill(BasePill pill) {
        pills.add(pill);
        addView(pill);
        if (pills.size() - 1 != currentIndex) {
            pill.setAlpha(0f);
            pill.setScaleX(0.8f);
            pill.setScaleY(0.8f);
            pill.setVisibility(GONE);
        } else {
            pill.setVisibility(VISIBLE);
            pill.onPillSelected();
        }
        pill.onStackVisibilityChanged(stackOnScreen);
    }

    public void clearPills() {
        if (!pills.isEmpty() && currentIndex < pills.size()) {
            pills.get(currentIndex).onPillUnselected();
        }
        pills.clear();
        removeAllViews();
        currentIndex = 0;
    }

    public void setCurrentIndex(int index) {
        if (index < 0 || index >= pills.size() || index == currentIndex) {
            return;
        }
        BasePill previous = pills.get(currentIndex);
        previous.setVisibility(GONE);
        previous.onPillUnselected();
        currentIndex = index;
        BasePill next = pills.get(index);
        next.setVisibility(VISIBLE);
        next.setAlpha(1f);
        next.setScaleX(1f);
        next.setScaleY(1f);
        next.setTranslationY(0f);
        next.onPillSelected();
        requestLayout();
    }

    public void updateColors() {
        for (BasePill pill : pills) {
            pill.updateColors();
        }
    }

    /** 0 — полоса скрыта, 1 — показана целиком. */
    public void setVisibilityFactor(float factor) {
        if (visibilityFactor == factor) {
            return;
        }
        visibilityFactor = factor;
        if (factor <= 0.01f) {
            setVisibility(GONE);
            return;
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        setAlpha(factor);
        setScaleX(AndroidUtilities.lerp(0.6f, 1f, factor));
        setScaleY(AndroidUtilities.lerp(0.6f, 1f, factor));
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (stackOnScreen == isVisible) {
            return;
        }
        stackOnScreen = isVisible;
        for (BasePill pill : pills) {
            pill.onStackVisibilityChanged(isVisible);
        }
    }

    // ---- Жесты ----

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (pills.isEmpty()) {
            return super.onInterceptTouchEvent(event);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = event.getRawX();
            startY = event.getRawY();
            isSwiping = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getRawX() - startX;
            float dy = event.getRawY() - startY;
            if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx) && pills.size() > 1) {
                isSwiping = true;
                if (currentAnimator != null) {
                    currentAnimator.cancel();
                }
                startY = event.getRawY() - (isSwipingUp ? -currentSwipeProgress * getHeight() : currentSwipeProgress * getHeight());
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pills.isEmpty()) {
            return super.onTouchEvent(event);
        }
        BasePill current = pills.get(currentIndex);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getRawX();
                startY = event.getRawY();
                isSwiping = false;
                maybeClick = true;
                longClickPerformed = false;
                current.setPressed(true);
                current.drawableHotspotChanged(event.getX(), event.getY());
                removeCallbacks(longPressRunnable);
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;
                if (!isSwiping && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    maybeClick = false;
                    removeCallbacks(longPressRunnable);
                    current.setPressed(false);
                    if (Math.abs(dy) > Math.abs(dx) && pills.size() > 1) {
                        isSwiping = true;
                        if (currentAnimator != null) {
                            currentAnimator.cancel();
                        }
                        startY = event.getRawY();
                        dy = 0;
                    }
                }
                if (isSwiping) {
                    handleSwipeProgress(dy);
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPressRunnable);
                current.setPressed(false);
                if (isSwiping) {
                    finishSwipe(event.getRawY() - startY);
                } else if (maybeClick && !longClickPerformed) {
                    current.onPillClicked();
                }
                maybeClick = false;
                isSwiping = false;
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(longPressRunnable);
                current.setPressed(false);
                if (isSwiping) {
                    cancelSwipe(isSwipingUp);
                }
                maybeClick = false;
                isSwiping = false;
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void handleSwipeProgress(float dy) {
        if (pills.size() <= 1) {
            return;
        }
        int height = getHeight();
        if (height <= 0) {
            return;
        }
        isSwipingUp = dy < 0;
        float progress = Math.abs(dy) / height;
        int next = isSwipingUp ? currentIndex + 1 : currentIndex - 1;
        if (PillStackConfig.infiniteScrolling() || (next >= 0 && next < pills.size())) {
            currentSwipeProgress = Math.min(progress, 1f);
        } else {
            currentSwipeProgress = progress;
        }
        applyProgress(currentSwipeProgress, isSwipingUp);
    }

    private void finishSwipe(float dy) {
        int height = getHeight();
        if (height <= 0) {
            cancelSwipe(isSwipingUp);
            return;
        }
        boolean canSwitch = true;
        if (!PillStackConfig.infiniteScrolling()) {
            int next = isSwipingUp ? currentIndex + 1 : currentIndex - 1;
            canSwitch = next >= 0 && next < pills.size();
        }
        if (Math.abs(dy) > height * 0.25f && canSwitch) {
            animateToNextPill(isSwipingUp);
        } else {
            cancelSwipe(isSwipingUp);
        }
    }

    private void applyProgress(float progress, boolean up) {
        BasePill current = pills.get(currentIndex);
        int next = up ? currentIndex + 1 : currentIndex - 1;
        if (PillStackConfig.infiniteScrolling()) {
            if (next >= pills.size()) next = 0;
            if (next < 0) next = pills.size() - 1;
        }
        for (int i = 0; i < pills.size(); i++) {
            if (i != currentIndex && i != next && pills.get(i).getVisibility() != GONE) {
                pills.get(i).setVisibility(GONE);
            }
        }
        if (!PillStackConfig.infiniteScrolling() && (next >= pills.size() || next < 0)) {
            // некуда листать — оттягиваем текущую с сопротивлением
            float overscroll = getHeight() * (float) (1.0 - 1.0 / (progress * 0.18f + 1.0));
            current.setTranslationY(up ? -overscroll : overscroll);
            current.setAlpha(1f);
            return;
        }
        float value = Math.min(progress, 1f);
        BasePill nextPill = pills.get(next);
        if (nextPill.getVisibility() != VISIBLE) {
            nextPill.setVisibility(VISIBLE);
        }
        float offset = getHeight() * value;
        current.setTranslationY(up ? -offset : offset);
        current.setAlpha(1f - value);
        float scaleDelta = 0.2f * value;
        current.setScaleX(1f - scaleDelta);
        current.setScaleY(1f - scaleDelta);
        nextPill.setScaleX(0.8f + scaleDelta);
        nextPill.setScaleY(0.8f + scaleDelta);
        nextPill.setAlpha(value);
        float from = up ? getHeight() : -getHeight();
        nextPill.setTranslationY(from - value * from);
    }

    private void animateToNextPill(boolean up) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }
        currentAnimator = ValueAnimator.ofFloat(currentSwipeProgress, 1f);
        currentAnimator.setDuration(250);
        currentAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        currentAnimator.addUpdateListener(animation -> applyProgress((float) animation.getAnimatedValue(), up));
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                BasePill previous = pills.get(currentIndex);
                previous.setVisibility(GONE);
                previous.setPressed(false);
                previous.setScaleX(1f);
                previous.setScaleY(1f);
                previous.onPillUnselected();

                currentIndex = up ? currentIndex + 1 : currentIndex - 1;
                if (PillStackConfig.infiniteScrolling()) {
                    if (currentIndex >= pills.size()) currentIndex = 0;
                    if (currentIndex < 0) currentIndex = pills.size() - 1;
                }
                currentIndex = Math.max(0, Math.min(currentIndex, pills.size() - 1));

                for (int i = 0; i < pills.size(); i++) {
                    if (i != currentIndex) {
                        pills.get(i).setVisibility(GONE);
                    }
                }
                BasePill selected = pills.get(currentIndex);
                selected.setVisibility(VISIBLE);
                selected.setScaleX(1f);
                selected.setScaleY(1f);
                selected.setTranslationY(0f);
                selected.setAlpha(1f);
                selected.onPillSelected();
                currentSwipeProgress = 0f;
                PillStackConfig.saveLastActivePillId(selected.getPillId());
            }
        });
        currentAnimator.start();
    }

    private void cancelSwipe(boolean up) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }
        currentAnimator = ValueAnimator.ofFloat(currentSwipeProgress, 0f);
        currentAnimator.setDuration(200);
        currentAnimator.addUpdateListener(animation -> applyProgress((float) animation.getAnimatedValue(), up));
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                for (int i = 0; i < pills.size(); i++) {
                    if (i != currentIndex) {
                        BasePill pill = pills.get(i);
                        pill.setVisibility(GONE);
                        pill.setPressed(false);
                        pill.setScaleX(1f);
                        pill.setScaleY(1f);
                    }
                }
                BasePill current = pills.get(currentIndex);
                current.setTranslationY(0f);
                current.setAlpha(1f);
                current.setScaleX(1f);
                current.setScaleY(1f);
                currentSwipeProgress = 0f;
            }
        });
        currentAnimator.start();
    }
}
