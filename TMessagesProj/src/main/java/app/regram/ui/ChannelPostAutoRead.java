package app.regram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;

/** Opt-in, per-account auto-read of verified channel reposts in discussion groups.
 * Never reads past pre-existing unread comments, or comments in the same batch. */
public final class ChannelPostAutoRead {
    private ChannelPostAutoRead() {}

    private static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("regram_channel_posts_" + account, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(int account, long dialogId) {
        return dialogId < 0 && prefs(account).getBoolean(Long.toString(dialogId), false);
    }

    public static boolean toggle(int account, long dialogId) {
        boolean value = !isEnabled(account, dialogId);
        prefs(account).edit().putBoolean(Long.toString(dialogId), value).apply();
        return value;
    }

    /** Check both sender and original forward source. Do not touch human comments
     * forwarded by a user, anonymous admins or posts in actual broadcast channels. */
    public static boolean isChannelRepost(int account, MessageObject object) {
        if (object == null || object.messageOwner == null || object.isOutOwner()) return false;
        long dialogId = object.getDialogId();
        if (!isEnabled(account, dialogId)) return false;
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null || !chat.megagroup) return false;
        TLRPC.Message message = object.messageOwner;
        if (message.from_id == null || message.from_id.channel_id <= 0 ||
                message.from_id.channel_id == -dialogId || message.fwd_from == null ||
                message.fwd_from.from_id == null) return false;
        return message.fwd_from.from_id.channel_id == message.from_id.channel_id;
    }

    /** Called only on the UI thread, on the notification copy of the messages.
     * The update/storage pipeline has already accepted these messages. */
    public static void filterNotifications(int account, ArrayList<MessageObject> notifications) {
        if (notifications == null || notifications.isEmpty()) return;
        MessagesController controller = MessagesController.getInstance(account);
        HashSet<Long> withComments = new HashSet<>();
        HashMap<Long, Integer> batchCounts = new HashMap<>();
        for (MessageObject object : notifications) {
            if (object == null) continue;
            long dialogId = object.getDialogId();
            batchCounts.put(dialogId, batchCounts.getOrDefault(dialogId, 0) + 1);
            if (!isChannelRepost(account, object)) withComments.add(dialogId);
        }
        for (int i = notifications.size() - 1; i >= 0; i--) {
            MessageObject object = notifications.get(i);
            if (!isChannelRepost(account, object) || withComments.contains(object.getDialogId()) ||
                    batchCounts.get(object.getDialogId()) != 1) continue;
            long dialogId = object.getDialogId();
            TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
            // Only the post itself is unread. Otherwise marking by message ID
            // would silently read earlier human comments, including on other devices.
            if (dialog == null || dialog.unread_count != 1 || object.getId() <= 0) continue;
            controller.markDialogAsRead(dialogId, object.getId(), 0, object.messageOwner.date,
                    false, 0, 0, true, 0);
            notifications.remove(i);
        }
    }
}
