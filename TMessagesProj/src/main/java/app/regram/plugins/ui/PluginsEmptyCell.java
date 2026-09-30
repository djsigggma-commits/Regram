package app.regram.plugins.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.StickerImageView;

final class PluginsEmptyCell extends FrameLayout {

    private static final String EMPTY_STICKER_PACK = "AnimatedEmojies";
    private static final int EMPTY_STICKER_INDEX = 47;

    private final LinearLayout content;
    private final LinkSpanDrawable.LinksTextView hintView;

    private boolean initialized;
    private boolean contentVisible;
    private int animationGeneration;

    PluginsEmptyCell(Context context, int currentAccount) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        StickerImageView stickerView = new StickerImageView(context, currentAccount);
        stickerView.setAspectFit(true);
        stickerView.setStickerPackName(EMPTY_STICKER_PACK);
        stickerView.setStickerNum(EMPTY_STICKER_INDEX);
        content.addView(stickerView, LayoutHelper.createLinear(130, 130, Gravity.CENTER,
                0, 0, 0, 16));

        hintView = new LinkSpanDrawable.LinksTextView(context);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintView.setGravity(Gravity.CENTER);
        hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hintView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        content.addView(hintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
    }

    void setState(boolean visible, CharSequence text, boolean animated) {
        boolean textChanged = !TextUtils.equals(hintView.getText(), text);
        if (initialized && visible == contentVisible) {
            if (!textChanged) {
                return;
            }
            if (!visible) {
                hintView.setText(text);
                return;
            }
        }
        int generation = ++animationGeneration;
        content.animate().setListener(null).cancel();

        if (!initialized || !animated || !isAttachedToWindow()) {
            hintView.setText(text);
            contentVisible = visible;
            initialized = true;
            content.setAlpha(visible ? 1f : 0f);
            content.setVisibility(visible ? VISIBLE : INVISIBLE);
            content.setEnabled(visible);
            hintView.setEnabled(visible);
            return;
        }

        initialized = true;
        content.setEnabled(visible);
        hintView.setEnabled(visible);

        if (textChanged && visible && contentVisible) {
            content.animate()
                    .alpha(0f)
                    .setDuration(90)
                    .withEndAction(() -> {
                        if (generation != animationGeneration) {
                            return;
                        }
                        hintView.setText(text);
                        content.animate()
                                .alpha(1f)
                                .setDuration(150)
                                .withEndAction(null)
                                .start();
                    })
                    .start();
        } else {
            if (textChanged) {
                hintView.setText(text);
            }
            content.setVisibility(VISIBLE);
            content.animate()
                    .alpha(visible ? 1f : 0f)
                    .setDuration(180)
                    .withEndAction(() -> {
                        if (generation == animationGeneration && !visible) {
                            content.setVisibility(INVISIBLE);
                        }
                    })
                    .start();
        }
        contentVisible = visible;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
