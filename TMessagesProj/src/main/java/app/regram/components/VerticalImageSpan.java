package app.regram.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class VerticalImageSpan extends ImageSpan {

    public VerticalImageSpan(Drawable drawable) {
        super(drawable);
    }

    public static SpannableStringBuilder createSpan(Context context, int resId, String text, String placeholder, int colorKey, Theme.ResourcesProvider resourcesProvider) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        List<Integer> positions = new ArrayList<>();
        int index = text.indexOf(placeholder);
        while (index >= 0) {
            positions.add(index);
            index = text.indexOf(placeholder, index + 1);
        }
        if (positions.isEmpty()) {
            return builder;
        }
        Drawable drawable = context.getDrawable(resId);
        if (drawable == null) {
            return builder;
        }
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(colorKey, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        for (int position : positions) {
            builder.setSpan(new VerticalImageSpan(drawable), position, position + placeholder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        Drawable drawable = getDrawable();
        canvas.save();
        Paint.FontMetricsInt fontMetrics = paint.getFontMetricsInt();
        int center = fontMetrics.descent - (fontMetrics.descent - fontMetrics.ascent) / 2;
        int half = (drawable.getBounds().bottom - drawable.getBounds().top) / 2;
        canvas.translate(x, y + center - half);
        if (LocaleController.isRTL) {
            canvas.scale(-1.0f, 1.0f, drawable.getIntrinsicWidth() >> 1, drawable.getIntrinsicHeight() >> 1);
        }
        drawable.draw(canvas);
        canvas.restore();
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fontMetricsInt) {
        Rect bounds = getDrawable().getBounds();
        if (fontMetricsInt != null) {
            Paint.FontMetricsInt fontMetrics = paint.getFontMetricsInt();
            int center = fontMetrics.ascent + (fontMetrics.descent - fontMetrics.ascent) / 2;
            int half = (bounds.bottom - bounds.top) / 2;
            fontMetricsInt.ascent = center - half;
            fontMetricsInt.top = center - half;
            fontMetricsInt.bottom = center + half;
            fontMetricsInt.descent = center + half;
        }
        return bounds.right;
    }
}
