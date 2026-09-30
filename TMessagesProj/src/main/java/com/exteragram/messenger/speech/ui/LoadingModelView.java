package com.exteragram.messenger.speech.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerImageView;

import java.util.Locale;

/**
 * Экран загрузки языковой модели распознавания речи.
 *
 * Своей модели у форка нет, но плагины каталога создают эту вьюху сами
 * ({@code LoadingModelView(context)}) и двигают прогресс — им нужен ровно
 * этот класс под именем exteraGram.
 */
public class LoadingModelView extends FrameLayout {

    public static class ProgressView extends View {

        private final Paint in = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint out = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat progressT =
                new AnimatedFloat(this, 350L, CubicBezierInterpolator.EASE_OUT);
        private float progress;

        public ProgressView(Context context) {
            super(context);
            in.setColor(Theme.getColor(Theme.key_switchTrackChecked));
            out.setColor(Theme.multAlpha(Theme.getColor(Theme.key_switchTrackChecked), 0.2f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF rect = AndroidUtilities.rectTmp;
            float radius = AndroidUtilities.dp(3);
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(rect, radius, radius, out);
            rect.set(0, 0, getMeasuredWidth() * progressT.set(progress), getMeasuredHeight());
            canvas.drawRoundRect(rect, radius, radius, in);
        }

        public void setProgress(float value) {
            progress = value;
            invalidate();
        }
    }

    StickerImageView imageView;
    AnimatedTextView percentsTextView;
    ProgressView progressView;
    TextView title;
    TextView subtitle;

    public LoadingModelView(Context context) {
        super(context);

        imageView = new StickerImageView(context, UserConfig.selectedAccount);
        imageView.getImageReceiver().setAutoRepeat(1);
        imageView.setStickerPackName("UtyaDuck");
        imageView.setStickerNum(16);
        addView(imageView, LayoutHelper.createFrame(150, 150f, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        percentsTextView = new AnimatedTextView(context, false, true, true);
        percentsTextView.setAnimationProperties(0.35f, 0, 120, CubicBezierInterpolator.EASE_OUT);
        percentsTextView.setGravity(Gravity.CENTER);
        percentsTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        percentsTextView.setTextSize(AndroidUtilities.dp(24));
        percentsTextView.setTypeface(AndroidUtilities.bold());
        addView(percentsTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32f,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 176, 0, 0));

        progressView = new ProgressView(context);
        addView(progressView, LayoutHelper.createFrame(240, 5f,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 226, 0, 0));

        title = new TextView(context);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(LocaleController.getString(R.string.DownloadingModel));
        addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 261, 0, 0));

        subtitle = new TextView(context);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setText(LocaleController.getString(R.string.DownloadingModelInfo));
        addView(subtitle, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 289, 0, 0));

        setProgress(0f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(350), MeasureSpec.EXACTLY));
    }

    public void setProgress(float value) {
        int percent = (int) Math.ceil(MathUtils.clamp(value, 0f, 1f) * 100f);
        percentsTextView.cancelAnimation();
        percentsTextView.setText(percent == 100
                        ? LocaleController.getString(R.string.ModelUnzipping)
                        : String.format(Locale.US, "%d%%", percent),
                !LocaleController.isRTL);
        progressView.setProgress(value);
    }
}
