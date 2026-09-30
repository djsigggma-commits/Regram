package app.regram.feed;

import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Чтение ленты из локальной базы: перечисление каналов пользователя и выборка их сообщений,
 * слитых в один поток по ключу (date, uid, mid) в порядке убывания.
 * Все методы выполняются на очереди MessagesStorage.
 */
final class FeedTimelineLoader {

    private static final int CHUNK_SIZE = 30;
    private static final int NEWER_PAGE_SIZE = 50;
    private static final int MAX_ROWS_SCANNED_FOR_UNREAD = 200;
    private static final int WINDOW_SIZE = 500;
    private static final int DIALOG_BATCH_SIZE = 64;
    private static final int ALBUM_TAIL_LOOKUP = 9;

    private final int currentAccount;

    private ChannelSet channelSetCache;

    private enum Direction {
        OLDER("<"),
        NEWER(">");

        final String operator;

        Direction(String operator) {
            this.operator = operator;
        }
    }

    public static final class ChannelEnumeration {
        boolean hasChannels;
        final ArrayList<ChannelSnapshot> included = new ArrayList<>();
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
    }

    public static final class ChannelSet {
        final int configGen;
        boolean hasChannels;
        final int sessionGen;
        final ArrayList<long[]> includedRows = new ArrayList<>();
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();

        public ChannelSet(int sessionGen, int configGen) {
            this.sessionGen = sessionGen;
            this.configGen = configGen;
        }
    }

    public static final class ChannelSnapshot {
        int depthDate;
        int depthMid;
        final long dialogId;
        boolean hasCached;
        boolean hasHole;
        int holeEnd;
        boolean incomplete;
        boolean localStartReached;
        final int readInboxMax;
        final int topMessage;
        final int unreadCount;

        public ChannelSnapshot(long dialogId, int readInboxMax, int unreadCount, int topMessage) {
            this.dialogId = dialogId;
            this.readInboxMax = readInboxMax;
            this.unreadCount = unreadCount;
            this.topMessage = topMessage;
        }
    }

    public static final class Cursor {
        int date;
        int mid;
        long uid;

        public boolean isEmpty() {
            return date == 0;
        }

        public void set(int date, long uid, int mid) {
            this.date = date;
            this.uid = uid;
            this.mid = mid;
        }
    }

    public static final class NewerPage {
        boolean hasMore;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        final Cursor first = new Cursor();
    }

    public static final class OlderPage {
        boolean hasIncomplete;
        int lastChunkRowCount;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        final ArrayList<long[]> backfillCandidates = new ArrayList<>();
        final Cursor last = new Cursor();
        final Cursor first = new Cursor();
    }

    public static final class WindowPage {
        boolean truncated;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
    }

    public FeedTimelineLoader(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    private static void appendCursorBound(StringBuilder sql, Cursor cursor, Direction direction, boolean inclusive) {
        String operator = direction.operator;
        String midOperator = inclusive ? operator + "= " : operator + " ";
        sql.append(" AND (date ");
        sql.append(operator);
        sql.append(' ');
        sql.append(cursor.date);
        sql.append(" OR date = ");
        sql.append(cursor.date);
        sql.append(" AND (uid ");
        sql.append(operator);
        sql.append(' ');
        sql.append(cursor.uid);
        sql.append(" OR uid = ");
        sql.append(cursor.uid);
        sql.append(" AND mid ");
        sql.append(midOperator);
        sql.append(cursor.mid);
        sql.append("))");
    }

    private ChannelSet buildChannelSet(FeedConfig feedConfig, int sessionGen, int configGen) {
        ChannelSet channelSet = new ChannelSet(sessionGen, configGen);
        try {
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
            StringBuilder sql = new StringBuilder("SELECT did, inbox_max, unread_count, last_mid FROM dialogs WHERE did < 0");
            if (!feedConfig.getIncludeArchived()) {
                sql.append(" AND folder_id != 1");
            }
            sql.append(" ORDER BY date DESC");

            ArrayList<long[]> rows = new ArrayList<>();
            ArrayList<Long> chatIds = new ArrayList<>();
            SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized(sql.toString(), new Object[0]);
            try {
                while (sqlCursor.next()) {
                    long dialogId = sqlCursor.longValue(0);
                    rows.add(new long[]{dialogId, sqlCursor.intValue(1), sqlCursor.intValue(2), sqlCursor.intValue(3)});
                    chatIds.add(-dialogId);
                }
            } finally {
                sqlCursor.dispose();
            }
            if (chatIds.isEmpty()) {
                return channelSet;
            }

            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            messagesStorage.getChatsInternal(TextUtils.join(",", chatIds), chats);
            LongSparseArray<TLRPC.Chat> chatsById = new LongSparseArray<>(chats.size());
            for (int i = 0; i < chats.size(); i++) {
                TLRPC.Chat chat = chats.get(i);
                if (chat != null) {
                    chatsById.put(chat.id, chat);
                }
            }
            for (int i = 0; i < rows.size(); i++) {
                long[] row = rows.get(i);
                TLRPC.Chat chat = chatsById.get(-row[0]);
                if (!FeedController.isEligibleChannel(chat)) {
                    continue;
                }
                channelSet.hasChannels = true;
                channelSet.channels.add(chat);
                if (!feedConfig.isExcluded(row[0])) {
                    channelSet.includedRows.add(row);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return channelSet;
    }

    private static void clusterGroupedMessages(ArrayList<TLRPC.Message> messages) {
        if (messages.size() < 3) {
            return;
        }
        HashMap<Long, ArrayList<TLRPC.Message>> albums = new HashMap<>();
        boolean hasAlbums = false;
        for (int i = 0; i < messages.size(); i++) {
            long groupedId = messages.get(i).grouped_id;
            if (groupedId != 0) {
                ArrayList<TLRPC.Message> album = albums.get(groupedId);
                if (album == null) {
                    album = new ArrayList<>();
                    albums.put(groupedId, album);
                } else {
                    hasAlbums = true;
                }
                album.add(messages.get(i));
            }
        }
        if (!hasAlbums) {
            return;
        }
        ArrayList<TLRPC.Message> clustered = new ArrayList<>(messages.size());
        HashSet<Long> appended = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            TLRPC.Message message = messages.get(i);
            long groupedId = message.grouped_id;
            if (groupedId == 0) {
                clustered.add(message);
            } else if (appended.add(groupedId)) {
                clustered.addAll(albums.get(groupedId));
            }
        }
        messages.clear();
        messages.addAll(clustered);
    }

    private static int compareDesc(Cursor first, Cursor second) {
        if (first.date != second.date) {
            return first.date > second.date ? -1 : 1;
        }
        if (first.uid != second.uid) {
            return first.uid > second.uid ? -1 : 1;
        }
        return -Integer.compare(first.mid, second.mid);
    }

    private void completeTrailingAlbum(MessagesStorage messagesStorage, OlderPage page, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) throws SQLiteException {
        if (page.messages.isEmpty()) {
            return;
        }
        TLRPC.Message tail = page.messages.get(page.messages.size() - 1);
        if (tail.grouped_id == 0) {
            return;
        }
        SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized("SELECT data, mid, date, uid FROM messages_v2 WHERE uid = " + tail.dialog_id + " AND mid > 0 AND mid < " + tail.id + " ORDER BY date DESC, mid DESC LIMIT " + ALBUM_TAIL_LOOKUP, new Object[0]);
        try {
            while (sqlCursor.next()) {
                TLRPC.Message message = readMessage(sqlCursor);
                if (message == null || message.grouped_id != tail.grouped_id) {
                    break;
                }
                page.messages.add(message);
                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
            }
        } finally {
            sqlCursor.dispose();
        }
    }

    private Cursor findUnreadBoundary(MessagesStorage messagesStorage, ArrayList<ChannelSnapshot> channels, int minDate) {
        StringBuilder condition = new StringBuilder();
        Cursor boundary = null;
        int batched = 0;
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            if (channel.topMessage > channel.readInboxMax || channel.unreadCount > 0) {
                if (condition.length() > 0) {
                    condition.append(" OR ");
                }
                condition.append("uid = ");
                condition.append(channel.dialogId);
                condition.append(" AND mid > ");
                condition.append(channel.readInboxMax);
                batched++;
            }
            if (batched > 0 && (batched == DIALOG_BATCH_SIZE || i == channels.size() - 1)) {
                Cursor batchBoundary = queryUnreadBoundary(messagesStorage, condition, minDate);
                if (batchBoundary != null && (boundary == null || compareDesc(batchBoundary, boundary) > 0)) {
                    boundary = batchBoundary;
                }
                condition.setLength(0);
                batched = 0;
            }
        }
        return boundary;
    }

    private static void loadChannelDepths(MessagesStorage messagesStorage, ArrayList<ChannelSnapshot> channels) throws SQLiteException {
        LongSparseArray<ChannelSnapshot> byDialogId = new LongSparseArray<>(channels.size());
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            channel.depthMid = 0;
            channel.depthDate = Integer.MAX_VALUE;
            channel.hasCached = false;
            channel.localStartReached = false;
            byDialogId.put(channel.dialogId, channel);
        }
        int from = 0;
        while (from < channels.size()) {
            int to = Math.min(from + DIALOG_BATCH_SIZE, channels.size());
            StringBuilder sql = new StringBuilder();
            for (int i = from; i < to; i++) {
                if (sql.length() > 0) {
                    sql.append(" UNION ALL ");
                }
                ChannelSnapshot channel = channels.get(i);
                sql.append("SELECT uid, mid, date FROM (SELECT uid, mid, date FROM messages_v2 WHERE uid = ");
                sql.append(channel.dialogId);
                sql.append(" AND mid >= ");
                sql.append(Math.max(channel.holeEnd, 1));
                sql.append(" ORDER BY date ASC, mid ASC LIMIT 1)");
            }
            SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized(sql.toString(), new Object[0]);
            try {
                while (sqlCursor.next()) {
                    ChannelSnapshot channel = byDialogId.get(sqlCursor.longValue(0));
                    if (channel != null) {
                        channel.depthMid = sqlCursor.intValue(1);
                        channel.depthDate = sqlCursor.intValue(2);
                        channel.hasCached = true;
                    }
                }
            } finally {
                sqlCursor.dispose();
            }
            from = to;
        }
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            channel.localStartReached = !channel.hasHole && channel.hasCached;
        }
    }

    private int loadChunk(MessagesStorage messagesStorage, String dialogIds, int minDate, OlderPage page, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) throws SQLiteException {
        StringBuilder sql = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
        sql.append(dialogIds);
        sql.append(") AND mid > 0");
        if (minDate > 0) {
            sql.append(" AND date >= ");
            sql.append(minDate);
        }
        if (!page.last.isEmpty()) {
            appendCursorBound(sql, page.last, Direction.OLDER, false);
        }
        sql.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT ");
        sql.append(CHUNK_SIZE);

        int rowCount = 0;
        SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized(sql.toString(), new Object[0]);
        try {
            while (sqlCursor.next()) {
                rowCount++;
                page.last.set(sqlCursor.intValue(2), sqlCursor.longValue(3), sqlCursor.intValue(1));
                if (page.first.isEmpty()) {
                    page.first.set(page.last.date, page.last.uid, page.last.mid);
                }
                TLRPC.Message message = readMessage(sqlCursor);
                if (message != null) {
                    page.messages.add(message);
                    MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                }
            }
        } finally {
            sqlCursor.dispose();
        }
        return rowCount;
    }

    private Cursor queryUnreadBoundary(MessagesStorage messagesStorage, StringBuilder condition, int minDate) {
        StringBuilder sql = new StringBuilder("SELECT date, uid, mid FROM messages_v2 WHERE mid > 0 AND (");
        sql.append(condition);
        sql.append(")");
        if (minDate > 0) {
            sql.append(" AND date >= ");
            sql.append(minDate);
        }
        sql.append(" ORDER BY date ASC, uid ASC, mid ASC LIMIT 1");
        try {
            SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized(sql.toString(), new Object[0]);
            try {
                if (!sqlCursor.next()) {
                    return null;
                }
                Cursor boundary = new Cursor();
                boundary.set(sqlCursor.intValue(0), sqlCursor.longValue(1), sqlCursor.intValue(2));
                return boundary;
            } finally {
                sqlCursor.dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private TLRPC.Message readMessage(SQLiteCursor sqlCursor) throws SQLiteException {
        NativeByteBuffer data = sqlCursor.byteBufferValue(0);
        if (data == null) {
            return null;
        }
        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
        if (message == null) {
            data.reuse();
            return null;
        }
        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
        data.reuse();
        if (message instanceof TLRPC.TL_messageEmpty || message.action != null) {
            return null;
        }
        message.id = sqlCursor.intValue(1);
        message.date = sqlCursor.intValue(2);
        message.dialog_id = sqlCursor.longValue(3);
        return message;
    }

    /**
     * Возвращает состав ленты: все подходящие каналы аккаунта и снимки тех из них,
     * что не скрыты пользователем. Результат кэшируется до смены поколения сессии
     * или настроек; forceRefresh пересобирает его принудительно.
     */
    public ChannelEnumeration enumerateChannels(FeedConfig feedConfig, int sessionGen, boolean forceRefresh) {
        ChannelSet channelSet = channelSetCache;
        int configGen = feedConfig.getGeneration();
        if (forceRefresh || channelSet == null || channelSet.sessionGen != sessionGen || channelSet.configGen != configGen) {
            channelSet = buildChannelSet(feedConfig, sessionGen, configGen);
            channelSetCache = channelSet;
        }
        ChannelEnumeration enumeration = new ChannelEnumeration();
        enumeration.hasChannels = channelSet.hasChannels;
        enumeration.channels.addAll(channelSet.channels);
        for (int i = 0; i < channelSet.includedRows.size(); i++) {
            long[] row = channelSet.includedRows.get(i);
            enumeration.included.add(new ChannelSnapshot(row[0], (int) row[1], (int) row[2], (int) row[3]));
        }
        return enumeration;
    }

    public void invalidateChannelCache() {
        channelSetCache = null;
    }

    /**
     * Перечитывает уже показанный отрезок ленты между двумя курсорами включительно.
     * Если отрезок вырос сверх лимита, страница помечается truncated и вызывающий
     * перестраивает ленту с нуля.
     */
    public WindowPage loadChannelWindow(ArrayList<Long> dialogIds, Cursor newest, Cursor oldest) {
        WindowPage page = new WindowPage();
        if (dialogIds.isEmpty() || newest.isEmpty() || oldest.isEmpty()) {
            return page;
        }
        try {
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
            sql.append(TextUtils.join(",", dialogIds));
            sql.append(") AND mid > 0");
            appendCursorBound(sql, newest, Direction.OLDER, true);
            appendCursorBound(sql, oldest, Direction.NEWER, true);
            sql.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT ");
            sql.append(WINDOW_SIZE + 1);

            SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized(sql.toString(), new Object[0]);
            try {
                int rowCount = 0;
                while (sqlCursor.next()) {
                    rowCount++;
                    if (rowCount > WINDOW_SIZE) {
                        page.truncated = true;
                        break;
                    }
                    TLRPC.Message message = readMessage(sqlCursor);
                    if (message != null) {
                        page.messages.add(message);
                        MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                    }
                }
            } finally {
                sqlCursor.dispose();
            }
            if (!usersToLoad.isEmpty()) {
                messagesStorage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    /**
     * Догружает сообщения новее переданного курсора — то, что пришло в каналы,
     * пока лента была открыта. hasMore выставляется, когда страница заполнена целиком.
     */
    public NewerPage loadNewerPage(ArrayList<ChannelSnapshot> channels, Cursor newest) {
        NewerPage page = new NewerPage();
        page.first.set(newest.date, newest.uid, newest.mid);
        try {
            ArrayList<Long> dialogIds = new ArrayList<>(channels.size());
            for (int i = 0; i < channels.size(); i++) {
                dialogIds.add(channels.get(i).dialogId);
            }
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
            sql.append(TextUtils.join(",", dialogIds));
            sql.append(") AND mid > 0");
            appendCursorBound(sql, newest, Direction.NEWER, false);
            sql.append(" ORDER BY date ASC, uid ASC, mid ASC LIMIT ");
            sql.append(NEWER_PAGE_SIZE);

            int rowCount = 0;
            SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized(sql.toString(), new Object[0]);
            try {
                while (sqlCursor.next()) {
                    rowCount++;
                    page.first.set(sqlCursor.intValue(2), sqlCursor.longValue(3), sqlCursor.intValue(1));
                    TLRPC.Message message = readMessage(sqlCursor);
                    if (message != null) {
                        page.messages.add(message);
                        MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                    }
                }
            } finally {
                sqlCursor.dispose();
            }
            page.hasMore = rowCount == NEWER_PAGE_SIZE;
            if (!usersToLoad.isEmpty()) {
                messagesStorage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    /**
     * Основная выборка ленты вниз от курсора. Сначала выясняет, докуда у каждого канала
     * есть локальная история: каналы, чей кэш обрывается выше общей границы, попадают в
     * backfillCandidates, а нижняя граница выборки поднимается до их глубины, чтобы не
     * показать дыру. Если хотя бы одному кандидату нечего показать вовсе, страница
     * возвращается пустой — сначала докачка с сервера.
     */
    public OlderPage loadOlderPage(ArrayList<ChannelSnapshot> channels, Cursor from, HashSet<Long> completeDialogIds) {
        OlderPage page = new OlderPage();
        boolean fromTimelineStart = from.isEmpty();
        page.last.set(from.date, from.uid, from.mid);
        try {
            ArrayList<Long> dialogIds = new ArrayList<>(channels.size());
            for (int i = 0; i < channels.size(); i++) {
                dialogIds.add(channels.get(i).dialogId);
            }
            String dialogIdsSql = TextUtils.join(",", dialogIds);
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);

            HashMap<Long, Integer> holeEnds = new HashMap<>();
            SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized("SELECT uid, max(end) FROM messages_holes WHERE uid IN (" + dialogIdsSql + ") GROUP BY uid", new Object[0]);
            try {
                while (sqlCursor.next()) {
                    holeEnds.put(sqlCursor.longValue(0), sqlCursor.intValue(1));
                }
            } finally {
                sqlCursor.dispose();
            }
            for (int i = 0; i < channels.size(); i++) {
                ChannelSnapshot channel = channels.get(i);
                Integer holeEnd = holeEnds.get(channel.dialogId);
                channel.hasHole = holeEnd != null;
                channel.holeEnd = holeEnd != null ? holeEnd : 0;
            }
            loadChannelDepths(messagesStorage, channels);

            int minDate = 0;
            for (int i = 0; i < channels.size(); i++) {
                ChannelSnapshot channel = channels.get(i);
                channel.incomplete = !channel.localStartReached && !completeDialogIds.contains(channel.dialogId);
                if (!channel.incomplete) {
                    continue;
                }
                page.hasIncomplete = true;
                minDate = Math.max(minDate, channel.depthDate);
                long backfillFromMessageId;
                if (channel.hasCached) {
                    backfillFromMessageId = channel.depthMid;
                } else {
                    int knownTop = Math.max(channel.holeEnd, channel.topMessage);
                    backfillFromMessageId = knownTop > 0 ? knownTop + 1 : 0;
                }
                page.backfillCandidates.add(new long[]{channel.dialogId, backfillFromMessageId, channel.depthDate});
            }
            Collections.sort(page.backfillCandidates, new Comparator<long[]>() {
                @Override
                public int compare(long[] first, long[] second) {
                    return Long.compare(second[2], first[2]);
                }
            });
            if (minDate == Integer.MAX_VALUE) {
                return page;
            }

            Cursor unreadBoundary = fromTimelineStart ? findUnreadBoundary(messagesStorage, channels, minDate) : null;
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            int loadedRows = 0;
            do {
                int chunkRows = loadChunk(messagesStorage, dialogIdsSql, minDate, page, usersToLoad, chatsToLoad);
                page.lastChunkRowCount = chunkRows;
                loadedRows += chunkRows;
                if (chunkRows < CHUNK_SIZE || unreadBoundary == null || loadedRows >= MAX_ROWS_SCANNED_FOR_UNREAD) {
                    break;
                }
            } while (compareDesc(page.last, unreadBoundary) < 0);
            completeTrailingAlbum(messagesStorage, page, usersToLoad, chatsToLoad);

            for (int i = 0; i < dialogIds.size(); i++) {
                long chatId = -dialogIds.get(i);
                if (!chatsToLoad.contains(chatId)) {
                    chatsToLoad.add(chatId);
                }
            }
            if (!usersToLoad.isEmpty()) {
                messagesStorage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        clusterGroupedMessages(page.messages);
        return page;
    }
}
