package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Locale;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.Paint.ColorPickerBottomSheet;
import org.telegram.ui.Stories.StoriesUtilities;
import app.regram.appearance.MonoColor;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Client settings only: no plugin engine, reflection, downloaded code or hooks. */
public final class NativeVisualSettingsActivity extends BaseNekoSettingsActivity {
    private final ConfigItem[] switches = {
            NekoConfig.regramMonoColor, NekoConfig.regramMonoGradient, NekoConfig.regramMonoAvatars,
            NekoConfig.regramMonoStories, NekoConfig.regramMonoNames, NekoConfig.regramMonoDialogs,
            NekoConfig.regramMonoProfiles, NekoConfig.regramAllLeft,
            NekoConfig.regramAllLeftPrivate, NekoConfig.regramAllLeftStatus,
            NekoConfig.regramMentionAvatars, NekoConfig.regramMentionChats, NekoConfig.regramMentionBots,
            NekoConfig.regramPlayerColor };
    private final int[] titles = {
            R.string.RegramMonoColor, R.string.RegramMonoGradient, R.string.RegramMonoAvatars,
            R.string.RegramMonoStories, R.string.RegramMonoNames, R.string.RegramMonoDialogs,
            R.string.RegramMonoProfiles, R.string.RegramAllLeft,
            R.string.RegramAllLeftPrivate, R.string.RegramAllLeftStatus,
            R.string.RegramMentionAvatars, R.string.RegramMentionChats, R.string.RegramMentionBots,
            R.string.RegramPlayerColor };
    private final int[] switchRows = new int[switches.length];
    private int monoHeader, sourceRow, customRow, monoInfo, leftHeader, leftInfo;
    private int mentionHeader, mentionPositionRow, mentionSizeRow, mentionInfo;
    private int playerHeader, playerPresetRow, playerHexRow, playerInfo;

    @Override protected void updateRows() {
        super.updateRows();
        monoHeader = addRow("monoHeader");
        switchRows[0] = addRow(switches[0].key);
        sourceRow = addRow(NekoConfig.regramMonoSource.key);
        customRow = addRow(NekoConfig.regramMonoCustomColor.key);
        for (int i = 1; i < 7; i++) switchRows[i] = addRow(switches[i].key);
        monoInfo = addRow("monoInfo");
        leftHeader = addRow("leftHeader");
        for (int i = 7; i < 10; i++) switchRows[i] = addRow(switches[i].key);
        leftInfo = addRow("leftInfo");
        mentionHeader = addRow("mentionHeader");
        switchRows[10] = addRow(switches[10].key);
        mentionPositionRow = addRow(NekoConfig.regramMentionPosition.key);
        mentionSizeRow = addRow(NekoConfig.regramMentionSize.key);
        for (int i = 11; i < 13; i++) switchRows[i] = addRow(switches[i].key);
        mentionInfo = addRow("mentionInfo");
        playerHeader = addRow("playerHeader");
        switchRows[13] = addRow(switches[13].key);
        playerPresetRow = addRow(NekoConfig.regramPlayerColorPreset.key);
        playerHexRow = addRow(NekoConfig.regramPlayerColorHex.key);
        playerInfo = addRow("playerInfo");
    }
    @Override protected String getActionBarTitle() { return getString(R.string.OEAppearanceNativeVisual); }
    @Override public int getSearchGuid() { return 21020; }
    @Override public int getSearchIcon() { return R.drawable.msg_theme; }
    @Override public String getSearchPrefix() { return "RegramVisual"; }
    @Override protected String getKey() { return "regram_native_visual"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }

    private String[] sources() {
        return new String[] { getString(R.string.RegramMonoButton), getString(R.string.RegramMonoAccent),
                getString(R.string.RegramMonoHeader), getString(R.string.RegramMonoLinks),
                getString(R.string.RegramMonoFloating), getString(R.string.RegramMonoOutgoing),
                getString(R.string.RegramMonoCustomColor) };
    }
    private int sourceIndex() {
        int source = NekoConfig.regramMonoSource.Int();
        return source >= 0 && source <= 6 ? source : 0;
    }
    private String[] mentionPositions() {
        return new String[] {getString(R.string.RegramMentionBefore), getString(R.string.RegramMentionAfter)};
    }
    private String[] mentionSizes() {
        return new String[] {getString(R.string.RegramMentionSmall), getString(R.string.RegramMentionMedium), getString(R.string.RegramMentionLarge)};
    }
    private String[] playerPresets() {
        return getParentActivity().getResources().getStringArray(R.array.RegramPlayerColorPresets);
    }
    private String hex(int color) { return String.format(Locale.US, "#%06X", color & 0xffffff); }

    private void applyVisualSettings() {
        Theme.applyDialogsTheme();
        StoriesUtilities.updateColors();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,
                        MessagesController.UPDATE_MASK_ALL);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        }
        if (parentLayout != null) parentLayout.rebuildAllFragmentViews(false, false);
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        for (int i = 0; i < switches.length; i++) {
            if (position == switchRows[i]) {
                boolean checked = switches[i].toggleConfigBool();
                if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(checked);
                applyVisualSettings();
                return;
            }
        }
        if (position == playerPresetRow) {
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle(getString(R.string.RegramPlayerPreset))
                    .setItems(playerPresets(), (dialog, which) -> {
                        NekoConfig.regramPlayerColorPreset.setConfigInt(which);
                        applyVisualSettings();
                    }).create());
        } else if (position == playerHexRow) {
            android.widget.EditText input = new android.widget.EditText(getParentActivity());
            input.setSingleLine(true);
            input.setText(NekoConfig.regramPlayerColorHex.String());
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle(getString(R.string.RegramPlayerHex)).setView(input)
                    .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                        Integer color = app.regram.player.PlayerColorOverride.parseHex(input.getText().toString());
                        if (color == null) {
                            android.widget.Toast.makeText(getParentActivity(), getString(R.string.RegramPlayerInvalidHex), android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        NekoConfig.regramPlayerColorHex.setConfigString(hex(color));
                        NekoConfig.regramPlayerColorPreset.setConfigInt(0);
                        applyVisualSettings();
                    }).setNegativeButton(getString(R.string.Cancel), null).create());
        } else if (position == mentionPositionRow || position == mentionSizeRow) {
            boolean location = position == mentionPositionRow;
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle(getString(location ? R.string.RegramMentionPosition : R.string.RegramMentionSize))
                    .setItems(location ? mentionPositions() : mentionSizes(), (dialog, which) -> {
                        (location ? NekoConfig.regramMentionPosition : NekoConfig.regramMentionSize).setConfigInt(which);
                        applyVisualSettings();
                    }).create());
        } else if (position == sourceRow) {
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle(getString(R.string.RegramMonoSource))
                    .setItems(sources(), (dialog, which) -> {
                        NekoConfig.regramMonoSource.setConfigInt(which);
                        applyVisualSettings();
                    }).create());
        } else if (position == customRow) {
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
            picker.setColor(NekoConfig.regramMonoCustomColor.Int());
            picker.setColorListener(color -> {
                NekoConfig.regramMonoCustomColor.setConfigInt(color | 0xff000000);
                NekoConfig.regramMonoSource.setConfigInt(6);
                applyVisualSettings();
            });
            showDialog(picker);
        }
    }
    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(position == monoHeader ? "MonoColor UI" : position == leftHeader ? "AllLeft" : position == mentionHeader ? "MentionAvatars" : "Player Color");
                    break;
                case TYPE_CHECK:
                    for (int i = 0; i < switches.length; i++) if (position == switchRows[i]) {
                        ((TextCheckCell) holder.itemView).setTextAndCheck(getString(titles[i]), switches[i].Bool(), i != 6 && i != 9 && i != 12);
                        break;
                    }
                    break;
                case TYPE_SETTINGS:
                    if (position == playerPresetRow || position == playerHexRow) {
                        int index = NekoConfig.regramPlayerColorPreset.Int();
                        if (index < 0 || index >= playerPresets().length) index = 0;
                        ((TextSettingsCell) holder.itemView).setTextAndValue(
                                getString(position == playerPresetRow ? R.string.RegramPlayerPreset : R.string.RegramPlayerHex),
                                position == playerPresetRow ? playerPresets()[index] : NekoConfig.regramPlayerColorHex.String(), true);
                        break;
                    }
                    if (position == mentionPositionRow || position == mentionSizeRow) {
                        boolean location = position == mentionPositionRow;
                        ((TextSettingsCell) holder.itemView).setTextAndValue(
                                getString(location ? R.string.RegramMentionPosition : R.string.RegramMentionSize),
                                location ? mentionPositions()[app.regram.chats.MentionAvatars.position()]
                                        : mentionSizes()[app.regram.chats.MentionAvatars.sizeIndex()], true);
                        break;
                    }
                    ((TextSettingsCell) holder.itemView).setTextAndValue(
                            getString(position == sourceRow ? R.string.RegramMonoSource : R.string.RegramMonoCustomColor),
                            position == sourceRow ? sources()[sourceIndex()] + "  " + hex(MonoColor.sourceColor())
                                    : hex(NekoConfig.regramMonoCustomColor.Int()), true);
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell info = (TextInfoPrivacyCell) holder.itemView;
                    info.setText(getString(position == monoInfo ? R.string.RegramMonoInfo : position == leftInfo ? R.string.RegramAllLeftInfo : position == mentionInfo ? R.string.RegramMentionInfo : R.string.RegramPlayerInfo));
                    info.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
        }
        @Override public int getItemViewType(int position) {
            if (position == monoHeader || position == leftHeader || position == mentionHeader || position == playerHeader) return TYPE_HEADER;
            if (position == sourceRow || position == customRow || position == mentionPositionRow || position == mentionSizeRow || position == playerPresetRow || position == playerHexRow) return TYPE_SETTINGS;
            if (position == monoInfo || position == leftInfo || position == mentionInfo || position == playerInfo) return TYPE_INFO_PRIVACY;
            return TYPE_CHECK;
        }
    }
}
