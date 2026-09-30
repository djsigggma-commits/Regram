package app.regram.feed;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Переводит исходящие запросы из синтетической нумерации ленты в настоящую.
 * Сообщения ленты живут под сквозными идентификаторами, поэтому перед отправкой на сервер
 * у запроса подменяются поля с id сообщений, а заодно и адресат (peer или channel),
 * если по восстановленным id видно, что запрос относится к другому диалогу.
 */
public abstract class FeedRequestNormalizer {

    private static final String TL_PACKAGE_PREFIX = "org.telegram.tgnet.";

    private static final Field[] EMPTY_FIELDS = new Field[0];
    private static final ClassMetadata EMPTY_METADATA = new ClassMetadata(null, null, null, null, EMPTY_FIELDS);

    private static final ConcurrentHashMap<Class<?>, ClassMetadata> metadataCache = new ConcurrentHashMap<>();

    private static final class ClassMetadata {
        private final Field requestPeerField;
        private final Field peerField;
        private final Field channelField;
        private final Field invoiceField;
        private final Field[] messageIdFields;

        private ClassMetadata(Field requestPeerField, Field peerField, Field channelField, Field invoiceField, Field[] messageIdFields) {
            this.requestPeerField = requestPeerField;
            this.peerField = peerField;
            this.channelField = channelField;
            this.invoiceField = invoiceField;
            this.messageIdFields = messageIdFields;
        }
    }

    private static ClassMetadata buildMetadata(Class<?> requestClass) {
        Field[] fields;
        try {
            fields = requestClass.getFields();
        } catch (Exception ignored) {
            fields = EMPTY_FIELDS;
        }
        Field fromPeerField = null;
        Field peerField = null;
        Field channelField = null;
        Field invoiceField = null;
        ArrayList<Field> messageIdFields = null;
        for (Field field : fields) {
            String name = field.getName();
            if ("from_peer".equals(name) && fromPeerField == null) {
                fromPeerField = field;
            } else if ("peer".equals(name) && peerField == null) {
                peerField = field;
            } else if ("channel".equals(name) && channelField == null) {
                channelField = field;
            } else if ("invoice".equals(name) && invoiceField == null) {
                invoiceField = field;
            }
            if (isMessageIdField(field)) {
                if (messageIdFields == null) {
                    messageIdFields = new ArrayList<>();
                }
                messageIdFields.add(field);
            }
        }
        return new ClassMetadata(
                fromPeerField != null ? fromPeerField : peerField,
                peerField,
                channelField,
                invoiceField,
                messageIdFields != null ? messageIdFields.toArray(new Field[0]) : EMPTY_FIELDS
        );
    }

    private static ClassMetadata getMetadata(Object request) {
        if (request == null) {
            return EMPTY_METADATA;
        }
        return metadataCache.computeIfAbsent(request.getClass(), FeedRequestNormalizer::buildMetadata);
    }

    private static boolean isMessageIdField(Field field) {
        if (field == null || Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        String name = field.getName();
        return "id".equals(name) || "msg_id".equals(name) || name.endsWith("_msg_id");
    }

    private static Object getFieldValue(Field field, Object request) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(request);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long getDialogId(Field field, Object request) {
        if (field == null) {
            return 0L;
        }
        try {
            Object value = field.get(request);
            if (value instanceof TLRPC.InputPeer) {
                return DialogObject.getPeerDialogId((TLRPC.InputPeer) value);
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static long getChannelDialogId(Field field, Object request) {
        Object value = getFieldValue(field, request);
        if (value instanceof TLRPC.InputChannel) {
            return getInputChannelDialogId((TLRPC.InputChannel) value);
        }
        return 0L;
    }

    private static long getInputChannelDialogId(TLRPC.InputChannel inputChannel) {
        if (inputChannel == null || inputChannel.channel_id == 0) {
            return 0L;
        }
        return -inputChannel.channel_id;
    }

    private static long mergeResolvedDialogIds(long current, long resolved) {
        if (current == 0) {
            return resolved;
        }
        if (resolved == 0 || current == resolved) {
            return current;
        }
        return 0L;
    }

    /**
     * Единственная точка входа: получает запрос перед отправкой и возвращает его же,
     * но с настоящими id сообщений и адресатом. Запросы, не относящиеся к ленте,
     * и любые не-TL объекты проходят насквозь без изменений.
     */
    public static TLObject normalize(int currentAccount, TLObject request) {
        if (request == null) {
            return request;
        }
        FeedController feedController = FeedController.peekInstance(currentAccount);
        if (feedController == null || feedController.hasNoSyntheticIds()) {
            return request;
        }
        if (!request.getClass().getName().startsWith(TL_PACKAGE_PREFIX)) {
            return request;
        }
        ClassMetadata metadata = getMetadata(request);
        if (metadata.messageIdFields.length != 0 || metadata.invoiceField != null) {
            normalizeMessageIds(currentAccount, feedController, request, metadata);
            normalizeInvoice(currentAccount, feedController, getFieldValue(metadata.invoiceField, request));
        }
        return request;
    }

    private static void normalizeInvoice(int currentAccount, FeedController feedController, Object invoice) {
        if (invoice instanceof TLRPC.TL_inputInvoiceMessage) {
            normalizeMessageIds(currentAccount, feedController, invoice);
        }
    }

    private static void normalizeMessageIds(int currentAccount, FeedController feedController, Object request) {
        normalizeMessageIds(currentAccount, feedController, request, getMetadata(request));
    }

    private static void normalizeMessageIds(int currentAccount, FeedController feedController, Object request, ClassMetadata metadata) {
        Field requestPeerField = metadata.requestPeerField;
        long requestDialogId = getDialogId(requestPeerField, request);
        if (requestDialogId == 0) {
            requestDialogId = getDialogId(metadata.peerField, request);
        }
        if (requestDialogId == 0) {
            requestDialogId = getChannelDialogId(metadata.channelField, request);
        }
        long resolvedDialogId = normalizeMessageIdFields(feedController, request, metadata);
        if (resolvedDialogId == 0 || resolvedDialogId == requestDialogId) {
            return;
        }
        if (requestPeerField != null) {
            setInputPeer(currentAccount, requestPeerField, request, resolvedDialogId);
        } else if (metadata.channelField != null) {
            setInputChannel(currentAccount, metadata.channelField, request, resolvedDialogId);
        }
    }

    private static long normalizeMessageIdFields(FeedController feedController, Object request, ClassMetadata metadata) {
        if (request == null) {
            return 0L;
        }
        long resolvedDialogId = 0L;
        for (Field field : metadata.messageIdFields) {
            resolvedDialogId = mergeResolvedDialogIds(resolvedDialogId, normalizeMessageIdField(feedController, request, field));
        }
        return resolvedDialogId;
    }

    private static long normalizeMessageIdField(FeedController feedController, Object request, Field field) {
        try {
            Object value = field.get(request);
            if (value instanceof Integer) {
                int syntheticId = (Integer) value;
                long dialogId = feedController.resolveRealDialogId(syntheticId);
                if (dialogId == 0) {
                    return 0L;
                }
                field.setInt(request, feedController.resolveRealMessageId(dialogId, syntheticId));
                return dialogId;
            }
            if (!(value instanceof ArrayList)) {
                return 0L;
            }
            ArrayList<?> ids = (ArrayList<?>) value;
            long resolvedDialogId = 0L;
            for (int i = 0; i < ids.size(); i++) {
                try {
                    Object id = ids.get(i);
                    if (!(id instanceof Integer)) {
                        continue;
                    }
                    int syntheticId = (Integer) id;
                    long dialogId = feedController.resolveRealDialogId(syntheticId);
                    if (dialogId != 0) {
                        setListInteger(ids, i, feedController.resolveRealMessageId(dialogId, syntheticId));
                        resolvedDialogId = mergeResolvedDialogIds(resolvedDialogId, dialogId);
                    }
                } catch (Exception ignored) {
                    return resolvedDialogId;
                }
            }
            return resolvedDialogId;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static void setInputChannel(int currentAccount, Field field, Object request, long dialogId) {
        if (currentAccount < 0 || dialogId >= 0) {
            return;
        }
        try {
            TLRPC.InputChannel inputChannel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
            if (inputChannel != null) {
                field.set(request, inputChannel);
            }
        } catch (Exception ignored) {
        }
    }

    private static void setInputPeer(int currentAccount, Field field, Object request, long dialogId) {
        if (currentAccount < 0) {
            return;
        }
        try {
            TLRPC.InputPeer inputPeer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            if (inputPeer != null) {
                field.set(request, inputPeer);
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void setListInteger(ArrayList<?> list, int index, int value) {
        ((ArrayList<Integer>) list).set(index, value);
    }
}
