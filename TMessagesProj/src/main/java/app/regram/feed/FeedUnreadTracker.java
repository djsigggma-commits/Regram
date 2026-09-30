package app.regram.feed;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Учёт непрочитанного в ленте.
 * Держит по каждому каналу максимальный прочитанный id, копит увиденные посты и отправляет
 * их на сервер пачкой с задержкой, чтобы прокрутка ленты не превращалась в поток запросов.
 */
final class FeedUnreadTracker {

    private static final long FLUSH_DELAY_MS = 1000L;
    private static final int ARCHIVE_FOLDER_ID = 1;
    private static final int NO_READ_ID = 0;

    private final int currentAccount;
    private final ArrayList<MessageObject> timeline;

    private final LongSparseArray<Integer> readInboxMaxByDialog = new LongSparseArray<>();
    private final LongSparseArray<Integer> pendingMaxReadId = new LongSparseArray<>();

    private final Runnable flushRunnable = this::flush;

    private boolean flushScheduled;

    public FeedUnreadTracker(int currentAccount, ArrayList<MessageObject> timeline) {
        this.currentAccount = currentAccount;
        this.timeline = timeline;
    }

    /**
     * Обновляет известный максимум прочитанного, пришедший извне (из диалога или с сервера).
     */
    public void applyReadInboxMax(long dialogId, int maxReadId) {
        if (maxReadId > readInboxMaxByDialog.get(dialogId, NO_READ_ID)) {
            readInboxMaxByDialog.put(dialogId, maxReadId);
        }
    }

    /**
     * Досылает накопленное и сбрасывает состояние — вызывается при перезагрузке ленты.
     */
    public void clear() {
        if (flushScheduled) {
            AndroidUtilities.cancelRunOnUIThread(flushRunnable);
            flushScheduled = false;
        }
        flush();
        readInboxMaxByDialog.clear();
    }

    /**
     * Считает непрочитанные посты в первых {@code count} строках ленты.
     */
    public int countUnreadBelow(ArrayList<MessageObject> messages, int count) {
        if (messages == null || readInboxMaxByDialog.isEmpty()) {
            return 0;
        }
        int limit = Math.min(count, messages.size());
        int unread = 0;
        for (int i = 0; i < limit; i++) {
            MessageObject message = messages.get(i);
            if (FeedMessageUtils.isPostRow(message) && isUnread(message)) {
                unread++;
            }
        }
        return unread;
    }

    /**
     * Индекс самого старого непрочитанного поста — место для разделителя «непрочитанное».
     */
    public int findFirstUnreadIndex(ArrayList<MessageObject> messages) {
        if (messages != null && !readInboxMaxByDialog.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (isUnread(messages.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Суммарный счётчик непрочитанного по каналам, попадающим в ленту.
     */
    public int getUnreadCount() {
        ArrayList<TLRPC.Dialog> unreadDialogs = collectUnreadFeedDialogs();
        int total = 0;
        for (int i = 0; i < unreadDialogs.size(); i++) {
            total += unreadDialogs.get(i).unread_count;
        }
        return total;
    }

    public boolean isUnread(MessageObject message) {
        return message != null
                && !message.isSponsored()
                && message.getRealId() > getEffectiveReadInboxMax(message.getDialogId());
    }

    /**
     * Отмечает прочитанными все каналы ленты и всё, что уже загружено в таймлайн.
     */
    public void markAllRead() {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        HashSet<Long> touchedDialogs = new HashSet<>();

        ArrayList<TLRPC.Dialog> unreadDialogs = collectUnreadFeedDialogs();
        for (int i = 0; i < unreadDialogs.size(); i++) {
            TLRPC.Dialog dialog = unreadDialogs.get(i);
            messagesController.markMentionsAsRead(dialog.id, 0L);
            messagesController.markDialogAsRead(dialog.id, dialog.top_message, dialog.top_message,
                    dialog.last_message_date, false, 0L, 0, true, 0);
            readInboxMaxByDialog.put(dialog.id, dialog.top_message);
            touchedDialogs.add(dialog.id);
        }

        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        boolean includeArchived = feedConfig.getIncludeArchived();
        for (int i = 0; i < timeline.size(); i++) {
            MessageObject message = timeline.get(i);
            if (message == null) {
                continue;
            }
            long dialogId = message.getDialogId();
            if (feedConfig.isExcluded(dialogId)) {
                continue;
            }
            if (!includeArchived) {
                TLRPC.Dialog dialog = messagesController.dialogs_dict.get(dialogId);
                if (dialog != null && dialog.folder_id == ARCHIVE_FOLDER_ID) {
                    continue;
                }
            }
            touchedDialogs.add(dialogId);
            int realId = message.getRealId();
            if (realId > readInboxMaxByDialog.get(dialogId, NO_READ_ID)) {
                readInboxMaxByDialog.put(dialogId, realId);
            }
        }

        for (long dialogId : touchedDialogs) {
            pendingMaxReadId.remove(dialogId);
        }
        if (pendingMaxReadId.isEmpty() && flushScheduled) {
            AndroidUtilities.cancelRunOnUIThread(flushRunnable);
            flushScheduled = false;
        }
    }

    /**
     * Пост показан пользователю: запоминаем и планируем отложенную отправку прочтения.
     */
    public void onPostSeen(long dialogId, int messageId) {
        if (dialogId == 0 || messageId <= 0 || messageId <= getEffectiveReadInboxMax(dialogId)) {
            return;
        }
        Integer pending = pendingMaxReadId.get(dialogId);
        if (pending != null && pending >= messageId) {
            return;
        }
        pendingMaxReadId.put(dialogId, messageId);
        if (!flushScheduled) {
            flushScheduled = true;
            AndroidUtilities.runOnUIThread(flushRunnable, FLUSH_DELAY_MS);
        }
    }

    private ArrayList<TLRPC.Dialog> collectUnreadFeedDialogs() {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        boolean includeArchived = feedConfig.getIncludeArchived();

        LongSparseArray<TLRPC.Dialog> dialogs = messagesController.dialogs_dict;
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.valueAt(i);
            if (dialog == null || dialog.unread_count <= 0) {
                continue;
            }
            long dialogId = dialog.id;
            if (!DialogObject.isChatDialog(dialogId) || feedConfig.isExcluded(dialogId)) {
                continue;
            }
            if (!includeArchived && dialog.folder_id == ARCHIVE_FOLDER_ID) {
                continue;
            }
            if (FeedController.isEligibleChannel(messagesController.getChat(-dialogId))) {
                result.add(dialog);
            }
        }
        return result;
    }

    private int countTimelineRows(long dialogId, int fromIdExclusive, int toIdInclusive) {
        int count = 0;
        for (int i = 0; i < timeline.size(); i++) {
            MessageObject message = timeline.get(i);
            if (message == null || message.getDialogId() != dialogId) {
                continue;
            }
            int realId = message.getRealId();
            if (realId > fromIdExclusive && realId <= toIdInclusive) {
                count++;
            }
        }
        return count;
    }

    private void flush() {
        flushScheduled = false;
        if (pendingMaxReadId.isEmpty()) {
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        for (int i = 0; i < pendingMaxReadId.size(); i++) {
            long dialogId = pendingMaxReadId.keyAt(i);
            int maxReadId = pendingMaxReadId.valueAt(i);
            int knownMaxReadId = readInboxMaxByDialog.get(dialogId, NO_READ_ID);
            if (maxReadId <= knownMaxReadId) {
                continue;
            }
            readInboxMaxByDialog.put(dialogId, maxReadId);
            int countDiff = Math.max(countTimelineRows(dialogId, knownMaxReadId, maxReadId), 1);
            messagesController.markDialogAsRead(dialogId, maxReadId, 0, currentTime, false, 0L, countDiff, true, 0);
        }
        pendingMaxReadId.clear();
    }

    private int getEffectiveReadInboxMax(long dialogId) {
        return Math.max(readInboxMaxByDialog.get(dialogId, NO_READ_ID), pendingMaxReadId.get(dialogId, NO_READ_ID));
    }
}
