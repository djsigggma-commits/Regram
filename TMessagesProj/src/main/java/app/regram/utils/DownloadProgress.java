package app.regram.utils;

import java.util.concurrent.Executor;

/** Latest-value delivery: a fast download cannot flood the UI message queue. */
public final class DownloadProgress {
    public interface Listener {
        void onProgress(long done, long total);
    }

    private final Executor ui;
    private Listener listener;
    private long done;
    private long total;
    private boolean queued;
    private boolean dirty;
    private final Runnable delivery = this::deliver;

    /** The executor may delay delivery to throttle updates (e.g. by 100 ms). */
    public DownloadProgress(Executor ui, Listener listener) {
        this.ui = ui;
        this.listener = listener;
    }

    public void update(long done, long total) {
        synchronized (this) {
            if (listener == null) return;
            this.done = done;
            this.total = total;
            dirty = true;
            if (queued) return;
            queued = true;
        }
        ui.execute(delivery);
    }

    /** Call on the UI thread before completion; flushes and releases the listener. */
    public void finish() {
        Listener callback;
        long bytes;
        long size;
        synchronized (this) {
            callback = dirty ? listener : null;
            bytes = done;
            size = total;
            listener = null;
            dirty = false;
        }
        if (callback != null) callback.onProgress(bytes, size);
    }

    private void deliver() {
        Listener callback;
        long bytes;
        long size;
        synchronized (this) {
            queued = false;
            callback = dirty ? listener : null;
            bytes = done;
            size = total;
            dirty = false;
        }
        if (callback != null) callback.onProgress(bytes, size);
    }
}
