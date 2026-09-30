package app.regram.feed;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Реестр каналов, пригодных для ленты. Слушает обновления списка диалогов, пересобирает набор
 * с задержкой и сообщает слушателям разницу — какие каналы добавились, какие пропали.
 */
public class FeedChannelRegistry implements NotificationCenter.NotificationCenterDelegate {

    private static final long REBUILD_DELAY = 500L;

    private static final FeedChannelRegistry[] instances = new FeedChannelRegistry[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] locks = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int account = 0; account < locks.length; account++) {
            locks[account] = new Object();
        }
    }

    public interface Listener {
        void onFeedChannelsChanged(HashSet<Long> added, HashSet<Long> removed);
    }

    public final int currentAccount;

    private final HashSet<Long> channelIds = new HashSet<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private boolean built;
    private boolean rebuildScheduled;

    private final Runnable rebuildRunnable = () -> {
        rebuildScheduled = false;
        rebuild(true);
    };

    private FeedChannelRegistry(int account) {
        currentAccount = account;
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.dialogsNeedReload));
    }

    public static FeedChannelRegistry getInstance(int num) {
        FeedChannelRegistry cached = instances[num];
        if (cached != null) {
            return cached;
        }
        synchronized (locks[num]) {
            cached = instances[num];
            if (cached == null) {
                cached = new FeedChannelRegistry(num);
                instances[num] = cached;
            }
        }
        return cached;
    }

    private void ensureBuilt() {
        if (built) {
            return;
        }
        built = true;
        rebuild(false);
    }

    private void rebuild(boolean notify) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        LongSparseArray<TLRPC.Dialog> dialogs = messagesController.dialogs_dict;
        HashSet<Long> current = new HashSet<>();
        for (int index = 0; index < dialogs.size(); index++) {
            TLRPC.Dialog dialog = dialogs.valueAt(index);
            if (dialog == null || !DialogObject.isChatDialog(dialog.id)) {
                continue;
            }
            if (FeedController.isEligibleChannel(messagesController.getChat(-dialog.id))) {
                current.add(dialog.id);
            }
        }

        HashSet<Long> added = null;
        HashSet<Long> removed = null;
        for (Long dialogId : current) {
            if (!channelIds.contains(dialogId)) {
                if (added == null) {
                    added = new HashSet<>();
                }
                added.add(dialogId);
            }
        }
        for (Long dialogId : channelIds) {
            if (!current.contains(dialogId)) {
                if (removed == null) {
                    removed = new HashSet<>();
                }
                removed.add(dialogId);
            }
        }
        if (added == null && removed == null) {
            return;
        }

        channelIds.clear();
        channelIds.addAll(current);
        if (!notify) {
            return;
        }
        if (added == null) {
            added = new HashSet<>();
        }
        if (removed == null) {
            removed = new HashSet<>();
        }
        for (int index = listeners.size() - 1; index >= 0; index--) {
            listeners.get(index).onFeedChannelsChanged(added, removed);
        }
    }

    /**
     * Подписка на изменения состава каналов. Первый вызов заодно строит набор,
     * поэтому слушатель не получит фиктивную «разницу» из пустого состояния.
     */
    public void addListener(Listener listener) {
        ensureBuilt();
        if (listeners.contains(listener)) {
            return;
        }
        listeners.add(listener);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.dialogsNeedReload) {
            return;
        }
        ensureBuilt();
        if (rebuildScheduled) {
            return;
        }
        rebuildScheduled = true;
        AndroidUtilities.runOnUIThread(rebuildRunnable, REBUILD_DELAY);
    }
}
