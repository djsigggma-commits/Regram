package app.regram.plugins.menus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import java.util.List;
import java.util.Map;

import app.regram.plugins.MenuItemRecord;
import app.regram.plugins.PluginsController;

/**
 * Подменю с пунктами плагинов внутри всплывающего меню «⋮» — swipe-back-слой,
 * как у exteraGram ({@code com.exteragram.messenger.plugins.ui.components.PluginsMenuWrapper}).
 *
 * Используется там, где пунктов может быть много и они не должны раздувать
 * основное меню: CHAT_ACTION_MENU (ChatActivity) и PROFILE_ACTION_MENU
 * (ProfileActivity). Владелец создаёт обёртку один раз, вешает её
 * {@link #getSwipeBack()} на {@code lazilyAddSwipeBackItem} и зовёт
 * {@link #rebuildMenu(List)} на {@code NotificationCenter.pluginMenuItemsUpdated}.
 */
public class PluginsMenuWrapper {

    private static final int GAP_ITEM_HEIGHT = 8;
    private static final int ITEM_HEIGHT = 48;
    private static final int SUBTITLE_ITEM_HEIGHT = 56;
    /** Выше этого подменю скроллится, а не растёт. Значение exteraGram. */
    private static final int MAX_HEIGHT_DP = 436;

    private final MenuItemRecord.MenuType menuType;
    private final Map<String, Object> contextData;
    private final Theme.ResourcesProvider resourcesProvider;
    private final LinearLayout swipeBack;
    private final LinearLayout menuItemsContainer;

    public PluginsMenuWrapper(PopupSwipeBackLayout swipeBackLayout,
                              MenuItemRecord.MenuType menuType,
                              Map<String, Object> contextData,
                              Theme.ResourcesProvider resourcesProvider) {
        this.menuType = menuType;
        this.contextData = contextData;
        this.resourcesProvider = resourcesProvider;

        Context context = swipeBackLayout.getContext();
        swipeBack = new LinearLayout(context);
        swipeBack.setOrientation(LinearLayout.VERTICAL);

        ActionBarMenuSubItem back = new ActionBarMenuSubItem(context, true, false, resourcesProvider);
        back.setItemHeight(44);
        back.setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back);
        back.getTextView().setPadding(
                LocaleController.isRTL ? 0 : AndroidUtilities.dp(40),
                0,
                LocaleController.isRTL ? AndroidUtilities.dp(40) : 0,
                0);
        back.setOnClickListener(v -> swipeBackLayout.closeForeground());
        swipeBack.addView(back, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        menuItemsContainer = new LinearLayout(context);
        menuItemsContainer.setOrientation(LinearLayout.VERTICAL);
        ScrollView scrollView = createScrollView(context);
        scrollView.addView(menuItemsContainer);
        swipeBack.addView(scrollView);

        rebuildMenu(null);
    }

    public LinearLayout getSwipeBack() {
        return swipeBack;
    }

    /** Есть ли что показывать: владелец по этому признаку прячет сам пункт. */
    public boolean hasItems() {
        return !currentItems().isEmpty();
    }

    private List<MenuItemRecord> currentItems() {
        return PluginsController.getInstance().getMenuItemsFor(menuType);
    }

    /** Переопределяется владельцем, чтобы закрыть всплывающее меню после клика. */
    public void closeMenu() {
    }

    /**
     * Пересобрать содержимое. {@code items == null} — взять текущий реестр.
     * Высота подменю подгоняется под содержимое до {@link #MAX_HEIGHT_DP}.
     */
    public void rebuildMenu(List<MenuItemRecord> items) {
        menuItemsContainer.removeAllViews();
        if (items == null) {
            items = currentItems();
        }
        Context context = menuItemsContainer.getContext();
        menuItemsContainer.addView(createGap(),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, GAP_ITEM_HEIGHT));

        int totalHeight = 0;
        for (final MenuItemRecord record : items) {
            if (record.text == null || record.text.isEmpty()) {
                continue;
            }
            if (!MenuInjector.isVisible(record, contextData)) {
                continue;
            }
            ActionBarMenuSubItem item = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
            item.setTextAndIcon(record.text, MenuInjector.resolveIcon(context, record.icon));
            item.setMinimumWidth(AndroidUtilities.dp(196));
            item.setOnClickListener(v -> {
                closeMenu();
                PluginsController.getInstance()
                        .dispatchMenuClick(record.pluginId, record.itemId, contextData);
            });
            int height = ITEM_HEIGHT;
            if (!TextUtils.isEmpty(record.subtext)) {
                item.setSubtext(record.subtext);
                height = SUBTITLE_ITEM_HEIGHT;
                item.setItemHeight(SUBTITLE_ITEM_HEIGHT);
            }
            menuItemsContainer.addView(item,
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, height));
            totalHeight += height;
        }

        applyHeight(totalHeight);
    }

    /**
     * Подменю растёт по содержимому, пока помещается; дальше фиксируется и
     * скроллится. Запас в 112 dp — чтобы не включать скролл ради одного пункта.
     */
    private void applyHeight(int totalHeight) {
        ViewGroup parent = menuItemsContainer.getParent() instanceof ViewGroup
                ? (ViewGroup) menuItemsContainer.getParent() : null;
        if (parent == null) {
            return;
        }
        int maxHeight = AndroidUtilities.dp(MAX_HEIGHT_DP);
        ViewGroup.LayoutParams params = parent.getLayoutParams();
        LinearLayout.LayoutParams linearParams = params instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) params
                : LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        linearParams.height = (totalHeight <= maxHeight || Math.abs(totalHeight - maxHeight) <= 112)
                ? LayoutHelper.WRAP_CONTENT
                : maxHeight;
        parent.setLayoutParams(linearParams);
    }

    private View createGap() {
        ActionBarPopupWindow.GapView gap =
                new ActionBarPopupWindow.GapView(menuItemsContainer.getContext(), resourcesProvider);
        gap.setDividerVisible(false);
        return gap;
    }

    /** ScrollView с затенением верхней кромки, когда содержимое уехало вверх. */
    private ScrollView createScrollView(Context context) {
        return new ScrollView(context) {
            private final AnimatedFloat shadowAlpha =
                    new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            private Drawable topShadow;
            private boolean wasScrollable;

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                float alpha = shadowAlpha.set(canScrollVertically(-1) ? 1f : 0f) * 0.5f;
                if (alpha <= 0f) {
                    return;
                }
                if (topShadow == null) {
                    topShadow = ContextCompat.getDrawable(context, R.drawable.header_shadow);
                }
                if (topShadow != null) {
                    topShadow.setBounds(0, getScrollY(), getWidth(),
                            getScrollY() + topShadow.getIntrinsicHeight());
                    topShadow.setAlpha((int) (255 * alpha));
                    topShadow.draw(canvas);
                }
            }

            @Override
            public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
                                       int dxUnconsumed, int dyUnconsumed) {
                super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
                boolean scrollable = canScrollVertically(-1);
                if (wasScrollable != scrollable) {
                    invalidate();
                    wasScrollable = scrollable;
                }
            }
        };
    }
}
