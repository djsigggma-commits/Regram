package app.regram.documents;

import java.io.*;
import java.util.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;

/** Generates offline markup. The Android WebView separately denies all requests/navigation. */
public final class DocumentWeb {
    public static final String POLICY = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src 'none'; media-src 'none'; frame-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
    private DocumentWeb() { }
    private static void checkTree(org.commonmark.node.Node root) throws IOException {
        org.commonmark.node.Node node = root; int depth = 0, count = 0;
        while (node != null) {
            DocumentText.check(); if (++count > 100000 || depth > 64) throw new IOException("Markdown tree limit");
            if (node.getFirstChild() != null) { node = node.getFirstChild(); depth++; continue; }
            while (node != root && node.getNext() == null) { node = node.getParent(); depth--; }
            if (node == root) break;
            node = node.getNext();
        }
    }
    public static String read(File file, String name, int theme) throws Exception {
        byte[] bytes; try (InputStream in = new FileInputStream(file)) { bytes = DocumentText.read(in, DocumentReader.MAX_WEB, false); }
        String source = DocumentText.decode(bytes, null).text;
        String ext = DocumentFormat.extension(name), body = source;
        String bg = theme == 1 ? "#fafaf7" : theme == 2 ? "#f4ecd8" : "#09090d";
        String fg = theme == 1 ? "#1c1c20" : theme == 2 ? "#433422" : "#e8e8ee";
        if (DocumentFormat.markdown(ext)) {
            List<org.commonmark.Extension> extensions = Arrays.asList(TablesExtension.create(), StrikethroughExtension.create());
            int lines = 0;
            for (int offset = 0; offset <= source.length();) {
                int end = source.indexOf('\n', offset); if (end < 0) end = source.length();
                if (++lines > 100000 || end - offset > 65536) throw new IOException("Markdown complexity limit");
                String line = source.substring(offset, end); offset = end + 1;
                int quotes = 0;
                for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '>') { if (++quotes > 64) throw new IOException("Markdown nesting limit"); } else if (c != ' ' && c != '\t') break; }
            }
            org.commonmark.node.Node parsed = Parser.builder().extensions(extensions).build().parse(source);
            checkTree(parsed);
            body = HtmlRenderer.builder().extensions(extensions).escapeHtml(true)
                    .attributeProviderFactory(context -> (node, tag, attrs) -> {
                        if (attrs.containsKey("href") && !attrs.get("href").trim().toLowerCase(Locale.ROOT).matches("^(https?://|mailto:|tg://|#).*")) attrs.remove("href");
                        if (tag.equals("img")) attrs.remove("src");
                    }).build()
                    .render(parsed);
            body = "<style>body{background:" + bg + ";color:" + fg + ";font:16px/1.65 sans-serif;padding:16px;overflow-wrap:anywhere}pre{overflow:auto}table{border-collapse:collapse}td,th{border:1px solid #888;padding:6px}img{max-width:100%}</style>" + body;
        } else if (ext.equals("svg")) {
            // Reject DTD before it can reach WebView, including encoded declarations.
            DocumentText.xml(bytes);
            body = "<style>svg{max-width:100%;height:auto}</style>" + source.replaceAll("(?s)<\\?xml.*?\\?>", "");
        }
        if (body.length() > DocumentReader.MAX_TEXT) throw new IOException("Generated markup limit exceeded");
        return "<!doctype html><html><head><meta charset=\"UTF-8\"><meta http-equiv=\"Content-Security-Policy\" content=\""
                + POLICY + "\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head><body>" + body + "</body></html>";
    }
}
