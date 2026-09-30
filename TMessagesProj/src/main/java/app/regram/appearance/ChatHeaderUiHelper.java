package app.regram.appearance;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityTopPanelLayout;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;

/**
 * Material 3 для шапки чата.
 * exteraGram: com/exteragram/messenger/utils/ui/ChatHeaderUiHelper.java (233 строки).
 *
 * Перенесены только те методы, у которых в нашем дереве есть на что опереться;
 * ProfileTransitionState опущен — см. notes.
 */
public final class ChatHeaderUiHelper {

    private ChatHeaderUiHelper() {
    }

    public static boolean isMaterial3ChatHeaderStyle() {
        return AppearanceConfig.newChatHeaderStyle();
    }

    /** Назад ? 4 : (m3 ? 57 : 52). */
    public static int getAvatarContainerLeftMargin(boolean withBackButton) {
        if (withBackButton) {
            return 4;
        }
        return isMaterial3ChatHeaderStyle() ? 57 : 52;
    }

    /** M3 ? 0 : 1 пиксель отступа вокруг аватарки. */
    public static int getAvatarInsetPx() {
        return isMaterial3ChatHeaderStyle() ? 0 : 1;
    }

    public static int getAvatarSizePx(int sizeDp) {
        return AndroidUtilities.dp(sizeDp) - getAvatarInsetPx() * 2;
    }

    /** M3 ? 46 : 42. */
    public static int getChatAvatarSizeDp() {
        return isMaterial3ChatHeaderStyle() ? 46 : 42;
    }

    /**
     * Радиус аватарки в шапке. У exteraGram это ExteraConfig.getAvatarCorners
     * (ChatHeaderUiHelper.java:159-165) из отдельного среза «углы аватарок»; у нас его нет,
     * поэтому радиусы пропорциональны размеру. При 42dp значения совпадают с прежними
     * (21 / 16 / 11 — ChatAvatarContainer.java:1510, 1618, 1626).
     */
    public static int getChatAvatarRadius(int sizeDp, boolean forum, boolean hasStories) {
        return AppearanceConfig.getAvatarCorners(AndroidUtilities.dp(sizeDp),
                forum ? AppearanceConfig.CORNER_TYPE_FORUM : AppearanceConfig.CORNER_TYPE_DEFAULT, hasStories);
    }

    /** Dp(m3 ? 78 : 48). */
    public static int getChatTopFadeHeight() {
        return AndroidUtilities.dp(isMaterial3ChatHeaderStyle() ? 78.0f : 48.0f);
    }

    /** M3 ? height + dp(42) : height. */
    public static int getChatTopFadeZone(int height) {
        return isMaterial3ChatHeaderStyle() ? height + AndroidUtilities.dp(42.0f) : height;
    }

    public static int getScaledChatTopFadeZone(ActionBar actionBar, int height) {
        final int measuredHeight = actionBar.getMeasuredHeight();
        final int zone = getChatTopFadeZone(measuredHeight);
        if (measuredHeight == 0 || zone >= height) {
            return height;
        }
        return zone + Math.round((height - zone) * 0.5f);
    }

    public static int getChatFadeColorKey() {
        return Theme.key_windowBackgroundGray;
    }

    public static void setupChatTopFade(ChatActivityFadeView fadeView, ActionBar actionBar, int fadeColor, int actionBarHeight) {
        fadeView.setFadeTopAlpha(actionBar.getVisibility() == ActionBar.VISIBLE ? 255 : 0);
        final int zone = getChatTopFadeZone(actionBarHeight);
        if (isMaterial3ChatHeaderStyle()) {
            fadeView.setFadeZoneTop(getScaledChatTopFadeZone(actionBar, zone));
            fadeView.setTopFadeColor(fadeColor);
        } else {
            fadeView.setFadeZoneTop(zone);
        }
        fadeView.setFadeHeightTop(getChatTopFadeHeight());
    }

    public static float getTopPanelActionBarGapOffset(ChatActivityTopPanelLayout topPanel) {
        if (!isMaterial3ChatHeaderStyle() || topPanel == null) {
            return 0.0f;
        }
        return AndroidUtilities.dp(4.0f) * topPanel.getMetadata().getTotalVisibility();
    }

    public static float getFinalTopPanelHeight(float height, ChatActivityTopPanelLayout topPanel) {
        return height + getTopPanelActionBarGapOffset(topPanel);
    }

    /** (a + dp(m3 ? -1 : -5)) - b * c. */
    public static float getTopPanelTranslationY(float base, float value, float factor) {
        return (base + AndroidUtilities.dp(isMaterial3ChatHeaderStyle() ? -1 : -5)) - value * factor;
    }

    public static boolean isLightChatStatusBar(ActionBar actionBar, int color) {
        if (!isMaterial3ChatHeaderStyle() || actionBar.isActionModeShowed()) {
            color = actionBar.getBackgroundColor();
        }
        return AndroidUtilities.computePerceivedBrightness(color) > 0.721f;
    }

    /** M3 убирает среднюю стеклянную плашку под заголовком и её тень. */
    public static void applyChatHeaderGlassStyle(ActionBar actionBar) {
        if (isMaterial3ChatHeaderStyle()) {
            actionBar.setDrawGlassMiddlePill(false);
            actionBar.setGlassShadowAlpha(0.0f);
        }
    }

    public static void setupGlassAvatarContainer(ChatAvatarContainer avatarContainer) {
        avatarContainer.setGlassMode();
        if (isMaterial3ChatHeaderStyle()) {
            avatarContainer.setAvatarSizeInDp(getChatAvatarSizeDp());
        }
    }
}
