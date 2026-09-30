package app.regram.feed;

import java.util.ArrayList;
import java.util.HashSet;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/**
 * Дозагружает старые посты каналов, у которых кончилась локальная история.
 * Раунд запрашивает несколько каналов сразу и завершается, когда ответили все
 * либо истёк таймаут.
 */
final class FeedBackfillCoordinator {

    private static final int MAX_CHANNELS_PER_ROUND = 4;
    private static final int MESSAGES_PER_CHANNEL = 20;
    private static final long ROUND_TIMEOUT_MS = 10000L;

    private final int currentAccount;
    private final Runnable onRoundFinished;
    private final int guid = ConnectionsManager.generateClassGuid();
    private final HashSet<Long> pending = new HashSet<>();
    private final HashSet<Long> exhausted = new HashSet<>();

    private int loadIndex;
    private int roundId;
    private boolean running;

    public FeedBackfillCoordinator(int currentAccount, Runnable onRoundFinished) {
        this.currentAccount = currentAccount;
        this.onRoundFinished = onRoundFinished;
    }

    private void finishRound() {
        running = false;
        roundId++;
        pending.clear();
        onRoundFinished.run();
    }

    private void onRoundTimeout(int startedRoundId) {
        if (startedRoundId == roundId && running) {
            exhausted.addAll(pending);
            finishRound();
        }
    }

    private void onResult(long dialogId) {
        if (running && pending.remove(dialogId) && pending.isEmpty()) {
            finishRound();
        }
    }

    public void cancel() {
        running = false;
        roundId++;
        pending.clear();
        ConnectionsManager.getInstance(currentAccount).cancelRequestsForGuid(guid);
    }

    public void clearExhausted() {
        exhausted.clear();
    }

    public HashSet<Long> getExhaustedSnapshot() {
        return new HashSet<>(exhausted);
    }

    /**
     * Обработчик {@code NotificationCenter.loadingMessagesFailed}: канал, чей запрос
     * не прошёл, помечается исчерпанным, чтобы раунд не ждал его вечно.
     */
    public void onLoadingMessagesFailed(Object... args) {
        if ((Integer) args[0] != guid) {
            return;
        }
        long dialogId = 0;
        Object request = args[1];
        if (request instanceof TLRPC.TL_messages_getHistory) {
            TLRPC.InputPeer peer = ((TLRPC.TL_messages_getHistory) request).peer;
            if (peer != null) {
                long peerId = peer.channel_id != 0 ? peer.channel_id : peer.chat_id;
                dialogId = -peerId;
            }
        }
        if (dialogId != 0) {
            exhausted.add(dialogId);
        }
        onResult(dialogId);
    }

    /**
     * Обработчик {@code NotificationCenter.messagesDidLoad}: неполная страница означает,
     * что у канала больше нечего дозагружать.
     */
    public void onMessagesDidLoad(Object... args) {
        if ((Integer) args[10] != guid) {
            return;
        }
        Long dialogId = (Long) args[0];
        if (((ArrayList<?>) args[2]).size() < MESSAGES_PER_CHANNEL) {
            exhausted.add(dialogId);
        }
        onResult(dialogId);
    }

    /**
     * Запускает раунд дозагрузки. Каждый элемент списка — пара
     * {@code {dialogId, maxId}}: канал и идентификатор, ниже которого просить историю.
     */
    public void startRound(ArrayList<long[]> candidates) {
        running = true;
        final int startedRoundId = ++roundId;
        pending.clear();

        int channelCount = Math.min(MAX_CHANNELS_PER_ROUND, candidates.size());
        for (int i = 0; i < channelCount; i++) {
            pending.add(candidates.get(i)[0]);
        }

        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        for (int i = 0; i < channelCount; i++) {
            long dialogId = candidates.get(i)[0];
            int maxId = (int) candidates.get(i)[1];
            messagesController.loadMessages(dialogId, 0L, false, MESSAGES_PER_CHANNEL, maxId, 0, false, 0,
                    guid, 0, 0, 0, 0L, 0, loadIndex++, false);
        }

        AndroidUtilities.runOnUIThread(() -> onRoundTimeout(startedRoundId), ROUND_TIMEOUT_MS);
    }
}
