package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import app.regram.camera.DualCameraSettings;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

public final class DualCameraSettingsActivity extends BaseNekoSettingsActivity {
    private int enabledRow, infoRow;

    private boolean supported() {
        return DualCameraSettings.isSupported(ApplicationLoader.applicationContext);
    }

    @Override protected void updateRows() {
        super.updateRows();
        enabledRow = addRow("rounddual_available", "RegramDualCameraTitle");
        infoRow = addRow("RegramDualCameraInfo");
    }
    @Override protected String getActionBarTitle() { return getString(R.string.RegramDualCameraTitle); }
    @Override protected String getKey() { return "regram_dual_camera"; }
    @Override public int getSearchGuid() { return 23090; }
    @Override public int getSearchIcon() { return R.drawable.msg_camera; }
    @Override public String getSearchPrefix() { return "RegramDualCamera"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        if (position == enabledRow) {
            boolean enabled = DualCameraSettings.isEnabled();
            if (!enabled && !supported()) return;
            DualCameraSettings.setEnabled(!enabled);
            // An unavailable device may still turn off a previously saved opt-in.
            listAdapter.notifyDataSetChanged();
        }
    }
    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) { return position == infoRow ? TYPE_INFO_PRIVACY : TYPE_CHECK; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            boolean available = supported();
            if (position == infoRow) {
                String text = getString(R.string.RegramDualCameraInfo);
                if (!available) text += "\n\n" + getString(R.string.RegramDualCameraUnavailable);
                ((TextInfoPrivacyCell) holder.itemView).setText(text);
            } else {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                boolean enabled = DualCameraSettings.isEnabled();
                cell.setTextAndCheck(getString(R.string.RegramDualCameraTitle), enabled, false);
                cell.setEnabled(available || enabled);
                cell.setAlpha(available || enabled ? 1f : 0.5f);
            }
        }
    }
}
