package app.regram.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.List;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Скролл со списком пунктов бокового меню.
 * exteraGram: {@code com/exteragram/messenger/drawer/DrawerMenuView.java} (161 строка).
 *
 * Сверху — градиент затухания высотой 16dp, он рисуется только когда список прокручен.
 */
public class DrawerMenuView extends ScrollView {

    /** Ровно 1 физический пиксель. */
    private static final float DIVIDER_HEIGHT_DP = 1.0f / AndroidUtilities.density;
    private static final int COLOR_KEY_BACKGROUND = Theme.key_windowBackgroundWhite;

    private final LinearLayout container;
    private final Paint topGradientPaint = new Paint();

    private LinearGradient topGradient;
    private int lastGradientColor;
    private Runnable onItemClick;

    public DrawerMenuView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, AndroidUtilities.dp(8.0f), 0,
                AndroidUtilities.dp(8.0f) + AndroidUtilities.navigationBarHeight);
        addView(container, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        updateGradient();
    }

    public void setOnItemClick(Runnable onItemClick) {
        this.onItemClick = onItemClick;
    }

    public void clearMenu() {
        container.removeAllViews();
    }

    /**
     * exteraGram: {@code DrawerMenuView.rebuildMenu} — идёт по сохранённой раскладке,
     * разделитель ставится «отложенно», поэтому висящие в начале и в конце схлопываются.
     */
    public void rebuildMenu(int currentAccount, BaseFragment fragment) {
        clearMenu();
        final MainMenuHelper.MenuContext ctx = MainMenuHelper.createMenuContext(currentAccount, fragment);
        boolean hasAnyItem = false;
        boolean dividerPending = false;
        final List<Integer> layout = MainMenuLayout.getLayout();
        for (int i = 0; i < layout.size(); i++) {
            final Integer id = layout.get(i);
            if (id == null) {
                continue;
            }
            if (id == MainMenuItem.DIVIDER.getId()) {
                if (hasAnyItem) {
                    dividerPending = true;
                }
                continue;
            }
            final List<MainMenuHelper.MenuItemInfo> items = MainMenuHelper.resolveDrawerMenuItems(id, ctx);
            if (items.isEmpty()) {
                continue;
            }
            if (dividerPending) {
                final View divider = new View(getContext());
                divider.setBackgroundColor(Theme.getDividerColor(null));
                container.addView(divider, createDividerLayoutParams());
                dividerPending = false;
            }
            for (MainMenuHelper.MenuItemInfo info : items) {
                final DrawerMenuItemView itemView = new DrawerMenuItemView(getContext());
                itemView.setMenuItem(id, currentAccount, info.iconRes(), info.text());
                itemView.setOnClickListener(v -> {
                    if (onItemClick != null) {
                        onItemClick.run();
                    }
                    if (info.onClick() != null) {
                        info.onClick().run();
                    }
                });
                if (info.onLongClick() != null) {
                    itemView.setOnLongClickListener(v -> {
                        if (onItemClick != null) {
                            onItemClick.run();
                        }
                        info.onLongClick().run();
                        return true;
                    });
                }
                container.addView(itemView);
            }
            hasAnyItem = true;
        }
        // re:gram plugins: пункты плагинов (DRAWER_MENU) в конце шторки.
        app.regram.plugins.menus.MenuInjector.appendDrawerItems(container, currentAccount, onItemClick);
    }

    public void updateColors() {
        for (int i = 0; i < container.getChildCount(); i++) {
            final View child = container.getChildAt(i);
            if (child instanceof DrawerMenuItemView) {
                ((DrawerMenuItemView) child).updateColors();
            } else {
                child.setBackgroundColor(Theme.getDividerColor(null));
            }
        }
        updateGradient();
    }

    public void updateUnreadCounters(int currentAccount) {
        for (int i = 0; i < container.getChildCount(); i++) {
            final View child = container.getChildAt(i);
            if (child instanceof DrawerMenuItemView) {
                ((DrawerMenuItemView) child).updateUnreadCounter(currentAccount);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (getScrollY() > 0) {
            canvas.save();
            canvas.translate(0.0f, getScrollY());
            canvas.drawRect(0.0f, 0.0f, getWidth(), AndroidUtilities.dp(16.0f), topGradientPaint);
            canvas.restore();
        }
    }

    private static LinearLayout.LayoutParams createDividerLayoutParams() {
        // gravity 87 из exteraGram = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL
        return LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, DIVIDER_HEIGHT_DP,
                Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 12, 8, 12, 8);
    }

    private void updateGradient() {
        final int color = Theme.getColor(COLOR_KEY_BACKGROUND);
        if (topGradient != null && color == lastGradientColor) {
            return;
        }
        lastGradientColor = color;
        topGradient = new LinearGradient(0.0f, 0.0f, 0.0f, AndroidUtilities.dp(16.0f),
                new int[]{color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP);
        topGradientPaint.setShader(topGradient);
        invalidate();
    }
}
