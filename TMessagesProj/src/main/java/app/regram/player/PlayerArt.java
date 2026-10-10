package app.regram.player;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.LruCache;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlayerArt {

    public interface SeedCallback {
        void onSeed(int seed);
    }

    private static final LruCache<String, Integer> seeds = new LruCache<>(64);
    // Accessed on the UI thread; one quantization job per cover, without scanning all requests.
    private static final HashMap<String, ArrayList<SeedCallback>> pending = new HashMap<>();
    private static final ExecutorService COLORS_IO = Executors.newSingleThreadExecutor(r ->
            new Thread(r, "player-colors"));

    private PlayerArt() {
    }

    public static String key(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        TLRPC.Document document = messageObject.getDocument();
        if (document != null && document.id != 0) {
            return "d" + document.id;
        }
        return "m" + messageObject.getDialogId() + "_" + messageObject.getId();
    }

    public static Integer cachedSeed(MessageObject messageObject) {
        String key = key(messageObject);
        return key == null ? null : seeds.get(key);
    }

    public static boolean isPlaying(MessageObject messageObject) {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        return playing != null && messageObject != null && TextUtils.equals(key(playing), key(messageObject));
    }

    public static Bitmap fileCover(MessageObject messageObject) {
        if (!isPlaying(messageObject)) {
            return null;
        }
        AudioInfo info = MediaController.getInstance().getAudioInfo();
        return info != null ? info.getCover() : null;
    }

    public static ImageLocation thumbLocation(MessageObject messageObject) {
        TLRPC.Document document = messageObject.getDocument();
        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 360) : null;
        if (!(thumb instanceof TLRPC.TL_photoSize) && !(thumb instanceof TLRPC.TL_photoSizeProgressive)) {
            thumb = null;
        }
        if (thumb != null) {
            return ImageLocation.getForDocument(thumb, document);
        }
        String small = messageObject.getArtworkUrl(true);
        if (small != null) {
            return ImageLocation.getForPath(small);
        }
        return null;
    }

    public static ImageLocation fullLocation(MessageObject messageObject) {
        String url = messageObject.getArtworkUrl(false);
        return TextUtils.isEmpty(url) ? null : ImageLocation.getForPath(url);
    }

    public static void requestSeed(MessageObject messageObject, Bitmap bitmap, int fallback, SeedCallback callback) {
        String key = key(messageObject);
        if (key == null) {
            return;
        }
        Integer cached = seeds.get(key);
        if (cached != null) {
            callback.onSeed(cached);
            return;
        }
        ArrayList<SeedCallback> callbacks = pending.get(key);
        if (callbacks != null) {
            callbacks.add(callback);
            return;
        }
        Bitmap sample = PlayerColors.sample(bitmap);
        if (sample == null) {
            return;
        }
        callbacks = new ArrayList<>();
        callbacks.add(callback);
        pending.put(key, callbacks);
        COLORS_IO.execute(() -> {
            final int seed;
            try {
                seed = PlayerColors.seedFromSample(sample, fallback);
            } finally {
                sample.recycle();
            }
            AndroidUtilities.runOnUIThread(() -> {
                seeds.put(key, seed);
                ArrayList<SeedCallback> ready = pending.remove(key);
                if (ready == null) return;
                for (SeedCallback subscriber : ready) {
                    try {
                        subscriber.onSeed(seed);
                    } catch (RuntimeException e) {
                        FileLog.e(e);
                    }
                }
            });
        });
    }
}
