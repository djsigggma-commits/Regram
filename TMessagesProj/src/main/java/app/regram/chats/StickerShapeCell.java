package app.regram.chats;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.TextPaint;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

/**
 * Плитки выбора формы стикеров: Default / Rounded / Message.
 * Порт компонента exteraGram, значение хранится в {@link ChatsConfig#stickerShape}.
 */
public class StickerShapeCell extends LinearLayout {

    private static class StickerShape extends FrameLayout {

        private final boolean isRounded;
        private final boolean isRoundedAsMsg;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;

        public StickerShape(Context context, boolean rounded, boolean roundedAsMsg) {
            super(context);
            setWillNotDraw(false);

            isRounded = rounded;
            isRoundedAsMsg = roundedAsMsg;

            textPaint.setTextSize(AndroidUtilities.dp(13));

            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
            outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(1f)));

            int shape = ChatsConfig.stickerShape();
            setSelected(!isRounded && !isRoundedAsMsg && shape == 0
                    || isRounded && shape == 1
                    || isRoundedAsMsg && shape == 2, false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int color = Theme.getColor(Theme.key_switchTrack);
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);

            rect.set(0, 0, getMeasuredWidth(), AndroidUtilities.dp(80));
            Theme.dialogs_onlineCirclePaint.setColor(Color.argb(20, r, g, b));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Theme.dialogs_onlineCirclePaint);

            float stroke = outlinePaint.getStrokeWidth() / 2;
            rect.set(stroke, stroke, getMeasuredWidth() - stroke, AndroidUtilities.dp(80) - stroke);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), outlinePaint);

            String text = isRounded
                    ? LocaleController.getString(R.string.OEChatsStickerShapeRounded)
                    : isRoundedAsMsg
                    ? LocaleController.getString(R.string.OEChatsStickerShapeMessage)
                    : LocaleController.getString(R.string.Default);
            int width = (int) Math.ceil(textPaint.measureText(text));
            canvas.drawText(text, (getMeasuredWidth() - width) >> 1, AndroidUtilities.dp(102), textPaint);

            rect.set(AndroidUtilities.dp(10), AndroidUtilities.dp(10), getMeasuredWidth() - AndroidUtilities.dp(10), AndroidUtilities.dp(70));
            Theme.dialogs_onlineCirclePaint.setColor(Color.argb(90, r, g, b));
            if (!isRounded && !isRoundedAsMsg) {
                canvas.drawRect(rect, Theme.dialogs_onlineCirclePaint);
            } else if (isRounded) {
                canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Theme.dialogs_onlineCirclePaint);
            } else {
                @SuppressLint("DrawAllocation") Rect bounds = new Rect();
                rect.round(bounds);
                int rad = AndroidUtilities.dp(SharedConfig.bubbleRadius);
                @SuppressLint("DrawAllocation") ShapeDrawable drawable = new ShapeDrawable(new RoundRectShape(
                        new float[]{rad, rad, rad, rad, rad, rad, rad / 3f, rad / 3f}, null, null));
                drawable.getPaint().setColor(Theme.dialogs_onlineCirclePaint.getColor());
                drawable.setBounds(bounds);
                drawable.draw(canvas);
            }
        }

        private void setProgress(float progress) {
            this.progress = progress;
            textPaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                    Theme.getColor(Theme.key_windowBackgroundWhiteValueText), progress));
            outlinePaint.setColor(ColorUtils.blendARGB(
                    ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F),
                    Theme.getColor(Theme.key_windowBackgroundWhiteValueText), progress));
            outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(AndroidUtilities.lerp(1f, 2f, progress))));
            invalidate();
        }

        private void setSelected(boolean selected, boolean animate) {
            float to = selected ? 1 : 0;
            if (to == progress && animate) {
                return;
            }
            if (animate) {
                ValueAnimator animator = ValueAnimator.ofFloat(progress, to).setDuration(250);
                animator.setInterpolator(Easings.easeInOutQuad);
                animator.addUpdateListener(animation -> setProgress((Float) animation.getAnimatedValue()));
                animator.start();
            } else {
                setProgress(to);
            }
        }
    }

    private final StickerShape[] stickerShape = new StickerShape[3];

    public StickerShapeCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(10), AndroidUtilities.dp(13), 0);

        for (int a = 0; a < 3; a++) {
            boolean rounded = a == 1;
            boolean roundedAsMsg = a == 2;
            stickerShape[a] = new StickerShape(context, rounded, roundedAsMsg);
            ScaleStateListAnimator.apply(stickerShape[a], 0.03f, 1.5f);
            addView(stickerShape[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, 8, 0, 8, 0));
            stickerShape[a].setOnClickListener(v -> {
                for (int b = 0; b < 3; b++) {
                    stickerShape[b].setSelected(v == stickerShape[b], true);
                }
                ChatsConfig.stickerShape.setConfigInt(rounded ? 1 : (roundedAsMsg ? 2 : 0));
                updateStickerPreview();
            });
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (int a = 0; a < 3; a++) {
            if (stickerShape[a] != null) {
                stickerShape[a].invalidate();
            }
        }
    }

    /** Вызывается после смены формы — чтобы обновить превью переписки. */
    protected void updateStickerPreview() {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(130), MeasureSpec.EXACTLY));
    }
}
