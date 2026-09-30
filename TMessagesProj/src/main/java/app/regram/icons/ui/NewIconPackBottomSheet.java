package app.regram.icons.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineEditText;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.Locale;

import app.regram.icons.IconPack;
import app.regram.icons.IconPackManager;
import app.regram.icons.IconPackStorage;
import app.regram.icons.IconPacksConfig;
import app.regram.icons.IconPacksEditorActivity;
import app.regram.icons.picker.IconPickerController;

/**
 * Шторка создания пака иконок и правки его метаданных
 * (порт {@code com.exteragram.messenger.icons.ui.components.NewIconPackBottomSheet}).
 *
 * <p>Три поля — имя, автор, версия — и одна кнопка. Без пака в конструкторе шторка создаёт
 * новый пак, включает его, делает редактируемым и сразу открывает
 * {@link IconPacksEditorActivity}; с паком — только переписывает metadata.json.
 *
 * <p>Отличия от эталона: id нового пака собирается из имени (как это уже делал наш
 * экран списка), а не из UUID, и вместо {@code NotificationCenter.iconPackUpdated}
 * вызывающий получает {@link #setOnDone(Runnable)} — в форке такого уведомления нет.
 */
public class NewIconPackBottomSheet extends BottomSheet {

    private static final int MAX_NAME_LENGTH = 64;
    private static final String DEFAULT_VERSION = "1.0";

    private final BaseFragment parentFragment;
    private final IconPack packToEdit;

    private OutlineEditText nameField;
    private OutlineEditText authorField;
    private OutlineEditText versionField;
    private ButtonWithCounterView doneButton;

    private Runnable onDone;

    public NewIconPackBottomSheet(BaseFragment parentFragment, Context context) {
        this(parentFragment, context, null);
    }

    public NewIconPackBottomSheet(BaseFragment parentFragment, Context context, @Nullable IconPack packToEdit) {
        super(context, true);
        this.parentFragment = parentFragment;
        this.packToEdit = packToEdit;
        fixNavigationBar();
        waitingKeyboard = true;
        smoothKeyboardAnimationEnabled = true;
        setCustomView(createView(getContext()));
        setTitle(getString(packToEdit == null ? R.string.IconPackCreate : R.string.IconPackEditInfo), true);
    }

    public void setOnDone(Runnable onDone) {
        this.onDone = onDone;
    }

    @SuppressLint("ClickableViewAccessibility")
    private android.view.View createView(Context context) {
        ScrollView scrollView = new ScrollView(context);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        container.setOnTouchListener((v, event) -> true);
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        FrameLayout fields = new FrameLayout(context);
        container.addView(fields, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        nameField = new OutlineEditText(context);
        nameField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        nameField.getEditText().setFilters(new InputFilter[]{new NameLengthFilter()});
        nameField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameField.setHint(getString(R.string.IconPackName));
        if (packToEdit != null) {
            nameField.getEditText().setText(packToEdit.getName());
        }
        nameField.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_NEXT) {
                return false;
            }
            focus(authorField);
            return true;
        });
        fields.addView(nameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58f,
                Gravity.LEFT | Gravity.TOP, 0f, 0f, 0f, 0f));

        authorField = new OutlineEditText(context);
        authorField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        authorField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        authorField.setHint(getString(R.string.IconPackAuthorOptional));
        authorField.getEditText().setText(packToEdit != null ? packToEdit.getAuthor() : defaultAuthor());
        authorField.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_NEXT) {
                return false;
            }
            focus(versionField);
            return true;
        });
        fields.addView(authorField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58f,
                Gravity.LEFT | Gravity.TOP, 0f, 68f, 0f, 0f));

        versionField = new OutlineEditText(context);
        versionField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        versionField.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
        versionField.setHint(getString(R.string.IconPackVersion));
        versionField.getEditText().setText(packToEdit != null ? packToEdit.getVersion() : DEFAULT_VERSION);
        versionField.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            doneButton.callOnClick();
            return true;
        });
        fields.addView(versionField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58f,
                Gravity.LEFT | Gravity.TOP, 0f, 136f, 0f, 0f));

        doneButton = new ButtonWithCounterView(context, resourcesProvider);
        doneButton.setRound();
        doneButton.setText(getString(packToEdit == null ? R.string.Create : R.string.Save), false);
        doneButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        doneButton.setOnClickListener(v -> doOnDone());
        container.addView(doneButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0f, 16f, 0f, 16f));

        return scrollView;
    }

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        if (nameField == null || nameField.getEditText() == null) {
            return;
        }
        focus(nameField);
        AndroidUtilities.showKeyboard(nameField.getEditText());
    }

    private static void focus(OutlineEditText field) {
        field.requestFocus();
        field.getEditText().setSelection(field.getEditText().length());
    }

    private void doOnDone() {
        if (nameField.getEditText().length() == 0) {
            AndroidUtilities.vibrate(nameField);
            AndroidUtilities.shakeView(nameField);
            return;
        }
        final String name = nameField.getEditText().getText().toString().trim();
        final String author = authorField.getEditText().getText().toString().trim();
        final String versionText = versionField.getEditText().getText().toString().trim();
        final String version = TextUtils.isEmpty(versionText) ? DEFAULT_VERSION : versionText;
        if (TextUtils.isEmpty(name)) {
            BulletinFactory.of(topBulletinContainer, resourcesProvider)
                    .createErrorBulletin(getString(R.string.IconPackNameCannotBeEmpty))
                    .show();
            return;
        }
        final String finalAuthor = TextUtils.isEmpty(author) ? getString(R.string.IconPackNoAuthor) : author;
        Utilities.globalQueue.postRunnable(() -> {
            if (packToEdit != null) {
                save(name, finalAuthor, version);
            } else {
                create(name, finalAuthor, version);
            }
        });
    }

    private void save(String name, String author, String version) {
        IconPack updated = new IconPack(packToEdit.getId(), name, author, version,
                packToEdit.getIcons(), packToEdit.getLocation());
        if (!IconPackStorage.saveIconPackMetadata(updated)) {
            showStorageError();
            return;
        }
        IconPackManager.getInstance().reloadInternal();
        AndroidUtilities.runOnUIThread(() -> {
            notifyDone();
            dismiss();
        });
    }

    private void create(String name, String author, String version) {
        final IconPack created = IconPackManager.getInstance()
                .createPack(generatePackId(name), name, author, version);
        if (created == null) {
            showStorageError();
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (!IconPacksConfig.enabled()) {
                IconPacksConfig.enabled.setConfigBool(true);
            }
            if (!IconPacksConfig.isPackActive(created.getId())) {
                IconPacksConfig.togglePack(created.getId());
            }
            IconPacksConfig.setEditingPackId(created.getId());
            IconPackManager.getInstance().reload();
            notifyDone();
            dismiss();
            if (parentFragment != null) {
                parentFragment.presentFragment(new IconPacksEditorActivity(created.getId()) {
                    @Override
                    public void onBecomeFullyVisible() {
                        if (LaunchActivity.instance != null) {
                            IconPickerController.setActive(LaunchActivity.instance, true);
                        }
                        super.onBecomeFullyVisible();
                    }
                });
            }
        });
    }

    private void notifyDone() {
        if (onDone != null) {
            onDone.run();
        }
    }

    private void showStorageError() {
        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(topBulletinContainer, resourcesProvider)
                .createErrorBulletin(getString(R.string.IconPackErrorStorage))
                .show());
    }

    /** Автор по умолчанию — свой @username, если он есть. */
    private static String defaultAuthor() {
        try {
            org.telegram.tgnet.TLRPC.User self = org.telegram.messenger.UserConfig
                    .getInstance(org.telegram.messenger.UserConfig.selectedAccount).getCurrentUser();
            String username = self == null ? null : org.telegram.messenger.UserObject.getPublicUsername(self);
            return TextUtils.isEmpty(username) ? "" : "@" + username;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Из имени делаем безопасный id; при коллизии добавляем суффикс. */
    private static String generatePackId(String name) {
        StringBuilder sb = new StringBuilder();
        String lower = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length() && sb.length() < 32; i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (c == ' ' || c == '-' || c == '_') {
                sb.append('_');
            }
        }
        String base = sb.length() == 0 ? "pack" : sb.toString();
        String id = base;
        int index = 1;
        while (IconPackStorage.findPackById(id) != null) {
            id = base + "_" + (++index);
        }
        return id;
    }

    /**
     * Ограничение имени пака в {@value #MAX_NAME_LENGTH} символов: при упоре в лимит
     * поле дрожит, как в эталоне, а суррогатная пара не режется пополам.
     */
    private class NameLengthFilter implements InputFilter {

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            int available = MAX_NAME_LENGTH - (dest.length() - (dend - dstart));
            int inserted = end - start;
            if (available < inserted) {
                AndroidUtilities.vibrate(nameField);
                AndroidUtilities.shakeView(nameField);
            }
            if (available <= 0) {
                return "";
            }
            if (available >= inserted) {
                return null;
            }
            int keepUntil = start + available;
            if (Character.isHighSurrogate(source.charAt(keepUntil - 1))) {
                keepUntil--;
                if (keepUntil == start) {
                    return "";
                }
            }
            return source.subSequence(start, keepUntil);
        }
    }
}
