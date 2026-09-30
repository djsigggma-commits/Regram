package app.regram.utils.text;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TranslateController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.translate.Translator;

/**
 * Перевод текста под именем {@code com.exteragram.messenger.utils.text.TranslatorUtils}.
 *
 * Своего движка перевода форк не заводит: под капотом — переводчики NagramX
 * ({@link Translator}). Совпадают имя класса, имя вложенного интерфейса и формы
 * {@code translate(...)} — именно их зовут плагины каталога.
 */
public final class TranslatorUtils {

    public interface TranslateCallback {

        void onFailed();

        default void onReqId(int reqId) {
        }

        default void onSuccess(String text) {
        }

        default void onSuccess(TLObject response, TLRPC.TL_error error) {
        }

        default void onSuccess(TLRPC.TL_textWithEntities text) {
        }
    }

    private TranslatorUtils() {
    }

    public static void translate(CharSequence text, String toLanguage,
                                 TranslateCallback callback) {
        translate(text, null, toLanguage, null, callback);
    }

    public static void translate(CharSequence text, String toLanguage,
                                 ArrayList<TLRPC.MessageEntity> entities,
                                 TranslateCallback callback) {
        translate(text, null, toLanguage, entities, callback);
    }

    public static void translate(CharSequence text, String fromLanguage, String toLanguage,
                                 ArrayList<TLRPC.MessageEntity> entities,
                                 TranslateCallback callback) {
        if (TextUtils.isEmpty(text)) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback::onFailed);
            }
            return;
        }
        Locale target = localeOf(toLanguage != null ? toLanguage : getResolvedTargetLanguageCode());
        ArrayList<TLRPC.MessageEntity> payload = entities == null ? new ArrayList<>() : entities;
        Translator.translate(target, text.toString(), payload, new Translator.Companion.TranslateCallBack2() {
            @Override
            public void onSuccess(TLRPC.TL_textWithEntities finalText) {
                if (callback == null) {
                    return;
                }
                callback.onSuccess(finalText);
                callback.onSuccess(finalText.text == null ? null : finalText.text.toString());
            }

            @Override
            public void onFailed(boolean unsupported, String message) {
                if (callback != null) {
                    callback.onFailed();
                }
            }
        });
    }

    public static String getResolvedTargetLanguageCode() {
        String configured = NekoConfig.translateToLang.String();
        if (!TextUtils.isEmpty(configured)) {
            return configured;
        }
        return LocaleController.getInstance().getCurrentLocale().getLanguage();
    }

    public static String getResolvedTargetLanguageCode(String fallback) {
        String code = getResolvedTargetLanguageCode();
        return TextUtils.isEmpty(code) ? fallback : code;
    }

    public static boolean isTargetLanguageFollowApp() {
        return TextUtils.isEmpty(NekoConfig.translateToLang.String());
    }

    public static String getTargetLanguageTitle() {
        return getLanguageDisplayName(getResolvedTargetLanguageCode());
    }

    public static String getLanguageDisplayName(String code) {
        if (TextUtils.isEmpty(code)) {
            return "";
        }
        Locale locale = localeOf(code);
        String name = locale.getDisplayName(LocaleController.getInstance().getCurrentLocale());
        return TextUtils.isEmpty(name) ? code : name;
    }

    public static String getLanguageTitleSystem(String code) {
        if (TextUtils.isEmpty(code)) {
            return "";
        }
        return localeOf(code).getDisplayName(Locale.US);
    }

    public static ArrayList<TranslateController.Language> getCurrentTargetLanguages() {
        return TranslateController.getLanguages();
    }

    private static Locale localeOf(String code) {
        if (TextUtils.isEmpty(code)) {
            return LocaleController.getInstance().getCurrentLocale();
        }
        String normalized = code.replace('_', '-');
        int dash = normalized.indexOf('-');
        return dash > 0
                ? new Locale(normalized.substring(0, dash), normalized.substring(dash + 1))
                : new Locale(normalized);
    }
}
