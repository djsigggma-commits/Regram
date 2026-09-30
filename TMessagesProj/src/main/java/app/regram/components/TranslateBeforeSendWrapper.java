package app.regram.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.translate.TranslatorKt;

@SuppressLint("ViewConstructor")
public abstract class TranslateBeforeSendWrapper extends ActionBarMenuSubItem {

    private static final int MIN_WIDTH_DP = 196;
    private static final int ITEM_HEIGHT_DP = 56;

    public TranslateBeforeSendWrapper(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context, top, bottom, resourcesProvider);
        setTextAndIcon(LocaleController.getString(R.string.TranslateTo), R.drawable.msg_translate);
        setSubtext(getTargetLanguageTitle());
        setMinimumWidth(AndroidUtilities.dp(MIN_WIDTH_DP));
        setItemHeight(ITEM_HEIGHT_DP);
        setOnClickListener(v -> onClick());
        setOnLongClickListener(this::showLanguageSelect);
        setRightIcon(R.drawable.msg_arrowright);
        getRightIcon().setOnClickListener(v -> showLanguageSelect(this));
    }

    public static Locale getTargetLanguage() {
        return TranslatorKt.getCode2Locale(NekoConfig.translateInputLang.String());
    }

    private static CharSequence getTargetLanguageTitle() {
        Locale currentLocale = LocaleController.getInstance().getCurrentLocale();
        return getTargetLanguage().getDisplayName(currentLocale != null ? currentLocale : Locale.getDefault());
    }

    private boolean showLanguageSelect(View anchor) {
        Translator.showTargetLangSelect(anchor, true, locale -> {
            NekoConfig.translateInputLang.setConfigString(TranslatorKt.getLocale2code(locale));
            setSubtext(getTargetLanguageTitle());
            return Unit.INSTANCE;
        });
        return true;
    }

    public abstract void onClick();
}
