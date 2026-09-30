package app.regram.utils;

import android.animation.AnimatorSet;
import android.graphics.drawable.Drawable;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * UI-утилиты, перенесённые из exteraGram
 */
public final class UIUtil {

    private UIUtil() {
    }

    /**
     * «Вдавливание» вью при нажатии: 80 мс до {@code 1 - scale}, отпускание — 350 мс
     * с {@link OvershootInterpolator}.
     *
     * exteraGram дополнительно поджимает маску ripple ({@code Theme.RippleRadMaskDrawable}
     * с padding'ом), но у нас {@code RippleRadMaskDrawable} не умеет padding, а
     * {@code Theme.java} правит другой агент — поэтому здесь только масштаб.
     * Визуально основную часть эффекта даёт именно он.
     */
    public static void applyScaleStateListAnimator(View view, float scale, float tension) {
        if (view == null) {
            return;
        }

        final float pressedScale = 1f - scale;

        final AnimatorSet pressed = new AnimatorSet();
        pressed.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, pressedScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, pressedScale)
        );
        pressed.setDuration(80);

        final AnimatorSet released = new AnimatorSet();
        released.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f)
        );
        released.setInterpolator(new OvershootInterpolator(tension));
        released.setDuration(350);

        final StateListAnimator animator = new StateListAnimator();
        animator.addState(new int[]{android.R.attr.state_pressed}, pressed);
        animator.addState(new int[0], released);

        view.setStateListAnimator(animator);
    }

    /**
     * Фон плавающей кнопки. Апстрим всегда рисует круг
     * ({@code Theme.createSimpleSelectorCircleDrawable}); exteraGram при включённом
     * squareFab рисует «squircle» с радиусом ceil(size * 16 / 56) dp
     *
     * @param size сторона кнопки в dp
     */
    public static Drawable createFabSelectorDrawable(int size, int color, int pressedColor) {
        if (size == 40) {
            final int background = org.telegram.ui.ActionBar.Theme.getColor(
                    org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite);
            color = androidx.core.graphics.ColorUtils.blendARGB(background, 0xFFFFFFFF, 0.1f);
            pressedColor = org.telegram.ui.ActionBar.Theme.blendOver(background,
                    org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_listSelector));
        }
        return org.telegram.ui.ActionBar.Theme.createSimpleSelectorRoundRectDrawable(
                org.telegram.messenger.AndroidUtilities.dp(
                        app.regram.appearance.AppearanceConfig.fabCornerRadius(size)),
                color, pressedColor);
    }
}
