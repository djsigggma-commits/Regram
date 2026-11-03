package app.regram.documents;

import java.io.*;
import java.util.*;

/** Format policy adapted from Doc Viewer 2.0.0 by Daxo-Developer / @Daxo_OS. */
public final class DocumentFormat {
    private static final Map<String, String> EXT = new HashMap<>(), MIME = new HashMap<>();
    static {
        register("pdf", "pdf");
        register("image", "jpg jpeg jpe png webp bmp gif heic heif avif ico dib");
        register("web", "html htm xhtml svg md markdown mdown");
        register("csv", "csv tsv tab"); register("json", "json geojson"); register("ipynb", "ipynb");
        register("docx", "docx docm dotx dotm"); register("xlsx", "xlsx xlsm xltx xltm");
        register("pptx", "pptx pptm ppsx potx"); register("odf", "odt ott ods ots odp otp");
        register("rtf", "rtf"); register("eml", "eml"); register("epub", "epub"); register("fb2", "fb2");
        register("archive", "zip jar aar tar tgz tbz2 txz");
        register("text", "txt text log jsonl ndjson xml plist yaml yml toml ini cfg conf env properties gradle cmake "
                + "srt vtt ass ssa lrc tex bib rst adoc org nfo diff patch vcf ics gpx kml tcx opml rss atom "
                + "xsd xsl xslt wsdl plugin py pyw pyi js mjs cjs jsx ts tsx java kt kts scala groovy c h cpp "
                + "cc cxx hpp hh cs go rs swift m mm php rb pl lua r dart sh bash zsh fish bat cmd ps1 psm1 "
                + "vbs sql css scss sass less vue svelte gql graphql proto tf hcl asm s v sv vhd vhdl hs ml "
                + "clj ex exs erl jl nim zig dockerfile makefile mk ninja gitignore gitattributes editorconfig "
                + "lock sum mod pro pri qml smali csproj sln vcxproj xaml aidl pem crt csr pub asc reg inf desktop service ahk au3");
        String[][] types = {{"application/pdf", "pdf"}, {"image/svg+xml", "web"}, {"text/html", "web"},
                {"text/markdown", "web"}, {"text/csv", "csv"}, {"text/tab-separated-values", "csv"},
                {"application/json", "json"}, {"application/rtf", "rtf"}, {"text/rtf", "rtf"},
                {"application/epub+zip", "epub"}, {"application/x-fictionbook+xml", "fb2"},
                {"message/rfc822", "eml"}, {"application/zip", "archive"}, {"application/x-tar", "archive"},
                {"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"},
                {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"},
                {"application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"},
                {"application/vnd.oasis.opendocument.text", "odf"}, {"application/vnd.oasis.opendocument.spreadsheet", "odf"},
                {"application/vnd.oasis.opendocument.presentation", "odf"}};
        for (String[] pair : types) MIME.put(pair[0], pair[1]);
    }
    private DocumentFormat() { }
    private static void register(String format, String extensions) { for (String ext : extensions.split(" ")) EXT.put(ext, format); }
    public static String extension(String name) {
        String base = new File(name == null ? "" : name).getName().toLowerCase(Locale.ROOT);
        int dot = base.lastIndexOf('.'); return dot < 0 ? "" : base.substring(dot + 1);
    }
    public static boolean markdown(String ext) { return ext.equals("md") || ext.equals("markdown") || ext.equals("mdown"); }
    public static String detect(String name, String mime) {
        String low = new File(name == null ? "" : name).getName().toLowerCase(Locale.ROOT);
        String ext = extension(low);
        // Do not steal theme/import/installation paths, even if the file looks textual.
        if (Arrays.asList("attheme", "extera", "apk", "aab", "dex", "so").contains(ext)) return null;
        String ml = mime == null ? "" : mime.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (low.endsWith(".tar.gz") || low.endsWith(".tar.bz2") || low.endsWith(".tar.xz")) return "archive";
        if (EXT.containsKey(ext)) return EXT.get(ext);
        if (MIME.containsKey(ml)) return MIME.get(ml);
        if (ml.startsWith("image/")) return "image";
        String base = low.replaceFirst("^\\.", "").split("\\.", 2)[0];
        if ((low.startsWith(".") && low.indexOf('.', 1) < 0) || (low.indexOf('.') < 0 &&
                Arrays.asList("readme", "license", "licence", "changelog", "makefile", "dockerfile", "gemfile", "procfile",
                        "authors", "contributors", "todo", "notice", "copying", "vagrantfile", "jenkinsfile", "cmakelists", "requirements").contains(base))) return "text";
        if (ext.isEmpty() || ml.isEmpty() || ml.equals("application/octet-stream") || ml.startsWith("text/")
                || ml.matches(".*(json|xml|javascript|yaml|x-sh|x-sql|toml|x-httpd-php)$")) return "sniff";
        return null;
    }
    public static String setting(String format) {
        switch (format) {
            case "docx": case "xlsx": case "pptx": case "odf": case "rtf": case "eml": return "docs";
            case "epub": case "fb2": return "books";
            case "text": case "json": case "csv": case "ipynb": case "sniff": return "text";
            default: return format;
        }
    }
    public static boolean textual(File file) throws IOException {
        byte[] head = new byte[8192]; int n;
        try (InputStream in = new FileInputStream(file)) { n = in.read(head); }
        if (n <= 0) return true;
        if (n >= 2 && ((head[0] == (byte) 0xff && head[1] == (byte) 0xfe) || (head[0] == (byte) 0xfe && head[1] == (byte) 0xff))) return true;
        int controls = 0;
        for (int i = 0; i < n; i++) { int b = head[i] & 255; if (b == 0) return false; if (b < 9 || (b > 13 && b < 32 && b != 27)) controls++; }
        return controls * 50 < n;
    }
}
