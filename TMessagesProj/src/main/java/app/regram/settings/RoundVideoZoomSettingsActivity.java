package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import app.regram.chats.ChatsConfig;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/** Uses the existing zoomSlider preference; does not introduce a second zoom policy. */
public final class RoundVideoZoomSettingsActivity extends BaseNekoSettingsActivity {
    private int enabledRow, infoRow;

    @Override protected void updateRows() {
        super.updateRows();
        enabledRow = addRow("zoomSlider", "RegramRoundVideoZoomTitle");
        infoRow = addRow("RegramRoundVideoZoomInfo");
    }
    @Override protected String getActionBarTitle() { return getString(R.string.RegramRoundVideoZoomTitle); }
    @Override protected String getKey() { return "regram_round_video_zoom"; }
    @Override public int getSearchGuid() { return 23100; }
    @Override public int getSearchIcon() { return R.drawable.msg_camera; }
    @Override public String getSearchPrefix() { return "RegramRoundVideoZoom"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        if (position == enabledRow) {
            ChatsConfig.zoomSlider.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(ChatsConfig.zoomSlider.Bool());
            }
        }
    }
    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) { return position == infoRow ? TYPE_INFO_PRIVACY : TYPE_CHECK; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (position == infoRow) {
                ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.RegramRoundVideoZoomInfo));
            } else {
                ((TextCheckCell) holder.itemView).setTextAndCheck(getString(R.string.RegramRoundVideoZoomTitle),
                        ChatsConfig.zoomSlider.Bool(), false);
            }
        }
    }
}
