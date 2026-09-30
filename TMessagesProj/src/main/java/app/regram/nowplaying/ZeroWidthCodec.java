package app.regram.nowplaying;

public final class ZeroWidthCodec {

    public static final char MARK = '⁤';
    public static final char OWNER_MARK = '﻿';

    private static final char[] ALPHABET = {'⁠', '⁡', '⁢', '⁣'};

    private ZeroWidthCodec() {
    }

    public static String stripToString(String text) {
        CharSequence stripped = strip(text);
        return stripped == null ? null : stripped.toString();
    }

    public static CharSequence strip(CharSequence text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == MARK || c == OWNER_MARK || valueOf(c) >= 0) {
                if (sb == null) {
                    sb = new StringBuilder(text.length());
                    sb.append(text, 0, i);
                }
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb != null ? sb.toString().trim() : text;
    }

    private static int valueOf(char c) {
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
