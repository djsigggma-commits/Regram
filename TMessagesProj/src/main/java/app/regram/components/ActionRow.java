package app.regram.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ряд круглых кнопок-действий для выпадающего меню.
 *
 * Показывает не больше {@link #MAX_ITEMS} кнопок: сначала доступные, затем
 * недоступные (они рисуются полупрозрачными и не реагируют на нажатие).
 */
@SuppressLint("ViewConstructor")
public class ActionRow extends FrameLayout {

    public static final int MAX_ITEMS = 4;

    private static final int HORIZONTAL_PADDING_DP = 10;
    private static final int ITEM_SIZE_DP = 40;
    private static final int GAP_DP = 8;

    private static final float DISABLED_ALPHA = 0.5f;
    private static final int ENTER_STEP_DELAY = 35;
    private static final int ENTER_START_DELAY = 100;
    private static final int ENTER_DURATION = 400;

    public static class ActionItem {

        public final int icon;
        public final boolean enabled;
        public final View.OnClickListener action;
        public final View.OnLongClickListener longAction;

        public ActionItem(int icon, boolean enabled, View.OnClickListener action) {
            this(icon, enabled, action, null);
        }

        public ActionItem(int icon, boolean enabled, View.OnClickListener action, View.OnLongClickListener longAction) {
            this.icon = icon;
            this.enabled = enabled;
            this.action = action;
            this.longAction = longAction;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            return icon == ((ActionItem) other).icon;
        }

        @Override
        public int hashCode() {
            return Objects.hash(icon);
        }
    }

    private final FrameLayout buttonsView;
    private final List<ActionItem> currentItems = new ArrayList<>();

    public ActionRow(Context context, Theme.ResourcesProvider resourcesProvider, List<ActionItem> items) {
        super(context);

        buttonsView = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int count = getChildCount();
                int free = (right - left) - AndroidUtilities.dp(count * ITEM_SIZE_DP + HORIZONTAL_PADDING_DP * 2);
                int gaps = Math.max(1, count - 1);
                for (int a = 0; a < count; a++) {
                    View child = getChildAt(a);
                    int x = AndroidUtilities.dp(a * ITEM_SIZE_DP + HORIZONTAL_PADDING_DP) + (free / gaps) * a;
                    int y = AndroidUtilities.dp(GAP_DP);
                    child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
                }
            }
        };
        addView(buttonsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        updateItems(items, resourcesProvider);
    }

    public void updateItems(List<ActionItem> items, Theme.ResourcesProvider resourcesProvider) {
        buttonsView.removeAllViews();
        currentItems.clear();

        List<ActionItem> disabled = new ArrayList<>();
        for (ActionItem item : items) {
            if (!item.enabled || currentItems.size() >= MAX_ITEMS) {
                disabled.add(item);
                continue;
            }
            addButton(resourcesProvider, item, currentItems.size());
            currentItems.add(item);
        }
        for (ActionItem item : disabled) {
            if (currentItems.size() >= MAX_ITEMS) {
                return;
            }
            addButton(resourcesProvider, item, currentItems.size());
            currentItems.add(item);
        }
    }

    public boolean isItemPresent(int icon) {
        for (int a = 0; a < buttonsView.getChildCount(); a++) {
            Object tag = buttonsView.getChildAt(a).getTag();
            if (tag instanceof ActionItem && ((ActionItem) tag).icon == icon) {
                return true;
            }
        }
        return false;
    }

    private void addButton(Theme.ResourcesProvider resourcesProvider, ActionItem item, int index) {
        Context context = getContext();
        ImageView imageView = new ImageView(context) {
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                setAlpha(enabled ? 1.0f : DISABLED_ALPHA);
            }
        };
        ScaleStateListAnimator.apply(imageView, 0.15f, 1.5f);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setEnabled(item.enabled);
        imageView.setImageDrawable(Objects.requireNonNull(ContextCompat.getDrawable(context, item.icon)).mutate());
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        imageView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP));
        imageView.setOnClickListener(item.action);
        if (item.longAction != null) {
            imageView.setOnLongClickListener(item.longAction);
        }
        imageView.setTag(item);
        imageView.setAlpha(0.0f);
        imageView.setTranslationX(AndroidUtilities.dp(12));
        buttonsView.addView(imageView, LayoutHelper.createFrame(ITEM_SIZE_DP, ITEM_SIZE_DP, Gravity.LEFT | Gravity.TOP));
        imageView.post(() -> imageView.animate()
                .alpha(item.enabled ? 1.0f : DISABLED_ALPHA)
                .translationX(0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setStartDelay(index * ENTER_STEP_DELAY + ENTER_START_DELAY)
                .setDuration(ENTER_DURATION)
                .start());
    }
}
