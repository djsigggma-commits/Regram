package app.regram.documents;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/** Bounded MIME text preview. Attachments are listed, never decoded to disk or opened automatically. */
final class DocumentMail {
    private static final class State {
        String plain = "", html = "";
        final List<String> attachments = new ArrayList<>();
        int parts;
    }
    static String read(byte[] raw) throws IOException {
        String source = new String(raw, StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
        State state = new State(); Map<String, String> headers = headers(source);
        part(source, state, 0);
        StringBuilder out = new StringBuilder();
        for (String header : new String[]{"from", "to", "cc", "subject", "date"}) if (headers.containsKey(header)) out.append(header).append(": ").append(words(headers.get(header))).append('\n');
        out.append('\n').append(state.plain.isEmpty() ? DocumentText.htmlText(state.html) : state.plain);
        if (!state.attachments.isEmpty()) out.append("\n\nAttachments / Вложения:\n• ").append(String.join("\n• ", state.attachments));
        if (out.length() > DocumentReader.MAX_TEXT) throw new IOException("MIME text limit");
        return out.toString();
    }
    private static Map<String, String> headers(String source) throws IOException {
        int end = source.indexOf("\n\n"); if (end < 0) end = source.length();
        if (end > 65536) throw new IOException("MIME headers too large");
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : source.substring(0, end).replaceAll("\n[ \\t]+", " ").split("\n")) {
            int colon = line.indexOf(':'); if (colon > 0) out.put(line.substring(0, colon).toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
        }
        return out;
    }
    private static String parameter(String value, String name) {
        Matcher matcher = Pattern.compile("(?:^|;)\\s*" + Pattern.quote(name) + "\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]*))", Pattern.CASE_INSENSITIVE).matcher(value);
        return matcher.find() ? matcher.group(1) != null ? matcher.group(1) : matcher.group(2) : "";
    }
    private static void part(String source, State state, int depth) throws IOException {
        DocumentText.check(); if (depth > 8 || ++state.parts > 128) throw new IOException("MIME part limit");
        Map<String, String> headers = headers(source); int split = source.indexOf("\n\n"); String body = split < 0 ? "" : source.substring(split + 2);
        String contentType = headers.getOrDefault("content-type", "text/plain"); String type = contentType.split(";", 2)[0].toLowerCase(Locale.ROOT).trim();
        String disposition = headers.getOrDefault("content-disposition", "");
        String filename = parameter(disposition, "filename"); if (filename.isEmpty()) filename = parameter(contentType, "name");
        if (!filename.isEmpty() || disposition.toLowerCase(Locale.ROOT).startsWith("attachment")) { state.attachments.add(filename.isEmpty() ? "(unnamed / без имени)" : words(filename)); return; }
        if (type.startsWith("multipart/")) {
            String boundary = parameter(contentType, "boundary"); if (boundary.isEmpty() || boundary.length() > 200) throw new IOException("Invalid MIME boundary");
            Matcher markers = Pattern.compile("(?m)^--" + Pattern.quote(boundary) + "(--)?[ \\t]*$").matcher(body);
            int start = -1;
            while (markers.find()) {
                if (start >= 0) part(body.substring(start, markers.start()), state, depth + 1);
                if (markers.group(1) != null) break;
                start = markers.end(); if (start < body.length() && body.charAt(start) == '\n') start++;
            }
            return;
        }
        if (!type.equals("text/plain") && !type.equals("text/html")) return;
        byte[] bytes = body.getBytes(StandardCharsets.ISO_8859_1);
        String encoding = headers.getOrDefault("content-transfer-encoding", "").trim().toLowerCase(Locale.ROOT);
        if (encoding.equals("base64")) try { bytes = Base64.getMimeDecoder().decode(bytes); } catch (Exception e) { throw new IOException("Invalid MIME base64"); }
        else if (encoding.equals("quoted-printable")) bytes = quoted(body, false);
        String charset = parameter(contentType, "charset"); String text;
        try { text = new String(bytes, Charset.forName(charset.isEmpty() ? "UTF-8" : charset)); } catch (Exception e) { text = new String(bytes, StandardCharsets.UTF_8); }
        if (type.equals("text/plain") && state.plain.isEmpty()) state.plain = text;
        if (type.equals("text/html") && state.html.isEmpty()) state.html = text;
    }
    private static byte[] quoted(String source, boolean word) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '=' && i + 1 < source.length() && source.charAt(i + 1) == '\n') { i++; continue; }
            if (c == '=' && i + 2 < source.length()) try { out.write(Integer.parseInt(source.substring(i + 1, i + 3), 16)); i += 2; continue; } catch (Exception ignored) { }
            out.write(word && c == '_' ? ' ' : (byte) c);
        }
        return out.toByteArray();
    }
    static String words(String source) {
        source = source.replaceAll("(?<=\\?=)\\s+(?==\\?)", "");
        Matcher matcher = Pattern.compile("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=").matcher(source); StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String decoded = matcher.group();
            try { byte[] bytes = matcher.group(2).equalsIgnoreCase("b") ? Base64.getDecoder().decode(matcher.group(3)) : quoted(matcher.group(3), true); decoded = new String(bytes, Charset.forName(matcher.group(1))); } catch (Exception ignored) { }
            matcher.appendReplacement(out, Matcher.quoteReplacement(decoded));
        }
        matcher.appendTail(out); return out.toString();
    }
}
