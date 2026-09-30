package app.regram.utils;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import java.util.List;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ViewPagerActivity;

/**
 * Material 3 predictive back: геометрия «уезжающей карточки» и уменьшенного экрана под ней.
 *
 * Построчный порт {@code com/exteragram/messenger/utils/ui/PredictiveBackAnimationHelper.java}
 * из exteraGram 12.9.0. Класс только считает прямоугольники/радиусы/альфы —
 * рисует их {@code ActionBarLayout.drawChild}.
 */
public final class PredictiveBackAnimationHelper {

    private float currentCornerRadius;
    private float initialTouchY;
    private float interpolatedProgress;
    private float progress;
    private float startCornerRadius;
    private float targetCornerRadius;

    private final RectF startClosingRect = new RectF();
    private final RectF targetClosingRect = new RectF();
    private final RectF currentClosingRect = new RectF();
    private final RectF startEnteringRect = new RectF();
    private final RectF targetEnteringRect = new RectF();
    private final RectF currentEnteringRect = new RectF();
    private final RectF commitStartClosingRect = new RectF();
    private final RectF commitTargetClosingRect = new RectF();
    private final RectF commitStartEnteringRect = new RectF();
    private final RectF commitTargetEnteringRect = new RectF();

    /** Интерполятор самого жеста. */
    private final Interpolator gestureInterpolator = new PathInterpolator(0.1f, 0.1f, 0.0f, 1.0f);
    /** Докатывание после отпускания — M3 «emphasized». */
    private final Interpolator postCommitInterpolator = createEmphasizedInterpolator();
    private final Interpolator verticalMoveInterpolator = new DecelerateInterpolator();

    private float closingAlpha = 1.0f;
    private float scrimAlphaMultiplier = 1.0f;

    private static Interpolator createEmphasizedInterpolator() {
        final Path path = new Path();
        path.moveTo(0.0f, 0.0f);
        path.cubicTo(0.05f, 0.0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1.0f, 1.0f, 1.0f);
        return new PathInterpolator(path);
    }

    public static void drawTransitionBackground(Canvas canvas, Drawable drawable, int width, int height, Rect tmp) {
        if (drawable == null) {
            return;
        }
        drawable.copyBounds(tmp);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        drawable.setBounds(tmp);
    }

    /** Фон нижележащего фрагмента — чтобы между разъехавшимися экранами не было дырки. */
    public static Drawable getTransitionBackground(List<BaseFragment> stack, BaseFragment fragment) {
        if (stack != null && stack.size() > 1) {
            fragment = stack.get(stack.size() - 2);
        }
        while (fragment instanceof ViewPagerActivity) {
            final BaseFragment visible = ((ViewPagerActivity) fragment).getCurrentVisibleFragment();
            if (visible == null || visible == fragment) {
                break;
            }
            fragment = visible;
        }
        if (fragment instanceof ProfileActivity) {
            return new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, fragment.getResourceProvider()));
        }
        final View view = fragment != null ? fragment.fragmentView : null;
        Drawable background = view != null ? view.getBackground() : null;
        if (background instanceof ColorDrawable && Color.alpha(((ColorDrawable) background).getColor()) == 0) {
            background = null;
        }
        if (background != null) {
            return background;
        }
        return new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, fragment != null ? fragment.getResourceProvider() : null));
    }

    private float getYOffset(float currentHeight, float touchY) {
        final float height = startClosingRect.height();
        final float dy = touchY - initialTouchY;
        final float half = height / 2.0f;
        if (half <= 0) {
            return 0;
        }
        final float sign = dy < 0.0f ? -1.0f : 1.0f;
        final float t = verticalMoveInterpolator.getInterpolation(Math.min(half, Math.abs(dy)) / half);
        return sign * t * Math.max(0.0f, (height - currentHeight) / 2.0f - AndroidUtilities.dp(8.0f));
    }

    private static void interpolate(RectF out, RectF from, RectF to, float t) {
        out.set(
                AndroidUtilities.lerp(from.left, to.left, t),
                AndroidUtilities.lerp(from.top, to.top, t),
                AndroidUtilities.lerp(from.right, to.right, t),
                AndroidUtilities.lerp(from.bottom, to.bottom, t)
        );
    }

    private static void scaleCentered(RectF rect, float scale) {
        final float cx = rect.centerX();
        final float cy = rect.centerY();
        final float w = rect.width() * scale / 2.0f;
        final float h = rect.height() * scale / 2.0f;
        rect.set(cx - w, cy - h, cx + w, cy + h);
    }

    public float getClosingAlpha() {
        return closingAlpha;
    }

    public void getClosingRect(RectF out) {
        out.set(currentClosingRect);
    }

    public float getClosingScale() {
        return startClosingRect.width() <= 0 ? 1f : currentClosingRect.width() / startClosingRect.width();
    }

    public float getCornerRadius() {
        return currentCornerRadius;
    }

    public void getEnteringRect(RectF out) {
        out.set(currentEnteringRect);
    }

    public float getEnteringScale() {
        return startClosingRect.width() <= 0 ? 1f : currentEnteringRect.width() / startClosingRect.width();
    }

    public int getPostCommitDuration() {
        return 375;
    }

    public float getProgress() {
        return progress;
    }

    public int getScrimAlpha(boolean dark) {
        return (int) ((dark ? 0.8f : 0.2f) * 255.0f * scrimAlphaMultiplier);
    }

    public float getSlideDistance() {
        return interpolatedProgress * AndroidUtilities.dp(336.0f);
    }

    public void prepareCommit() {
        commitStartClosingRect.set(currentClosingRect);
        commitStartEnteringRect.set(currentEnteringRect);
        commitTargetEnteringRect.set(startClosingRect);
        commitTargetClosingRect.set(startClosingRect);
        commitTargetClosingRect.offset(currentClosingRect.left + AndroidUtilities.dp(96.0f), 0.0f);
    }

    public void reset() {
        startClosingRect.setEmpty();
        targetClosingRect.setEmpty();
        currentClosingRect.setEmpty();
        startEnteringRect.setEmpty();
        targetEnteringRect.setEmpty();
        currentEnteringRect.setEmpty();
        commitStartClosingRect.setEmpty();
        commitTargetClosingRect.setEmpty();
        commitStartEnteringRect.setEmpty();
        commitTargetEnteringRect.setEmpty();
        progress = 0.0f;
        interpolatedProgress = 0.0f;
        closingAlpha = 1.0f;
        scrimAlphaMultiplier = 1.0f;
        initialTouchY = 0.0f;
        startCornerRadius = 0.0f;
        targetCornerRadius = 0.0f;
        currentCornerRadius = 0.0f;
    }

    public void setScrimAlphaMultiplier(float value) {
        scrimAlphaMultiplier = Utilities.clamp01(value);
    }

    /**
     * @param toRight жест от левого края — карточка прижимается к правому краю
     * @param cornerRadius стартовый радиус (физические углы экрана)
     */
    public void start(int width, int height, float touchY, boolean toRight, float cornerRadius) {
        initialTouchY = touchY;
        progress = 0.0f;
        interpolatedProgress = 0.0f;
        closingAlpha = 1.0f;
        scrimAlphaMultiplier = 1.0f;
        startCornerRadius = cornerRadius;
        targetCornerRadius = Math.max(cornerRadius, AndroidUtilities.dp(40.0f));
        currentCornerRadius = cornerRadius;

        startClosingRect.set(0.0f, 0.0f, Math.max(1, width), Math.max(1, height));
        targetClosingRect.set(startClosingRect);
        scaleCentered(targetClosingRect, 0.85f);
        if (toRight) {
            targetClosingRect.offset(startClosingRect.right - targetClosingRect.right - AndroidUtilities.dp(8.0f), 0.0f);
        }
        currentClosingRect.set(startClosingRect);

        startEnteringRect.set(startClosingRect);
        scaleCentered(startEnteringRect, Utilities.clamp(
                (startClosingRect.height() - cornerRadius * 2.0f) / startClosingRect.height(), 0.95f, 0.85f));
        startEnteringRect.offset(-Math.max(startEnteringRect.width() * 0.14999998f, AndroidUtilities.dp(96.0f)), 0.0f);
        targetEnteringRect.set(startEnteringRect);
        scaleCentered(targetEnteringRect, 0.85f);
        currentEnteringRect.set(startEnteringRect);
    }

    public void update(float rawProgress, float touchY) {
        progress = Utilities.clamp01(rawProgress);
        interpolatedProgress = gestureInterpolator.getInterpolation(progress);
        closingAlpha = 1.0f;
        scrimAlphaMultiplier = 1.0f;
        interpolate(currentClosingRect, startClosingRect, targetClosingRect, interpolatedProgress);
        final float yOffset = getYOffset(currentClosingRect.height(), touchY);
        currentClosingRect.offset(0.0f, yOffset);
        interpolate(currentEnteringRect, startEnteringRect, targetEnteringRect, interpolatedProgress);
        currentEnteringRect.offset(0.0f, yOffset);
        currentCornerRadius = AndroidUtilities.lerp(startCornerRadius, targetCornerRadius, interpolatedProgress);
    }

    public void updateCommitProgress(float rawProgress) {
        final float t = Utilities.clamp01(rawProgress);
        final float interpolated = postCommitInterpolator.getInterpolation(t);
        closingAlpha = Math.max(1.0f - 5.0f * t, 0.0f);
        scrimAlphaMultiplier = 1.0f - t;
        interpolate(currentClosingRect, commitStartClosingRect, commitTargetClosingRect, interpolated);
        interpolate(currentEnteringRect, commitStartEnteringRect, commitTargetEnteringRect, interpolated);
        currentCornerRadius = AndroidUtilities.lerp(targetCornerRadius, startCornerRadius, interpolated);
    }
}
