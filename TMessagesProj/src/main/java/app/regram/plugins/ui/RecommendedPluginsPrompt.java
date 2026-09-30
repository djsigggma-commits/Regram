package app.regram.plugins.ui;

import android.app.Activity;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import app.regram.plugins.PluginsConstants;
import app.regram.plugins.PluginsController;

/**
 * One question on the main screen: install the recommended plugin pack.
 * Asked once, after login, and only when no other dialog is already up.
 */
public final class RecommendedPluginsPrompt {
    private static RecommendedPluginsInstaller installer;
    private static boolean dialogShown;
    private static int attempt;

    private RecommendedPluginsPrompt() {}

    public static void onMainScreen(Activity activity, int account) {
        if (activity == null || activity.isFinishing()) return;
        int token = ++attempt;
        // After the chat list's own permission dialogs (they wait up to 4 seconds).
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> tryShow(activity, account, token), 4500);
    }

    private static void tryShow(Activity activity, int account, int token) {
        if (token != attempt || activity.isFinishing() || dialogShown) return;
        PluginsController controller = PluginsController.getInstance();
        if (controller.getPreferences() == null
                || controller.getPreferences().getBoolean(PluginsConstants.KEY_RECOMMENDED_PROMPT, false)
                || installer != null) {
            return;
        }
        if (!UserConfig.getInstance(account).isClientActivated()
                || UserConfig.getInstance(account).unacceptedTermsOfService != null
                || (SharedConfig.passcodeHash.length() > 0 && SharedConfig.appLocked)
                || ApplicationLoader.mainInterfacePaused) {
            return;
        }
        BaseFragment top = LaunchActivity.getLastFragment();
        if (top != null && top.visibleDialog != null && top.visibleDialog.isShowing()) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> tryShow(activity, account, token), 1500);
            return;
        }
        dialogShown = true;
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.RegramRecommendedDownload))
                .setMessage(activity.getString(R.string.RegramRecommendedPrompt))
                .setPositiveButton(activity.getString(R.string.RegramRecommendedInstall), (dialog, which) -> {
                    markAsked(controller);
                    begin(activity, account);
                })
                .setNegativeButton(activity.getString(R.string.RegramRecommendedLater), (dialog, which) -> markAsked(controller))
                .setOnCancelListener(dialog -> markAsked(controller))
                .setOnDismissListener(dialog -> dialogShown = false)
                .show();
    }

    private static void markAsked(PluginsController controller) {
        if (controller.getPreferences() == null) return;
        controller.getPreferences().edit()
                .putBoolean(PluginsConstants.KEY_RECOMMENDED_PROMPT, true)
                .apply();
    }

    private static void begin(Activity activity, int account) {
        if (installer != null || activity.isFinishing()) return;
        installer = new RecommendedPluginsInstaller(activity, account, new RecommendedPluginsInstaller.Callback() {
            @Override
            public void onFound(int count, Runnable confirm) {
                if (activity.isFinishing() || installer == null) {
                    if (installer != null) installer.cancel();
                    return;
                }
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.RegramRecommendedDownload))
                        .setMessage(LocaleController.formatString(R.string.RegramRecommendedWarning, count))
                        .setPositiveButton(activity.getString(R.string.OK), (dialog, which) -> confirm.run())
                        .setNegativeButton(activity.getString(R.string.Cancel), (dialog, which) -> installer.cancel())
                        .setOnCancelListener(dialog -> {
                            if (installer != null) installer.cancel();
                        })
                        .show();
            }

            @Override
            public void onFinished(String message) {
                installer = null;
                if (activity.isFinishing() || message == null) return;
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.RegramRecommendedDownload))
                        .setMessage(message)
                        .setPositiveButton(activity.getString(R.string.OK), null)
                        .show();
            }
        });
        installer.start();
    }
}
