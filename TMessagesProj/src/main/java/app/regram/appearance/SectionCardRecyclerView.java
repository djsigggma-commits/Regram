package app.regram.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurredRecyclerView;

/**
 * openExtera: список настроек с единой скруглённой карточкой-секцией и мягкой тенью-elevation,
 * точно как в exteraGram 12.9.0.
 *
 * <p>Геометрию (группировку смежных ячеек в секцию, горизонтальные отступы, раздельные радиусы
 * top/bottom крайних ячеек, клип к области видимости) полностью обеспечивает уже присутствующий
 * в проекте штатный механизм {@code RecyclerListView} — {@code ListSectionsDecoration} +
 * {@code drawSectionsBackgrounds()}, включаемый через {@code listView.setSections(...)}. Он
 * вызывает {@link #drawBackgroundRect(Canvas, RectF, float, float, float)} для каждой секции,
 * рисуя фон ПОД ячейками (в {@code ItemDecoration.onDraw}, до контента).</p>
 *
 * <p>Единственное отличие от стока: штатный {@code drawBackgroundRect} рисует тень только при
 * глобальном флаге {@code SharedConfig.shadowsInSections} (по умолчанию выключен). Здесь тень
 * форсируется ЛОКАЛЬНО — только для экранов настроек re:gram/Neko — без правки глобального
 * {@code RecyclerListView} и без изменения глобального конфига. Порт логики
 * {@code RecyclerListView.drawBackgroundRect} один-в-один, но с всегда включённой тенью.</p>
 *
 * <p>При {@code AppearanceConfig.sectionRadius() <= 0} метод делегирует в {@code super} —
 * поведение полностью стоковое (без карточек/тени), дефолт-safe.</p>
 */
public class SectionCardRecyclerView extends BlurredRecyclerView {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] radii = new float[8];

    // Тень-elevation включена всегда — именно она даёт «экстеровский» вид карточки.
    private boolean forceShadows = true;

    public SectionCardRecyclerView(Context context) {
        super(context);
    }

    public void setForceShadows(boolean value) {
        forceShadows = value;
    }

    /**
     * Вызывается штатным {@code drawSectionsBackgrounds} как {@code drawSectionBackground}-колбэк
     * (см. {@code setSections(..., this::drawBackgroundRect, ...)}). Рисует скруглённый фон секции
     * цветом {@code key_windowBackgroundWhite} с тенью-elevation.
     *
     * <p>Внутри — только Canvas-примитивы и {@link Theme#getColor}/{@link Theme#multAlpha}; никаких
     * виртуальных вызовов, ведущих обратно в отрисовку (иначе рекурсия/краш).</p>
     */
    @Override
    public void drawBackgroundRect(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha) {
        if (!forceShadows || AppearanceConfig.sectionRadius() <= 0) {
            // Дефолт-safe: сток (тень по глобальному флагу; при радиусе 0 карточек фактически нет).
            super.drawBackgroundRect(canvas, rect, topRadius, bottomRadius, alpha);
            return;
        }

        strokePaint.setColor(0);
        strokePaint.setShadowLayer(AndroidUtilities.dpf2(0.33f), 0, 0, Theme.multAlpha(0x0C000000, alpha));
        fillPaint.setShadowLayer(AndroidUtilities.dpf2(2f), 0, AndroidUtilities.dpf2(0.33f), Theme.multAlpha(0x0A000000, alpha));
        fillPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), alpha));

        if (topRadius == bottomRadius) {
            canvas.drawRoundRect(rect, topRadius, topRadius, strokePaint);
            canvas.drawRoundRect(rect, topRadius, topRadius, fillPaint);
        } else {
            path.rewind();
            radii[0] = radii[1] = radii[2] = radii[3] = topRadius;
            radii[4] = radii[5] = radii[6] = radii[7] = bottomRadius;
            path.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(path, strokePaint);
            canvas.drawPath(path, fillPaint);
        }
    }
}
