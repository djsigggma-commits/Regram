package app.regram.feed;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сопоставление сообщений ленты с оригиналами в каналах. В синтетическом чате идентификаторы
 * должны быть уникальными, поэтому каждому сообщению выдаётся свой сгенерированный id, а исходная
 * пара (диалог, id) запоминается для обратного разрешения. Здесь же выбирается «главное»
 * сообщение альбома, по которому рисуется группа.
 */
final class FeedMessageIdentityMap {

    private static final int FIRST_GENERATED_ID = Integer.MAX_VALUE - 10;

    private final HashMap<MessageCompositeID, Integer> generatedIds = new HashMap<>();
    private final ConcurrentHashMap<Integer, MessageCompositeID> realIdsByGeneratedId = new ConcurrentHashMap<>();
    private final HashMap<MessageCompositeID, MessageObject> messagesByRealId = new HashMap<>();
    private final HashMap<GroupKey, MessageObject> primaryByGroup = new HashMap<>();

    private int lastGeneratedId = FIRST_GENERATED_ID;

    public static final class GroupKey {
        final long dialog_id;
        final long groupedId;

        public GroupKey(long dialogId, long groupedId) {
            this.dialog_id = dialogId;
            this.groupedId = groupedId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || GroupKey.class != obj.getClass()) {
                return false;
            }
            GroupKey other = (GroupKey) obj;
            return dialog_id == other.dialog_id && groupedId == other.groupedId;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(dialog_id) * 31 + Long.hashCode(groupedId);
        }
    }

    public static final class MessageCompositeID {
        final long dialog_id;
        final int id;

        public MessageCompositeID(long dialogId, int id) {
            this.dialog_id = dialogId;
            this.id = id;
        }

        public MessageCompositeID(TLRPC.Message message) {
            this.dialog_id = MessageObject.getDialogId(message);
            this.id = message.id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || MessageCompositeID.class != obj.getClass()) {
                return false;
            }
            MessageCompositeID other = (MessageCompositeID) obj;
            return dialog_id == other.dialog_id && id == other.id;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(dialog_id) * 31 + id;
        }
    }

    private void updatePrimaryGroupFlag(MessageObject messageObject, long dialogId, int realId) {
        if (!messageObject.hasValidGroupId()) {
            messageObject.isPrimaryGroupMessage = false;
            return;
        }
        GroupKey groupKey = new GroupKey(dialogId, messageObject.messageOwner.grouped_id);
        MessageObject previousPrimary = primaryByGroup.get(groupKey);
        if (previousPrimary != null && realId <= previousPrimary.getRealId()) {
            messageObject.isPrimaryGroupMessage = false;
            return;
        }
        messageObject.isPrimaryGroupMessage = true;
        if (previousPrimary != null) {
            previousPrimary.isPrimaryGroupMessage = false;
        }
        primaryByGroup.put(groupKey, messageObject);
    }

    public void clear() {
        generatedIds.clear();
        realIdsByGeneratedId.clear();
        messagesByRealId.clear();
        primaryByGroup.clear();
        lastGeneratedId = FIRST_GENERATED_ID;
    }

    /**
     * Поиск сообщения по идентификатору любой природы: сначала как по настоящему id канала,
     * затем — как по сгенерированному для ленты.
     */
    public MessageObject getByAnyId(long dialogId, int id) {
        MessageObject byRealId = messagesByRealId.get(new MessageCompositeID(dialogId, id));
        if (byRealId != null) {
            return byRealId;
        }
        int resolvedId = resolveRealMessageId(dialogId, id);
        if (resolvedId != id) {
            return messagesByRealId.get(new MessageCompositeID(dialogId, resolvedId));
        }
        return null;
    }

    public MessageObject getByRealId(long dialogId, int id) {
        return messagesByRealId.get(new MessageCompositeID(dialogId, id));
    }

    public boolean isEmpty() {
        return realIdsByGeneratedId.isEmpty();
    }

    public void purge(MessageObject messageObject) {
        MessageCompositeID compositeId = new MessageCompositeID(messageObject.getDialogId(), messageObject.getRealId());
        generatedIds.remove(compositeId);
        messagesByRealId.remove(compositeId);
        realIdsByGeneratedId.remove(messageObject.getId());
        if (messageObject.hasValidGroupId()) {
            GroupKey groupKey = new GroupKey(compositeId.dialog_id, messageObject.messageOwner.grouped_id);
            if (primaryByGroup.get(groupKey) == messageObject) {
                primaryByGroup.remove(groupKey);
            }
        }
    }

    /**
     * Регистрирует сообщение в ленте: выдаёт ему сгенерированный id, сохраняя настоящий в realId.
     * Возвращает true, если сообщение попало в ленту впервые.
     */
    public boolean register(MessageObject messageObject) {
        messageObject.reactionsLastCheckTime = Long.MAX_VALUE;
        MessageCompositeID compositeId = new MessageCompositeID(messageObject.messageOwner);
        int realId = messageObject.messageOwner.id;
        Integer generatedId = generatedIds.get(compositeId);
        if (generatedId == null) {
            generatedId = lastGeneratedId--;
            generatedIds.put(compositeId, generatedId);
        }
        realIdsByGeneratedId.put(generatedId, compositeId);

        boolean added;
        if (messagesByRealId.containsKey(compositeId)) {
            added = false;
        } else {
            updatePrimaryGroupFlag(messageObject, compositeId.dialog_id, realId);
            messagesByRealId.put(compositeId, messageObject);
            added = true;
        }

        TLRPC.Message message = messageObject.messageOwner;
        message.realId = realId;
        message.id = generatedId;
        return added;
    }

    public void releaseRow(MessageObject messageObject) {
        messagesByRealId.remove(new MessageCompositeID(messageObject.getDialogId(), messageObject.getRealId()));
        if (messageObject.hasValidGroupId()) {
            GroupKey groupKey = new GroupKey(messageObject.getDialogId(), messageObject.messageOwner.grouped_id);
            if (primaryByGroup.get(groupKey) == messageObject) {
                primaryByGroup.remove(groupKey);
            }
        }
    }

    /**
     * Подменяет уже зарегистрированное сообщение новым объектом, сохраняя выданный ранее id
     * и роль главного в альбоме.
     */
    public void replace(MessageObject messageObject) {
        messageObject.reactionsLastCheckTime = Long.MAX_VALUE;
        MessageCompositeID compositeId = new MessageCompositeID(messageObject.getDialogId(), messageObject.getRealId());
        generatedIds.put(compositeId, messageObject.getId());
        realIdsByGeneratedId.put(messageObject.getId(), compositeId);
        MessageObject previous = messagesByRealId.put(compositeId, messageObject);
        if (messageObject.hasValidGroupId()) {
            GroupKey groupKey = new GroupKey(compositeId.dialog_id, messageObject.messageOwner.grouped_id);
            if (previous != null && primaryByGroup.get(groupKey) == previous) {
                primaryByGroup.put(groupKey, messageObject);
            }
        }
    }

    public long resolveRealDialogId(int generatedId) {
        MessageCompositeID compositeId = realIdsByGeneratedId.get(generatedId);
        return compositeId != null ? compositeId.dialog_id : 0L;
    }

    public int resolveRealMessageId(long dialogId, int generatedId) {
        MessageCompositeID compositeId = realIdsByGeneratedId.get(generatedId);
        if (compositeId == null || compositeId.dialog_id != dialogId) {
            return generatedId;
        }
        return compositeId.id;
    }
}
