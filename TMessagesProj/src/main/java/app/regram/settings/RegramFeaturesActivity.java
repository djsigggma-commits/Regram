package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextCell;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/** Navigation only: each feature retains its existing settings and preference storage. */
public final class RegramFeaturesActivity extends BaseNekoSettingsActivity {
    private int nativeVisualRow, quickThemeRow;
    private int termuxRow, documentViewerRow, spenRow, creditsRow;

    @Override protected void updateRows() {
        super.updateRows();
        nativeVisualRow = addRow("nativeVisual", "OEAppearanceNativeVisual");
        quickThemeRow = addRow("quickTheme", "RegramQuickTheme");
        termuxRow = addRow("regramTermuxBridge", "RegramTermuxTitle");
        documentViewerRow = addRow("regramDocumentViewer", "RegramDocTitle");
        spenRow = addRow("regramSPen", "RegramSPenTitle");
        creditsRow = addRow("regramCreatorsAndSources");
    }

    @Override protected String getActionBarTitle() { return getString(R.string.RegramFeatures); }
    @Override protected String getKey() { return "regram_features"; }
    @Override public int getSearchGuid() { return 23060; }
    @Override public int getSearchIcon() { return R.drawable.msg_theme; }
    @Override public String getSearchPrefix() { return "RegramFeatures"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }

    @Override protected void onItemClick(View view, int position, float x, float y) {
        if (position == nativeVisualRow) {
            presentFragment(new NativeVisualSettingsActivity());

        } else if (position == quickThemeRow) {
            presentFragment(new QuickThemeActivity());
        } else if (position == termuxRow) {
            presentFragment(new TermuxBridgeActivity());
        } else if (position == documentViewerRow) {
            presentFragment(new NativeDocumentSettingsActivity());
        } else if (position == spenRow) {
            presentFragment(new NativeSPenSettingsActivity());
        } else if (position == creditsRow) {
            presentFragment(new app.regram.ui.RegramCreditsActivity());
        }
    }

    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) {
            return position == termuxRow || position == documentViewerRow || position == spenRow || position == creditsRow
                    ? TYPE_TEXT : TYPE_DETAIL_SETTINGS;
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (getItemViewType(position) == TYPE_TEXT) {
                TextCell cell = (TextCell) holder.itemView;
                if (position == termuxRow) {
                    cell.setTextAndIcon(getString(R.string.RegramTermuxTitle), R.drawable.msg_settings, true);
                } else if (position == documentViewerRow) {
                    cell.setTextAndIcon(getString(R.string.RegramDocTitle), R.drawable.msg_filehq, true);
                } else if (position == spenRow) {
                    cell.setTextAndIcon(getString(R.string.RegramSPenTitle), R.drawable.msg_edit, true);
                } else if (position == creditsRow) {
                    cell.setTextAndIcon(getString(R.string.RegramCreatorsAndSources), R.drawable.msg_groups, false);
                }
                return;
            }
            TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
            if (position == nativeVisualRow) {
                cell.setTextAndValueAndIcon(getString(R.string.OEAppearanceNativeVisual),
                        getString(R.string.OEAppearanceNativeVisualInfo), R.drawable.msg_theme, true);

            } else if (position == quickThemeRow) {
                cell.setTextAndValueAndIcon(getString(R.string.RegramQuickTheme),
                        getString(R.string.RegramQuickThemeSummary), R.drawable.msg_theme, true);
            }
        }
    }
}
