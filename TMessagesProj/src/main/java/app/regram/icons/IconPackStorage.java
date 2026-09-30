package app.regram.icons;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Хранение паков иконок на диске + установка из архива (порт IconPackStorage).
 *
 * Ограничения архива взяты из оригинала: не больше {@link #MAX_ENTRIES} файлов,
 * не больше {@link #MAX_TOTAL_UNCOMPRESSED_SIZE} суммарно после распаковки,
 * не больше {@link #MAX_SINGLE_FILE_SIZE} на один файл и степень сжатия одного файла
 * не выше {@link #MAX_COMPRESSION_RATIO} (защита от zip-бомб). Плюс защита от zip-slip.
 */
public class IconPackStorage {

    public static final String PACK_EXTENSION = ".icons";
    private static final String METADATA_NAME = "metadata.json";
    private static final String PACKS_DIR = "oe_icon_packs";

    /** Максимальное число файлов в архиве. */
    public static final int MAX_ENTRIES = 3000;
    /** Максимальный суммарный размер после распаковки — 100 МБ. */
    public static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 100L * 1024 * 1024;
    /** Максимальный размер одного файла — 10 МБ. */
    public static final long MAX_SINGLE_FILE_SIZE = 10L * 1024 * 1024;
    /** Максимальная степень сжатия одного файла. */
    public static final double MAX_COMPRESSION_RATIO = 250.0;
    /** Максимальный размер metadata.json — 256 КБ. */
    public static final long MAX_METADATA_SIZE = 262144L;
    /** Максимальный размер самого файла архива. */
    public static final long MAX_ARCHIVE_SIZE = MAX_TOTAL_UNCOMPRESSED_SIZE;

    private static final Object sync = new Object();
    private static volatile Map<String, IconPack> cachedPacks;

    private IconPackStorage() {
    }

    /** Исключение с кодом ошибки, понятным пользователю. */
    public static class IconPackStorageException extends Exception {
        private final IconPackStorageError error;

        public IconPackStorageException(IconPackStorageError error) {
            super(error.name());
            this.error = error;
        }

        public IconPackStorageError getError() {
            return error;
        }
    }

    public static File getIconPacksDirectory() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), PACKS_DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** id пака используется как имя каталога, поэтому он должен быть безопасным. */
    public static boolean isValidPackId(String packId) {
        if (packId == null || TextUtils.isEmpty(packId.trim())) {
            return false;
        }
        if (packId.indexOf('/') >= 0 || packId.indexOf('\\') >= 0 || packId.indexOf(0) >= 0) {
            return false;
        }
        return !".".equals(packId) && !"..".equals(packId);
    }

    // ---- Чтение установленных паков ----

    public static List<IconPack> getInstalledPacks() {
        synchronized (sync) {
            Map<String, IconPack> cache = cachedPacks;
            if (cache != null) {
                return new ArrayList<>(cache.values());
            }
            Map<String, IconPack> result = new LinkedHashMap<>();
            File[] dirs = getIconPacksDirectory().listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    if (!dir.isDirectory()) {
                        continue;
                    }
                    File metadata = new File(dir, METADATA_NAME);
                    if (!metadata.isFile()) {
                        continue;
                    }
                    IconPack pack = parseMetadataFile(metadata);
                    if (pack != null) {
                        result.put(pack.getId(), pack);
                    }
                }
            }
            cachedPacks = result;
            return new ArrayList<>(result.values());
        }
    }

    public static IconPack findPackById(String packId) {
        if (!isValidPackId(packId)) {
            return null;
        }
        synchronized (sync) {
            Map<String, IconPack> cache = cachedPacks;
            if (cache != null) {
                return cache.get(packId);
            }
        }
        getInstalledPacks();
        synchronized (sync) {
            Map<String, IconPack> cache = cachedPacks;
            return cache == null ? null : cache.get(packId);
        }
    }

    public static void invalidateCache() {
        synchronized (sync) {
            cachedPacks = null;
        }
    }

    public static void deletePack(String packId) {
        if (!isValidPackId(packId)) {
            return;
        }
        File dir = new File(getIconPacksDirectory(), packId);
        deleteRecursively(dir);
        invalidateCache();
    }

    /**
     * Возвращает файл иконки внутри пака или null. Путь обязательно проверяется на выход
     * за пределы каталога пака.
     */
    public static File resolveIconFile(IconPack pack, String relativePath) {
        if (pack == null || TextUtils.isEmpty(relativePath)) {
            return null;
        }
        File root = pack.getLocation();
        if (root == null) {
            root = new File(getIconPacksDirectory(), pack.getId());
        }
        try {
            File canonicalRoot = root.getCanonicalFile();
            File target = new File(canonicalRoot, relativePath).getCanonicalFile();
            if (!target.getPath().startsWith(canonicalRoot.getPath() + File.separator)) {
                return null;
            }
            return target.isFile() ? target : null;
        } catch (Exception e) {
            FileLog.e("openExtera: failed to resolve icon file for pack " + pack.getId(), e);
            return null;
        }
    }

    // ---- Установка ----

    /**
     * Устанавливает пак из архива. Возвращает null при успехе или код ошибки.
     * Блокирующий вызов, запускать не на UI-потоке.
     */
    public static IconPackStorageError installPack(File archive) {
        File temp = null;
        try {
            if (archive == null || !archive.isFile()) {
                return IconPackStorageError.INVALID_ARCHIVE;
            }
            if (archive.length() > MAX_ARCHIVE_SIZE) {
                return IconPackStorageError.ARCHIVE_TOO_LARGE;
            }
            temp = createTempDirectory("oe_icons_install");
            JSONObject metadata = extractPackArchive(archive, temp);
            IconPack parsed = parseMetadata(metadata, temp, temp);

            File target = new File(getIconPacksDirectory(), parsed.getId());
            deleteRecursively(target);
            if (!temp.renameTo(target)) {
                copyRecursively(temp, target);
                deleteRecursively(temp);
            }
            temp = null;
            invalidateCache();
            return null;
        } catch (IconPackStorageException e) {
            FileLog.e("openExtera: icon pack install failed: " + e.getError());
            return e.getError();
        } catch (Exception e) {
            FileLog.e("openExtera: icon pack install failed", e);
            return errorFromException(e);
        } finally {
            if (temp != null) {
                deleteRecursively(temp);
            }
        }
    }

    /** Разбирает архив без установки — для предпросмотра. */
    public static IconPack parsePackFromArchive(File archive) {
        File temp = null;
        try {
            temp = createTempDirectory("oe_icons_preview");
            JSONObject metadata = extractPackArchive(archive, temp);
            return parseMetadata(metadata, temp, temp);
        } catch (Exception e) {
            FileLog.e("openExtera: failed to parse icon pack", e);
            return null;
        } finally {
            if (temp != null) {
                deleteRecursively(temp);
            }
        }
    }

    /** Упаковывает установленный пак обратно в архив (для «поделиться»). */
    public static File bundlePack(String packId) {
        if (!isValidPackId(packId)) {
            return null;
        }
        IconPack pack = findPackById(packId);
        if (pack == null) {
            return null;
        }
        File source = new File(getIconPacksDirectory(), packId);
        if (!source.isDirectory()) {
            return null;
        }
        File outDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "oe_shared_packs");
        deleteRecursively(outDir);
        //noinspection ResultOfMethodCallIgnored
        outDir.mkdirs();
        File out = new File(outDir, sanitizeFileName(pack.getName()) + PACK_EXTENSION);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            zipDirectory(source, "", zos);
        } catch (Exception e) {
            FileLog.e("openExtera: failed to bundle icon pack " + packId, e);
            return null;
        }
        return out;
    }

    /** Записывает metadata.json пака на диск. */
    public static boolean saveIconPackMetadata(IconPack pack) {
        if (pack == null || !isValidPackId(pack.getId())) {
            return false;
        }
        try {
            File dir = new File(getIconPacksDirectory(), pack.getId());
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File tmp = new File(dir, METADATA_NAME + ".tmp");
            try (OutputStream os = new FileOutputStream(tmp)) {
                os.write(pack.toJson().toString(4).getBytes(StandardCharsets.UTF_8));
            }
            File target = new File(dir, METADATA_NAME);
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (!tmp.renameTo(target)) {
                return false;
            }
            invalidateCache();
            return true;
        } catch (Exception e) {
            FileLog.e("openExtera: error saving icon pack metadata", e);
            return false;
        }
    }

    // ---- Внутреннее ----

    private static IconPackStorageError errorFromException(Exception e) {
        if (e instanceof IconPackStorageException) {
            return ((IconPackStorageException) e).getError();
        }
        if (e instanceof SecurityException) {
            return IconPackStorageError.INVALID_METADATA;
        }
        if (e instanceof org.json.JSONException) {
            return IconPackStorageError.INVALID_METADATA;
        }
        if (e instanceof IOException) {
            return IconPackStorageError.STORAGE_ERROR;
        }
        return IconPackStorageError.UNKNOWN;
    }

    private static File createTempDirectory(String prefix) throws IconPackStorageException {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), prefix + "_" + System.currentTimeMillis());
        deleteRecursively(dir);
        if (!dir.mkdirs()) {
            throw new IconPackStorageException(IconPackStorageError.STORAGE_ERROR);
        }
        return dir;
    }

    /**
     * Распаковывает архив в targetDir с полной валидацией и возвращает разобранный metadata.json.
     */
    private static JSONObject extractPackArchive(File archive, File targetDir) throws Exception {
        long totalUncompressed = 0;
        int entryCount = 0;
        String canonicalTarget = targetDir.getCanonicalPath();

        ZipFile zipFile;
        try {
            zipFile = new ZipFile(archive);
        } catch (Exception e) {
            throw new IconPackStorageException(IconPackStorageError.INVALID_ARCHIVE);
        }
        try {
            Iterator<? extends ZipEntry> it = Collections.list(zipFile.entries()).iterator();
            while (it.hasNext()) {
                ZipEntry entry = it.next();
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entryCount > MAX_ENTRIES) {
                    throw new IconPackStorageException(IconPackStorageError.TOO_MANY_FILES);
                }

                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new IconPackStorageException(IconPackStorageError.INVALID_ARCHIVE);
                }
                File outFile = new File(targetDir, name);
                if (!outFile.getCanonicalPath().startsWith(canonicalTarget + File.separator)) {
                    // zip-slip
                    throw new IconPackStorageException(IconPackStorageError.INVALID_ARCHIVE);
                }

                long declaredSize = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (declaredSize > MAX_SINGLE_FILE_SIZE) {
                    throw new IconPackStorageException(IconPackStorageError.FILE_TOO_LARGE);
                }
                if (declaredSize > 0 && compressedSize > 0
                        && (double) declaredSize / (double) compressedSize > MAX_COMPRESSION_RATIO) {
                    throw new IconPackStorageException(IconPackStorageError.COMPRESSION_RATIO_TOO_HIGH);
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IconPackStorageException(IconPackStorageError.STORAGE_ERROR);
                }

                long written = 0;
                byte[] buffer = new byte[16 * 1024];
                try (InputStream is = zipFile.getInputStream(entry);
                     OutputStream os = new FileOutputStream(outFile)) {
                    int read;
                    while ((read = is.read(buffer)) > 0) {
                        written += read;
                        // размер в заголовке может врать — проверяем и по факту
                        if (written > MAX_SINGLE_FILE_SIZE) {
                            throw new IconPackStorageException(IconPackStorageError.FILE_TOO_LARGE);
                        }
                        if (totalUncompressed + written > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                            throw new IconPackStorageException(IconPackStorageError.ARCHIVE_TOO_LARGE);
                        }
                        os.write(buffer, 0, read);
                    }
                }
                if (compressedSize > 0 && (double) written / (double) compressedSize > MAX_COMPRESSION_RATIO) {
                    throw new IconPackStorageException(IconPackStorageError.COMPRESSION_RATIO_TOO_HIGH);
                }
                totalUncompressed += written;
            }
        } finally {
            try {
                zipFile.close();
            } catch (Exception ignore) {
            }
        }

        File metadata = new File(targetDir, METADATA_NAME);
        if (!metadata.isFile()) {
            // допускаем один вложенный каталог верхнего уровня
            File[] children = targetDir.listFiles();
            File nested = null;
            if (children != null && children.length == 1 && children[0].isDirectory()
                    && new File(children[0], METADATA_NAME).isFile()) {
                nested = children[0];
            }
            if (nested == null) {
                throw new IconPackStorageException(IconPackStorageError.MISSING_METADATA);
            }
            flattenInto(nested, targetDir);
            metadata = new File(targetDir, METADATA_NAME);
            if (!metadata.isFile()) {
                throw new IconPackStorageException(IconPackStorageError.MISSING_METADATA);
            }
        }
        if (metadata.length() > MAX_METADATA_SIZE) {
            throw new IconPackStorageException(IconPackStorageError.METADATA_TOO_LARGE);
        }
        try {
            return new JSONObject(readText(metadata));
        } catch (org.json.JSONException e) {
            throw new IconPackStorageException(IconPackStorageError.INVALID_METADATA);
        }
    }

    private static void flattenInto(File nested, File targetDir) {
        File[] files = nested.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            File dest = new File(targetDir, file.getName());
            if (!file.renameTo(dest)) {
                try {
                    copyRecursively(file, dest);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        deleteRecursively(nested);
    }

    private static IconPack parseMetadataFile(File file) {
        try {
            if (file.length() > MAX_METADATA_SIZE) {
                return null;
            }
            return parseMetadata(new JSONObject(readText(file)), file.getParentFile(), file.getParentFile());
        } catch (Exception e) {
            FileLog.e("openExtera: error parsing " + METADATA_NAME, e);
            return null;
        }
    }

    /**
     * Разбирает metadata.json. Пути иконок нормализуются относительно packRoot,
     * выход за его пределы отбрасывается.
     */
    private static IconPack parseMetadata(JSONObject json, File packRoot, File location) throws Exception {
        String name = json.optString("packName", "");
        String id = json.optString("packId", "");
        if (TextUtils.isEmpty(name)) {
            throw new IconPackStorageException(IconPackStorageError.INVALID_METADATA);
        }
        if (!isValidPackId(id)) {
            throw new IconPackStorageException(IconPackStorageError.INVALID_METADATA);
        }
        String author = json.optString("author", "");
        String version = json.optString("version", "1.0");

        Map<String, String> icons = new LinkedHashMap<>();
        JSONObject iconsJson = json.optJSONObject("icons");
        if (iconsJson != null) {
            Iterator<String> keys = iconsJson.keys();
            String canonicalRoot = packRoot == null ? null : packRoot.getCanonicalPath();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = iconsJson.optString(key, null);
                if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
                    continue;
                }
                if (!isValidResourceName(key)) {
                    continue;
                }
                if (canonicalRoot != null) {
                    File resolved = new File(packRoot, value).getCanonicalFile();
                    if (!resolved.getPath().startsWith(canonicalRoot + File.separator)) {
                        continue;
                    }
                    value = resolved.getPath().substring(canonicalRoot.length() + 1).replace(File.separatorChar, '/');
                }
                icons.put(key, value);
            }
        }
        return new IconPack(id, name, author, version, icons, location);
    }

    public static boolean isValidResourceName(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String sanitizeFileName(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length() && sb.length() < 64; i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            }
        }
        return sb.length() == 0 ? "icon_pack" : sb.toString();
    }

    private static String readText(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                bos.write(buffer, 0, read);
            }
            return bos.toString("UTF-8");
        }
    }

    private static void zipDirectory(File dir, String prefix, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        byte[] buffer = new byte[16 * 1024];
        for (File file : files) {
            String entryName = prefix + file.getName();
            if (file.isDirectory()) {
                zipDirectory(file, entryName + "/", zos);
                continue;
            }
            zos.putNextEntry(new ZipEntry(entryName));
            try (InputStream is = new FileInputStream(file)) {
                int read;
                while ((read = is.read(buffer)) > 0) {
                    zos.write(buffer, 0, read);
                }
            }
            zos.closeEntry();
        }
    }

    private static void copyRecursively(File source, File target) throws IOException {
        if (source.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            target.mkdirs();
            File[] files = source.listFiles();
            if (files != null) {
                for (File file : files) {
                    copyRecursively(file, new File(target, file.getName()));
                }
            }
            return;
        }
        File parent = target.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (InputStream is = new FileInputStream(source); OutputStream os = new FileOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = is.read(buffer)) > 0) {
                os.write(buffer, 0, read);
            }
        }
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
