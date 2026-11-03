package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import app.regram.input.SPenInput;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

public final class NativeSPenSettingsActivity extends BaseNekoSettingsActivity {
    private final String[] keys = {"enabled", "instant_selection", "double_tap_select_all", "message_selection", "hover_click_menu"};
    private final int[] titles = {R.string.RegramSPenEnabled, R.string.RegramSPenInstant,
            R.string.RegramSPenDouble, R.string.RegramSPenMessages, R.string.RegramSPenHover};
    private final int[] rows = new int[keys.length];
    private int info;

    private boolean value(int i) { return SPenInput.preferences().getBoolean(keys[i], i != 0); }
    @Override protected void updateRows() {
        super.updateRows();
        for (int i = 0; i < rows.length; i++) rows[i] = addRow("spen_" + keys[i]);
        info = addRow("spen_info");
    }
    @Override protected String getActionBarTitle() { return getString(R.string.RegramSPenTitle); }
    @Override protected String getKey() { return "regram_spen_settings"; }
    @Override public int getSearchGuid() { return 23040; }
    @Override public int getSearchIcon() { return R.drawable.msg_edit; }
    @Override public String getSearchPrefix() { return "RegramSPen"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        for (int i = 0; i < rows.length; i++) if (rows[i] == position) {
            boolean next = !value(i);
            SPenInput.preferences().edit().putBoolean(keys[i], next).apply();
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(next);
            return;
        }
    }
    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) { return position == info ? TYPE_INFO_PRIVACY : TYPE_CHECK; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (position == info) {
                ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.RegramSPenInfo));
                return;
            }
            for (int i = 0; i < rows.length; i++) if (rows[i] == position) {
                ((TextCheckCell) holder.itemView).setTextAndCheck(getString(titles[i]), value(i), true);
            }
        }
    }
}
