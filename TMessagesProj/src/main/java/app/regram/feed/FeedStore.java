package app.regram.feed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import org.telegram.messenger.MessageObject;

/**
 * Хранилище загруженной ленты: упорядоченный список постов, карта синтетических
 * идентификаторов, набор скрытых каналов и курсоры пагинации.
 */
public final class FeedStore {

    private static final int LOADING_PLACEHOLDER_ROWS = 3;

    private int count;
    private boolean endReached;

    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final FeedMessageIdentityMap identityMap = new FeedMessageIdentityMap();
    private final HashSet<Long> hiddenDialogIds = new HashSet<>();
    private final FeedTimelineLoader.Cursor oldestCursor = new FeedTimelineLoader.Cursor();
    private final FeedTimelineLoader.Cursor newestCursor = new FeedTimelineLoader.Cursor();

    /**
     * Порядок постов в ленте: сначала по дате, затем по диалогу, затем по идентификатору.
     * Возвращает положительное число, если первая тройка новее второй.
     */
    public static int compareTimeline(int date, long uid, int mid, int otherDate, long otherUid, int otherMid) {
        if (date != otherDate) {
            return Integer.compare(date, otherDate);
        }
        if (uid != otherUid) {
            return Long.compare(uid, otherUid);
        }
        return Integer.compare(mid, otherMid);
    }

    private int findMergeIndex(MessageObject message, int from) {
        int index = from;
        while (index < messages.size()) {
            MessageObject current = messages.get(index);
            if (current != null && compareTimeline(current.messageOwner.date, current.getDialogId(), current.getRealId(),
                    message.messageOwner.date, message.getDialogId(), message.getRealId()) < 0) {
                break;
            }
            index++;
        }
        while (index > 0 && index < messages.size()) {
            MessageObject previous = messages.get(index - 1);
            MessageObject current = messages.get(index);
            if (previous == null || current == null
                    || previous.getGroupId() == 0
                    || previous.getGroupId() != current.getGroupId()
                    || previous.getDialogId() != current.getDialogId()) {
                break;
            }
            index++;
        }
        return index;
    }

    private static boolean isPagingRow(MessageObject message) {
        return message != null && !message.isDateObject && message.messageOwner != null && message.getRealId() > 0;
    }

    private void onRowsRemoved() {
        if (!rebuildPagingCursorsFromLoadedRows()) {
            endReached = false;
        }
        updateCount();
    }

    private void purgeRow(MessageObject message, ArrayList<Integer> removedIds, HashSet<Integer> seenIds) {
        identityMap.purge(message);
        if (seenIds.add(message.getId())) {
            removedIds.add(message.getId());
        }
    }

    private boolean rebuildPagingCursorsFromLoadedRows() {
        boolean oldestCursorWasSet = !oldestCursor.isEmpty();

        int newestDate = 0;
        long newestUid = 0;
        int newestMid = 0;
        int oldestDate = 0;
        long oldestUid = 0;
        int oldestMid = 0;

        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (!isPagingRow(message)) {
                continue;
            }
            int date = message.messageOwner.date;
            long uid = message.getDialogId();
            int mid = message.getRealId();

            if (newestDate == 0 || compareTimeline(date, uid, mid, newestDate, newestUid, newestMid) > 0) {
                newestDate = date;
                newestUid = uid;
                newestMid = mid;
            }
            if (oldestDate == 0 || compareTimeline(date, uid, mid, oldestDate, oldestUid, oldestMid) < 0) {
                oldestDate = date;
                oldestUid = uid;
                oldestMid = mid;
            }
        }

        if (newestDate == 0) {
            oldestCursor.set(0, 0L, 0);
            newestCursor.set(0, 0L, 0);
            return false;
        }

        if (oldestCursorWasSet
                && compareTimeline(oldestDate, oldestUid, oldestMid, oldestCursor.date, oldestCursor.uid, oldestCursor.mid) > 0) {
            endReached = false;
        }

        newestCursor.set(newestDate, newestUid, newestMid);
        oldestCursor.set(oldestDate, oldestUid, oldestMid);
        return true;
    }

    private void updateCount() {
        int visibleCount = 0;
        if (!messages.isEmpty()) {
            visibleCount = getVisibleCount() + (endReached ? 0 : LOADING_PLACEHOLDER_ROWS);
        }
        count = visibleCount;
    }

    /**
     * Добавляет страницу постов в начало или конец ленты без пересортировки.
     * Возвращает только те объекты, которые действительно попали в ленту.
     */
    public ArrayList<MessageObject> appendMessages(ArrayList<MessageObject> incoming, boolean toStart) {
        ArrayList<MessageObject> accepted = new ArrayList<>(incoming.size());
        for (MessageObject message : incoming) {
            if (identityMap.register(message)) {
                accepted.add(message);
            }
        }
        if (toStart) {
            ArrayList<MessageObject> reversed = new ArrayList<>(accepted);
            Collections.reverse(reversed);
            messages.addAll(0, reversed);
        } else {
            messages.addAll(accepted);
        }
        updateCount();
        return accepted;
    }

    /**
     * Приводит набор скрытых каналов в соответствие со списком включённых в ленту.
     * Возвращает true, если состав скрытых изменился.
     */
    public boolean applyIncludedDialogs(HashSet<Long> includedDialogIds) {
        HashSet<Long> loadedDialogIds = getLoadedDialogIds();
        boolean changed = false;
        for (Long dialogId : loadedDialogIds) {
            if (!includedDialogIds.contains(dialogId)) {
                changed |= hiddenDialogIds.add(dialogId);
            }
        }
        Iterator<Long> iterator = hiddenDialogIds.iterator();
        while (iterator.hasNext()) {
            Long dialogId = iterator.next();
            if (includedDialogIds.contains(dialogId) || !loadedDialogIds.contains(dialogId)) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            updateCount();
        }
        return changed;
    }

    public void clear() {
        messages.clear();
        identityMap.clear();
        hiddenDialogIds.clear();
        endReached = false;
        count = 0;
        oldestCursor.set(0, 0L, 0);
        newestCursor.set(0, 0L, 0);
    }

    /**
     * Удаляет из ленты историю канала до указанного идентификатора включительно.
     * В {@code changed[0]} кладёт признак того, что лента изменилась.
     */
    public ArrayList<Integer> deleteHistory(long dialogId, int maxId, boolean[] changed) {
        ArrayList<Integer> removedIds = new ArrayList<>();
        HashSet<Integer> seenIds = new HashSet<>();
        boolean removed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject message = messages.get(i);
            if (message != null && message.getDialogId() == dialogId && message.getRealId() > 0 && message.getRealId() <= maxId) {
                messages.remove(i);
                purgeRow(message, removedIds, seenIds);
                removed = true;
            }
        }
        if (removed) {
            if (!hasMessagesForDialog(dialogId)) {
                hiddenDialogIds.remove(dialogId);
            }
            onRowsRemoved();
        }
        changed[0] = removed;
        return removedIds;
    }

    /**
     * Удаляет конкретные посты канала. В {@code changed[0]} кладёт признак изменения ленты,
     * возвращает синтетические идентификаторы удалённых строк.
     */
    public ArrayList<Integer> deleteMessages(long dialogId, ArrayList<Integer> realIds, boolean[] changed) {
        ArrayList<Integer> removedIds = new ArrayList<>();
        if (realIds == null) {
            return removedIds;
        }
        HashSet<Integer> targetIds = new HashSet<>(realIds);
        HashSet<Integer> seenIds = new HashSet<>();
        boolean removed = false;
        for (int i = 0; i < realIds.size(); i++) {
            MessageObject message = identityMap.getByRealId(dialogId, realIds.get(i));
            if (message != null) {
                removed |= messages.remove(message);
                purgeRow(message, removedIds, seenIds);
            }
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject message = messages.get(i);
            if (message != null && message.getDialogId() == dialogId && targetIds.contains(message.getRealId())) {
                messages.remove(i);
                purgeRow(message, removedIds, seenIds);
                removed = true;
            }
        }
        if (removed) {
            onRowsRemoved();
        }
        changed[0] = removed;
        return removedIds;
    }

    public int getCount() {
        return count;
    }

    public HashSet<Long> getHiddenSnapshot() {
        return new HashSet<>(hiddenDialogIds);
    }

    public HashSet<Long> getLoadedDialogIds() {
        HashSet<Long> dialogIds = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message != null) {
                dialogIds.add(message.getDialogId());
            }
        }
        return dialogIds;
    }

    public MessageObject getMessage(long dialogId, int id) {
        return identityMap.getByAnyId(dialogId, id);
    }

    public ArrayList<MessageObject> getMessages() {
        return messages;
    }

    public FeedTimelineLoader.Cursor getNewestCursor() {
        return newestCursor;
    }

    public FeedTimelineLoader.Cursor getOldestCursor() {
        return oldestCursor;
    }

    public int getVisibleCount() {
        if (hiddenDialogIds.isEmpty()) {
            return messages.size();
        }
        int visible = 0;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message != null && !hiddenDialogIds.contains(message.getDialogId())) {
                visible++;
            }
        }
        return visible;
    }

    public ArrayList<MessageObject> getVisibleMessages() {
        if (hiddenDialogIds.isEmpty()) {
            return new ArrayList<>(messages);
        }
        ArrayList<MessageObject> visible = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message != null && !hiddenDialogIds.contains(message.getDialogId())) {
                visible.add(message);
            }
        }
        return visible;
    }

    public boolean hasMessagesForDialog(long dialogId) {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message != null && message.getDialogId() == dialogId) {
                return true;
            }
        }
        return false;
    }

    public boolean hasNoSyntheticIds() {
        return identityMap.isEmpty();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public boolean isEndReached() {
        return endReached;
    }

    /**
     * Вставляет посты в уже загруженную ленту с сохранением порядка,
     * не разрывая альбомы. Возвращает реально вставленные объекты.
     */
    public ArrayList<MessageObject> mergeRows(ArrayList<MessageObject> incoming) {
        ArrayList<MessageObject> accepted = new ArrayList<>(incoming.size());
        for (MessageObject message : incoming) {
            if (identityMap.register(message)) {
                accepted.add(message);
            }
        }
        int index = 0;
        int mergeIndex = 0;
        while (index < accepted.size()) {
            MessageObject first = accepted.get(index);
            long groupId = first.getGroupId();
            int groupEnd = index + 1;
            while (groupId != 0 && groupEnd < accepted.size()
                    && accepted.get(groupEnd).getGroupId() == groupId
                    && accepted.get(groupEnd).getDialogId() == first.getDialogId()) {
                groupEnd++;
            }
            mergeIndex = findMergeIndex(first, mergeIndex);
            while (index < groupEnd) {
                messages.add(mergeIndex, accepted.get(index));
                index++;
                mergeIndex++;
            }
        }
        updateCount();
        return accepted;
    }

    public void replaceMessage(MessageObject oldMessage, MessageObject newMessage) {
        if (oldMessage == null || newMessage == null) {
            return;
        }
        int index = messages.indexOf(oldMessage);
        if (index >= 0) {
            messages.set(index, newMessage);
        }
        identityMap.replace(newMessage);
    }

    public long resolveRealDialogId(int generatedId) {
        return identityMap.resolveRealDialogId(generatedId);
    }

    public int resolveRealMessageId(long dialogId, int id) {
        return identityMap.resolveRealMessageId(dialogId, id);
    }

    public void setEndReached(boolean endReached) {
        this.endReached = endReached;
        updateCount();
    }

    public boolean setHidden(long dialogId, boolean hidden) {
        boolean changed = hidden ? hiddenDialogIds.add(dialogId) : hiddenDialogIds.remove(dialogId);
        if (changed) {
            updateCount();
        }
        return changed;
    }

    /**
     * Обрезает ленту до указанного количества строк, отбрасывая самые старые посты
     * и сдвигая курсор дозагрузки. Возвращает true, если что-то удалено.
     */
    public boolean trim(int limit) {
        if (messages.size() <= limit) {
            return false;
        }
        MessageObject boundary = messages.get(limit - 1);
        int boundaryDate = boundary.messageOwner.date;
        long boundaryUid = boundary.getDialogId();
        int boundaryMid = boundary.getRealId();

        boolean removed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject message = messages.get(i);
            if (message != null && compareTimeline(message.messageOwner.date, message.getDialogId(), message.getRealId(),
                    boundaryDate, boundaryUid, boundaryMid) < 0) {
                messages.remove(i);
                identityMap.releaseRow(message);
                removed = true;
            }
        }
        if (!removed) {
            return false;
        }
        if (messages.isEmpty()) {
            oldestCursor.set(0, 0L, 0);
        } else {
            int oldestDate = 0;
            long oldestUid = 0;
            int oldestMid = 0;
            for (int i = 0; i < messages.size(); i++) {
                MessageObject message = messages.get(i);
                if (message != null && (oldestDate == 0
                        || compareTimeline(message.messageOwner.date, message.getDialogId(), message.getRealId(),
                                oldestDate, oldestUid, oldestMid) < 0)) {
                    oldestDate = message.messageOwner.date;
                    oldestUid = message.getDialogId();
                    oldestMid = message.getRealId();
                }
            }
            oldestCursor.set(oldestDate, oldestUid, oldestMid);
        }
        endReached = false;
        updateCount();
        return true;
    }
}
