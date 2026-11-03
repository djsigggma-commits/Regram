package app.regram.chats;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.text.style.URLSpan;
import java.util.ArrayList;
import java.util.Locale;
import java.text.BreakIterator;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.URLSpanBrowser;
import org.telegram.ui.Components.URLSpanReplacement;
import tw.nekomimi.nekogram.NekoConfig;

/** Native MentionAvatars (inspired by @limeplug 1.1.3). No text insertion or entity offset changes. */
public final class MentionAvatars {
    private MentionAvatars() {}
    private static final int MAX_MENTIONS = 40;
    private static final String[] USERNAME_PREFIXES = {
            "https://t.me/", "http://t.me/", "https://telegram.me/", "http://telegram.me/"
    };
    public static final int POSITION_BEFORE = 0;
    public static final int POSITION_AFTER = 1;
    public static final int POSITION_INSTEAD = 2;
    public static int signature() {
        if (!NekoConfig.regramMentionAvatars.Bool()) return 0;
        // Two independent bits for each selector: position=2 must not alias size=1.
        return 1 | (position() << 1) | (sizeIndex() << 3)
                | (NekoConfig.regramMentionBots.Bool() ? 32 : 0) | (NekoConfig.regramMentionChats.Bool() ? 64 : 0);
    }
    public static int position() {
        int position = NekoConfig.regramMentionPosition.Int();
        return position >= POSITION_BEFORE && position <= POSITION_INSTEAD ? position : POSITION_BEFORE;
    }
    public static int sizeIndex() {
        int size = NekoConfig.regramMentionSize.Int();
        return size >= 0 && size <= 2 ? size : 1;
    }
    public static float sizeDp() { return 14 + 2 * sizeIndex(); }

    public static CharSequence apply(CharSequence text, int account) {
        if (!(text instanceof Spanned)) return text;
        Spanned input = (Spanned) text;
        MentionAvatarSpan[] previous = input.getSpans(0, text.length(), MentionAvatarSpan.class);
        boolean enabled = NekoConfig.regramMentionAvatars.Bool();
        if (!enabled && previous.length == 0) return text;
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        for (MentionAvatarSpan span : previous) {
            span.setParent(null);
            result.removeSpan(span);
        }
        if (!enabled) return result;
        MessagesController controller = MessagesController.getInstance(account);
        int added = 0;
        int position = position();
        float size = sizeDp();
        for (URLSpan url : result.getSpans(0, result.length(), URLSpan.class)) {
            if (added >= MAX_MENTIONS) break;
            int start = result.getSpanStart(url), end = result.getSpanEnd(url);
            if (start < 0 || end <= start || end > result.length() || blocked(result, start, end) || blockedStyle(url)) continue;
            String username = usernameFromUrl(url.getURL());
            if (username == null && !(url instanceof URLSpanUserMention)) continue;
            TLObject peer = null;
            if (url instanceof URLSpanNoUnderline) peer = ((URLSpanNoUnderline) url).getObject();
            if (peer == null && url instanceof URLSpanUserMention) {
                try { peer = controller.getUser(Long.parseLong(url.getURL())); }
                catch (NumberFormatException ignored) {}
            }
            if (peer == null && username != null) peer = controller.getUserOrChat(username);
            if (!allowed(peer)) continue;
            // ID mentions and labelled links without an @ fall back to the leading avatar.
            boolean before = position != POSITION_AFTER;
            int at = position == POSITION_INSTEAD ? atIndex(result, start, end) : -1;
            if (at >= 0 && blocked(result, at, at + 1)) continue;
            boolean replaceAt = at >= 0;
            int[] range = replaceAt ? new int[] {at, at + 1} : avatarRange(result, start, end, before);
            MentionAvatarSpan span = new MentionAvatarSpan(account, size, before, replaceAt, peer, url);
            result.setSpan(span, range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            added++;
        }
        return result;
    }
    private static int atIndex(CharSequence text, int start, int end) {
        if (text.charAt(start) == '@') return start;
        // Some entity ranges cover only the username, leaving the @ just before it.
        return start > 0 && text.charAt(start - 1) == '@' ? start - 1 : -1;
    }
    private static int[] avatarRange(CharSequence text, int start, int end, boolean before) {
        // Cover an edge grapheme, not the entire URL/name: long links must still wrap.
        String label = text.subSequence(start, end).toString();
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(label);
        int left = before ? 0 : characters.preceding(label.length());
        int right = before ? characters.following(0) : label.length();
        boolean ascii = true;
        for (int i = 0; i < label.length(); i++) if (label.charAt(i) > 127) { ascii = false; break; }
        if (!ascii) {
            // Keep short non-ASCII edge words together for contextual glyph shaping.
            BreakIterator words = BreakIterator.getWordInstance(Locale.ROOT);
            words.setText(label);
            int wordLeft = before ? 0 : words.preceding(label.length());
            int wordRight = before ? words.following(0) : label.length();
            if (wordLeft >= 0 && wordRight > wordLeft && wordRight - wordLeft <= 16) {
                left = wordLeft;
                right = wordRight;
            }
        }
        if (left < 0 || right <= left) return new int[] {start, end};
        return new int[] {start + left, start + right};
    }
    public static void collect(CharSequence text, ArrayList<MentionAvatarSpan> output) {
        if (text instanceof Spanned) {
            for (MentionAvatarSpan span : ((Spanned) text).getSpans(0, text.length(), MentionAvatarSpan.class)) {
                if (!output.contains(span)) output.add(span);
            }
        }
    }
    private static boolean blockedStyle(URLSpan url) {
        TextStyleSpan.TextStyleRun style = null;
        if (url instanceof URLSpanNoUnderline) style = ((URLSpanNoUnderline) url).getTextStyleRun();
        else if (url instanceof URLSpanBrowser) style = ((URLSpanBrowser) url).getStyle();
        else if (url instanceof URLSpanReplacement) style = ((URLSpanReplacement) url).getTextStyleRun();
        return style != null && (style.flags & (TextStyleSpan.FLAG_STYLE_SPOILER
                | TextStyleSpan.FLAG_STYLE_MONO | TextStyleSpan.FLAG_STYLE_CODE)) != 0;
    }
    private static boolean blocked(Spanned text, int start, int end) {
        if (text.getSpans(start, end, ReplacementSpan.class).length != 0) return true;
        for (TextStyleSpan span : text.getSpans(start, end, TextStyleSpan.class)) {
            // Do not disclose a hidden mention's identity through a visible avatar.
            if (span.isSpoiler() || span.isMono()) return true;
        }
        return false;
    }
    public static boolean allowed(TLObject peer) {
        if (peer instanceof TLRPC.User) return NekoConfig.regramMentionBots.Bool() || !((TLRPC.User) peer).bot;
        return peer instanceof TLRPC.Chat && NekoConfig.regramMentionChats.Bool();
    }
    public static String usernameFromUrl(String url) {
        if (url == null) return null;
        String value = url.trim();
        String lower = value.toLowerCase(Locale.US);
        int start;
        int end = value.length();
        if (value.startsWith("@")) {
            start = 1;
        } else if (lower.startsWith("tg://resolve?domain=")) {
            start = 20;
            end = usernameEnd(value, start, true);
        } else {
            start = -1;
            for (String prefix : USERNAME_PREFIXES) if (lower.startsWith(prefix)) {
                start = prefix.length();
                end = usernameEnd(value, start, false);
                break;
            }
            if (start < 0) return null;
        }
        if (end <= start || end - start > 32) return null;
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) return null;
        }
        return value.substring(start, end).toLowerCase(Locale.US);
    }
    private static int usernameEnd(String value, int start, boolean resolve) {
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '#' || (resolve ? c == '&' : c == '/' || c == '?')) return i;
        }
        return value.length();
    }
}
