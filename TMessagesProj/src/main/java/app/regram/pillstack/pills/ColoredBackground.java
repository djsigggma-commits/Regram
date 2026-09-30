package app.regram.pillstack.pills;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Скруглённый фон пилюли курса: вертикальный градиент из двух цветов.
 *
 * Поверх градиента exteraGram (crypto/utils/ColoredBackground) кладёт обводку в
 * толщину волоса — светлую сверху, гаснущую к середине и чуть заметную снизу.
 * Она рисуется только в тёмной теме и не в Monet: на светлом фоне и на обоях
 * системной палитры её попросту не видно, а пилюля от неё грязнится.
 */
public class ColoredBackground extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();

    /** Цвета по умолчанию — BLUE_LIGHT exteraGram: с ними идёт пилюля TON. */
    public ColoredBackground() {
        this(0xFF1BA4ED, 0xFF1488E1);
    }

    public ColoredBackground(int colorTop, int colorBottom) {
        // Высота градиента у exteraGram фиксированная — dp(28), высота самой пилюли,
        // а не bounds: при загрузке пилюля короче, и градиент не должен ехать.
        paint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(28),
                new int[]{colorTop, colorBottom}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        strokePaint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(28),
                new int[]{0x4DFFFFFF, 0x00FFFFFF, 0x1AFFFFFF}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final float radius = AndroidUtilities.dp(14);
        rectF.set(getBounds());
        canvas.drawRoundRect(rectF, radius, radius, paint);
        if (!Theme.isCurrentThemeDark() || isMonetTheme()) {
            return;
        }
        final float width = AndroidUtilities.dp(1);
        strokePaint.setStrokeWidth(width);
        rectF.inset(width / 2f, width / 2f);
        canvas.drawRoundRect(rectF, radius, radius, strokePaint);
    }

    /** Проверка вида Theme.isCurrentThemeMonet(): у нас она живёт в ThemeInfo. */
    private static boolean isMonetTheme() {
        Theme.ThemeInfo active = Theme.getActiveTheme();
        return active != null && active.isMonet();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
