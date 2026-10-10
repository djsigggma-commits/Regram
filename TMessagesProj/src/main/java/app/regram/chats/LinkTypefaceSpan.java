package app.regram.chats;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;

public final class LinkTypefaceSpan extends MetricAffectingSpan {

    private final Typeface typeface;

    public LinkTypefaceSpan(@NonNull Typeface typeface) {
        this.typeface = typeface;
    }

    @Override
    public void updateMeasureState(@NonNull TextPaint paint) {
        paint.setTypeface(typeface);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint paint) {
        paint.setTypeface(typeface);
    }
}
