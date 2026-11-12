package app.regram.fonts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Bounded sfnt import. Android's Typeface loader performs the final format validation. */
public final class FontFileImport {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private FontFileImport() {}

    public static void copy(InputStream input, OutputStream output) throws IOException {
        if (input == null) throw new IOException("No font stream");
        byte[] header = new byte[4];
        int read = 0;
        while (read < header.length) {
            int n = input.read(header, read, header.length - read);
            if (n < 0) throw new IOException("Truncated font");
            if (n == 0) throw new IOException("Empty read");
            read += n;
        }
        int magic = (header[0] & 255) << 24 | (header[1] & 255) << 16 | (header[2] & 255) << 8 | header[3] & 255;
        if (magic != 0x00010000 && magic != 0x4f54544f && magic != 0x74746366 && magic != 0x74727565) {
            throw new IOException("Not an OpenType/TrueType font");
        }
        output.write(header);
        int total = header.length;
        byte[] buffer = new byte[16384];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (n == 0) throw new IOException("Empty read");
            if (n > MAX_BYTES - total) throw new IOException("Font exceeds 20 MiB");
            output.write(buffer, 0, n);
            total += n;
        }
    }

    public static String displayName(String name) {
        if (name == null) return "Font";
        String clean = name.replaceAll("[\\p{Cntrl}/\\\\]", " ").trim();
        clean = clean.replaceFirst("(?i)\\.(ttf|otf|ttc)$", "");
        if (clean.isEmpty()) return "Font";
        return clean.substring(0, Math.min(clean.length(), 80));
    }
}
