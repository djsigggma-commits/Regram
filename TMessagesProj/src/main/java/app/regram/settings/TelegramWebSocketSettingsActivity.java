package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TelegramWebSocketTransport;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

public final class TelegramWebSocketSettingsActivity extends BaseNekoSettingsActivity {
    private int enabled, poolSize, cloudflare, domains, fallback, logs, source;
    @Override protected void updateRows() {
        super.updateRows();
        enabled = addRow("ws_enabled", "RegramWsEnabled");
        poolSize = addRow("ws_pool_size", "RegramWsPoolSize");
        cloudflare = addRow("ws_cloudflare", "RegramWsCloudflare");
        domains = addRow("ws_domains", "RegramWsDomains");
        fallback = addRow("ws_tcp_fallback", "RegramWsFallback");
        logs = addRow("ws_logs", "RegramWsLogs");
        source = addRow("ws_source");
    }
    @Override protected String getActionBarTitle() { return getString(R.string.RegramWsTitle); }
    @Override protected String getKey() { return "regram_websocket_settings"; }
    @Override public int getSearchGuid() { return 23070; }
    @Override public int getSearchIcon() { return R.drawable.proxy_on_solar; }
    @Override public String getSearchPrefix() { return "RegramWs"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }

    @Override protected void onItemClick(View view, int position, float x, float y) {
        String key = position == enabled ? "enabled" : position == cloudflare ? "cloudflare"
                : position == fallback ? "tcp_fallback" : null;
        if (key != null) {
            boolean next = !TelegramWebSocketTransport.preferences().getBoolean(key, TelegramWebSocketTransport.defaultBoolean(key));
            TelegramWebSocketTransport.preferences().edit().putBoolean(key, next).apply();
            TelegramWebSocketTransport.settingsChanged();
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(next);
        } else if (position == poolSize && getParentActivity() != null) {
            int[] sizes = {0, 2, TelegramWebSocketTransport.DEFAULT_POOL_SIZE, 8};
            CharSequence[] labels = {getString(R.string.RegramWsPoolOff), "2", "4", "8"};
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.RegramWsPoolSize))
                    .setItems(labels, (dialog, which) -> {
                        TelegramWebSocketTransport.preferences().edit().putInt("pool_size", sizes[which]).apply();
                        TelegramWebSocketTransport.settingsChanged();
                        listAdapter.notifyItemChanged(poolSize);
                    }).create());
        } else if (position == domains && getParentActivity() != null) {
            EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
            input.setTextSize(16);
            input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
            input.setText(TelegramWebSocketTransport.preferences().getString("domains", ""));
            input.setHint(getString(R.string.RegramWsDomainsHint));
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
            AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.RegramWsDomains)).setView(input)
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.Save), null).create();
            showDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String value = TelegramWebSocketTransport.normalizeDomains(input.getText().toString());
                    TelegramWebSocketTransport.preferences().edit().putString("domains", value).apply();
                    TelegramWebSocketTransport.settingsChanged();
                    listAdapter.notifyItemChanged(domains);
                    dialog.dismiss();
                } catch (IllegalArgumentException error) { input.setError(getString(R.string.RegramWsInvalidDomains)); }
            });
        } else if (position == logs && getParentActivity() != null) {
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.RegramWsLogs))
                    .setMessage(TelegramWebSocketTransport.diagnostics()).setPositiveButton(getString(R.string.OK), null).create());
        } else if (position == source && getParentActivity() != null) {
            Browser.openUrl(getParentActivity(), "https://github.com/amurcanov/tg-ws-proxy-android");
        }
    }

    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public int getItemViewType(int position) {
            return position == enabled || position == cloudflare || position == fallback ? TYPE_CHECK : TYPE_SETTINGS;
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (getItemViewType(position) == TYPE_CHECK) {
                String key = position == enabled ? "enabled" : position == cloudflare ? "cloudflare" : "tcp_fallback";
                int title = position == enabled ? R.string.RegramWsEnabled : position == cloudflare
                        ? R.string.RegramWsCloudflare : R.string.RegramWsFallback;
                ((TextCheckCell) holder.itemView).setTextAndCheck(getString(title),
                        TelegramWebSocketTransport.preferences().getBoolean(key, TelegramWebSocketTransport.defaultBoolean(key)), true);
            } else {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == poolSize) {
                    int size = TelegramWebSocketTransport.poolSize();
                    cell.setTextAndValue(getString(R.string.RegramWsPoolSize), size == 0
                            ? getString(R.string.RegramWsPoolOff) : String.valueOf(size), true);
                } else if (position == domains) {
                    String value = TelegramWebSocketTransport.preferences().getString("domains", "");
                    cell.setTextAndValue(getString(R.string.RegramWsDomains), value.isEmpty()
                            ? getString(R.string.RegramWsAutomatic) : value, true);
                } else cell.setText(getString(position == logs ? R.string.RegramWsLogs : R.string.RegramWsSource), position != source);
            }
        }
    }
}
