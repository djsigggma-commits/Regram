package app.regram.utils;

import android.graphics.Point;

import org.telegram.messenger.AndroidUtilities;

/**
 * Мелкие утилиты, перенесённые из exteraGram
 */
public final class AppUtils {

    private AppUtils() {
    }

    /**
     * Порог скорости флика для свайпа «назад» / переключения страниц.
     *
     * Апстрим везде использует 3500 px/s — это очень много, жест почти не срабатывает.
     * exteraGram снижает порог и делает его зависящим от ориентации:
     * 1250 px/s в ландшафте, 850 px/s в портрете.
     */
    public static int getSwipeVelocity() {
        final Point size = AndroidUtilities.displaySize;
        return size.x > size.y ? 1250 : 850;
    }

    /**
     * Сторона видеокружка. Апстрим берёт значение из серверного конфига телеграма
     * (`MessagesController.roundVideoSize`, дефолт 384); exteraGram этот конфиг
     * игнорирует и всегда пишет 512
     *
     * Берём максимум из двух, чтобы серверное значение могло только повысить сторону,
     * но не вернуть нас к 384.
     */
    public static int getRoundVideoResolution(int serverValue) {
        return Math.max(ROUND_VIDEO_RESOLUTION, serverValue);
    }

    public static final int ROUND_VIDEO_RESOLUTION = 512;

    /**
     * Дописывает схему к ссылке из markdown-разметки {@code [текст](url)}.
     *
     * NagramX кладёт в {@code URLSpanReplacement} то, что написал пользователь, как есть,
     * поэтому {@code [текст](example.com)} даёт ссылку без схемы.
     */
    public static String ensureUrlHasHttps(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.contains("://")) {
            return url;
        }
        // WEB_URL инициализируется в static-блоке и может остаться null, если Pattern не собрался.
        final java.util.regex.Pattern webUrl = org.telegram.messenger.LinkifyPort.WEB_URL;
        return webUrl != null && webUrl.matcher(url).matches() ? "https://".concat(url) : url;
    }
}
