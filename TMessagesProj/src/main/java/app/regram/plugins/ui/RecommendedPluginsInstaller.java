package app.regram.plugins.ui;

import android.app.Activity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.regram.plugins.PluginsController;

/** One-shot, user-initiated import of document posts from the official public channel. */
final class RecommendedPluginsInstaller implements NotificationCenter.NotificationCenterDelegate {
    interface Callback {
        void onFound(int count, Runnable confirm);
        void onFinished(String message);
    }

    private static final String CHANNEL = "regram_backdoor_plugins";
    private static final int MAX_PAGES = 10;
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final String[] EXTENSIONS = {".py", ".plugin", ".elyx", ".eaf"};
    // Do not block the global queue while copying a potentially 20 MB plugin.
    private static final ExecutorService COPY_IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "regram-plugin-copy");
        thread.setDaemon(true);
        return thread;
    });
    private final Activity activity;
    private final int account;
    private final Callback callback;
    private final ArrayList<TLRPC.Message> pending = new ArrayList<>();
    private final Set<Long> documents = new HashSet<>();
    private int pages, installed, failed, nextIndex;
    private boolean finished;
    private TLRPC.Message downloading;
    private File staged;
    private String awaited;
    private final Runnable timeout = () -> failDownload();

    RecommendedPluginsInstaller(Activity activity, int account, Callback callback) {
        this.activity = activity;
        this.account = account;
        this.callback = callback;
    }

    void start() {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = CHANNEL;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (finished) return;
                    if (!(response instanceof TLRPC.TL_contacts_resolvedPeer) || error != null) {
                        finish(activity.getString(R.string.RegramRecommendedNetworkError));
                        return;
                    }
                    TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                    if (!(resolved.peer instanceof TLRPC.TL_peerChannel)) {
                        finish(activity.getString(R.string.RegramRecommendedNetworkError));
                        return;
                    }
                    long channelId = resolved.peer.channel_id;
                    for (TLRPC.Chat chat : resolved.chats) {
                        if (chat.id == channelId && chat.access_hash != 0) {
                            TLRPC.TL_inputPeerChannel peer = new TLRPC.TL_inputPeerChannel();
                            peer.channel_id = chat.id;
                            peer.access_hash = chat.access_hash;
                            fetch(peer, 0);
                            return;
                        }
                    }
                    finish(activity.getString(R.string.RegramRecommendedNetworkError));
                }));
    }

    private void fetch(TLRPC.InputPeer peer, int offset) {
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer;
        req.offset_id = offset;
        req.limit = 100;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (finished) return;
                    if (!(response instanceof TLRPC.messages_Messages) || error != null) {
                        finish(activity.getString(R.string.RegramRecommendedNetworkError));
                        return;
                    }
                    ArrayList<TLRPC.Message> messages = ((TLRPC.messages_Messages) response).messages;
                    int oldest = Integer.MAX_VALUE;
                    for (TLRPC.Message message : messages) {
                        if (message.id > 0) oldest = Math.min(oldest, message.id);
                        TLRPC.Document doc = message.media != null ? message.media.document : null;
                        if (doc == null || doc.size <= 0 || doc.size > MAX_BYTES
                                || extension(doc) == null || !documents.add(doc.id)) continue;
                        pending.add(message);
                    }
                    if (++pages < MAX_PAGES && messages.size() >= 100 && oldest > 0
                            && oldest != Integer.MAX_VALUE && oldest != offset) {
                        fetch(peer, oldest);
                    } else if (pending.isEmpty()) {
                        finish(activity.getString(R.string.RegramRecommendedEmpty));
                    } else {
                        // Older versions first: if the channel reposted a plugin, the newest wins.
                        java.util.Collections.reverse(pending);
                        callback.onFound(pending.size(), this::begin);
                    }
                }));
    }

    private static String extension(TLRPC.Document doc) {
        for (TLRPC.DocumentAttribute attribute : doc.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                String name = ((TLRPC.TL_documentAttributeFilename) attribute).file_name;
                if (name == null) return null;
                name = name.toLowerCase(Locale.ROOT);
                for (String ext : EXTENSIONS) {
                    if (name.endsWith(ext)) return ext;
                }
            }
        }
        return null;
    }

    private void begin() {
        if (finished) return;
        PluginsController controller = PluginsController.getInstance();
        if (controller.isSafeMode()) {
            finish(activity.getString(R.string.RegramRecommendedSafeMode));
            return;
        }
        if (!controller.isEngineEnabled()) controller.setEngineEnabled(true);
        next();
    }

    private void next() {
        if (finished) return;
        if (nextIndex == pending.size()) {
            finish(activity.getString(R.string.RegramRecommendedResult, installed, failed));
            return;
        }
        downloading = pending.get(nextIndex++);
        TLRPC.Document doc = downloading.media.document;
        FileLoader loader = FileLoader.getInstance(account);
        File local = loader.getPathToAttach(doc, null, false);
        if (local != null && local.isFile() && local.length() == doc.size) {
            install(local);
            return;
        }
        awaited = FileLoader.getAttachFileName(doc);
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.fileLoaded);
        center.addObserver(this, NotificationCenter.fileLoadFailed);
        AndroidUtilities.runOnUIThread(timeout, 60000);
        loader.loadFile(doc, downloading, FileLoader.PRIORITY_NORMAL, 0);
    }

    @Override
    public void didReceivedNotification(int id, int currentAccount, Object... args) {
        if (finished || awaited == null || args.length == 0 || !awaited.equals(args[0])) return;
        clearDownload();
        if (id == NotificationCenter.fileLoaded) {
            File local = args.length > 1 && args[1] instanceof File ? (File) args[1]
                    : FileLoader.getInstance(account).getPathToAttach(downloading.media.document, null, false);
            if (local != null && local.isFile() && local.length() == downloading.media.document.size) {
                install(local);
                return;
            }
        }
        failed++;
        next();
    }

    private void failDownload() {
        if (finished || awaited == null) return;
        clearDownload();
        failed++;
        next();
    }

    private void clearDownload() {
        awaited = null;
        AndroidUtilities.cancelRunOnUIThread(timeout);
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.removeObserver(this, NotificationCenter.fileLoaded);
        center.removeObserver(this, NotificationCenter.fileLoadFailed);
    }

    private void install(File source) {
        TLRPC.Document doc = downloading.media.document;
        String ext = extension(doc);
        if (ext == null) { failed++; next(); return; }
        COPY_IO.execute(() -> {
            File file = null;
            try {
                file = File.createTempFile("recommended-", ext, activity.getCacheDir());
                try (FileInputStream in = new FileInputStream(source);
                     FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    long count = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        count += read;
                        if (count > MAX_BYTES) throw new java.io.IOException("Plugin too large");
                        out.write(buffer, 0, read);
                    }
                    if (count != doc.size) throw new java.io.IOException("Incomplete plugin");
                }
            } catch (Exception e) {
                FileLog.e("Recommended plugin copy failed", e);
                if (file != null) file.delete();
                AndroidUtilities.runOnUIThread(() -> { failed++; next(); });
                return;
            }
            File stagedFile = file;
            AndroidUtilities.runOnUIThread(() -> {
                if (finished) { stagedFile.delete(); return; }
                staged = stagedFile;
                PluginsController.getInstance().installPlugin(stagedFile, true, (ok, error, plugin) -> {
                    stagedFile.delete();
                    staged = null;
                    if (finished) return;
                    if (ok) installed++; else {
                        failed++;
                        FileLog.e("Recommended plugin install failed: " + error);
                    }
                    next();
                });
            });
        });
    }

    void cancel() {
        finish(null);
    }

    private void finish(String message) {
        if (finished) return;
        finished = true;
        clearDownload();
        pending.clear();
        documents.clear();
        downloading = null;
        if (staged != null) staged.delete();
        callback.onFinished(message);
    }
}
