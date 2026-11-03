package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;
import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import app.regram.documents.NativeDocViewer;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/** Native Doc Viewer options; existing plugin settings are not imported or modified. */
public final class NativeDocumentSettingsActivity extends BaseNekoSettingsActivity {
    private final String[] keys = {"enabled", "pdf", "docs", "books", "text", "web", "image", "archive", "invert", "fitwidth", "remember", "volume", "keepon", "wrap", "prettyjson", "csv_table"};
    private final int[] titles = {R.string.RegramDocEnabled, R.string.RegramDocPdf, R.string.RegramDocDocs,
            R.string.RegramDocBooks, R.string.RegramDocText, R.string.RegramDocWeb, R.string.RegramDocImage,
            R.string.RegramDocArchive, R.string.RegramDocInvert, R.string.RegramDocFit, R.string.RegramDocRemember,
            R.string.RegramDocVolume, R.string.RegramDocKeepOn, R.string.RegramDocWrap, R.string.RegramDocPrettyJson, R.string.RegramDocCsv};
    private final int[] rows = new int[keys.length]; private int info;
    private boolean fallback(int index) { return index != 0 && index != 8 && index != 9; }
    @Override protected void updateRows() { super.updateRows(); for (int i = 0; i < rows.length; i++) rows[i] = addRow("docviewer_" + keys[i]); info = addRow("docviewer_info"); }
    @Override protected String getActionBarTitle() { return getString(R.string.RegramDocTitle); }
    @Override protected String getKey() { return "regram_doc_viewer_settings"; }
    @Override public int getSearchGuid() { return 23030; }
    @Override public int getSearchIcon() { return R.drawable.msg_filehq; }
    @Override public String getSearchPrefix() { return "RegramDoc"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        for (int i = 0; i < rows.length; i++) if (rows[i] == position) {
            boolean value = !NativeDocViewer.option(keys[i], fallback(i));
            NativeDocViewer.settings().edit().putBoolean(keys[i], value).apply();
            if (i == 0 && !value) NativeDocViewer.closeAll();
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(value);
            return;
        }
    }
    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) { return position == info ? TYPE_INFO_PRIVACY : TYPE_CHECK; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (position == info) { ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.RegramDocSettingsInfo)); return; }
            for (int i = 0; i < rows.length; i++) if (rows[i] == position) ((TextCheckCell) holder.itemView).setTextAndCheck(getString(titles[i]), NativeDocViewer.option(keys[i], fallback(i)), true);
        }
    }
}
