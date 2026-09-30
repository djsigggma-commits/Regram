package app.regram.proxy;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import app.regram.OpenExteraConfig;

/**
 * Условия отключения прокси плашками в строку. Их три и они не влезают в ширину
 * на узких экранах, поэтому ряд прокручивается вбок.
 */
public class ProxyDisableChipsView extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView titleView;
    private final LinearLayout chipsLayout;
    private Runnable onChanged;

    public ProxyDisableChipsView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(VERTICAL);
        setPadding(0, dp(10), 0, dp(12));

        titleView = new TextView(context);
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setText(getString(R.string.ProxyDisableOn));
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                21, 0, 21, 0));

        final HorizontalScrollView scrollView = new HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(dp(17), 0, dp(17), 0);
        addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                0, 9, 0, 0));

        chipsLayout = new LinearLayout(context);
        chipsLayout.setOrientation(HORIZONTAL);
        scrollView.addView(chipsLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT));

        addChip(ProxyDisableCondition.VPN, R.string.ProxyDisableOnVpn, R.drawable.msg_secret);
        addChip(ProxyDisableCondition.MOBILE_DATA, R.string.ProxyDisableOnMobileData, R.drawable.msg_mini_customize);
        addChip(ProxyDisableCondition.WIFI, R.string.ProxyDisableOnWiFi, R.drawable.msg_channel);

        updateColors();
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    private void addChip(ProxyDisableCondition condition, int textRes, int iconRes) {
        final Chip chip = new Chip(getContext(), condition, getString(textRes), iconRes);
        chipsLayout.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 34,
                0, 0, 8, 0));
    }

    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        for (int i = 0; i < chipsLayout.getChildCount(); i++) {
            ((Chip) chipsLayout.getChildAt(i)).updateColors();
        }
    }

    private class Chip extends LinearLayout {

        private final ProxyDisableCondition condition;
        private final ImageView iconView;
        private final TextView textView;

        Chip(Context context, ProxyDisableCondition condition, CharSequence text, int iconRes) {
            super(context);
            this.condition = condition;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(11), 0, dp(13), 0);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconView.setImageResource(iconRes);
            addView(iconView, LayoutHelper.createLinear(17, 17, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

            textView = new TextView(context);
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setText(text);
            textView.setSingleLine();
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL));

            ScaleStateListAnimator.apply(this, 0.03f, 1.5f);
            setOnClickListener(v -> toggle());
        }

        private void toggle() {
            final boolean enabled = !OpenExteraConfig.isProxyDisabledOn(condition);
            OpenExteraConfig.setProxyDisabledOn(condition, enabled);
            updateColors();
            if (onChanged != null) {
                onChanged.run();
            }
        }

        void updateColors() {
            final boolean checked = OpenExteraConfig.isProxyDisabledOn(condition);
            final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            final int background = checked
                    ? accent
                    : Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.09f);
            final int content = checked
                    ? Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);

            setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(17), background,
                    Theme.multAlpha(content, 0.1f)));
            textView.setTextColor(content);
            iconView.setColorFilter(new PorterDuffColorFilter(content, PorterDuff.Mode.SRC_IN));
        }
    }
}
