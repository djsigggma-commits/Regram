package app.regram.icons;

import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.graphics.PathParser;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

/**
 * Форма иконки приложения — та же, что у лаунчера.
 *
 * Перенос exteraGram 12.9.0:preferences/utils/IconShapeHelper.java. Логотип в
 * шапке настроек обрезается по маске системы: на Pixel он круг, на других
 * прошивках — сквиркл, капля или прямоугольник со скруглением. Смысл в том,
 * чтобы логотип в приложении выглядел так же, как на рабочем столе.
 *
 * Форма берётся из {@link AdaptiveIconDrawable#getIconMask()} (API 26+), а если
 * оттуда пусто — из системной строки {@code config_icon_mask}. Ничего не
 * нашлось — рисуем круг.
 */
public final class IconShapeHelper {

    /** Круг: запасной вариант, если форму системы получить не удалось. */
    private static final String DEFAULT_CIRCLE_PATH = "M50,0A50,50,0,0,1,50,100A50,50,0,0,1,50,0";

    private static Path cachedSystemPath;
    private static boolean isSystemPathSquare;
    private static boolean cacheInitialized;

    private static final RectF scratchRect = new RectF();
    private static final Matrix scratchMatrix = new Matrix();

    private IconShapeHelper() {
    }

    /**
     * Путь формы под размер {@code width}×{@code height} (в dp).
     *
     * @param cornerRadius радиус для прямоугольных масок, dp. У прямоугольной
     *                     маски системы углы острые, а логотипу это не идёт —
     *                     exteraGram в этом случае рисует скруглённый прямоугольник
     *                     сам, а не тянет системный путь.
     */
    public static Path getFinalIconShapePath(float width, float height, float cornerRadius) {
        boolean useSystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && IconPacksConfig.useSystemIconShape();
        if (useSystem && !cacheInitialized) {
            initSystemPathCache();
        }
        Path source = useSystem && cachedSystemPath != null ? cachedSystemPath : getDefaultPath();

        float w = AndroidUtilities.dpf2(width);
        float h = AndroidUtilities.dpf2(height);
        float radius = AndroidUtilities.dpf2(cornerRadius);
        if (radius <= 0f || !useSystem || !isSystemPathSquare) {
            return resizePath(source, w, h);
        }
        Path path = new Path();
        scratchRect.set(0f, 0f, w, h);
        path.addRoundRect(scratchRect, radius, radius, Path.Direction.CW);
        return path;
    }

    /** Забыть форму системы: её меняют в настройках лаунчера без перезапуска приложения. */
    public static void invalidate() {
        cacheInitialized = false;
        cachedSystemPath = null;
        isSystemPathSquare = false;
    }

    private static Path getDefaultPath() {
        return PathParser.createPathFromPathData(DEFAULT_CIRCLE_PATH);
    }

    private static void initSystemPathCache() {
        try {
            Path path = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ColorDrawable empty = new ColorDrawable(0);
                Path mask = new AdaptiveIconDrawable(empty, empty).getIconMask();
                if (mask != null && !mask.isEmpty()) {
                    path = new Path(mask);
                }
            }
            if (path == null) {
                Resources system = Resources.getSystem();
                int id = system.getIdentifier("config_icon_mask", "string", "android");
                if (id != 0) {
                    String data = system.getString(id);
                    if (!TextUtils.isEmpty(data)) {
                        path = PathParser.createPathFromPathData(data);
                    }
                }
            }
            if (path == null || path.isEmpty()) {
                cachedSystemPath = null;
                isSystemPathSquare = false;
            } else {
                cachedSystemPath = path;
                RectF bounds = new RectF();
                path.computeBounds(bounds, true);
                isSystemPathSquare = calculateIfShouldUseRoundedRect(path, bounds);
            }
        } catch (Exception e) {
            FileLog.e(e);
            cachedSystemPath = null;
            isSystemPathSquare = false;
        } finally {
            cacheInitialized = true;
        }
    }

    private static Path resizePath(Path path, float width, float height) {
        Path out = new Path();
        if (path == null || path.isEmpty() || width <= 0f || height <= 0f) {
            return out;
        }
        path.computeBounds(scratchRect, true);
        if (scratchRect.width() <= 0f || scratchRect.height() <= 0f) {
            return out;
        }
        scratchMatrix.reset();
        scratchMatrix.setRectToRect(scratchRect, new RectF(0f, 0f, width, height),
                Matrix.ScaleToFit.FILL);
        path.transform(scratchMatrix, out);
        return out;
    }

    /** Прямоугольная ли маска — точно или с точностью до острых углов. */
    private static boolean calculateIfShouldUseRoundedRect(Path path, RectF bounds) {
        if (path.isEmpty()) {
            return false;
        }
        Region clip = new Region((int) bounds.left, (int) bounds.top,
                (int) bounds.right, (int) bounds.bottom);
        Region region = new Region();
        region.setPath(path, clip);
        return region.isRect() || hasSharpCorners(path, bounds);
    }

    /**
     * Заполнены ли все четыре угла маски.
     *
     * Проба — квадрат в десятую часть меньшей стороны (но не меньше 3 px) в
     * каждом углу: если маска накрывает все четыре, это прямоугольник, пусть
     * и слегка скруглённый.
     */
    private static boolean hasSharpCorners(Path path, RectF bounds) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) {
            return false;
        }
        int probe = Math.max(3, (int) (Math.min(bounds.width(), bounds.height()) * 0.1f));
        Region region = new Region();
        region.setPath(path, new Region((int) bounds.left, (int) bounds.top,
                (int) bounds.right, (int) bounds.bottom));

        int left = (int) bounds.left;
        int top = (int) bounds.top;
        int right = (int) bounds.right;
        int bottom = (int) bounds.bottom;
        Rect[] corners = {
                new Rect(left, top, left + probe, top + probe),
                new Rect(right - probe, top, right, top + probe),
                new Rect(right - probe, bottom - probe, right, bottom),
                new Rect(left, bottom - probe, left + probe, bottom),
        };
        Region scratch = new Region();
        for (Rect corner : corners) {
            scratch.set(region);
            if (!scratch.op(corner, Region.Op.INTERSECT)) {
                return false;
            }
        }
        return true;
    }
}
