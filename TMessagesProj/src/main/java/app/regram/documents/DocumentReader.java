package app.regram.documents;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import org.json.*;
import org.apache.commons.compress.archivers.zip.*;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import static app.regram.documents.DocumentText.*;

/** Read-only parsers adapted from the inspectable Doc Viewer plugin, with cumulative budgets. */
public final class DocumentReader {
    public static final int MAX_TEXT = 12 * 1024 * 1024, MAX_WEB = 4 * 1024 * 1024, PAGE_CHARS = 30000;
    public static final long MAX_UNPACKED = 64L * 1024 * 1024, MAX_CONTAINER = 128L * 1024 * 1024;
    public static final int MAX_ROWS = 3000, MAX_COLS = 60, MAX_ENTRIES = 20000;
    public static final class Options {
        public boolean wrap = true, prettyJson = true, csvTable = true;
    }
    public static final class Result {
        public final List<String> pages;
        public final int font;
        public final boolean wrap, raw;
        public final String encoding, separator;
        Result(List<String> pages, int font, boolean wrap, boolean raw, String encoding, String separator) {
            this.pages = Collections.unmodifiableList(pages.isEmpty() ? Collections.singletonList("") : pages);
            this.font = font; this.wrap = wrap; this.raw = raw; this.encoding = encoding; this.separator = separator;
        }
    }
    private final List<String> output = new ArrayList<>();
    private int outputChars;
    private DocumentReader() { }
    private void add(String text) throws IOException {
        check(); if (text.length() > MAX_TEXT - outputChars) throw new IOException("Combined document text exceeds 12 MiB");
        outputChars += text.length(); output.addAll(pages(text, PAGE_CHARS));
    }
    public static Result load(File file, String format, String name, Options options, String encoding) throws Exception {
        check(); DocumentReader reader = new DocumentReader(); int font = 0; boolean wrap = true, raw = false; String used = null, separator = "\n\n";
        String ext = DocumentFormat.extension(name);
        switch (format) {
            case "text": case "json": case "csv": case "ipynb": {
                byte[] bytes; try (InputStream in = new FileInputStream(file)) { bytes = read(in, MAX_TEXT, true); }
                boolean cut = bytes.length > MAX_TEXT;
                if (cut) bytes = Arrays.copyOf(bytes, MAX_TEXT);
                Decoded decoded = decode(bytes, encoding); String text = decoded.text; used = decoded.encoding;
                raw = true; font = 2; wrap = options.wrap; separator = "";
                if (format.equals("json") && options.prettyJson && text.length() <= 3 * 1024 * 1024) {
                    try { jsonDepth(text); Object json = new JSONTokener(text).nextValue();
                        if (json instanceof JSONObject) text = ((JSONObject) json).toString(2);
                        else if (json instanceof JSONArray) text = ((JSONArray) json).toString(2);
                    } catch (Exception ignored) { } // Invalid JSON is still viewable as source.
                } else if (format.equals("csv") && options.csvTable) { text = table(csv(text, ext), true); wrap = false; }
                else if (format.equals("ipynb")) try { text = notebook(text); } catch (Exception ignored) { }
                else if (Arrays.asList("txt", "text", "nfo", "srt", "vtt", "lrc", "rst", "adoc", "org").contains(ext)) font = 0;
                if (cut) text += "\n\n[Truncated: first 12 MiB / Обрезано: первые 12 МиБ]";
                // The truncation notice must not itself exceed the output budget.
                if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT - 100) + "\n[Truncated / Обрезано]";
                reader.add(text); break;
            }
            case "rtf": {
                byte[] bytes; try (InputStream in = new FileInputStream(file)) { bytes = read(in, MAX_TEXT, false); }
                reader.add(rtf(bytes)); separator = ""; break;
            }
            case "eml": {
                byte[] bytes; try (InputStream in = new FileInputStream(file)) { bytes = read(in, MAX_TEXT, false); }
                reader.add(DocumentMail.read(bytes)); separator = ""; break;
            }
            case "fb2": {
                X root; try (InputStream in = new FileInputStream(file)) { root = xml(read(in, MAX_TEXT, false)); }
                StringBuilder body = new StringBuilder();
                for (X title : root.all("book-title")) { body.append(title.text()).append("\n\n"); break; }
                for (X node : root.children()) if (node.name.equals("body")) fb2(node, body, 0);
                reader.add(body.toString()); font = 1; separator = ""; break;
            }
            case "archive": reader.add(archive(file)); font = 2; wrap = false; separator = ""; break;
            case "docx": case "xlsx": case "pptx": case "odf": case "epub": {
                try (Package zip = new Package(file)) {
                    switch (format) {
                        case "docx": { StringBuilder body = new StringBuilder(); word(zip.xml("word/document.xml"), body); reader.add(body.toString()); separator = ""; break; }
                        case "xlsx": reader.xlsx(zip); font = 2; wrap = false; break;
                        case "pptx": reader.slides(zip); break;
                        case "odf": {
                            X root = zip.xml("content.xml"); List<X> sheets = root.all("spreadsheet");
                            if (!sheets.isEmpty()) { for (X sheet : sheets.get(0).children()) if (sheet.name.equals("table")) reader.add("── " + sheet.attr("name") + " ──\n\n" + table(odfRows(sheet), true)); font = 2; wrap = false; }
                            else if (!root.all("presentation").isEmpty()) { int i = 0; for (X page : root.all("page")) { StringBuilder body = new StringBuilder(); odf(page, body); reader.add("── Slide / Слайд " + (++i) + " ──\n\n" + body); } }
                            else { StringBuilder body = new StringBuilder(); odf(root, body); reader.add(body.toString()); separator = ""; }
                            break;
                        }
                        case "epub": reader.epub(zip); font = 1; break;
                    }
                }
                break;
            }
            default: throw new IOException("Unsupported document format: " + format);
        }
        return new Result(reader.output, font, wrap, raw, used, separator);
    }
    static void jsonDepth(String text) throws IOException {
        boolean string = false, escape = false; int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            if ((i & 8191) == 0) check(); char c = text.charAt(i);
            if (string) { if (escape) escape = false; else if (c == '\\') escape = true; else if (c == '"') string = false; }
            else if (c == '"') string = true;
            else if (c == '{' || c == '[') { if (++depth > 64) throw new IOException("JSON nesting limit"); }
            else if (c == '}' || c == ']') depth--;
        }
    }
    private static ZipFile.Builder zipBuilder(File file) {
        // Zstandard JNI is intentionally not bundled. Listing ZIP metadata still works;
        // document parts accept only STORED/DEFLATED and never invoke this codec.
        return ZipFile.builder().setFile(file).setCharset(Charset.forName("IBM866"))
                .setZstdInputStreamFactory(input -> { throw new IOException("Zstandard ZIP decoding is not supported"); });
    }
    static final class Package implements AutoCloseable {
        final ZipFile zip;
        final Set<String> names = new LinkedHashSet<>();
        long readBytes;
        Package(File file) throws IOException {
            if (file.length() > MAX_CONTAINER) throw new IOException("Container exceeds 128 MiB");
            zip = zipBuilder(file).get();
            try {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) { check(); ZipArchiveEntry entry = entries.nextElement();
                    if (names.size() >= MAX_ENTRIES || !names.add(entry.getName())) throw new IOException("Too many or duplicate ZIP entries"); }
            } catch (Exception e) { zip.close(); throw e; }
        }
        byte[] bytes(String name) throws IOException {
            check(); ZipArchiveEntry entry = zip.getEntry(name);
            if (entry == null) throw new FileNotFoundException("Missing document part: " + name);
            if (entry.getGeneralPurposeBit().usesEncryption()) throw new IOException("Encrypted document part");
            if (entry.getMethod() != 0 && entry.getMethod() != 8) throw new IOException("Unsupported document ZIP compression");
            if (entry.getSize() > MAX_TEXT) throw new IOException("Document part exceeds 12 MiB");
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = read(in, (int) Math.min(MAX_TEXT, MAX_UNPACKED - readBytes), false);
                readBytes += bytes.length;
                java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(bytes);
                if (entry.getCrc() >= 0 && crc.getValue() != entry.getCrc()) throw new IOException("Document ZIP CRC mismatch");
                return bytes;
            }
        }
        X xml(String name) throws Exception { return DocumentText.xml(bytes(name)); }
        public void close() throws IOException { zip.close(); }
    }
    static String resolve(String base, String target) throws IOException {
        if (target.contains(":" ) || target.startsWith("//") || target.indexOf('\\') >= 0) throw new IOException("External document relationship blocked");
        String path = target.startsWith("/") ? target.substring(1) : base + target;
        ArrayDeque<String> pieces = new ArrayDeque<>();
        for (String piece : path.split("/")) {
            if (piece.equals("..")) { if (pieces.isEmpty()) throw new IOException("Relationship escapes package"); pieces.removeLast(); }
            else if (!piece.isEmpty() && !piece.equals(".")) pieces.addLast(piece);
        }
        return String.join("/", pieces);
    }
    private static String relationId(X node) {
        String id = node.attr("http://schemas.openxmlformats.org/officeDocument/2006/relationships|id");
        return id.isEmpty() ? node.attr("id") : id;
    }
    private static Map<String, String> relationships(Package zip, String part, String base) throws Exception {
        Map<String, String> out = new HashMap<>(); if (!zip.names.contains(part)) return out;
        for (X item : zip.xml(part).children()) if (!item.attr("TargetMode").equalsIgnoreCase("External"))
            out.put(item.attr("Id"), resolve(base, item.attr("Target")));
        return out;
    }
    private static String inline(X node) throws IOException {
        StringBuilder out = new StringBuilder();
        for (Object child : node.content) {
            if (!(child instanceof X)) out.append(child);
            else {
                X x = (X) child;
                switch (x.name) {
                    case "tab": out.append('\t'); break;
                    case "br": case "cr": case "line-break": out.append('\n'); break;
                    case "s": out.append(String.join("", Collections.nCopies(number(x.attr("c"), 1, 1, 1000), " "))); break;
                    case "drawing": case "pict": case "image": out.append("[image / изображение]"); break;
                    case "note": break;
                    default: out.append(inline(x));
                }
            }
            if (out.length() > MAX_TEXT) throw new IOException("Inline text expansion limit");
        }
        return out.toString();
    }
    private static void block(StringBuilder out, String text) throws IOException {
        if (out.length() + text.length() > MAX_TEXT) throw new IOException("Document text limit exceeded");
        if (!text.trim().isEmpty()) { if (out.length() > 0) out.append("\n\n"); out.append(text); }
    }
    private static void word(X node, StringBuilder out) throws IOException {
        check();
        if (node.name.equals("p")) {
            String text = inline(node);
            List<X> style = node.all("pStyle");
            if (!style.isEmpty() && style.get(0).attr("val").toLowerCase(Locale.ROOT).startsWith("heading")) text = "# " + text;
            else if (!node.all("numPr").isEmpty()) text = "• " + text;
            block(out, text);
        } else if (node.name.equals("tbl")) {
            List<List<String>> rows = new ArrayList<>();
            for (X row : node.children()) if (row.name.equals("tr") && rows.size() < MAX_ROWS) {
                List<String> cells = new ArrayList<>(); for (X cell : row.children()) if (cell.name.equals("tc") && cells.size() < MAX_COLS) cells.add(inline(cell)); rows.add(cells);
            }
            block(out, table(rows, false));
        } else for (X child : node.children()) word(child, out);
    }
    private void xlsx(Package zip) throws Exception {
        List<String> shared = new ArrayList<>();
        if (zip.names.contains("xl/sharedStrings.xml")) for (X si : zip.xml("xl/sharedStrings.xml").children()) shared.add(si.text());
        Set<Integer> dates = new HashSet<>();
        if (zip.names.contains("xl/styles.xml")) {
            X styles = zip.xml("xl/styles.xml"); Map<String, String> formats = new HashMap<>();
            for (X format : styles.all("numFmt")) formats.put(format.attr("numFmtId"), format.attr("formatCode"));
            for (X xfs : styles.all("cellXfs")) { int i = 0; for (X xf : xfs.children()) {
                int id = number(xf.attr("numFmtId"), 0, 0, 10000);
                String custom = formats.getOrDefault(String.valueOf(id), "").replaceAll("\"[^\"]*\"|\\\\.|\\[[^]]*]", "");
                if ((id >= 14 && id <= 22) || (id >= 27 && id <= 36) || (id >= 45 && id <= 47) || (id >= 50 && id <= 58) || custom.matches("(?i).*[ymdhs].*")) dates.add(i);
                i++;
            } }
        }
        Map<String, String> rels = relationships(zip, "xl/_rels/workbook.xml.rels", "xl/");
        Map<String, String> sheets = new LinkedHashMap<>(); boolean epoch1904 = false;
        if (zip.names.contains("xl/workbook.xml")) {
            X workbook = zip.xml("xl/workbook.xml");
            for (X pr : workbook.all("workbookPr")) epoch1904 = pr.attr("date1904").equals("1") || pr.attr("date1904").equals("true");
            for (X sheet : workbook.all("sheet")) { String part = rels.get(relationId(sheet)); if (part != null && zip.names.contains(part)) sheets.put(part, sheet.attr("name")); }
        }
        if (sheets.isEmpty()) for (String name : sorted(zip.names, "xl/worksheets/[^/]+\\.xml")) sheets.put(name, name);
        for (Map.Entry<String, String> sheet : sheets.entrySet()) {
            List<List<String>> rows = new ArrayList<>(); boolean cut = false;
            for (X row : zip.xml(sheet.getKey()).all("row")) {
                check(); int gap = number(row.attr("r"), rows.size() + 1, 1, Integer.MAX_VALUE) - rows.size() - 1;
                if (gap > 0 && gap <= 50) for (int i = 0; i < gap && rows.size() < MAX_ROWS; i++) rows.add(Collections.emptyList());
                if (rows.size() >= MAX_ROWS) { cut = true; break; }
                List<String> cells = new ArrayList<>();
                for (X cell : row.children()) if (cell.name.equals("c")) {
                    int col = cell.attr("r").isEmpty() ? cells.size() : column(cell.attr("r"));
                    if (col < 0 || col >= MAX_COLS) continue;
                    while (cells.size() <= col) cells.add("");
                    List<X> values = cell.all("v"); String value = values.isEmpty() ? "" : values.get(0).text();
                    String type = cell.attr("t");
                    if (type.equals("inlineStr")) { List<X> inline = cell.all("is"); value = inline.isEmpty() ? "" : inline.get(0).text(); }
                    else if (type.equals("s")) { int index = number(value, -1, -1, Integer.MAX_VALUE); value = index >= 0 && index < shared.size() ? shared.get(index) : ""; }
                    else if (type.equals("b")) value = value.equals("1") ? "TRUE" : "FALSE";
                    else if (dates.contains(number(cell.attr("s"), -1, -1, Integer.MAX_VALUE)) && !value.isEmpty()) value = excelDate(value, epoch1904);
                    cells.set(col, value);
                }
                rows.add(cells);
            }
            add("── " + sheet.getValue() + " ──\n\n" + table(rows, true) + (cut ? "\n[First 3000 rows / Первые 3000 строк]" : ""));
        }
    }
    static int column(String ref) {
        long n = 0; for (int i = 0; i < ref.length(); i++) { char c = ref.charAt(i); if (c < 'A' || c > 'Z') break; n = n * 26 + c - 'A' + 1; if (n > MAX_COLS) return MAX_COLS; } return (int) n - 1;
    }
    static String excelDate(String value, boolean epoch1904) {
        try {
            double serial = Double.parseDouble(value); if (!Double.isFinite(serial) || Math.abs(serial) > 1000000) return value;
            Calendar base = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT); base.clear(); base.set(epoch1904 ? 1904 : 1899, epoch1904 ? 0 : 11, epoch1904 ? 1 : 30);
            // Excel's fictitious 1900-02-29: serials before 60 use the earlier epoch offset.
            if (!epoch1904 && serial < 60) serial++;
            long millis = base.getTimeInMillis() + Math.round(serial * 86400000);
            String format = serial == Math.floor(serial) ? "yyyy-MM-dd" : serial < 1 ? "HH:mm:ss" : "yyyy-MM-dd HH:mm";
            SimpleDateFormat date = new SimpleDateFormat(format, Locale.ROOT); date.setTimeZone(TimeZone.getTimeZone("UTC")); return date.format(new Date(millis));
        } catch (Exception e) { return value; }
    }
    private void slides(Package zip) throws Exception {
        Map<String, String> rels = relationships(zip, "ppt/_rels/presentation.xml.rels", "ppt/"); List<String> order = new ArrayList<>();
        if (zip.names.contains("ppt/presentation.xml")) for (X id : zip.xml("ppt/presentation.xml").all("sldId")) { String path = rels.get(relationId(id)); if (zip.names.contains(path)) order.add(path); }
        if (order.isEmpty()) order = sorted(zip.names, "ppt/slides/slide\\d+\\.xml");
        int index = 0; for (String name : order) { StringBuilder body = new StringBuilder(); for (X p : zip.xml(name).all("p")) block(body, inline(p)); add("── Slide / Слайд " + (++index) + " ──\n\n" + body); }
    }
    private static List<String> sorted(Set<String> names, String regex) {
        ArrayList<String> out = new ArrayList<>(); for (String name : names) if (name.matches(regex)) out.add(name);
        out.sort((a, b) -> natural(a).compareTo(natural(b))); return out;
    }
    private static String natural(String name) { Matcher m = Pattern.compile("\\d+").matcher(name); StringBuffer out = new StringBuffer(); while (m.find()) m.appendReplacement(out, String.format(Locale.ROOT, "%020d", Long.parseLong(m.group().length() > 18 ? "0" : m.group()))); m.appendTail(out); return out.toString(); }
    private static List<List<String>> odfRows(X table) throws IOException {
        List<List<String>> out = new ArrayList<>(); long characters = 0;
        for (X row : table.all("table-row")) {
            ArrayList<String> cells = new ArrayList<>();
            for (X cell : row.children()) if (cell.name.equals("table-cell") || cell.name.equals("covered-table-cell")) {
                String text = inline(cell).trim(); int repeat = number(cell.attr("number-columns-repeated"), 1, 1, MAX_COLS);
                for (int i = 0; i < repeat && cells.size() < MAX_COLS; i++) cells.add(text);
            }
            int repeat = number(row.attr("number-rows-repeated"), 1, 1, 20);
            if (cells.stream().allMatch(String::isEmpty)) repeat = 1;
            long rowChars = 0; for (String cell : cells) rowChars += cell.length();
            characters += rowChars * repeat;
            if (characters > MAX_TEXT) throw new IOException("ODF repeated-cell text limit");
            for (int i = 0; i < repeat && out.size() < MAX_ROWS; i++) out.add(cells);
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }
    private static void odf(X node, StringBuilder out) throws IOException {
        check(); if (node.name.equals("p") || node.name.equals("h")) block(out, (node.name.equals("h") ? "# " : "") + inline(node));
        else if (node.name.equals("table")) block(out, table(odfRows(node), false));
        else if (!Arrays.asList("tracked-changes", "sequence-decls", "variable-decls").contains(node.name)) for (X child : node.children()) odf(child, out);
    }
    private static void fb2(X node, StringBuilder out, int depth) throws IOException {
        check();
        if (Arrays.asList("title", "p", "subtitle", "text-author", "v").contains(node.name)) block(out, (node.name.equals("title") ? "# " : "") + node.text().trim());
        else if (Arrays.asList("body", "section", "epigraph", "cite", "poem", "stanza", "annotation").contains(node.name)) for (X child : node.children()) fb2(child, out, depth + 1);
    }
    private void epub(Package zip) throws Exception {
        List<X> roots = zip.xml("META-INF/container.xml").all("rootfile");
        if (roots.isEmpty()) throw new IOException("EPUB rootfile missing");
        String opfName = resolve("", roots.get(0).attr("full-path")); X opf = zip.xml(opfName);
        String base = opfName.contains("/") ? opfName.substring(0, opfName.lastIndexOf('/') + 1) : "";
        Map<String, String> items = new HashMap<>(); for (X item : opf.all("item")) items.put(item.attr("id"), item.attr("href"));
        for (X item : opf.all("itemref")) {
            String href = items.get(item.attr("idref")); if (href == null) continue;
            String path = resolve(base, URLDecoder.decode(href.split("#", 2)[0].replace("+", "%2B"), "UTF-8"));
            if (zip.names.contains(path)) { String text = htmlText(decode(zip.bytes(path), null).text); if (!text.isEmpty()) add(text); }
        }
        if (output.isEmpty()) throw new IOException("EPUB contains no readable text");
    }
    public static List<List<String>> csv(String source, String ext) throws IOException {
        char delimiter = ext.equals("tsv") || ext.equals("tab") ? '\t' : ',';
        if (!ext.equals("tsv") && !ext.equals("tab")) {
            String head = source.split("\n", 2)[0]; int best = -1;
            for (char c : new char[]{',', ';', '\t', '|'}) { int count = 0; for (int i = 0; i < head.length(); i++) if (head.charAt(i) == c) count++; if (count > best) { best = count; delimiter = c; } }
        }
        ArrayList<List<String>> rows = new ArrayList<>(); ArrayList<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i <= source.length(); i++) {
            if ((i & 4095) == 0) check(); char c = i == source.length() ? '\n' : source.charAt(i);
            if (c == '"') { if (quoted && i + 1 < source.length() && source.charAt(i + 1) == '"') { cell.append('"'); i++; } else if (quoted || cell.length() == 0) quoted = !quoted; else cell.append(c); }
            else if (quoted) cell.append(c);
            else if (c == delimiter || c == '\n') {
                if (row.size() < MAX_COLS) row.add(cell.toString()); cell.setLength(0);
                if (c == '\n') { rows.add(row); row = new ArrayList<>(); if (rows.size() >= MAX_ROWS) break; }
            } else cell.append(c);
        }
        if (quoted) throw new IOException("Unclosed CSV quote"); return rows;
    }
    static String table(List<List<String>> rows, boolean pad) throws IOException {
        int[] widths = new int[MAX_COLS]; ArrayList<List<String>> clean = new ArrayList<>(); long cleanedChars = 0;
        for (List<String> row : rows) {
            check(); ArrayList<String> cells = new ArrayList<>();
            for (int i = 0; i < Math.min(MAX_COLS, row.size()); i++) {
                String value = row.get(i).replaceAll("\\s+", " ").trim(); if (pad && value.length() > 40) value = value.substring(0, 39) + "…";
                cleanedChars += value.length(); if (cleanedChars > MAX_TEXT) throw new IOException("Table text limit");
                widths[i] = Math.max(widths[i], value.length()); cells.add(value);
            }
            clean.add(cells);
        }
        StringBuilder out = new StringBuilder();
        for (List<String> row : clean) {
            if (out.length() > 0) out.append('\n');
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(" │ "); String cell = row.get(i); out.append(cell);
                if (pad) for (int j = cell.length(); j < widths[i]; j++) out.append(' ');
                if (out.length() > MAX_TEXT) throw new IOException("Table output limit");
            }
        }
        if (rows.size() >= MAX_ROWS) out.append("\n[First 3000 rows / Первые 3000 строк]");
        return out.toString();
    }
    private static String joined(Object value) {
        if (value instanceof JSONArray) { StringBuilder out = new StringBuilder(); JSONArray array = (JSONArray) value; for (int i = 0; i < array.length(); i++) out.append(array.optString(i)); return out.toString(); }
        return value == null || value == JSONObject.NULL ? "" : value.toString();
    }
    private static String notebook(String source) throws Exception {
        jsonDepth(source); JSONObject notebook = new JSONObject(source); JSONArray cells = notebook.optJSONArray("cells");
        if (cells == null) { cells = new JSONArray(); JSONArray sheets = notebook.optJSONArray("worksheets"); if (sheets != null) for (int i = 0; i < sheets.length(); i++) { JSONArray part = sheets.getJSONObject(i).optJSONArray("cells"); if (part != null) for (int j = 0; j < part.length(); j++) cells.put(part.get(j)); } }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < cells.length(); i++) {
            check(); JSONObject cell = cells.getJSONObject(i); block(text, joined(cell.opt(cell.has("source") ? "source" : "input")));
            JSONArray outputs = cell.optJSONArray("outputs");
            if (outputs != null) for (int j = 0; j < outputs.length(); j++) {
                JSONObject out = outputs.getJSONObject(j); Object content = out.opt("text");
                if (content == null && out.has("traceback")) content = out.opt("traceback");
                if (content == null && out.optJSONObject("data") != null) content = out.getJSONObject("data").opt("text/plain");
                if (content != null) block(text, "Out:\n" + joined(content).replaceAll("\u001b\\[[0-9;]*[A-Za-z]", ""));
            }
        }
        return text.toString();
    }
    private static String archive(File file) throws Exception {
        if (file.length() > MAX_CONTAINER) throw new IOException("Archive exceeds 128 MiB");
        StringBuilder out = new StringBuilder(); long total = 0; int count = 0;
        byte[] signature = new byte[4]; try (InputStream in = new FileInputStream(file)) { in.read(signature); }
        if (signature[0] == 'P' && signature[1] == 'K') {
            try (ZipFile zip = zipBuilder(file).get()) {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements() && count < MAX_ENTRIES) {
                    check(); ZipArchiveEntry entry = entries.nextElement(); count++;
                    total = Math.addExact(total, Math.max(0, entry.getSize()));
                    out.append(entry.getGeneralPurposeBit().usesEncryption() ? "[password / пароль] " : "")
                            .append(entry.getSize()).append(" B  ").append(entry.getName()).append('\n');
                    if (out.length() > MAX_TEXT) throw new IOException("Archive listing limit");
                }
                if (entries.hasMoreElements()) out.append("[First 20000 entries / Первые 20000 записей]\n");
            }
        } else {
            String name = file.getName().toLowerCase(Locale.ROOT);
            try (InputStream raw = new BufferedInputStream(new FileInputStream(file))) {
                InputStream decompressed = raw;
                if (name.endsWith(".gz") || name.endsWith(".tgz")) decompressed = new GZIPInputStream(raw);
                else if (name.endsWith(".bz2") || name.endsWith(".tbz2")) decompressed = new BZip2CompressorInputStream(raw);
                else if (name.endsWith(".xz") || name.endsWith(".txz")) decompressed = new XZCompressorInputStream(raw, false, 16384);
                try (TarArchiveInputStream tar = new TarArchiveInputStream(new LimitedStream(decompressed, MAX_UNPACKED))) {
                    TarArchiveEntry entry;
                    while (count < MAX_ENTRIES && (entry = tar.getNextEntry()) != null) {
                        check(); count++; total = Math.addExact(total, Math.max(0, entry.getSize()));
                        out.append(entry.getSize()).append(" B  ").append(entry.getName()).append(entry.isDirectory() ? "/" : "").append('\n');
                        if (out.length() > MAX_TEXT) throw new IOException("Archive listing limit");
                    }
                    if (count == MAX_ENTRIES) out.append("[Listing limit / Предел списка]\n");
                }
            }
        }
        return "Entries / Записей: " + count + " · " + total + " B\n\n" + out;
    }
    static final class LimitedStream extends FilterInputStream {
        private long remaining;
        LimitedStream(InputStream in, long limit) { super(in); remaining = limit; }
        @Override public int read() throws IOException { byte[] one = new byte[1]; return read(one, 0, 1) == -1 ? -1 : one[0] & 255; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            check(); if (length == 0) return 0;
            if (remaining <= 0) throw new IOException("Archive expansion exceeds 64 MiB");
            int n = in.read(bytes, offset, (int) Math.min(length, remaining)); if (n > 0) remaining -= n; return n;
        }
        @Override public long skip(long length) throws IOException {
            byte[] block = new byte[8192]; long skipped = 0;
            while (skipped < length) { int n = read(block, 0, (int) Math.min(block.length, length - skipped)); if (n == -1) break; skipped += n; }
            return skipped;
        }
    }
    static int number(String value, int fallback, int min, int max) { try { return Math.max(min, Math.min(max, Integer.parseInt(value))); } catch (Exception e) { return fallback; } }
}
