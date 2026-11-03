package app.regram.settings;

import static org.telegram.messenger.LocaleController.getString;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import app.regram.termux.TermuxBridge;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/** Native settings; bridge secrets never enter plugin settings or account backups. */
public final class TermuxBridgeActivity extends BaseNekoSettingsActivity {
    private final String[] keys = {"enabled", "pair", "port", "restart", "test", "ai_endpoint", "ai_key", "ai_model", "ai_extra", "ai_test", "ai_docs", "allow_exec", "rotate", "info"};
    private final int[] titles = {R.string.RegramTermuxEnabled, R.string.RegramTermuxPair, R.string.RegramTermuxPort,
            R.string.RegramTermuxRestart, R.string.RegramTermuxTest, R.string.RegramTermuxEndpoint, R.string.RegramTermuxKey,
            R.string.RegramTermuxModel, R.string.RegramTermuxExtra, R.string.RegramTermuxAiTest, R.string.RegramTermuxDocs,
            R.string.RegramTermuxExec, R.string.RegramTermuxRotate, R.string.RegramTermuxInfo};
    private final int[] rows = new int[keys.length];
    @Override protected void updateRows() { super.updateRows(); for (int i = 0; i < keys.length; i++) rows[i] = addRow("termux_" + keys[i]); }
    @Override protected String getActionBarTitle() { return getString(R.string.RegramTermuxTitle); }
    @Override protected String getKey() { return "regram_termux_settings"; }
    @Override public int getSearchGuid() { return 23020; }
    @Override public int getSearchIcon() { return R.drawable.msg_settings; }
    @Override public String getSearchPrefix() { return "RegramTermux"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }
    @Override public void onResume() { super.onResume(); refresh(); }
    private void refresh() { if (listAdapter != null) listAdapter.notifyDataSetChanged(); }
    private void notice(String message) { if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show(); }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        int index = -1; for (int i = 0; i < rows.length; i++) if (rows[i] == position) { index = i; break; }
        if (index < 0) return;
        String key = keys[index]; TermuxBridge bridge = TermuxBridge.getInstance();
        switch (key) {
            case "enabled":
                boolean enabled = !TermuxBridge.enabled();
                if (enabled) confirm(getString(R.string.RegramTermuxEnableWarning), () -> setEnabled(true));
                else setEnabled(false);
                break;
            case "allow_exec":
                if (!TermuxBridge.allowExec()) confirm(getString(R.string.RegramTermuxExecWarning), () -> setExec(true));
                else setExec(false);
                break;
            case "pair":
                if (!TermuxBridge.enabled()) confirm(getString(R.string.RegramTermuxEnableWarning), () -> { setEnabled(true); pair(); });
                else pair();
                break;
            case "restart": bridge.start(); refresh(); notice(bridge.status()); break;
            case "test": bridge.selfTest(); break;
            case "rotate": confirm(getString(R.string.RegramTermuxRotateWarning), () -> { bridge.rotateToken(); refresh(); }); break;
            case "ai_docs":
                try { AndroidUtilities.addToClipboard(bridge.aiDocs()); notice(getString(R.string.TextCopied)); }
                catch (Exception e) { notice(e.getMessage()); }
                break;
            case "ai_test": bridge.testAi(result -> AndroidUtilities.runOnUIThread(() -> notice(result))); break;
            case "info": break;
            default: edit(key, titles[index]);
        }
    }
    private void setEnabled(boolean value) {
        TermuxBridge.settings().edit().putBoolean("enabled", value).commit();
        if (value) TermuxBridge.getInstance().attach(getParentActivity());
        else TermuxBridge.getInstance().stop();
        refresh();
    }
    private void setExec(boolean value) {
        TermuxBridge.settings().edit().putBoolean("allow_exec", value).commit();
        TermuxBridge.getInstance().start(); // Invalidate existing sessions, handles, listeners and views.
        refresh();
    }
    private void confirm(String message, Runnable accepted) {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(getActionBarTitle())
                .setMessage(message).setPositiveButton(getString(R.string.OK), (d, which) -> accepted.run())
                .setNegativeButton(getString(R.string.Cancel), null).create());
    }
    private void pair() {
        try {
            String command = TermuxBridge.getInstance().pairingCommand(); refresh();
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(getString(R.string.RegramTermuxPair))
                    .setMessage(getString(R.string.RegramTermuxPairInfo) + "\n\n" + command)
                    .setPositiveButton(getString(R.string.Copy), (d, which) -> AndroidUtilities.addToClipboard(command))
                    .setNegativeButton(getString(R.string.Cancel), null).create());
        } catch (Exception e) { notice(e.getMessage()); }
    }
    private String defaultValue(String key) {
        if (key.equals("port")) return "42691";
        if (key.equals("ai_endpoint")) return TermuxBridge.AI_URL;
        if (key.equals("ai_model")) return TermuxBridge.AI_MODEL;
        return "";
    }
    private void edit(String key, int title) {
        EditText input = new EditText(getParentActivity());
        input.setText(TermuxBridge.settings().getString(key, defaultValue(key)));
        input.setSingleLine(!key.equals("ai_extra"));
        if (key.equals("port")) input.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (key.equals("ai_key")) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(getString(title)).setView(input)
                .setPositiveButton(getString(R.string.Save), (d, which) -> {
                    String value = input.getText().toString().trim();
                    try {
                        if (key.equals("port")) {
                            int port = Integer.parseInt(value);
                            if (port < 1024 || port > 65535) throw new IllegalArgumentException("1024–65535");
                        }
                        if (key.equals("ai_endpoint")) {
                            java.net.URI uri = new java.net.URI(value);
                            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                                throw new IllegalArgumentException("HTTPS");
                        }
                        TermuxBridge.settings().edit().putString(key, value).commit();
                        if (key.equals("port")) TermuxBridge.getInstance().start();
                        refresh();
                    } catch (Exception e) { notice(e.getMessage()); }
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }
    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        private int index(int position) { for (int i = 0; i < rows.length; i++) if (rows[i] == position) return i; return -1; }
        @Override public int getItemViewType(int position) {
            int i = index(position);
            if (i == 0 || i == 11) return TYPE_CHECK;
            return i == 13 ? TYPE_INFO_PRIVACY : TYPE_SETTINGS;
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            int i = index(position); if (i < 0) return;
            if (holder.itemView instanceof TextCheckCell) ((TextCheckCell) holder.itemView).setTextAndCheck(getString(titles[i]), i == 0 ? TermuxBridge.enabled() : TermuxBridge.allowExec(), true);
            else if (holder.itemView instanceof TextInfoPrivacyCell) ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.RegramTermuxInfo));
            else {
                String value = ""; String key = keys[i];
                if (key.equals("ai_key")) value = TermuxBridge.settings().getString(key, "").isEmpty() ? "" : "••••••••";
                else if (key.equals("port") || key.equals("ai_model")) value = TermuxBridge.settings().getString(key, defaultValue(key));
                else if (key.equals("restart")) value = TermuxBridge.getInstance().status();
                ((TextSettingsCell) holder.itemView).setTextAndValue(getString(titles[i]), value, true);
            }
        }
    }
}
