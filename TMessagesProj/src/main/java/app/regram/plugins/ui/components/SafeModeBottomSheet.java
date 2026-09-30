package app.regram.plugins.ui.components;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerImageView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import app.regram.plugins.PluginsController;

/**
 * Предложение выйти из безопасного режима движка плагинов.
 *
 * Имя и конструктор совпадают с {@code com.exteragram.messenger.plugins.ui.components
 * .SafeModeBottomSheet}: плагины каталога зовут {@code SafeModeBottomSheet(fragment).show()}.
 */
public class SafeModeBottomSheet extends BottomSheet {

    private static final String STICKER_PACK = "AnimatedEmojies";
    private static final int STICKER_NUM = 12;

    public SafeModeBottomSheet(BaseFragment fragment) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        Activity activity = fragment.getParentActivity();
        fixNavigationBar();

        FrameLayout container = new FrameLayout(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        container.addView(content);

        StickerImageView sticker = new StickerImageView(activity, currentAccount);
        sticker.setStickerPackName(STICKER_PACK);
        sticker.setStickerNum(STICKER_NUM);
        sticker.getImageReceiver().setAutoRepeat(1);
        content.addView(sticker, LayoutHelper.createLinear(144, 144,
                Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        TextView title = new TextView(activity);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(LocaleController.getString(R.string.PluginsSafeMode));
        content.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 40, 20, 40, 0));

        TextView info = new TextView(activity);
        info.setGravity(Gravity.CENTER);
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        info.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        info.setText(LocaleController.getString(R.string.PluginsSafeModeSummary));
        content.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 21, 8, 21, 0));

        ButtonWithCounterView button = new ButtonWithCounterView(activity, true, resourcesProvider);
        button.setText(LocaleController.getString(R.string.Disable), false);
        button.setOnClickListener(v -> {
            dismiss();
            PluginsController.getInstance().restart(false);
        });
        content.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48,
                16, 28, 16, 16));

        setCustomView(container);
    }
}
