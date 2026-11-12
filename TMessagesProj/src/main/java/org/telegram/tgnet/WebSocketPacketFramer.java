package org.telegram.tgnet;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Obfuscated abridged MTProto: one complete transport packet per WS binary message.
 * The cipher is only a framing cursor; original ciphertext is forwarded unchanged. */
final class WebSocketPacketFramer {
    static final int MAX_PACKET = 16 * 1024 * 1024 - 4;
    private final InputStream input;
    private final byte[] scratch = new byte[8192];
    private Cipher cursor;

    WebSocketPacketFramer(InputStream input) { this.input = input; }

    byte[] handshake() throws Exception {
        byte[] init = read(64);
        cursor = Cipher.getInstance("AES/CTR/NoPadding");
        cursor.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Arrays.copyOfRange(init, 8, 40), "AES"),
                new IvParameterSpec(Arrays.copyOfRange(init, 40, 56)));
        byte[] plain = cursor.update(init);
        for (int i = 56; i < 60; i++) {
            if ((plain[i] & 255) != 0xef) throw new IOException("Unsupported MTProto transport");
        }
        return init;
    }

    byte[] packet() throws IOException {
        byte[] first = read(1);
        int words = cursor.update(first)[0] & 0x7f; // high bit requests a quick ACK
        byte[] tail = words == 0x7f ? read(3) : new byte[0];
        if (tail.length != 0) {
            byte[] plain = cursor.update(tail);
            words = (plain[0] & 255) | ((plain[1] & 255) << 8) | ((plain[2] & 255) << 16);
        }
        long length = words * 4L;
        if (length == 0 || length > MAX_PACKET) throw new IOException("Invalid MTProto packet size");
        byte[] body = read((int) length);
        // Advance without allocating a second payload-sized plaintext buffer.
        try {
            for (int offset = 0; offset < body.length; offset += scratch.length) {
                cursor.update(body, offset, Math.min(scratch.length, body.length - offset), scratch, 0);
            }
        } catch (javax.crypto.ShortBufferException error) { throw new IOException(error); }
        byte[] message = new byte[1 + tail.length + body.length];
        message[0] = first[0];
        System.arraycopy(tail, 0, message, 1, tail.length);
        System.arraycopy(body, 0, message, 1 + tail.length, body.length);
        return message;
    }

    private byte[] read(int count) throws IOException {
        byte[] bytes = new byte[count];
        int offset = 0;
        while (offset < count) {
            int n = input.read(bytes, offset, count - offset);
            if (n < 0) throw new EOFException();
            if (n > 0) offset += n;
        }
        return bytes;
    }
}
