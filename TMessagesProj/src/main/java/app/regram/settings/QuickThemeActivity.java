package app.regram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import app.regram.appearance.QuickTheme;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;

/** Immediate lightweight preview; preferences and cached UI paints update on release. */
public final class QuickThemeActivity extends BaseFragment {
    private LinearLayout content;
    private Preview preview;
    private TextCheckCell enabled;
    private Slider[] sliders;
    private final ConfigItem[] items = {
            NekoConfig.regramQuickTransparency, NekoConfig.regramQuickContrast,
            NekoConfig.regramQuickSaturation, NekoConfig.regramQuickBrightness
    };
    private final int[] titles = {R.string.RegramQuickTransparency, R.string.RegramQuickContrast,
            R.string.RegramQuickSaturation, R.string.RegramQuickBrightness};
    private final int[] minimums = {0, 50, 0, -30}, maximums = {80, 150, 200, 30};

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.RegramQuickTheme));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        fragmentView = scroll;

        preview = new Preview(context);
        preview.setContentDescription(getString(R.string.RegramQuickPreview));
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(240)));
        enabled = new TextCheckCell(context);
        enabled.setTextAndCheck(getString(R.string.RegramQuickEnable), NekoConfig.regramQuickTheme.Bool(), true);
        enabled.setOnClickListener(view -> {
            enabled.setChecked(NekoConfig.regramQuickTheme.toggleConfigBool());
            refreshControls();
            applyTheme();
        });
        content.addView(enabled);
        sliders = new Slider[items.length];
        for (int i = 0; i < items.length; i++) {
            sliders[i] = new Slider(context, i);
            content.addView(sliders[i], new LinearLayout.LayoutParams(-1, dp(94)));
        }
        TextSettingsCell reset = new TextSettingsCell(context);
        reset.setText(getString(R.string.RegramQuickReset), false);
        reset.setOnClickListener(view -> {
            for (Slider slider : sliders) slider.dirty = false;
            QuickTheme.reset();
            refreshControls();
            applyTheme();
        });
        content.addView(reset);
        TextView info = new TextView(context);
        info.setText(getString(R.string.RegramQuickInfo));
        info.setTextSize(14);
        info.setPadding(dp(21), dp(12), dp(21), dp(20));
        info.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        content.addView(info);
        refreshControls();
        return fragmentView;
    }

    private void refreshControls() {
        if (enabled == null) return;
        enabled.setChecked(NekoConfig.regramQuickTheme.Bool());
        if (sliders != null) for (Slider slider : sliders) slider.refresh();
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        preview.invalidate();
    }

    private void applyTheme() {
        Theme.applyCommonTheme();
        Theme.applyDialogsTheme();
        Theme.applyChatTheme(false, true);
        if (parentLayout != null) parentLayout.rebuildAllFragmentViews(false, false);
        if (actionBar != null) actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        if (content != null) content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (preview != null) preview.invalidate();
    }

    @Override
    public void onFragmentDestroy() {
        // Also persist the last value if navigation interrupts a drag.
        if (sliders != null) for (Slider slider : sliders) {
            if (slider.dirty) {
                slider.dirty = false;
                items[slider.index].saveConfig();
            }
        }
        // Update cached paints, but never rebuild the navigation stack during removal.
        Theme.applyCommonTheme();
        Theme.applyDialogsTheme();
        Theme.applyChatTheme(false, true);
        super.onFragmentDestroy();
    }

    private final class Slider extends FrameLayout {
        final int index;
        final TextView label;
        final SeekBarView seek;
        boolean dirty;
        Slider(Context context, int index) {
            super(context);
            this.index = index;
            label = new TextView(context);
            label.setTextSize(15);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(label, LayoutHelper.createFrame(-1, 28, Gravity.TOP, 21, 12, 21, 0));
            seek = new SeekBarView(context, true, getResourceProvider());
            seek.setReportChanges(true);
            seek.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override public void onSeekBarDrag(boolean stop, float progress) {
                    int value = minimums[index] + Math.round((maximums[index] - minimums[index]) * progress);
                    value = QuickTheme.clamp(value, minimums[index], maximums[index]);
                    if (items[index].Int() != value) {
                        items[index].value = value;
                        dirty = true;
                        updateLabel(value);
                        preview.invalidate();
                    }
                    if (stop) persist();
                }
                @Override public void onSeekBarPressed(boolean pressed) { if (!pressed) persist(); }
            });
            addView(seek, LayoutHelper.createFrame(-1, 44, Gravity.TOP, 6, 40, 6, 0));
        }
        void updateLabel(int value) {
            String text = getString(titles[index]) + ": " + value + "%";
            label.setText(text);
            seek.setContentDescription(text);
        }
        void refresh() {
            int value = QuickTheme.clamp(items[index].Int(), minimums[index], maximums[index]);
            seek.setProgress((value - minimums[index]) / (float) (maximums[index] - minimums[index]));
            seek.setEnabled(NekoConfig.regramQuickTheme.Bool());
            setAlpha(NekoConfig.regramQuickTheme.Bool() ? 1f : .5f);
            updateLabel(value);
        }
        void persist() {
            if (!dirty) return;
            dirty = false;
            items[index].saveConfig();
            applyTheme();
        }
    }

    private static final class Preview extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF bubble = new RectF();
        Preview(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            int width = getWidth();
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray));
            // Deliberately patterned background makes bubble transparency visible.
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            paint.setAlpha(30);
            for (int y = dp(12); y < getHeight(); y += dp(24))
                for (int x = dp(12); x < width; x += dp(24)) canvas.drawCircle(x, y, dp(2), paint);
            paint.setAlpha(255);
            paint.setTextSize(dp(16));
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            canvas.drawText(getString(R.string.RegramQuickPreview), dp(21), dp(30), paint);
            drawMessage(canvas, dp(16), dp(52), width - dp(70), dp(112), false);
            drawMessage(canvas, dp(54), dp(132), width - dp(16), dp(196), true);
        }
        void drawMessage(Canvas canvas, int left, int top, int right, int bottom, boolean outgoing) {
            bubble.set(left, top, right, bottom);
            int save = canvas.saveLayerAlpha(left, top, right, bottom, QuickTheme.bubbleAlpha());
            paint.setColor(Theme.getColor(outgoing ? Theme.key_chat_outBubble : Theme.key_chat_inBubble));
            canvas.drawRoundRect(bubble, dp(16), dp(16), paint);
            canvas.restoreToCount(save);
            paint.setColor(Theme.getColor(outgoing ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
            paint.setTextSize(dp(14));
            canvas.drawText(getString(outgoing ? R.string.RegramQuickOutgoing : R.string.RegramQuickIncoming),
                    left + dp(12), top + dp(32), paint);
        }
    }
}
