package org.telegram.ui.Components.blur3.drawable.color;

import androidx.annotation.ColorInt;

import org.telegram.ui.ActionBar.Theme;

public interface BlurredBackgroundColorProvider {
    @ColorInt int getShadowColor();
    @ColorInt int getBackgroundColor();
    @ColorInt int getStrokeColorTop();
    @ColorInt int getStrokeColorBottom();

    /**
     * Цвет сплошного контура (режим {@code GlassOutlineStyle.SOLID}).
     * По умолчанию цвет разделителя.
     */
    @ColorInt default int getStrokeColorFull() {
        return Theme.getDividerColor(null);
    }
}
