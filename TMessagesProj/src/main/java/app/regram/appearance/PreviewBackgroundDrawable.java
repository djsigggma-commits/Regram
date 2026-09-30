package app.regram.appearance;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Подложка превью: скруглённый прямоугольник с контуром, который «загорается»
 * акцентом, когда карточка выбрана. Порт
 * {@code com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable} (12.9.0).
 *
 * Цвета берутся из {@link PreviewColors} прямо в draw(), поэтому смена темы
 * подхватывается сама.
 */
public class PreviewBackgroundDrawable extends Drawable {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final float radius;

    private float selectionProgress;
    private int alpha = 0xFF;

    public PreviewBackgroundDrawable() {
        this(12f);
    }

    public PreviewBackgroundDrawable(float cornerRadiusDp) {
        strokePaint.setStyle(Paint.Style.STROKE);
        radius = AndroidUtilities.dp(cornerRadiusDp);
    }

    /** Контур наружу отдан затем же, зачем у exteraGram: превью подкрашивают его вручную. */
    public Paint getStrokePaint() {
        return strokePaint;
    }

    public float getSelectionProgress() {
        return selectionProgress;
    }

    /** 0 — обычная карточка, 1 — выбранная: контур толще и акцентного цвета. */
    public void setSelectionProgress(float progress) {
        if (selectionProgress == progress) {
            return;
        }
        selectionProgress = progress;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        backgroundPaint.setColor(PreviewColors.getBackgroundColor());
        strokePaint.setColor(ColorUtils.blendARGB(PreviewColors.getOutlineColor(),
                Theme.getColor(Theme.key_windowBackgroundWhiteValueText), selectionProgress));
        // setColor затирает альфу, поэтому setAlpha применяется после него, а не один раз.
        if (alpha != 0xFF) {
            backgroundPaint.setAlpha(backgroundPaint.getAlpha() * alpha / 0xFF);
            strokePaint.setAlpha(strokePaint.getAlpha() * alpha / 0xFF);
        }
        strokePaint.setStrokeWidth(AndroidUtilities.dp(AndroidUtilities.lerp(0.5f, 2f, selectionProgress)));

        float half = strokePaint.getStrokeWidth() / 2f;
        rectF.set(getBounds().left + half, getBounds().top + half,
                getBounds().right - half, getBounds().bottom - half);
        canvas.drawRoundRect(rectF, radius, radius, backgroundPaint);
        canvas.drawRoundRect(rectF, radius, radius, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) {
            return;
        }
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        backgroundPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
