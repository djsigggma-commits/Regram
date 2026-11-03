package app.regram.documents;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.apache.commons.text.StringEscapeUtils;

/** Bounded text/XML/RTF/MIME helpers. Never resolves XML entities or writes archive entries. */
public final class DocumentText {
    private DocumentText() { }
    public static void check() throws IOException { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("cancelled"); }
    public static byte[] read(InputStream in, int limit, boolean truncate) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] block = new byte[8192]; int n;
        while ((n = in.read(block, 0, Math.min(block.length, limit + 1 - out.size()))) != -1) {
            check(); out.write(block, 0, n);
            if (out.size() > limit) { if (!truncate) throw new IOException("Document size limit exceeded"); return out.toByteArray(); }
        }
        return out.toByteArray();
    }
    public static final class Decoded {
        public final String text, encoding;
        Decoded(String text, String encoding) { this.text = text; this.encoding = encoding; }
    }
    public static Decoded decode(byte[] bytes, String requested) {
        String encoding = requested;
        if (encoding == null) {
            if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) encoding = "UTF-16LE";
            else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) encoding = "UTF-16BE";
            else {
                try {
                    CharsetDecoder validator = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
                    ByteBuffer input = ByteBuffer.wrap(bytes); java.nio.CharBuffer output = java.nio.CharBuffer.allocate(8192);
                    while (true) { CoderResult result = validator.decode(input, output, false); if (result.isError()) result.throwException(); if (result.isUnderflow()) break; output.clear(); }
                    encoding = "UTF-8"; // An incomplete final codepoint in a truncated preview is not CP1251.
                }
                catch (CharacterCodingException e) { encoding = "windows-1251"; }
            }
        }
        String text = new String(bytes, Charset.forName(encoding));
        if (text.startsWith("\ufeff")) text = text.substring(1);
        return new Decoded(text.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", ""), encoding);
    }
    public static int[] bitmapSize(int width, int height, int targetWidth, int maxPixels) throws IOException {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || maxPixels <= 0) throw new IOException("Invalid bitmap dimensions");
        double scale = Math.min(targetWidth * 2.0 / width, Math.min(4096.0 / Math.max(width, height), Math.sqrt(maxPixels / ((double) width * height))));
        return new int[]{Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale))};
    }
    public static List<String> pages(String text, int size) {
        if (size < 2) throw new IllegalArgumentException("page size");
        ArrayList<String> pages = new ArrayList<>();
        if (text.isEmpty()) { pages.add(""); return pages; }
        for (int i = 0; i < text.length();) {
            int end = Math.min(text.length(), i + size);
            if (end < text.length()) {
                int line = text.lastIndexOf('\n', end - 1);
                if (line > i + size / 2) end = line + 1;
                if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
            }
            pages.add(text.substring(i, end)); i = end;
        }
        return pages;
    }
    public static String htmlText(String text) {
        // Linear, text-only extraction. Unclosed script/comments discard the remainder,
        // instead of retrying a backtracking regex at every opening tag.
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) != '<') { out.append(text.charAt(i++)); continue; }
            if (text.startsWith("<!--", i)) { int end = text.indexOf("-->", i + 4); if (end < 0) break; i = end + 3; continue; }
            int end = text.indexOf('>', i + 1); if (end < 0) { out.append(text.substring(i)); break; }
            String tag = text.substring(i + 1, end).trim().toLowerCase(Locale.ROOT).split("[\\s/]+", 2)[0];
            boolean closing = tag.isEmpty() && text.charAt(i + 1) == '/';
            if (closing) tag = text.substring(i + 2, end).trim().toLowerCase(Locale.ROOT).split("[\\s/]+", 2)[0];
            i = end + 1;
            if (!closing && Arrays.asList("script", "style", "head").contains(tag)) {
                int close = -1; String target = "</" + tag;
                for (int p = i; p <= text.length() - target.length(); p++) if (text.regionMatches(true, p, target, 0, target.length())) { close = p; break; }
                if (close < 0) break; int finish = text.indexOf('>', close); if (finish < 0) break; i = finish + 1; continue;
            }
            if (tag.equals("li") && !closing) out.append("\n• ");
            else if (Arrays.asList("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "br", "tr", "section", "blockquote", "li").contains(tag)) out.append('\n');
            else if (closing && (tag.equals("td") || tag.equals("th"))) out.append(" │ ");
        }
        return StringEscapeUtils.unescapeHtml4(out.toString()).replace('\u00a0', ' ').replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }
    public static final class X {
        public final String name;
        public final Map<String, String> attrs = new HashMap<>();
        public final List<Object> content = new ArrayList<>();
        X(String name) { this.name = name; }
        public String attr(String name) { return attrs.getOrDefault(name, ""); }
        public List<X> children() { ArrayList<X> out = new ArrayList<>(); for (Object o : content) if (o instanceof X) out.add((X) o); return out; }
        public List<X> all(String name) { ArrayList<X> out = new ArrayList<>(); collect(this, name, out); return out; }
        private static void collect(X x, String name, List<X> out) { if (x.name.equals(name)) out.add(x); for (Object child : x.content) if (child instanceof X) collect((X) child, name, out); }
        public String text() { StringBuilder out = new StringBuilder(); append(this, out); return out.toString(); }
        private static void append(X x, StringBuilder out) { for (Object child : x.content) { if (child instanceof X) append((X) child, out); else out.append(child); } }
    }
    private static String local(String name) { int colon = name.indexOf(':'); return colon < 0 ? name : name.substring(colon + 1); }
    public static X xml(byte[] data) throws Exception {
        String encoding = null;
        if (!(data.length >= 2 && ((data[0] & 255) == 255 || (data[0] & 255) == 254))) {
            Matcher declaration = Pattern.compile("encoding\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
                    .matcher(new String(data, 0, Math.min(256, data.length), StandardCharsets.ISO_8859_1));
            encoding = declaration.find() ? declaration.group(1) : "UTF-8";
        }
        String source = decode(data, encoding).text;
        // Check decoded XML, including UTF-16: fail closed independently of parser feature support.
        if (Pattern.compile("<!\\s*(DOCTYPE|ENTITY)", Pattern.CASE_INSENSITIVE).matcher(source).find()) throw new IOException("DTD/entities are not allowed");
        SAXParserFactory factory = SAXParserFactory.newInstance(); factory.setNamespaceAware(true);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        for (String feature : new String[]{"http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities", "http://apache.org/xml/features/nonvalidating/load-external-dtd"})
            try { reader.setFeature(feature, false); } catch (SAXException ignored) { } // Decoded precheck + rejecting resolver remain mandatory.
        reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entity blocked"); });
        ArrayDeque<X> stack = new ArrayDeque<>(); X[] root = new X[1]; int[] nodes = {0}, chars = {0};
        reader.setContentHandler(new DefaultHandler() {
            public void startElement(String uri, String name, String qName, Attributes attrs) throws SAXException {
                try { check(); } catch (IOException e) { throw new SAXException(e); }
                if (++nodes[0] > 100000 || stack.size() >= 64 || attrs.getLength() > 128) throw new SAXException("XML complexity limit exceeded");
                X node = new X(name.isEmpty() ? local(qName) : name);
                for (int i = 0; i < attrs.getLength(); i++) {
                    String attr = attrs.getLocalName(i).isEmpty() ? local(attrs.getQName(i)) : attrs.getLocalName(i);
                    node.attrs.put(attr, attrs.getValue(i));
                    node.attrs.put(attrs.getURI(i) + "|" + attr, attrs.getValue(i));
                }
                if (stack.isEmpty()) root[0] = node; else stack.peek().content.add(node);
                stack.push(node);
            }
            public void endElement(String uri, String name, String qName) { stack.pop(); }
            public void characters(char[] data, int start, int length) throws SAXException {
                chars[0] += length; if (chars[0] > DocumentReader.MAX_TEXT) throw new SAXException("XML text limit exceeded");
                if (stack.isEmpty()) return;
                List<Object> contents = stack.peek().content;
                if (contents.isEmpty() || !(contents.get(contents.size() - 1) instanceof StringBuilder)) contents.add(new StringBuilder());
                ((StringBuilder) contents.get(contents.size() - 1)).append(data, start, length);
            }
        });
        reader.setErrorHandler(new DefaultHandler() { public void error(SAXParseException e) throws SAXException { throw e; } public void fatalError(SAXParseException e) throws SAXException { throw e; } });
        reader.parse(new InputSource(new StringReader(source)));
        if (root[0] == null) throw new IOException("Empty XML");
        return root[0];
    }
    public static String rtf(byte[] data) throws IOException {
        String source = new String(data, StandardCharsets.ISO_8859_1);
        Matcher cp = Pattern.compile("\\\\ansicpg(\\d+)").matcher(source);
        Charset charset;
        try { charset = Charset.forName(cp.find() ? "windows-" + cp.group(1) : "windows-1252"); } catch (Exception e) { charset = Charset.forName("windows-1252"); }
        Set<String> skipWords = new HashSet<>(Arrays.asList(("fonttbl colortbl stylesheet info pict header footer headerl headerr headerf footerr footerf footnote themedata colorschememapping latentstyles datastore xmlnstbl listtable listoverridetable rsidtbl generator object fldinst bkmkstart bkmkend revtbl pgptbl private template filetbl nonshppict falt panose").split(" ")));
        StringBuilder out = new StringBuilder(); ArrayDeque<int[]> stack = new ArrayDeque<>();
        boolean skip = false; int uc = 1, pending = 0;
        for (int i = 0; i < source.length();) {
            if ((i & 1023) == 0) check();
            char c = source.charAt(i++);
            if (c == '{') { if (stack.size() > 64) throw new IOException("RTF nesting limit"); stack.push(new int[]{skip ? 1 : 0, uc}); }
            else if (c == '}') { if (!stack.isEmpty()) { int[] state = stack.pop(); skip = state[0] != 0; uc = state[1]; } }
            else if (c == '\\' && i < source.length()) {
                char next = source.charAt(i++);
                if (next == '\'') {
                    if (i + 2 > source.length()) break;
                    int value; try { value = Integer.parseInt(source.substring(i, i + 2), 16); } catch (Exception e) { value = 63; }
                    i += 2; if (!skip) { if (pending > 0) pending--; else out.append(new String(new byte[]{(byte) value}, charset)); }
                } else if (Character.isLetter(next)) {
                    int begin = i - 1; while (i < source.length() && Character.isLetter(source.charAt(i))) i++;
                    String word = source.substring(begin, i); int number = 0, numberBegin = i;
                    if (i < source.length() && source.charAt(i) == '-') i++;
                    while (i < source.length() && Character.isDigit(source.charAt(i))) i++;
                    if (i > numberBegin) try { number = Integer.parseInt(source.substring(numberBegin, i)); } catch (Exception ignored) { }
                    if (i < source.length() && source.charAt(i) == ' ') i++;
                    if (word.equals("bin")) { if (number < 0 || number > source.length() - i) throw new IOException("Invalid RTF binary length"); i += number; continue; }
                    if (skip) continue;
                    if (skipWords.contains(word)) skip = true;
                    else if (word.equals("uc")) uc = Math.max(0, Math.min(10, number));
                    else if (word.equals("u")) { out.append((char) (number & 65535)); pending = uc; }
                    else if (Arrays.asList("par", "sect", "page", "line").contains(word)) out.append('\n');
                    else if (word.equals("tab")) out.append('\t');
                    else if (word.equals("emdash")) out.append('—'); else if (word.equals("endash")) out.append('–'); else if (word.equals("bullet")) out.append('•');
                } else if (!skip) {
                    if (next == '*') skip = true;
                    else if (pending > 0) pending--;
                    else if (next == '~') out.append('\u00a0');
                    else if (next == '_' ) out.append('‑');
                    else if (next == '\\' || next == '{' || next == '}') out.append(next);
                }
            } else if (!skip && c != '\r' && c != '\n') { if (pending > 0) pending--; else if (c < 128) out.append(c); else out.append(new String(new byte[]{(byte) c}, charset)); }
            if (out.length() > DocumentReader.MAX_TEXT) throw new IOException("RTF output limit");
        }
        return out.toString().trim();
    }
}
