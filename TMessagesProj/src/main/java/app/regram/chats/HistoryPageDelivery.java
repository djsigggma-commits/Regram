package app.regram.chats;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Delivers an already loaded history page even if optional reply/story loading stalls. */
public final class HistoryPageDelivery implements Runnable {
    public static final long AUXILIARY_WAIT_MS = 1000;

    private final AtomicBoolean delivered = new AtomicBoolean();
    private final Runnable delivery;
    private final Consumer<Runnable> cancelTimeout;

    public HistoryPageDelivery(Runnable delivery, Consumer<Runnable> cancelTimeout) {
        this.delivery = delivery;
        this.cancelTimeout = cancelTimeout;
    }

    /** Both the timeout and auxiliary completion must invoke this on the UI thread. */
    @Override
    public void run() {
        if (delivered.compareAndSet(false, true)) {
            cancelTimeout.accept(this);
            delivery.run();
        }
    }
}
