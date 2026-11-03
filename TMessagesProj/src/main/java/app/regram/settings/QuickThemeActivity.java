package app.regram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.Paint.ColorPickerBottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import app.regram.appearance.QuickTheme;
import app.regram.appearance.QuickThemePresets;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;

/** Immediate lightweight preview; preferences and cached UI paints update on release. */
public final class QuickThemeActivity extends BaseFragment {
    private LinearLayout content;
    private Preview preview;
    private TextCheckCell enabled;
    private TextCheckCell hideNavigationBackdrop;
    private Slider[] sliders;
    private final ConfigItem[] items = {
            NekoConfig.regramQuickTransparency, NekoConfig.regramQuickContrast,
            NekoConfig.regramQuickSaturation, NekoConfig.regramQuickBrightness,
            NekoConfig.regramQuickFoldersTransparency, NekoConfig.regramQuickNavigationTransparency,
            NekoConfig.regramQuickIncomingTransparency, NekoConfig.regramQuickOutgoingTransparency,
            NekoConfig.regramQuickBubbleRadius, NekoConfig.regramQuickFolderRadius,
            NekoConfig.regramQuickNavigationRadius, NekoConfig.regramQuickBlur
    };
    private final int[] titles = {R.string.RegramQuickTransparency, R.string.RegramQuickContrast,
            R.string.RegramQuickSaturation, R.string.RegramQuickBrightness,
            R.string.RegramQuickFoldersTransparency, R.string.RegramQuickNavigationTransparency,
            R.string.RegramQuickIncomingTransparency, R.string.RegramQuickOutgoingTransparency,
            R.string.RegramQuickBubbleRadius, R.string.RegramQuickFolderRadius,
            R.string.RegramQuickNavigationRadius, R.string.RegramQuickBlur};
    private final int[] minimums = {0, 50, 0, -30, 0, 0, -1, -1, -1, -1, -1, 0};
    private final int[] maximums = {80, 150, 200, 30, 100, 100, 80, 80, 24, 32, 48, 200};
    private final ConfigItem[] colors = {NekoConfig.regramQuickFolderColor, NekoConfig.regramQuickNavigationColor,
            NekoConfig.regramQuickIncomingColor, NekoConfig.regramQuickOutgoingColor};
    private final int[] colorTitles = {R.string.RegramQuickFolderColor, R.string.RegramQuickNavigationColor,
            R.string.RegramQuickIncomingColor, R.string.RegramQuickOutgoingColor};
    private TextSettingsCell[] colorCells;

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
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(328)));
        enabled = new TextCheckCell(context);
        enabled.setTextAndCheck(getString(R.string.RegramQuickEnable), NekoConfig.regramQuickTheme.Bool(), true);
        enabled.setOnClickListener(view -> {
            enabled.setChecked(NekoConfig.regramQuickTheme.toggleConfigBool());
            refreshControls();
            applyTheme();
        });
        content.addView(enabled);
        hideNavigationBackdrop = new TextCheckCell(context);
        hideNavigationBackdrop.setOnClickListener(view -> {
            NekoConfig.regramQuickHideNavigationBackdrop.toggleConfigBool();
            refreshControls();
            applyTheme();
        });
        content.addView(hideNavigationBackdrop);
        sliders = new Slider[items.length];
        for (int i = 0; i < items.length; i++) {
            sliders[i] = new Slider(context, i);
            content.addView(sliders[i], new LinearLayout.LayoutParams(-1, dp(94)));
        }
        colorCells = new TextSettingsCell[colors.length];
        for (int i = 0; i < colors.length; i++) {
            final int index = i;
            colorCells[i] = new TextSettingsCell(context);
            colorCells[i].setOnClickListener(view -> chooseColor(index));
            content.addView(colorCells[i]);
        }
        TextSettingsCell presets = new TextSettingsCell(context);
        presets.setText(getString(R.string.RegramQuickPresets), true);
        presets.setOnClickListener(view -> showPresets());
        content.addView(presets);
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
        if (hideNavigationBackdrop != null) {
            hideNavigationBackdrop.setTextAndCheck(getString(R.string.RegramQuickHideNavigationBackdrop),
                    NekoConfig.regramQuickHideNavigationBackdrop.Bool(), true);
            hideNavigationBackdrop.setEnabled(NekoConfig.regramQuickTheme.Bool());
            hideNavigationBackdrop.setAlpha(NekoConfig.regramQuickTheme.Bool() ? 1f : .5f);
        }
        if (sliders != null) for (Slider slider : sliders) slider.refresh();
        if (colorCells != null) for (int i = 0; i < colorCells.length; i++) {
            colorCells[i].setTextAndValue(getString(colorTitles[i]), colors[i].Int() == 0
                    ? getString(R.string.RegramQuickOriginal) : String.format("#%06X", colors[i].Int() & 0xffffff), true);
            colorCells[i].setEnabled(NekoConfig.regramQuickTheme.Bool());
            colorCells[i].setAlpha(NekoConfig.regramQuickTheme.Bool() ? 1f : .5f);
        }
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        preview.invalidate();
    }

    private void chooseColor(int index) {
        if (!NekoConfig.regramQuickTheme.Bool()) return;
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(colorTitles[index]))
                .setItems(new String[]{getString(R.string.RegramQuickPickColor), getString(R.string.RegramQuickOriginal)}, (dialog, which) -> {
                    if (which == 1) {
                        colors[index].setConfigInt(0);
                        refreshControls();
                        applyTheme();
                        return;
                    }
                    ColorPickerBottomSheet picker = new ColorPickerBottomSheet(getParentActivity(), getResourceProvider());
                    picker.setPipetteDelegate(new ColorPickerBottomSheet.PipetteDelegate() {
                        public void onStartColorPipette() {}
                        public void onStopColorPipette() {}
                        public ViewGroup getContainerView() { return null; }
                        public View getSnapshotDrawingView() { return null; }
                        public void onDrawImageOverCanvas(Bitmap bitmap, Canvas canvas) {}
                        public boolean isPipetteVisible() { return false; }
                        public boolean isPipetteAvailable() { return false; }
                        public void onColorSelected(int color) {}
                    });
                    int key = index == 2 ? Theme.key_chat_inBubble : index == 3 ? Theme.key_chat_outBubble : Theme.key_windowBackgroundWhite;
                    picker.setColor(colors[index].Int() == 0 ? Theme.getColor(key) : colors[index].Int());
                    picker.setColorListener(color -> {
                        colors[index].setConfigInt(color | 0xff000000);
                        refreshControls();
                        applyTheme();
                    });
                    showDialog(picker);
                }).create());
    }

    private void showPresets() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.RegramQuickPresets))
                .setItems(new String[]{getString(R.string.RegramQuickGlass), getString(R.string.RegramQuickSoft),
                        getString(R.string.RegramQuickVivid), getString(R.string.RegramQuickSavePreset),
                        getString(R.string.RegramQuickLoadPreset), getString(R.string.RegramQuickDeletePreset)}, (dialog, which) -> {
                    if (which < 3) {
                        clearPendingSliders();
                        QuickThemePresets.builtIn(which);
                        refreshControls();
                        applyTheme();
                    } else showPresetSlots(which);
                }).create());
    }

    private void showPresetSlots(int operation) {
        String[] names = new String[3];
        for (int i = 0; i < names.length; i++) {
            String name = QuickThemePresets.name(i);
            names[i] = (i + 1) + ". " + (name.isEmpty() ? getString(R.string.RegramQuickEmptySlot) : name);
        }
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.RegramQuickPresets)).setItems(names, (dialog, slot) -> {
                    if (operation == 3) {
                        EditText input = new EditText(getParentActivity());
                        input.setSingleLine(true);
                        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
                        input.setHint(getString(R.string.RegramQuickPresetName));
                        input.setText(QuickThemePresets.name(slot));
                        input.setPadding(dp(21), dp(12), dp(21), dp(12));
                        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                                .setTitle(getString(R.string.RegramQuickSavePreset)).setView(input)
                                .setPositiveButton(getString(R.string.Save), (d, w) -> {
                                    String name = input.getText().toString().trim();
                                    if (name.isEmpty()) name = getString(R.string.RegramQuickPresets) + " " + (slot + 1);
                                    if (!QuickThemePresets.save(slot, name)) presetError();
                                }).setNegativeButton(getString(R.string.Cancel), null).create());
                    } else if (operation == 4) {
                        if (QuickThemePresets.load(slot)) {
                            clearPendingSliders();
                            refreshControls();
                            applyTheme();
                        } else presetError();
                    } else {
                        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                                .setTitle(getString(R.string.RegramQuickDeletePreset)).setMessage(names[slot])
                                .setPositiveButton(getString(R.string.Delete), (d, w) -> QuickThemePresets.delete(slot))
                                .setNegativeButton(getString(R.string.Cancel), null).create());
                    }
                }).create());
    }

    private void presetError() {
        android.widget.Toast.makeText(getParentActivity(), getString(R.string.RegramQuickPresetError), android.widget.Toast.LENGTH_SHORT).show();
    }

    private void clearPendingSliders() {
        if (sliders != null) for (Slider slider : sliders) slider.dirty = false;
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
            String unit = index >= 8 && index <= 10 ? " dp" : "%";
            String text = getString(titles[index]) + ": " + (value == -1 && index >= 6
                    ? getString(index <= 7 ? R.string.RegramQuickLinked : R.string.RegramQuickOriginal) : value + unit);
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
            if (!QuickTheme.hideNavigationBackdrop()) {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                canvas.drawRect(0, dp(204), width, getHeight(), paint);
            }
            drawSurface(canvas, width, 210, R.string.RegramQuickFoldersTransparency, QuickTheme.foldersAlpha());
            drawSurface(canvas, width, 266, R.string.RegramQuickNavigationTransparency, QuickTheme.navigationAlpha());
        }
        void drawSurface(Canvas canvas, int width, int top, int title, int alpha) {
            bubble.set(dp(16), dp(top), width - dp(16), dp(top + 46));
            boolean folders = title == R.string.RegramQuickFoldersTransparency;
            int baseColor = Theme.getColor(Theme.key_windowBackgroundWhite);
            paint.setColor(folders ? QuickTheme.folderColor(baseColor) : QuickTheme.navigationColor(baseColor));
            paint.setAlpha(Math.round((paint.getColor() >>> 24) * alpha / 255f));
            int radius = dp(folders ? QuickTheme.folderRadius(16) : QuickTheme.navigationRadius(16));
            canvas.drawRoundRect(bubble, radius, radius, paint);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            paint.setTextSize(dp(14));
            canvas.drawText(getString(title), dp(28), dp(top + 28), paint);
        }
        void drawMessage(Canvas canvas, int left, int top, int right, int bottom, boolean outgoing) {
            bubble.set(left, top, right, bottom);
            int save = canvas.saveLayerAlpha(left, top, right, bottom, QuickTheme.bubbleAlpha(outgoing));
            paint.setColor(Theme.getColor(outgoing ? Theme.key_chat_outBubble : Theme.key_chat_inBubble));
            int radius = dp(QuickTheme.bubbleRadius(16));
            canvas.drawRoundRect(bubble, radius, radius, paint);
            canvas.restoreToCount(save);
            paint.setColor(Theme.getColor(outgoing ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
            paint.setTextSize(dp(14));
            canvas.drawText(getString(outgoing ? R.string.RegramQuickOutgoing : R.string.RegramQuickIncoming),
                    left + dp(12), top + dp(32), paint);
        }
    }
}
