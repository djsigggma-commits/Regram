package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

public final class BluetoothCallAudioSettingsActivity extends BaseNekoSettingsActivity {
    private int enabledRow, infoRow;

    @Override protected void updateRows() {
        super.updateRows();
        enabledRow = addRow("RegramHdBluetoothCalls", "RegramHdBluetoothCallsTitle");
        infoRow = addRow("RegramHdBluetoothCallsInfo");
    }
    @Override protected String getActionBarTitle() { return getString(R.string.RegramHdBluetoothCallsTitle); }
    @Override protected String getKey() { return "regram_bluetooth_call_audio"; }
    @Override public int getSearchGuid() { return 23080; }
    @Override public int getSearchIcon() { return R.drawable.msg_call_bluetooth; }
    @Override public String getSearchPrefix() { return "RegramHdBluetoothCalls"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        if (position == enabledRow) {
            NekoConfig.regramHdBluetoothCalls.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.regramHdBluetoothCalls.Bool());
            }
        }
    }
    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) { return position == infoRow ? TYPE_INFO_PRIVACY : TYPE_CHECK; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (position == infoRow) {
                ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.RegramHdBluetoothCallsInfo));
            } else {
                ((TextCheckCell) holder.itemView).setTextAndCheck(getString(R.string.RegramHdBluetoothCallsTitle),
                        NekoConfig.regramHdBluetoothCalls.Bool(), false);
            }
        }
    }
}
