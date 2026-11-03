package app.regram.termux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Dependency-free framing and authentication primitives, also tested on the host JVM. */
public final class TermuxWire {
    public static final int MAX_LINE = 1024 * 1024;
    private TermuxWire() { }

    public static String secret() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder(48);
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    public static boolean matches(String expected, String supplied) {
        return !expected.isEmpty() && supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    public static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int b;
        while ((b = input.read()) != -1) {
            if (b == '\n') return bytes.toString("UTF-8").replaceAll("\\r$", "");
            if (bytes.size() >= MAX_LINE) throw new IOException("request too large");
            bytes.write(b);
        }
        // Never execute an incomplete request after EOF.
        if (bytes.size() != 0) throw new IOException("incomplete request");
        return null;
    }

    /** One-use, monotonic-time pairing grant; URL secret is separate from the RPC token. */
    public static final class Pairing {
        private String path = "";
        private long deadline;
        public synchronized String open(long now) {
            path = "/i/" + secret();
            deadline = now + 300_000;
            return path;
        }
        public synchronized boolean consume(String candidate, long now) {
            if (now >= deadline || !matches(path, candidate)) return false;
            clear();
            return true;
        }
        public synchronized void clear() { path = ""; deadline = 0; }
    }
}
