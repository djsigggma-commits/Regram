package app.regram.ui;

import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.Spanned;
import android.util.LruCache;
import android.view.View;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.AvatarSpan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import tw.nekomimi.nekogram.NekoConfig;

/** Render an avatar in place of the @ glyph without changing message/entity offsets.
 * No requests or image work on the message-layout thread; username resolution is
 * only started from a visible dialog preview, is coalesced and rate-limited. */
public final class MentionAvatars {
    private MentionAvatars() {}

    private static final class MentionAvatarSpan extends AvatarSpan {
        final String key;
        /** @username, которого нет в кэше: буква вместо аватарки, пока не порезолвится. */
        String pendingUsername;
        MentionAvatarSpan(int account, String key) {
            super(null, account, 16);
            this.key = key;
            needDrawShadow = false;
        }
    }

    private static final long RETRY_AFTER_MS = 10 * 60 * 1000L;
    private static final LruCache<String, Long> attempted = new LruCache<>(128);
    private static final HashMap<String, ArrayList<MentionAvatarSpan>> waiting = new HashMap<>();
    private static int requestsInFlight;

    public static void decorate(Spannable text, ArrayList<TLRPC.MessageEntity> entities, int account) {
        decorate(text, entities, account, null);
    }

    /** @param visibleParent non-null only for an attached/visible dialog preview */
    public static void decorate(Spannable text, ArrayList<TLRPC.MessageEntity> entities,
                                int account, View visibleParent) {
        if (text == null) return;
        MentionAvatarSpan[] previous = text.getSpans(0, text.length(), MentionAvatarSpan.class);
        if (!NekoConfig.regramMentionAvatars.Bool() || entities == null || entities.isEmpty()) {
            for (MentionAvatarSpan span : previous) {
                span.setParent(null);
                text.removeSpan(span);
            }
            return;
        }
        MessagesController controller = MessagesController.getInstance(account);
        HashSet<MentionAvatarSpan> used = previous.length == 0 ? null : new HashSet<>();
        for (TLRPC.MessageEntity entity : entities) {
            if (!(entity instanceof TLRPC.TL_messageEntityMention ||
                    entity instanceof TLRPC.TL_messageEntityMentionName)) continue;
            int start = entity.offset;
            if (entity.length < 2 || start < 0 || start >= text.length() ||
                    start + entity.length > text.length() || text.charAt(start) != '@') continue;
            String username = text.subSequence(start + 1, start + entity.length).toString();
            String key = entity instanceof TLRPC.TL_messageEntityMentionName
                    ? "id:" + ((TLRPC.TL_messageEntityMentionName) entity).user_id
                    : username.toLowerCase(Locale.ROOT);
            MentionAvatarSpan avatar = null;
            for (MentionAvatarSpan old : previous) {
                if (text.getSpanStart(old) == start && old.key.equals(key)) {
                    avatar = old;
                    if (used != null) used.add(old);
                    break;
                }
            }
            if (avatar == null) {
                if (text.getSpans(start, start + 1, AvatarSpan.class).length != 0) continue;
                avatar = new MentionAvatarSpan(account, key);
                TLObject peer = entity instanceof TLRPC.TL_messageEntityMentionName
                        ? controller.getUser(((TLRPC.TL_messageEntityMentionName) entity).user_id)
                        : controller.getUserOrChat(username);
                if (peer instanceof TLRPC.User || peer instanceof TLRPC.Chat) {
                    avatar.setObject(peer);
                } else if (entity instanceof TLRPC.TL_messageEntityMention) {
                    // Do not leave a blank glyph when the mentioned user has not been cached.
                    avatar.setName(username);
                    avatar.pendingUsername = username;
                } else {
                    continue;
                }
                text.setSpan(avatar, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (peer == null && visibleParent != null && entity instanceof TLRPC.TL_messageEntityMention) {
                    resolveVisible(account, username, avatar);
                }
            }
            if (visibleParent != null) avatar.setParent(visibleParent);
        }
        for (MentionAvatarSpan old : previous) {
            if (used == null || !used.contains(old)) {
                old.setParent(null);
                text.removeSpan(old);
            }
        }
    }

    /**
     * Ячейка чата приняла спаны: тот же ограниченный резолв, что и в
     * предпросмотре диалогов. {@code generateLayout()} считается на воркере и
     * не имеет права слать запросы, поэтому он ставит букву вместо аватарки, а
     * недополученных пользователей добираем отсюда.
     */
    public static void resolveDeferred(CharSequence text, View parent, int account) {
        if (!(text instanceof Spanned) || parent == null ||
                !NekoConfig.regramMentionAvatars.Bool()) {
            return;
        }
        MentionAvatarSpan[] spans = ((Spanned) text).getSpans(0, text.length(),
                MentionAvatarSpan.class);
        for (MentionAvatarSpan span : spans) {
            if (span.pendingUsername != null) {
                resolveVisible(account, span.pendingUsername, span);
            }
        }
    }

    private static void resolveVisible(int account, String username, MentionAvatarSpan span) {
        // The layout code may run on a worker; never start networking from it.
        if (Looper.myLooper() != Looper.getMainLooper() || username.length() < 2 ||
                username.length() > 32 || !username.matches("[A-Za-z0-9_]+")) return;
        String key = account + ":" + username.toLowerCase(Locale.ROOT);
        ArrayList<MentionAvatarSpan> pending = waiting.get(key);
        if (pending != null) {
            if (pending.size() < 32) pending.add(span);
            return;
        }
        long now = SystemClock.uptimeMillis();
        Long last = attempted.get(key);
        if (requestsInFlight >= 4 || last != null && now - last < RETRY_AFTER_MS) return;
        attempted.put(key, now);
        pending = new ArrayList<>();
        pending.add(span);
        waiting.put(key, pending);
        requestsInFlight++;
        MessagesController.getInstance(account).getUserNameResolver().resolve(username, peerId -> {
            ArrayList<MentionAvatarSpan> spans = waiting.remove(key);
            requestsInFlight--;
            if (peerId == null || peerId == 0 || peerId == Long.MAX_VALUE || spans == null) return;
            for (MentionAvatarSpan item : spans) {
                item.setDialogId(peerId);
                item.pendingUsername = null;
                item.invalidateParent();
            }
        });
    }
}
