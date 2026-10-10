package app.regram.settings.utils;

import static org.telegram.messenger.LocaleController.getString;

import android.text.Layout;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;

import java.util.ArrayList;

public final class SettingsRowMenu {

    private SettingsRowMenu() {
    }

    public static void show(BaseFragment fragment, View row, String link) {
        ItemOptions options = ItemOptions.makeOptions(fragment, row);
        boolean hasText = false;
        if (hasTruncatedText(row)) {
            ArrayList<CharSequence> texts = new ArrayList<>();
            collectTexts(row, texts);
            int maxWidth = AndroidUtilities.dp(280);
            for (int i = 0; i < texts.size(); i++) {
                options.addText(texts.get(i), i == 0 ? 14 : 13, maxWidth);
            }
            hasText = !texts.isEmpty();
        }
        options.addGapIf(hasText);
        options.add(R.drawable.msg_link, getString(R.string.CopyLink), () -> {
            AndroidUtilities.addToClipboard(link);
            BulletinFactory.of(fragment).createCopyLinkBulletin().show();
        });
        options.show();
    }

    private static boolean hasTruncatedText(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Layout layout = textView.getLayout();
            CharSequence text = textView.getText();
            if (layout == null || TextUtils.isEmpty(text)) {
                return false;
            }
            for (int i = 0; i < layout.getLineCount(); i++) {
                if (layout.getEllipsisCount(i) > 0) {
                    return true;
                }
            }
            return layout.getLineCount() == 1 && Layout.getDesiredWidth(text, textView.getPaint()) > layout.getWidth() + 1;
        }
        if (view instanceof SimpleTextView) {
            SimpleTextView textView = (SimpleTextView) view;
            CharSequence text = textView.getText();
            if (TextUtils.isEmpty(text)) {
                return false;
            }
            int shownWidth = textView.getTextWidth();
            if (shownWidth > textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight()) {
                return true;
            }
            return Layout.getDesiredWidth(text, textView.getTextPaint()) > shownWidth + AndroidUtilities.dp(1);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasTruncatedText(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectTexts(View view, ArrayList<CharSequence> texts) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        CharSequence text = null;
        if (view instanceof TextView) {
            text = ((TextView) view).getText();
        } else if (view instanceof SimpleTextView) {
            text = ((SimpleTextView) view).getText();
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTexts(group.getChildAt(i), texts);
            }
            return;
        }
        if (!TextUtils.isEmpty(text)) {
            texts.add(text);
        }
    }
}
