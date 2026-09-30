package app.regram.appearance;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

/**
 * Выбор формы плавающей кнопки: две карточки-превью, круг и «squircle».
 * Порт {@code com.exteragram.messenger.preferences.appearance.components.FabShapeCell} (12.9.0).
 *
 * Значение живёт в {@link AppearanceConfig#squareFab} и реально применяется:
 * FragmentFloatingButton:74 берёт из него форму и радиус.
 */
@SuppressLint("ViewConstructor")
public class FabShapeCell extends LinearLayout {

    /** Одна карточка: макет списка чатов с кнопкой нужной формы в правом нижнем углу. */
    private static class FabShape extends FrameLayout {

        private final PreviewBackgroundDrawable backgroundDrawable = new PreviewBackgroundDrawable(12f);
        private final RectF rect = new RectF();
        private final boolean squareFab;

        private float progress;
        private ValueAnimator animator;

        FabShape(Context context, boolean squareFab) {
            super(context);
            setWillNotDraw(false);
            this.squareFab = squareFab;
            setBackground(backgroundDrawable);
            setSelected(squareFab == AppearanceConfig.squareFab(), false);
        }

        private void setProgress(float value) {
            progress = value;
            backgroundDrawable.setSelectionProgress(value);
        }

        void setSelected(boolean selected, boolean animated) {
            float to = selected ? 1f : 0f;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (!animated) {
                setProgress(to);
                return;
            }
            if (to == progress) {
                return;
            }
            animator = ValueAnimator.ofFloat(progress, to).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(a -> setProgress((Float) a.getAnimatedValue()));
            animator.start();
        }

        @Override
        @SuppressLint("DrawAllocation")
        protected void onDraw(Canvas canvas) {
            final int centerX = AndroidUtilities.dp(22);
            final int half = centerX / 2;
            int centerY = AndroidUtilities.dp(21);

            for (int a = 0; a < 2; a++) {
                centerY += AndroidUtilities.dp(a == 0 ? 0 : 32);

                Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(false));
                float corners = AppearanceConfig.getAvatarCorners(half * 2f);
                canvas.drawRoundRect(centerX - half, centerY - half, centerX + half, centerY + half,
                        corners, corners, Theme.dialogs_onlineCirclePaint);

                for (int b = 0; b < 2; b++) {
                    Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(b == 0));
                    int shift = b * 10;
                    rect.set(AndroidUtilities.dp(41), centerY - AndroidUtilities.dp(7 - shift),
                            getMeasuredWidth() - AndroidUtilities.dp(b == 0 ? 70 : 55),
                            centerY - AndroidUtilities.dp(3 - shift));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2),
                            Theme.dialogs_onlineCirclePaint);
                }
            }

            Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            rect.set(getMeasuredWidth() - AndroidUtilities.dp(42), getMeasuredHeight() - AndroidUtilities.dp(42),
                    getMeasuredWidth() - AndroidUtilities.dp(12), getMeasuredHeight() - AndroidUtilities.dp(12));
            float fabCorners = AndroidUtilities.dp(squareFab ? 9 : 100);
            canvas.drawRoundRect(rect, fabCorners, fabCorners, Theme.dialogs_onlineCirclePaint);

            Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.filled_fab_compose_32);
            if (icon != null) {
                icon.setColorFilter(new PorterDuffColorFilter(
                        Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN));
                icon.setBounds(getMeasuredWidth() - AndroidUtilities.dp(37), getMeasuredHeight() - AndroidUtilities.dp(37),
                        getMeasuredWidth() - AndroidUtilities.dp(17), getMeasuredHeight() - AndroidUtilities.dp(17));
                icon.draw(canvas);
            }
        }
    }

    private final FabShape[] fabShape = new FabShape[2];

    private Runnable onChanged;
    private boolean needDivider = true;

    public FabShapeCell(Context context) {
        this(context, null);
    }

    /**
     * @param onChanged зовётся после сохранения настройки — экрану обычно нужно
     *                  пересобрать фрагменты, чтобы кнопка сменила форму сразу
     */
    public FabShapeCell(Context context, Runnable onChanged) {
        super(context);
        this.onChanged = onChanged;
        setWillNotDraw(false);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(15), AndroidUtilities.dp(13), AndroidUtilities.dp(21));

        for (int a = 0; a < 2; a++) {
            final boolean square = a == 1;
            fabShape[a] = new FabShape(context, square);
            ScaleStateListAnimator.apply(fabShape[a], 0.03f, 1.5f);
            addView(fabShape[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                    0.5f, 8, 0, 8, 0));
            fabShape[a].setOnClickListener(v -> {
                for (int b = 0; b < 2; b++) {
                    fabShape[b].setSelected(v == fabShape[b], true);
                }
                AppearanceConfig.squareFab.setConfigBool(square);
                if (this.onChanged != null) {
                    this.onChanged.run();
                }
            });
        }
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public void setNeedDivider(boolean needDivider) {
        if (this.needDivider != needDivider) {
            this.needDivider = needDivider;
            invalidate();
        }
    }

    /** Перечитать настройку, если её поменяли снаружи (например сбросом настроек). */
    public void updateSelection(boolean animated) {
        for (int a = 0; a < 2; a++) {
            fabShape[a].setSelected((a == 1) == AppearanceConfig.squareFab(), animated);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        for (int a = 0; a < 2; a++) {
            if (fabShape[a] != null) {
                fabShape[a].invalidate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!needDivider) {
            return;
        }
        canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1,
                Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY));
    }
}
