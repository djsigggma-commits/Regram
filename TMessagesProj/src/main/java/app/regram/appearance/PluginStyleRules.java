package app.regram.appearance;

/** Pure native presentation math for Chip Folders 1.0.2. */
public final class PluginStyleRules {
    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public static float unit(float v) { return Float.isNaN(v) ? 0 : Math.max(0, Math.min(1, v)); }
    public static float mix(float a, float b, float p) { return a + (b - a) * unit(p); }
    public static float chipRadius(int shape, float progress, float height, float active, float inactive) {
        float half = Math.max(0, height / 2);
        switch (clamp(shape, 0, 7)) {
            case 1: return Math.min(half, 14);
            case 2: return Math.min(half, 10);
            case 3: return Math.min(half, 6);
            case 4: return mix(half, Math.min(half, 11), progress);
            case 5: case 6: case 7: return mix(Math.min(half, inactive), Math.min(half, active), progress);
            default: return half;
        }
    }
    public static float wave(int animation, int speed, float progress) {
        float p = unit(progress);
        if (p <= 0 || p >= 1 || animation == 0) return 0;
        float t = Math.min(1, p * clamp(speed, 50, 200) / 100f);
        return animation == 4 ? (float) Math.sin(t * Math.PI * 3) * (1 - t) : (float) Math.sin(t * Math.PI);
    }
    public static float scaleX(int animation, int speed, float progress) {
        return scaleXFromWave(animation, wave(animation, speed, progress));
    }
    public static float scaleXFromWave(int animation, float wave) {
        float w = wave * .12f;
        return animation == 2 || animation == 3 ? 1 - w : 1 + w;
    }
    public static float scaleY(int animation, int speed, float progress) {
        return scaleYFromWave(animation, wave(animation, speed, progress));
    }
    public static float scaleYFromWave(int animation, float wave) {
        float w = wave * .12f;
        return animation == 1 ? 1 - w * .6f : animation == 2 ? 1 + w * .6f : animation == 3 ? 1 - w : 1 + w;
    }
    private PluginStyleRules() {}
}
