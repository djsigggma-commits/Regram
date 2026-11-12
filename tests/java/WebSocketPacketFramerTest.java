package org.telegram.tgnet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Real AES-CTR vectors, without Android or third-party test libraries. */
public final class WebSocketPacketFramerTest {
    private static final class Wire {
        final byte[] init;
        final Cipher encrypt;
        Wire(int protocol) throws Exception {
            byte[] plain = new byte[64];
            for (int i = 0; i < plain.length; i++) plain[i] = (byte) (i * 7 + 2);
            Arrays.fill(plain, 56, 60, (byte) protocol);
            plain[60] = 2;
            plain[61] = 0;
            encrypt = Cipher.getInstance("AES/CTR/NoPadding");
            encrypt.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Arrays.copyOfRange(plain, 8, 40), "AES"),
                    new IvParameterSpec(Arrays.copyOfRange(plain, 40, 56)));
            init = plain.clone();
            byte[] encrypted = encrypt.update(plain);
            System.arraycopy(encrypted, 56, init, 56, 8);
        }
        byte[] packet(int words, boolean extended, boolean ack) {
            byte[] bytes = new byte[(extended ? 4 : 1) + words * 4];
            bytes[0] = (byte) ((extended ? 0x7f : words) | (ack ? 0x80 : 0));
            if (extended) {
                bytes[1] = (byte) words;
                bytes[2] = (byte) (words >> 8);
                bytes[3] = (byte) (words >> 16);
            }
            for (int i = extended ? 4 : 1; i < bytes.length; i++) bytes[i] = (byte) (i * 13);
            return encrypt.update(bytes);
        }
    }
    private static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part);
        return out.toByteArray();
    }
    private static void equal(byte[] left, byte[] right) {
        if (!Arrays.equals(left, right)) throw new AssertionError("Ciphertext/boundaries changed");
    }
    private static void fails(WebSocketPacketFramer framer, boolean handshake) throws Exception {
        try {
            if (handshake) framer.handshake(); else framer.packet();
            throw new AssertionError("Malformed/truncated packet accepted");
        } catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        // Coalesced TCP stream, short/long packets, high quick-ACK bit and cipher cursor continuity.
        Wire wire = new Wire(0xef);
        byte[] a = wire.packet(1, false, true);
        byte[] b = wire.packet(127, true, true);
        byte[] c = wire.packet(32000, true, false);
        byte[] d = wire.packet(126, false, false);
        byte[] stream = concat(wire.init, a, b, c, d);
        for (int fragment : new int[]{1, 3, 64, 8192, stream.length}) {
            InputStream chunks = new FilterInputStream(new ByteArrayInputStream(stream)) {
                @Override public int read(byte[] bytes, int offset, int count) throws IOException {
                    return super.read(bytes, offset, Math.min(fragment, count));
                }
            };
            WebSocketPacketFramer framer = new WebSocketPacketFramer(chunks);
            equal(wire.init, framer.handshake());
            for (byte[] packet : new byte[][]{a, b, c, d}) equal(packet, framer.packet());
            fails(framer, false); // clean EOF does not spin
        }
        for (int protocol : new int[]{0xdd, 0xee, 0}) {
            Wire invalid = new Wire(protocol);
            fails(new WebSocketPacketFramer(new ByteArrayInputStream(invalid.init)), true);
        }
        Wire zero = new Wire(0xef);
        WebSocketPacketFramer empty = new WebSocketPacketFramer(new ByteArrayInputStream(concat(zero.init, zero.packet(0, false, false))));
        empty.handshake();
        fails(empty, false);
        Wire oversized = new Wire(0xef);
        byte[] badSize = oversized.encrypt.update(new byte[]{0x7f, 0, 0, 0x40}); // 16 MiB body + header exceeds WS queue
        WebSocketPacketFramer large = new WebSocketPacketFramer(new ByteArrayInputStream(concat(oversized.init, badSize)));
        large.handshake();
        fails(large, false); // reject before allocation or payload read
        // Every truncation point in the initial handshake and a long packet must fail.
        for (int end = 0; end < 64; end++) {
            fails(new WebSocketPacketFramer(new ByteArrayInputStream(Arrays.copyOf(stream, end))), true);
        }
        Wire cut = new Wire(0xef);
        byte[] full = concat(cut.init, cut.packet(127, true, false));
        for (int end : new int[]{64, 65, 66, 67, 68, full.length - 1}) {
            WebSocketPacketFramer truncated = new WebSocketPacketFramer(new ByteArrayInputStream(Arrays.copyOf(full, end)));
            truncated.handshake();
            fails(truncated, false);
        }
        System.out.println("WebSocketPacketFramer: OK");
    }
}
