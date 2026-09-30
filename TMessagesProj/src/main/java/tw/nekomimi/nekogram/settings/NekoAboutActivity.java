package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextSettingsCell;

import app.regram.ui.RegramCreditsActivity;
import tw.nekomimi.nekogram.DatacenterActivity;

/** Legacy About entry point. Credits are maintained in one place for re:gram. */
public class NekoAboutActivity extends BaseNekoSettingsActivity {

    private int creditsRow;
    private int datacenterStatusRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        creditsRow = addRow("regramCreatorsAndSources");
        datacenterStatusRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.About);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == creditsRow) {
            presentFragment(new RegramCreditsActivity());
        } else if (position == datacenterStatusRow) {
            presentFragment(new DatacenterActivity(0));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new BaseListAdapter(context) {
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText(getString(position == creditsRow
                        ? R.string.RegramCreatorsAndSources : R.string.DatacenterStatus),
                        position == creditsRow);
            }

            @Override
            public int getItemViewType(int position) {
                return TYPE_SETTINGS;
            }
        };
    }
}
