package app.regram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import app.regram.plugins.PluginPermissions;
import app.regram.plugins.PluginTrustLevel;
import app.regram.plugins.PluginsController;
import app.regram.plugins.ui.PluginSettingsActivity;
import app.regram.plugins.ui.PluginsActivity;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/** Entry point and permanently visible credit for the original Custom Profile author. */
public class CustomProfileActivity extends BaseNekoSettingsActivity {
    private int creatorRow;
    private int featureRow;
    private int infoRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        creatorRow = addRow("customProfileCreator");
        featureRow = addRow("customProfileFeature");
        infoRow = addRow();
    }

    @Override
    public int getSearchGuid() { return 28011; }

    @Override
    public int getSearchIcon() { return R.drawable.msg_settings_old; }

    @Override
    protected String getActionBarTitle() { return getString(R.string.RegramCustomProfile); }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == creatorRow) {
            if (getParentActivity() != null) {
                Browser.openUrl(getParentActivity(), "https://t.me/roflplugins");
            }
        } else if (position == featureRow) {
            openFeature();
        }
    }

    private void openFeature() {
        PluginsController controller = PluginsController.getInstance();
        if (BundledCustomProfile.isInstalled(controller)) {
            if (controller.getPlugin(BundledCustomProfile.ID) != null
                    && controller.getPlugin(BundledCustomProfile.ID).loaded) {
                presentFragment(new PluginSettingsActivity(BundledCustomProfile.ID));
            } else {
                presentFragment(new PluginsActivity());
            }
            return;
        }
        Context context = getParentActivity();
        if (context == null) return;
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setTitle(getString(R.string.RegramCustomProfileInstall));
        dialog.setMessage(getString(R.string.RegramCustomProfileWarning));
        dialog.setNegativeButton(getString(R.string.Cancel), null);
        dialog.setPositiveButton(getString(R.string.RegramCustomProfileInstall), (d, which) ->
                BundledCustomProfile.install(context, (ok, error) -> {
                    if (!ok) {
                        showStatus(error == null ? "Custom Profile installation failed" : error);
                        return;
                    }
                    // The original DEX uses profile/UI hooks and the original network service.
                    // Trusted is not a sandbox: the confirmation explicitly warns about this.
                    PluginPermissions.setGranted(BundledCustomProfile.ID, PluginPermissions.REQUESTABLE);
                    PluginTrustLevel.setLevel(BundledCustomProfile.ID, PluginTrustLevel.TRUSTED);
                    // installPlugin already started the Python runtime: load once before
                    // setEngineEnabled schedules a rescan of enabled plugins.
                    if (!controller.setPluginEnabled(BundledCustomProfile.ID, true)) {
                        showStatus(getString(R.string.RegramCustomProfileFailed));
                        return;
                    }
                    controller.setEngineEnabled(true);
                    showStatus(getString(R.string.RegramCustomProfileInstalled));
                }));
        showDialog(dialog.create());
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
                    ((TextInfoPrivacyCell) holder.itemView).setText(
                            getString(R.string.RegramCustomProfileInfo));
                    return;
                }
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == creatorRow) {
                    cell.setTextAndValue(getString(R.string.RegramCustomProfileCreator),
                            "@roflplugins", true);
                } else if (position == featureRow) {
                    cell.setText(getString(BundledCustomProfile.isInstalled(PluginsController.getInstance())
                            ? R.string.RegramCustomProfileOpen : R.string.RegramCustomProfileInstall), false);
                }
            }
        };
    }
}
