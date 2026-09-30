package app.regram.utils;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Распознавание текстовых и кодовых файлов, которые стоит открывать не «скачать и отдать
 * стороннему приложению», а внутренним Instant View с подсветкой синтаксиса.
 *
 * exteraGram: {@code com/exteragram/messenger/utils/MarkdownUtils.java} (12.9.0), перенесён целиком.
 * Потребитель — {@code org.telegram.ui.Components.MarkdownParser}: {@code isMarkdown} получает
 * третий дизъюнкт, а {@code fromMarkdown} — развилку «markdown или преформат».
 */
public final class MarkdownUtils {

    private MarkdownUtils() {
    }

    /**
     * Расширения, которые считаем markdown-подобными, хотя языка у них нет:
     * такой файл уйдёт в обычный markdown-парсер.
     */
    private static final String[] MARKDOWN_TEXT_EXTENSIONS = {"txt", "text"};

    /** MIME-префиксы, по которым файл открывается во вьюере даже без узнаваемого расширения. */
    private static final String[] MARKDOWN_MIME_PREFIXES = {
            "text/plain", "text/x-diff", "text/x-patch", "text/csv", "text/xml", "text/yaml",
            "text/x-yaml", "text/css", "text/javascript", "application/json", "application/ld+json",
            "application/json5", "application/xml", "application/yaml", "application/x-yaml",
            "application/javascript", "application/x-javascript", "application/x-sh"
    };

    /** расширение (без точки, в нижнем регистре) → имя языка для {@code CodeHighlighting}. */
    private static final HashMap<String, String> PREFORMATTED_EXTENSION_LANGUAGES = new HashMap<>();
    /** имя файла целиком (в нижнем регистре) → язык: Dockerfile, Makefile, .gitignore и т.п. */
    private static final HashMap<String, String> PREFORMATTED_FILENAMES = new HashMap<>();

    static {
        // Таблица один в один, порядок сохранён
        addLanguage("plain", "log");
        addLanguage("diff", "diff", "patch");
        addLanguage("json", "json", "webmanifest");
        addLanguage("json5", "json5");
        addLanguage("xml", "xml", "rss", "atom");
        addLanguage("svg", "svg");
        addLanguage("html", "html", "htm", "xhtml");
        addLanguage("css", "css");
        addLanguage("scss", "scss");
        addLanguage("sass", "sass");
        addLanguage("less", "less");
        addLanguage("javascript", "js", "mjs", "cjs");
        addLanguage("jsx", "jsx");
        addLanguage("typescript", "ts");
        addLanguage("tsx", "tsx");
        addLanguage("java", "java");
        addLanguage("kotlin", "kt", "kts");
        addLanguage("gradle", "gradle");
        addLanguage("groovy", "groovy");
        addLanguage("python", "py", "pyw", "plugin");
        addLanguage("bash", "sh", "bash", "zsh", "fish", "shell");
        addLanguage("powershell", "ps1", "psm1", "psd1");
        addLanguage("batch", "bat", "cmd");
        addLanguage("sql", "sql");
        addLanguage("yaml", "yaml", "yml");
        addLanguage("ini", "ini", "toml", "properties", "props", "conf", "cfg", "config", "env", "dotenv");
        addLanguage("csv", "csv", "tsv");
        addLanguage("docker", "dockerfile");
        addLanguage("makefile", "make", "mk", "mak");
        addLanguage("cmake", "cmake");
        addLanguage("go", "go");
        addLanguage("rust", "rs");
        addLanguage("swift", "swift");
        addLanguage("dart", "dart");
        addLanguage("php", "php", "phtml");
        addLanguage("ruby", "rb", "gemspec");
        addLanguage("c", "c");
        addLanguage("cpp", "h", "hh", "hpp", "hxx", "cpp", "cc", "cxx");
        addLanguage("csharp", "cs");
        addLanguage("fsharp", "fs", "fsx");
        addLanguage("visual-basic", "vb", "vba");
        addLanguage("lua", "lua");
        addLanguage("perl", "pl", "pm");
        addLanguage("r", "r");
        addLanguage("scala", "scala");
        addLanguage("haskell", "hs");
        addLanguage("elixir", "ex", "exs");
        addLanguage("erlang", "erl", "hrl");
        addLanguage("protobuf", "proto", "protobuf");
        addLanguage("graphql", "graphql", "gql");
        addLanguage("glsl", "glsl", "vert", "frag", "geom", "comp");
        addLanguage("http", "http");

        addFilename("docker", "Dockerfile");
        addFilename("makefile", "Makefile", "GNUmakefile");
        addFilename("cmake", "CMakeLists.txt");
        addFilename("git", ".gitignore", ".gitattributes", ".gitmodules");
        addFilename("docker", ".dockerignore");
        addFilename("ini", ".editorconfig", ".env");
    }

    private static void addFilename(String language, String... names) {
        for (String name : names) {
            PREFORMATTED_FILENAMES.put(name.toLowerCase(Locale.ROOT), language);
        }
    }

    private static void addLanguage(String language, String... extensions) {
        // ключи кладём как есть: сюда попадают только литералы из статического блока,
        // а на чтении расширение всегда прогоняется через normalizeExtension.
        for (String extension : extensions) {
            PREFORMATTED_EXTENSION_LANGUAGES.put(extension, language);
        }
    }

    /**
     * Режет текст на блоки {@code pageBlockPreformatted} по {@code chunkSize} символов.
     *
     * Разрез идёт по последнему переводу строки перед границей — иначе подсветка ломается
     * посреди токена, а строка визуально рвётся между блоками.
     * Пустой файл всё равно даёт один блок, чтобы вьюер не показал пустую статью.
     */
    public static void appendPreformattedBlocks(List<TL_iv.PageBlock> blocks, String text, String language, int chunkSize) {
        final int size = Math.max(1, chunkSize);
        if (TextUtils.isEmpty(text)) {
            final TL_iv.pageBlockPreformatted block = new TL_iv.pageBlockPreformatted();
            block.text = plain("");
            block.language = language == null ? "" : language;
            blocks.add(block);
            return;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            if (end < text.length()) {
                final int lastNewLine = text.lastIndexOf('\n', end - 1);
                if (lastNewLine > start) {
                    end = lastNewLine + 1;
                }
            }
            final TL_iv.pageBlockPreformatted block = new TL_iv.pageBlockPreformatted();
            block.text = plain(text.substring(start, end));
            block.language = language == null ? "" : language;
            blocks.add(block);
            start = end;
        }
    }

    /** Имя файла без пути — учитываются оба разделителя, файл мог приехать с Windows. */
    private static String getBaseName(String path) {
        final int index = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return index >= 0 ? path.substring(index + 1) : path;
    }

    /** Первый {@code TL_documentAttributeFilename} документа. */
    public static String getDocumentFileName(TLRPC.Document document) {
        if (document != null && document.attributes != null) {
            final ArrayList<TLRPC.DocumentAttribute> attributes = document.attributes;
            for (int i = 0, size = attributes.size(); i < size; i++) {
                final TLRPC.DocumentAttribute attribute = attributes.get(i);
                if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                    return attribute.file_name;
                }
            }
        }
        return null;
    }

    /**
     * Расширение из имени файла. Точка на нулевой позиции не считается расширением
     * (иначе {@code .gitignore} стал бы расширением {@code gitignore})
     */
    private static String getExtensionFromFileName(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        final String baseName = getBaseName(fileName);
        final int dot = baseName.lastIndexOf('.');
        if (dot > 0 && dot < baseName.length() - 1) {
            return normalizeExtension(baseName.substring(dot + 1));
        }
        return "";
    }

    /**
     * Язык подсветки: сначала по имени файла, потом по расширению, потом по MIME.
     * Пустая строка означает «это не код» — вызывающий уходит в обычный markdown.
     */
    public static String getPreformattedLanguage(String fileName, String extension, String mimeType) {
        final String byFileName = getPreformattedLanguageByFileName(fileName);
        if (!TextUtils.isEmpty(byFileName)) {
            return byFileName;
        }

        String ext = normalizeExtension(extension);
        if (TextUtils.isEmpty(ext)) {
            // у документа расширения может не быть вовсе — достаём из имени
            ext = getExtensionFromFileName(fileName);
        }
        final String byExtension = PREFORMATTED_EXTENSION_LANGUAGES.get(ext);
        if (!TextUtils.isEmpty(byExtension)) {
            return byExtension;
        }

        if (TextUtils.isEmpty(mimeType)) {
            return "";
        }
        final String mime = mimeType.toLowerCase(Locale.ROOT);
        if (mime.startsWith("text/x-diff") || mime.startsWith("text/x-patch")) {
            return "diff";
        }
        if (mime.startsWith("text/csv")) {
            return "csv";
        }
        if (mime.startsWith("text/xml") || mime.startsWith("application/xml")) {
            return "xml";
        }
        // json5 проверяется раньше json: "application/json5" начинается с "application/json"
        if (mime.startsWith("application/json5")) {
            return "json5";
        }
        if (mime.startsWith("application/json") || mime.startsWith("application/ld+json")) {
            return "json";
        }
        if (mime.startsWith("text/yaml") || mime.startsWith("text/x-yaml")
                || mime.startsWith("application/yaml") || mime.startsWith("application/x-yaml")) {
            return "yaml";
        }
        if (mime.startsWith("text/css")) {
            return "css";
        }
        if (mime.startsWith("text/javascript") || mime.startsWith("application/javascript")
                || mime.startsWith("application/x-javascript")) {
            return "javascript";
        }
        if (mime.startsWith("application/x-sh")) {
            return "bash";
        }
        return "";
    }

    /**
     * Файлы без расширения и с суффиксами вида {@code Dockerfile.dev}, {@code .env.local}
     */
    private static String getPreformattedLanguageByFileName(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        final String name = getBaseName(fileName).toLowerCase(Locale.ROOT);
        final String language = PREFORMATTED_FILENAMES.get(name);
        if (!TextUtils.isEmpty(language)) {
            return language;
        }
        if (name.startsWith("dockerfile.")) {
            return "docker";
        }
        if (name.startsWith("makefile.")) {
            return "makefile";
        }
        if (name.startsWith(".env.")) {
            return "ini";
        }
        return "";
    }

    /** Стоит ли открывать это сообщение внутренним вьюером. */
    public static boolean isExteraMarkdown(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        return isExteraMarkdownExtension(messageObject.getExtension())
                || isExteraMarkdownMime(messageObject.getMimeType())
                || !TextUtils.isEmpty(getPreformattedLanguage(
                        getDocumentFileName(messageObject.getDocument()),
                        messageObject.getExtension(),
                        messageObject.getMimeType()));
    }

    public static boolean isExteraMarkdownExtension(String extension) {
        if (isMarkdownTextExtension(extension)) {
            return true;
        }
        return !TextUtils.isEmpty(getPreformattedLanguage(null, extension, null));
    }

    public static boolean isExteraMarkdownMime(String mimeType) {
        if (TextUtils.isEmpty(mimeType)) {
            return false;
        }
        final String mime = mimeType.toLowerCase(Locale.ROOT);
        for (String prefix : MARKDOWN_MIME_PREFIXES) {
            if (mime.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMarkdownTextExtension(String extension) {
        final String ext = normalizeExtension(extension);
        for (String known : MARKDOWN_TEXT_EXTENSIONS) {
            if (known.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    /** Срезает ведущие точки и приводит к нижнему регистру: «.KT» → «kt». */
    private static String normalizeExtension(String extension) {
        if (TextUtils.isEmpty(extension)) {
            return "";
        }
        String result = extension.trim();
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        return result.toLowerCase(Locale.ROOT);
    }

    private static TL_iv.RichText plain(String text) {
        final TL_iv.textPlain result = new TL_iv.textPlain();
        result.text = text == null ? "" : text;
        return result;
    }
}
