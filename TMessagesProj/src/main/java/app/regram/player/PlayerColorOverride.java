package app.regram.player;

import tw.nekomimi.nekogram.NekoConfig;

/** Native equivalent of Player Color 1.3 (@neptuniy237); original palette remains when off/invalid. */
public final class PlayerColorOverride {
    private PlayerColorOverride() {}
    private static final int[] PRESETS = {0, 0xfff44336, 0xffe91e63, 0xff9c27b0, 0xff3f51b5,
            0xff2196f3, 0xff00bcd4, 0xff4caf50, 0xffcddc39, 0xffffc107, 0xffff9800, 0xff795548, 0xff808080};
    private static volatile Parsed cached;
    private static final class Parsed {
        final String text;
        final Integer color;
        Parsed(String text, Integer color) { this.text = text; this.color = color; }
    }
    public static Integer currentColor() {
        if (!NekoConfig.regramPlayerColor.Bool()) return null;
        int preset = NekoConfig.regramPlayerColorPreset.Int();
        if (preset > 0 && preset < PRESETS.length) return PRESETS[preset];
        String text = NekoConfig.regramPlayerColorHex.String();
        Parsed entry = cached;
        if (entry != null && java.util.Objects.equals(entry.text, text)) return entry.color;
        Integer color = parseHex(text);
        cached = new Parsed(text, color);
        return color;
    }
    public static int apply(int original) {
        Integer color = currentColor();
        return color == null ? original : color;
    }
    public static Integer parseHex(String text) {
        if (text == null) return null;
        String hex = text.trim();
        while (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 3) hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        if (!hex.matches("[0-9a-fA-F]{6}")) return null;
        return 0xff000000 | Integer.parseInt(hex, 16);
    }
}
