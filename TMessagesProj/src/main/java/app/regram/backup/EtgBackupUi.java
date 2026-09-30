package app.regram.backup;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Intent;

import com.google.gson.JsonObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;

import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.utils.FileUtil;
import tw.nekomimi.nekogram.utils.ShareUtil;

/** Диалоги импорта и экспорта настроек exteraGram: экран настроек и тап по файлу в чате. */
public final class EtgBackupUi {

    private EtgBackupUi() {
    }

    public static void export(BaseFragment fragment) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        try {
            File file = new File(AndroidUtilities.getCacheDir(), EtgBackup.generateBackupName());
            FileUtil.writeUtf8String(EtgBackup.buildBackup(true), file);
            ShareUtil.shareFile(activity, file);
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(activity, e);
        }
    }

    /** Спрашивает подтверждение и импортирует. Возвращает false, если файл не наш. */
    public static boolean confirmImport(BaseFragment fragment, File file) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null || file == null) {
            return false;
        }
        JsonObject backup = EtgBackup.readBackup(file);
        int known = backup == null ? 0 : EtgBackup.countKnownKeys(backup);
        if (known == 0) {
            BulletinFactory.of(fragment)
                    .createErrorBulletin(getString(R.string.OEGeneralImportEtgEmpty))
                    .show();
            return false;
        }
        AlertUtil.showConfirm(activity,
                getString(R.string.OEGeneralImportEtgSettings),
                LocaleController.formatString(R.string.OEGeneralImportEtgConfirm, known),
                R.drawable.msg_settings,
                getString(R.string.Import),
                false,
                () -> apply(fragment, backup));
        return true;
    }

    private static void apply(BaseFragment fragment, JsonObject backup) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        int applied = EtgBackup.applyBackup(backup);

        LocaleController.getInstance().recreateFormatters();
        Theme.reloadAllResources(activity);
        if (fragment.getParentLayout() != null) {
            fragment.getParentLayout().rebuildAllFragmentViews(false, false);
        }
        NotificationCenter center = NotificationCenter.getInstance(UserConfig.selectedAccount);
        center.postNotificationName(NotificationCenter.reloadInterface);
        center.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
        center.postNotificationName(NotificationCenter.mainUserInfoChanged);
        center.postNotificationName(NotificationCenter.dialogFiltersUpdated);
        center.postNotificationName(NotificationCenter.dialogsNeedReload, true);

        BulletinFactory.of(fragment)
                .createSimpleBulletin(R.raw.contact_check,
                        LocaleController.formatString(R.string.OEGeneralImportEtgDone, applied))
                .show();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(getString(R.string.OEGeneralImportEtgSettings));
        builder.setMessage(getString(R.string.RestartAppToTakeEffect));
        builder.setPositiveButton(getString(R.string.RestartApp), (dialog, which) ->
                AppRestartHelper.triggerRebirth(activity, new Intent(activity, LaunchActivity.class)));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    /** Тап по документу в чате: если это .extera, предлагаем импорт и забираем обработку себе. */
    public static boolean handleFileTap(BaseFragment fragment, File file, String documentName) {
        if (file == null && documentName == null) {
            return false;
        }
        boolean matches = (file != null && file.getName().toLowerCase().endsWith(EtgBackup.EXTENSION))
                || (documentName != null && documentName.toLowerCase().endsWith(EtgBackup.EXTENSION));
        return matches && confirmImport(fragment, file);
    }
}
