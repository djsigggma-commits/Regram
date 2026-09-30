package app.regram.plugins;

public final class ClassAliases {

    public static final String ROOT = "com.exteragram.messenger";

    public static final String MEDIA_ROOT = "com.google.android.exoplayer2";

    /** Наше прежнее имя пакета: так к классам обращались плагины под exteraless. */
    public static final String LEGACY_ROOT = "app.exteraless";

    public static final String OUR_ROOT = "app.regram";

    private static final String[] ROOTS = {ROOT, MEDIA_ROOT, LEGACY_ROOT};

    private static final String[][] EXACT = {
            {"com.exteragram.messenger.utils.chats.ChatUtils", "com.exteragram.messenger.utils.chats.ChatUtils"},
            {"com.exteragram.messenger.utils.ChatUtils", "com.exteragram.messenger.utils.chats.ChatUtils"},
            {"com.exteragram.messenger.utils.text.LocaleUtils", "com.exteragram.messenger.utils.text.LocaleUtils"},
            {"com.exteragram.messenger.utils.LocaleUtils", "com.exteragram.messenger.utils.text.LocaleUtils"},
            {"com.exteragram.messenger.utils.AppUtils", "com.exteragram.messenger.utils.AppUtils"},
            {"com.exteragram.messenger.R", "org.telegram.messenger.R"},
            {"com.exteragram.messenger.utils.system.VibratorUtils", "com.exteragram.messenger.utils.system.VibratorUtils"},
            {"com.exteragram.messenger.ai.AiConfig", "app.regram.ai.AiConfig"},
            {"com.exteragram.messenger.ai.AiController", "com.exteragram.messenger.ai.AiController"},
            {"com.exteragram.messenger.ai.ui.ResponseAlert", "com.exteragram.messenger.ai.ui.ResponseAlert"},
            {"com.exteragram.messenger.ai.ui.GenerateFromMessageBottomSheet", "com.exteragram.messenger.ai.ui.GenerateFromMessageBottomSheet"},
            {"com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet", "com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet"},
            {"com.exteragram.messenger.utils.system.SystemUtils", "com.exteragram.messenger.utils.system.SystemUtils"},
            {"com.exteragram.messenger.utils.SystemUtils", "com.exteragram.messenger.utils.system.SystemUtils"},
            {"com.exteragram.messenger.preferences.MainPreferencesActivity", "app.regram.settings.OpenExteraSettingsActivity"},
            {"com.exteragram.messenger.preferences.GeneralPreferencesActivity", "app.regram.settings.OpenExteraGeneralActivity"},
            {"com.exteragram.messenger.preferences.AppearancePreferencesActivity", "app.regram.settings.OpenExteraAppearanceActivity"},
            {"com.exteragram.messenger.preferences.ChatsPreferencesActivity", "app.regram.settings.OpenExteraChatsActivity"},
            {"com.exteragram.messenger.preferences.OtherPreferencesActivity", "app.regram.settings.OpenExteraOtherActivity"},
            {"com.exteragram.messenger.preferences.AppNavigationPreferencesActivity", "app.regram.settings.OpenExteraAppNavigationActivity"},
            {"com.exteragram.messenger.preferences.BasePreferencesActivity", "com.exteragram.messenger.preferences.BasePreferencesActivity"},
            {"com.exteragram.messenger.preferences.components.AltSeekbar", "app.regram.appearance.AltSeekbar"},
            {"com.exteragram.messenger.utils.chats.MainMenuHelper", "app.regram.drawer.MainMenuHelper"},
            {"com.exteragram.messenger.icons.ui.IconPacksActivity", "app.regram.icons.IconPacksActivity"},
            {"com.exteragram.messenger.pillstack.ui.pills.crypto.utils.ColoredBackground", "app.regram.pillstack.pills.ColoredBackground"},
            {"com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPill", "app.regram.pillstack.pills.WeatherPill"},
            {"com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity", "app.regram.pillstack.PillStackSettingsActivity"},
            {"com.exteragram.messenger.pillstack.ui.PillStackLayout", "app.regram.pillstack.PillStackView"},
            {"com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPreferencesActivity", "app.regram.pillstack.pills.weather.WeatherSettingsActivity"},
            {"com.exteragram.messenger.preferences.appearance.AppearancePreferencesActivity", "app.regram.settings.OpenExteraAppearanceActivity"},
            {"com.exteragram.messenger.nowplaying.ui.components.NowPlayingCard", "app.regram.components.ProfileMusicCard"},
            {"com.exteragram.messenger.nowplaying.NowPlayingController", "app.regram.nowplaying.NowPlayingController"},
            {"com.exteragram.messenger.proxy.ProxyController", "app.regram.proxy.ProxyController"},
            {"com.exteragram.messenger.utils.ui.UIUtil", "app.regram.utils.UIUtil"},
            {"com.exteragram.messenger.utils.ui.MainTabsUiHelper", "app.regram.appearance.MainTabsUiHelper"},
            {"com.google.android.exoplayer2.util.Consumer", "androidx.media3.common.util.Consumer"},
            {"com.google.android.exoplayer2.video.VideoSize", "androidx.media3.common.VideoSize"},
            {"com.google.android.exoplayer2.PlaybackException", "androidx.media3.common.PlaybackException"},
            {"com.google.android.exoplayer2.PlaybackParameters", "androidx.media3.common.PlaybackParameters"},
            {"com.google.android.exoplayer2.C", "androidx.media3.common.C"},
    };

    private static final String[][] PREFIXES = {
            {"com.exteragram.messenger.ai.data.", "app.regram.ai.data."},
            {"com.exteragram.messenger.ai.network.", "app.regram.ai.network."},
            {"com.exteragram.messenger.pillstack.core.", "app.regram.pillstack."},
            {"com.exteragram.messenger.pillstack.ui.pills.", "app.regram.pillstack.pills."},
            {"com.exteragram.messenger.pillstack.ui.", "app.regram.pillstack."},
            {"com.exteragram.messenger.preferences.", "app.regram.settings."},
            {"com.exteragram.messenger.plugins.", "app.regram.plugins."},
            {"com.exteragram.messenger.icons.", "app.regram.icons."},
            {"com.exteragram.messenger.camera.", "app.regram.camera."},
            {"com.exteragram.messenger.backup.", "app.regram.backup."},
            {"com.exteragram.messenger.feed.", "app.regram.feed."},
            {"com.exteragram.messenger.drawer.", "app.regram.drawer."},
            {"com.exteragram.messenger.components.", "app.regram.components."},
            {"com.exteragram.messenger.utils.", "app.regram.utils."},
            {"app.exteraless.", "app.regram."},
    };

    private ClassAliases() {
    }

    public static String resolve(String name) {
        if (name == null || !underRoot(name)) {
            return name;
        }
        String exact = lookup(name);
        if (exact != null) {
            return exact;
        }
        int dollar = name.indexOf('$');
        String outer = dollar < 0 ? name : name.substring(0, dollar);
        String nested = dollar < 0 ? "" : name.substring(dollar);
        exact = lookup(outer);
        if (exact != null) {
            return exact + nested;
        }
        for (String[] pair : PREFIXES) {
            if (outer.startsWith(pair[0])) {
                return pair[1] + outer.substring(pair[0].length()) + nested;
            }
        }
        return name;
    }

    private static boolean underRoot(String name) {
        for (String root : ROOTS) {
            if (name.startsWith(root + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String lookup(String name) {
        for (String[] pair : EXACT) {
            if (pair[0].equals(name)) {
                return pair[1];
            }
        }
        return null;
    }
}
