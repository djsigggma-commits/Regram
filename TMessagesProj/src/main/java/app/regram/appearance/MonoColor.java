package app.regram.appearance;

import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import android.os.Build;
import tw.nekomimi.nekogram.helpers.MonetHelper;
import tw.nekomimi.nekogram.NekoConfig;

/** Native rendering policy inspired by MonoColor UI 1.7 by @limeplug.
 * Never modifies theme maps, peer data, bitmaps or drawable source colors. */
public final class MonoColor {
    private MonoColor() {}
    private static volatile Palette cached;
    private static final int FALLBACK = 0xff4faede;

    public static boolean avatars() {
        return NekoConfig.regramMonoColor.Bool() && NekoConfig.regramMonoAvatars.Bool();
    }
    public static boolean profiles() {
        return NekoConfig.regramMonoColor.Bool() && NekoConfig.regramMonoProfiles.Bool();
    }
    public static boolean hasAvatarPhoto(Object peer) {
        return peer instanceof TLRPC.User && ((TLRPC.User) peer).photo != null
                || peer instanceof TLRPC.Chat && ((TLRPC.Chat) peer).photo != null;
    }
    public static int sourceColor() {
        int source = NekoConfig.regramMonoSource.Int();
        int color;
        switch (source) {
            case 1:
                Theme.ThemeInfo theme = Theme.getActiveTheme();
                color = 0;
                if (theme != null) {
                    if (theme.hasAccentColors()) color = theme.getAccentColor(theme.currentAccentId);
                    if (color == 0 && theme.isMonet() && Build.VERSION.SDK_INT >= 31) {
                        int index = theme.currentAccentId == 90 ? 1 : theme.currentAccentId == 89 ? 2 : 0;
                        color = MonetHelper.getColor("a" + (index + 1) + "_" + (theme.isDark() ? "200" : "600"));
                    }
                }
                if (color == 0) color = Theme.getColor(Theme.key_featuredStickers_addButton);
                break;
            case 2: color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader); break;
            case 3: color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText); break;
            case 4: color = Theme.getColor(Theme.key_dialogFloatingButton); break;
            case 5: color = Theme.getColor(Theme.key_chat_outBubble); break;
            case 6: color = NekoConfig.regramMonoCustomColor.Int(); break;
            default: color = Theme.getColor(Theme.key_featuredStickers_addButton); break;
        }
        return color == 0 ? FALLBACK : color | 0xff000000;
    }
    private static Palette palette() {
        int color = sourceColor();
        boolean dark = Theme.isCurrentThemeDark();
        boolean gradient = NekoConfig.regramMonoGradient.Bool();
        Palette p = cached;
        if (p != null && p.source == color && p.dark == dark && p.gradient == gradient) return p;
        synchronized (MonoColor.class) {
            p = cached;
            if (p == null || p.source != color || p.dark != dark || p.gradient != gradient) {
                cached = p = new Palette(color, dark, gradient);
            }
        }
        return p;
    }
    public static int avatarColor(boolean second) { return palette().colors[second ? 1 : 0]; }
    public static ColorFilter avatarFilter() { return avatars() ? palette().filter : null; }
    public static int peerColor(int index) { return palette().peer[index]; }

    /** Null means use the original color, including animated and fragment-local themes. */
    public static Integer override(int key) {
        if (!NekoConfig.regramMonoColor.Bool()) return null;
        int role = -1;
        if (NekoConfig.regramMonoAvatars.Bool()) {
            for (int k : Theme.keys_avatar_background) if (key == k) { role = 0; break; }
            for (int k : Theme.keys_avatar_background2) if (key == k) { role = 1; break; }
            if (key == Theme.key_avatar_backgroundSaved || key == Theme.key_avatar_backgroundArchived
                    || key == Theme.key_avatar_backgroundGray || key == Theme.key_avatar_backgroundInProfileBlue
                    || key == Theme.key_avatar_backgroundActionBarBlue) role = 0;
            if (key == Theme.key_avatar_background2Saved || key == Theme.key_avatar_backgroundArchivedHidden) role = 1;
        }
        if (NekoConfig.regramMonoNames.Bool()) {
            for (int k : Theme.keys_avatar_nameInMessage) if (key == k) { role = 2; break; }
        }
        if (NekoConfig.regramMonoStories.Bool()) {
            if (key == Theme.key_stories_circle1 || key == Theme.key_stories_circle_dialog1) role = 3;
            if (key == Theme.key_stories_circle2 || key == Theme.key_stories_circle_dialog2) role = 4;
            if (key == Theme.key_stories_circle_closeFriends1) role = 5;
            if (key == Theme.key_stories_circle_closeFriends2) role = 6;
            if (key == Theme.key_stories_circle_live1) role = 7;
            if (key == Theme.key_stories_circle_live2) role = 8;
        }
        if (NekoConfig.regramMonoDialogs.Bool()) {
            if (key == Theme.key_chats_name || key == Theme.key_chats_nameArchived
                    || key == Theme.key_chats_nameMessage || key == Theme.key_chats_nameMessageArchived
                    || key == Theme.key_chats_nameMessage_threeLines || key == Theme.key_chats_nameMessageArchived_threeLines) role = 9;
            if (key == Theme.key_chats_message || key == Theme.key_chats_messageArchived
                    || key == Theme.key_chats_message_threeLines) role = 10;
        }
        return role < 0 ? null : palette().colors[role];
    }
    private static float brightness(int c) {
        return (Color.red(c) * .2126f + Color.green(c) * .7152f + Color.blue(c) * .0722f) / 255f;
    }
    private static int blend(int a, int b, float f) {
        return Color.rgb(Math.round(Color.red(a) * (1-f) + Color.red(b) * f),
                Math.round(Color.green(a) * (1-f) + Color.green(b) * f),
                Math.round(Color.blue(a) * (1-f) + Color.blue(b) * f));
    }
    private static final class Palette {
        final int source;
        final boolean dark, gradient;
        final int[] colors = new int[11], peer = new int[6];
        final ColorFilter filter;
        Palette(int c, boolean dark, boolean gradient) {
            source = c; this.dark = dark; this.gradient = gradient;
            float light = brightness(c);
            int avatar = light > .92f ? blend(c, Color.BLACK, .18f) : light < .14f ? blend(c, Color.WHITE, .28f) : c;
            int name = c;
            for (int i = 0; i < 6 && (dark ? brightness(name) < .7f : brightness(name) > .48f); i++) {
                name = blend(name, dark ? Color.WHITE : Color.BLACK, .22f);
            }
            int name2 = gradient ? blend(name, dark ? Color.WHITE : Color.BLACK, .16f) : name;
            int story = light < .35f ? blend(c, Color.WHITE, .3f) : c;
            int story2 = gradient ? blend(story, Color.WHITE, .22f) : story;
            colors[0] = avatar; colors[1] = gradient ? blend(avatar, Color.BLACK, .18f) : avatar;
            colors[2] = name; colors[3] = story; colors[4] = story2;
            colors[5] = blend(story, Color.WHITE, .1f);
            colors[6] = gradient ? blend(story2, Color.WHITE, .12f) : colors[5];
            colors[7] = blend(story, Color.BLACK, .12f);
            colors[8] = gradient ? blend(story, Color.WHITE, .08f) : colors[7];
            colors[9] = name; colors[10] = blend(name, dark ? 0xffe6e6e6 : 0xff3a3a3a, .42f);
            peer[0] = name; peer[1] = name2; peer[2] = colors[0]; peer[3] = colors[1]; peer[4] = story; peer[5] = story2;
            float r = Color.red(avatar) / 255f, g = Color.green(avatar) / 255f, b = Color.blue(avatar) / 255f;
            filter = new ColorMatrixColorFilter(new float[] {
                    r*.213f,r*.715f,r*.072f,0,0, g*.213f,g*.715f,g*.072f,0,0,
                    b*.213f,b*.715f,b*.072f,0,0, 0,0,0,1,0 });
        }
    }
}
