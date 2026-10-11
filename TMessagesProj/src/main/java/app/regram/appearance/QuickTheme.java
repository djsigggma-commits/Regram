package app.regram.appearance;

import org.telegram.ui.ActionBar.Theme;
import tw.nekomimi.nekogram.NekoConfig;

/** Reversible palette adjustments; never changes theme files, photographs or text alpha. */
public final class QuickTheme {
    private static final ThreadLocal<Boolean> resolving = new ThreadLocal<>();
    private static final ThreadLocal<TransformCache> transformCache = new ThreadLocal<TransformCache>() {
        @Override protected TransformCache initialValue() { return new TransformCache(); }
    };

    private QuickTheme() {}

    public static boolean beginResolve() {
        if (!NekoConfig.regramQuickTheme.Bool()) return false;
        if (NekoConfig.regramQuickContrast.Int() == 100 && NekoConfig.regramQuickSaturation.Int() == 100
                && NekoConfig.regramQuickBrightness.Int() == 0) return false;
        if (Boolean.TRUE.equals(resolving.get())) return false;
        resolving.set(true);
        return true;
    }

    public static void endResolve(boolean owner) {
        if (owner) resolving.set(false);
    }

    public static int adjust(int key, int color) {
        // Whitelist only palette surfaces/accents, not numeric theme flags, masks,
        // photographs, peer colors or ordinary message text.
        if (!isPaletteColor(key) || (color >>> 24) == 0) return color;
        return transformCache.get().adjust(color, NekoConfig.regramQuickContrast.Int(),
                NekoConfig.regramQuickSaturation.Int(), NekoConfig.regramQuickBrightness.Int());
    }

    private static boolean isPaletteColor(int key) {
        return key == Theme.key_windowBackgroundWhite || key == Theme.key_windowBackgroundGray
                || key == Theme.key_actionBarDefault || key == Theme.key_actionBarDefaultArchived
                || key == Theme.key_dialogBackground || key == Theme.key_dialogFloatingButton
                || key == Theme.key_featuredStickers_addButton
                || key == Theme.key_windowBackgroundWhiteBlueHeader
                || key == Theme.key_windowBackgroundWhiteBlueText
                || key == Theme.key_chat_messagePanelBackground
                || key == Theme.key_chat_inBubble || key == Theme.key_chat_inBubbleSelected
                || key == Theme.key_chat_outBubble || key == Theme.key_chat_outBubbleSelected
                || key == Theme.key_chat_outBubbleGradient1 || key == Theme.key_chat_outBubbleGradient2
                || key == Theme.key_chat_outBubbleGradient3;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Pure color math, with original alpha preserved and identity defaults. */
    public static int transform(int color, int contrastPercent, int saturationPercent, int brightnessPercent) {
        float contrast = clamp(contrastPercent, 50, 150) / 100f;
        float saturation = clamp(saturationPercent, 0, 200) / 100f;
        float brightness = clamp(brightnessPercent, -30, 30) * 255f / 100f;
        if (contrast == 1f && saturation == 1f && brightness == 0f) return color;
        int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
        float luminance = red * .2126f + green * .7152f + blue * .0722f;
        int r = channel((luminance + (red - luminance) * saturation - 127.5f) * contrast + 127.5f + brightness);
        int g = channel((luminance + (green - luminance) * saturation - 127.5f) * contrast + 127.5f + brightness);
        int b = channel((luminance + (blue - luminance) * saturation - 127.5f) * contrast + 127.5f + brightness);
        return (color & 0xff000000) | (r << 16) | (g << 8) | b;
    }

    private static int channel(float value) { return clamp(Math.round(value), 0, 255); }

    /** Fixed-size, per-thread cache: no boxing, locks or allocations during palette drawing.
     * Source ARGB and all parameters are in the key, so animated/local themes cannot go stale. */
    private static final class TransformCache {
        private static final int SIZE = 64;
        private final int[] inputs = new int[SIZE], outputs = new int[SIZE], parameters = new int[SIZE];

        int adjust(int color, int contrast, int saturation, int brightness) {
            contrast = clamp(contrast, 50, 150);
            saturation = clamp(saturation, 0, 200);
            brightness = clamp(brightness, -30, 30);
            if (contrast == 100 && saturation == 100 && brightness == 0) return color;
            int params = (contrast << 16) | (saturation << 8) | (brightness + 30);
            int slot = (color ^ (color >>> 16)) & (SIZE - 1);
            if (inputs[slot] == color && parameters[slot] == params) return outputs[slot];
            int result = transform(color, contrast, saturation, brightness);
            inputs[slot] = color;
            parameters[slot] = params;
            outputs[slot] = result;
            return result;
        }
    }

    public static int bubbleAlpha() {
        return NekoConfig.regramQuickTheme.Bool()
                ? Math.round(255 * (100 - clamp(NekoConfig.regramQuickTransparency.Int(), 0, 80)) / 100f) : 255;
    }

    public static void reset() {
        NekoConfig.regramQuickTheme.setConfigBool(false);
        NekoConfig.regramQuickTransparency.setConfigInt(0);
        NekoConfig.regramQuickContrast.setConfigInt(100);
        NekoConfig.regramQuickSaturation.setConfigInt(100);
        NekoConfig.regramQuickBrightness.setConfigInt(0);
    }
}
