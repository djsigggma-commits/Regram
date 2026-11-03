package app.regram.updater;

import java.io.IOException;
import java.util.Locale;

/** Platform-independent APK selection and download integrity checks. */
public final class UpdateArtifact {
    private static final String[] ABIS = {"arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86"};

    private UpdateArtifact() {}

    public static int select(String[] names, String[] supportedAbis) {
        int selected = -1;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) continue;
            String name = names[i].toLowerCase(Locale.ROOT);
            if (!name.endsWith(".apk")) continue;
            String architecture = null;
            // Match longer names first: x86_64 is not an x86 build.
            for (String abi : ABIS) {
                if (name.contains(abi)) {
                    architecture = abi;
                    break;
                }
            }
            int rank = supportedAbis.length;
            if (architecture != null) {
                rank = -1;
                for (int j = 0; j < supportedAbis.length; j++) {
                    if (architecture.equalsIgnoreCase(supportedAbis[j])) {
                        rank = j;
                        break;
                    }
                }
                if (rank < 0) continue;
            }
            if (rank < best) {
                best = rank;
                selected = i;
            }
        }
        return selected;
    }

    public static void verifyLength(long downloaded, long contentLength, long assetSize) throws IOException {
        if (downloaded <= 0 || contentLength >= 0 && downloaded != contentLength
                || assetSize > 0 && downloaded != assetSize) {
            throw new IOException("Incomplete APK download: " + downloaded + " bytes");
        }
    }
}
