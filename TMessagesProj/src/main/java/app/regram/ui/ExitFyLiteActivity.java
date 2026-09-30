package app.regram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import java.util.Arrays;

import app.regram.plugins.PluginPermissions;
import app.regram.plugins.PluginTrustLevel;
import app.regram.plugins.PluginsController;
import app.regram.plugins.ui.PluginSettingsActivity;
import app.regram.plugins.ui.PluginsActivity;
import org.telegram.ui.ActionBar.AlertDialog;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import app.regram.general.GeneralHelper;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.ProxyListActivity;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

public class ExitFyLiteActivity extends BaseNekoSettingsActivity {
    private int fullExitFyRow;
    private int subscriptionRow;
    private int refreshRow;
    private int optimizeRow;
    private int proxiesRow;
    private int infoRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        fullExitFyRow = addRow("exitFyFullRuntime");
        subscriptionRow = addRow("exitFyLiteSubscription");
        refreshRow = addRow("exitFyLiteRefresh");
        optimizeRow = addRow("exitFyLiteOptimize");
        proxiesRow = addRow("exitFyLiteProxies");
        infoRow = addRow();
    }

    @Override
    public int getSearchGuid() { return 28010; }

    @Override
    public int getSearchIcon() { return R.drawable.msg_settings_old; }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.RegramExitFy);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == fullExitFyRow) {
            openFullExitFy();
        } else if (position == subscriptionRow) {
            editSubscription();
        } else if (position == refreshRow) {
            ExitFyLite.refreshSubscription(ExitFyLite.getSubscriptionUrl(), this::showStatus);
        } else if (position == optimizeRow) {
            ExitFyLite.optimizeNow(this::showStatus);
        } else if (position == proxiesRow) {
            presentFragment(new ProxyListActivity());
        }
    }

    private void openFullExitFy() {
        PluginsController controller = PluginsController.getInstance();
        if (BundledExitFy.isInstalled(controller)) {
            if (controller.getPlugin(BundledExitFy.ID) != null
                    && controller.getPlugin(BundledExitFy.ID).loaded) {
                presentFragment(new PluginSettingsActivity(BundledExitFy.ID));
            } else {
                presentFragment(new PluginsActivity());
            }
            return;
        }
        Context context = getParentActivity();
        if (context == null) return;
        if (!BundledExitFy.isSupported()) {
            showStatus(getString(R.string.RegramExitFyUnsupported));
            return;
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setTitle(getString(R.string.RegramExitFyInstall));
        dialog.setMessage(getString(R.string.RegramExitFyWarning));
        dialog.setNegativeButton(getString(R.string.Cancel), null);
        dialog.setPositiveButton(getString(R.string.RegramExitFyInstall), (d, which) -> {
            BundledExitFy.install(context, (ok, error) -> {
                if (!ok) {
                    showStatus(error == null ? "exitFy installation failed" : error);
                    return;
                }
                // Consent covers native code, network, Java hooks and proxy settings.
                PluginPermissions.setGranted(BundledExitFy.ID, Arrays.asList(
                        PluginPermissions.NETWORK, PluginPermissions.SETTINGS,
                        PluginPermissions.HOOKS, PluginPermissions.NATIVE));
                PluginTrustLevel.setLevel(BundledExitFy.ID, PluginTrustLevel.TRUSTED);
                // installPlugin started the runtime already; load once before the
                // engine's asynchronous rescan to avoid a duplicate load.
                if (!controller.setPluginEnabled(BundledExitFy.ID, true)) {
                    showStatus("exitFy could not start; check Plugins for details");
                    return;
                }
                controller.setEngineEnabled(true);
                showStatus(getString(R.string.RegramExitFyInstalled));
            });
        });
        showDialog(dialog.create());
    }

    private void editSubscription() {
        GeneralHelper.showTextInputDialog(
                this,
                getString(R.string.RegramExitFyLiteSubscription),
                "https://example.com/proxies.txt",
                ExitFyLite.getSubscriptionUrl(),
                value -> {
                    ExitFyLite.setSubscriptionUrl(value);
                    if (listView != null && listView.getAdapter() != null) {
                        listView.getAdapter().notifyItemChanged(subscriptionRow);
                    }
                });
    }

    private void showStatus(String message) {
        Context context = getParentActivity();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new BaseListAdapter(context) {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == TYPE_SETTINGS;
            }

            @Override
            public int getItemViewType(int position) {
                return position == infoRow ? TYPE_INFO_PRIVACY : TYPE_SETTINGS;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
                if (position == infoRow) {
                    ((TextInfoPrivacyCell) holder.itemView).setText(getString(R.string.RegramExitFyLiteInfo));
                    return;
                }
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == fullExitFyRow) {
                    cell.setText(getString(BundledExitFy.isInstalled(PluginsController.getInstance())
                            ? R.string.RegramExitFyOpen : R.string.RegramExitFyInstall), true);
                } else if (position == subscriptionRow) {
                    String value = ExitFyLite.getSubscriptionUrl();
                    cell.setTextAndValue(getString(R.string.RegramExitFyLiteSubscription), value, true);
                } else if (position == refreshRow) {
                    cell.setText(getString(R.string.RegramExitFyLiteRefresh), true);
                } else if (position == optimizeRow) {
                    cell.setText(getString(R.string.RegramExitFyLiteOptimize), true);
                } else if (position == proxiesRow) {
                    cell.setText(getString(R.string.ProxySettings), false);
                }
            }
        };
    }
}
