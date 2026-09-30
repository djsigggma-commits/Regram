package app.regram.feed;

import android.view.Gravity;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ItemOptions;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Меню по тапу на аватар канала в ленте: открыть канал, спрятать его из ленты, выйти.
 * Выход из канала сразу вычищает его посты из ленты — иначе строки остались бы висеть
 * до следующей полной перезагрузки.
 */
public abstract class FeedChannelAvatarMenu {

    private static void deleteFeedRows(ChatActivity chatActivity, long dialogId, Consumer<ArrayList<Integer>> onRowsDeleted) {
        ArrayList<Integer> deletedIds = FeedController.getInstance(chatActivity.getCurrentAccount())
                .deleteHistory(dialogId, Integer.MAX_VALUE);
        if (onRowsDeleted != null) {
            onRowsDeleted.accept(deletedIds);
        }
    }

    private static void leaveChannel(final ChatActivity chatActivity, final TLRPC.Chat chat, final Runnable onLeft, final Consumer<ArrayList<Integer>> onRowsDeleted) {
        if (chatActivity.getParentActivity() == null) {
            return;
        }
        AlertsCreator.createClearOrDeleteDialogAlert(chatActivity, false, chat, null, false, true, false, false, revoke -> {
            long dialogId = -chat.id;
            if (ChatObject.isNotInChat(chat)) {
                chatActivity.getMessagesController().deleteDialog(dialogId, 0, revoke);
            } else {
                chatActivity.getMessagesController().deleteParticipantFromChat(
                        chat.id,
                        chatActivity.getMessagesController().getUser(chatActivity.getUserConfig().getClientUserId()),
                        null,
                        revoke,
                        revoke);
            }
            deleteFeedRows(chatActivity, dialogId, onRowsDeleted);
            if (onLeft != null) {
                onLeft.run();
            }
        });
    }

    public static void show(final ChatActivity chatActivity, ChatMessageCell cell, final TLRPC.Chat chat, Runnable onOpenChat, final Runnable onLeft, final Consumer<ArrayList<Integer>> onRowsDeleted) {
        if (chatActivity == null || cell == null || chat == null) {
            return;
        }
        boolean canLeave = !chat.creator && !ChatObject.isNotInChat(chat);
        boolean isChannel = chat.broadcast;
        ItemOptions.makeOptions(chatActivity, cell)
                .add(
                        isChannel ? R.drawable.msg_channel : R.drawable.msg_discussion,
                        LocaleController.getString(isChannel ? R.string.OpenChannel2 : R.string.OpenGroup2),
                        onOpenChat)
                .add(
                        R.drawable.menu_hide_gift,
                        LocaleController.getString(R.string.FeedHideChannel),
                        () -> chatActivity.hideFeedChannelWithUndo(-chat.id, chat.title))
                .addIf(
                        canLeave,
                        R.drawable.msg_leave,
                        LocaleController.getString(isChannel ? R.string.LeaveChannelMenu : R.string.LeaveMegaMenu),
                        true,
                        () -> leaveChannel(chatActivity, chat, onLeft, onRowsDeleted))
                .setDrawScrim(false)
                .setGravity(Gravity.LEFT)
                .forceBottom(true)
                .show();
    }
}
