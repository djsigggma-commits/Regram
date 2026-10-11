package app.regram.updater;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Numeric release ordering, independent of Android and tolerant of tags/APK filenames. */
public final class ReleaseVersion {
    private static final Pattern VERSION = Pattern.compile(
            "(?<![\\d.])(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?![\\d.])");

    private ReleaseVersion() {}

    public static int[] parse(String... sources) {
        for (String source : sources) {
            if (source == null) continue;
            Matcher matcher = VERSION.matcher(source);
            if (!matcher.find()) continue;
            try {
                int[] parts = new int[4];
                for (int i = 0; i < parts.length; i++) {
                    String part = matcher.group(i + 1);
                    parts[i] = part == null ? 0 : Integer.parseInt(part);
                }
                return parts;
            } catch (NumberFormatException ignored) {
                // An invalid/overflowing tag must not crash the updater; try the next source.
            }
        }
        return null;
    }

    public static Integer compare(String local, String... remoteSources) {
        int[] remote = parse(remoteSources);
        int[] current = parse(local);
        if (remote == null || current == null) return null;
        for (int i = 0; i < current.length; i++) {
            if (remote[i] != current[i]) return Integer.compare(remote[i], current[i]);
        }
        return 0;
    }
}
