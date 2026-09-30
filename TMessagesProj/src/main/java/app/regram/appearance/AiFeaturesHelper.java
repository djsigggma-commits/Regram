package app.regram.appearance;

import android.text.TextUtils;

import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ArticleViewer;

/**
 * Гейты апстримных AI-функций Telegram: кнопка редактора, саммари сообщения
 * и врезка саммари в Instant View.
 */
public final class AiFeaturesHelper {

    /** Заголовок врезки, которой Telegram помечает саммари страницы. */
    private static final String IV_SUMMARY_CAPTION = "Cocoon AI Summary";

    private AiFeaturesHelper() {
    }

    public static boolean isAiEditorHidden() {
        return AppearanceConfig.hideAiEditor();
    }

    public static boolean isMessageSummaryHidden() {
        return AppearanceConfig.hideMessageSummary();
    }

    public static boolean shouldHideIvBlock(TL_iv.PageBlock block) {
        if (!AppearanceConfig.hideIvSummary()) {
            return false;
        }
        final TL_iv.RichText caption;
        if (block instanceof TL_iv.pageBlockBlockquote) {
            caption = ((TL_iv.pageBlockBlockquote) block).caption;
        } else if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
            caption = ((TL_iv.pageBlockBlockquoteBlocks) block).caption;
        } else {
            return false;
        }
        final CharSequence plain = ArticleViewer.getPlainText(caption);
        return plain != null && TextUtils.equals(IV_SUMMARY_CAPTION, plain.toString());
    }
}
