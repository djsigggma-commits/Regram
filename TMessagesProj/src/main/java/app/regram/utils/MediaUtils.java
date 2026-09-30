package app.regram.utils;

import android.media.ExifInterface;
import android.os.Build;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Разбор JPEG-заголовка и чистка геометок EXIF.
 *
 * Перенос exteraGram 12.9.0:utils/MediaUtils.java.
 *
 * Платформа определяется по первым байтам файла: JFIF-заголовок и начало
 * таблицы квантования у Android, iOS, macOS и десктопных редакторов различаются
 * побайтово, поэтому по префиксу видно, чем снимок сделан или обработан. Это
 * догадка по сигнатуре, а не метаданные, и на пересохранённом файле она скажет
 * про последний редактор, а не про камеру.
 *
 * Важно: в декомпиляции exteraGram цикл сравнения сломан — внутренний {@code break}
 * выходит только из перебора байтов, после чего значение возвращается всегда,
 * то есть любой JPEG опознавался бы как «Desktop». Здесь реализовано то, что
 * очевидно имелось в виду: полное совпадение префикса.
 */
public final class MediaUtils {

    /** Сколько байт заголовка хватает всем сигнатурам (самая длинная — 64). */
    private static final int HEADER_BYTES = 64;

    private static final Map<byte[], String> PLATFORM_HEADERS = new LinkedHashMap<>();

    static {
        PLATFORM_HEADERS.put(hex("FFD8FFE000104A46494600010101006000600000FFDB0043000403030403030404030405040405060A07060606060D090A080A0F0D10100F0D0F0E1113181411"), "Desktop");
        PLATFORM_HEADERS.put(hex("FFD8FFE000104A46494600010101004800480000FFE202184943435F50524F46494C4500010100000208"), "Web");
        PLATFORM_HEADERS.put(hex("FFD8FFE000104A46494600010100000100010000FFE202184943435F50524F46494C450001010000020800000000043000006D6E74725247422058595A2007E0"), "Android");
        PLATFORM_HEADERS.put(hex("FFD8FFE000104A46494600010101004800480000FFE201D84943435F50524F46494C45000101000001C800000000043000006D6E74725247422058595A2007E0"), "Android");
        PLATFORM_HEADERS.put(hex("FFD8FFE000104A46494600010100000100010000FFE201D84943435F50524F46494C45000101000001C800000000043000006D6E74725247422058595A2007E0"), "Android");
        PLATFORM_HEADERS.put(hex("FFD8FFE000104A46494600010100000100010000FFDB004300090607080706090807080A0A090B0D160F0D0C0C0D1B14151016201D2222201D1F1F2428342C24"), "iOS");
        PLATFORM_HEADERS.put(hex("FFD8FFE000104A46494600010100000100010000FFDB004300080606070605080707070909080A0C140D0C0B0B0C1912130F141D1A1F1E1D1A1C1C20242E2720"), "macOS");
        PLATFORM_HEADERS.put(hex("FFD8FFE000104A46494600010101004800480000FFE201DB4943435F50524F46494C45000101000001CB00000000024000006D6E74725247422058595A200000"), "macOS");
    }

    /** GPS-теги EXIF, доступные на всех поддерживаемых версиях Android. */
    private static final String[] GEO_TAGS = {
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_TIMESTAMP,
    };

    private MediaUtils() {
    }

    /**
     * Чем снят или обработан снимок: {@code "Android"}, {@code "iOS"},
     * {@code "macOS"}, {@code "Desktop"}, {@code "Web"} — или {@code null},
     * если сигнатура незнакомая.
     */
    public static String getPhotoPlatform(String path) {
        byte[] header = readFileHeader(path, HEADER_BYTES);
        if (header == null) {
            return null;
        }
        for (Map.Entry<byte[], String> entry : PLATFORM_HEADERS.entrySet()) {
            byte[] signature = entry.getKey();
            if (header.length < signature.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < signature.length; i++) {
                if (header[i] != signature[i]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Скопировать файл, вычистив из копии координаты съёмки.
     *
     * @return true, если хотя бы один тег был найден и убран.
     */
    public static boolean removeGeolocation(String sourcePath, String targetPath) throws IOException {
        File source = new File(sourcePath);
        if (!source.exists()) {
            return false;
        }
        File target = new File(targetPath);
        copyFile(source, target);

        List<String> tags = new ArrayList<>(Arrays.asList(GEO_TAGS));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tags.addAll(Arrays.asList(
                    ExifInterface.TAG_GPS_AREA_INFORMATION,
                    ExifInterface.TAG_GPS_DOP,
                    ExifInterface.TAG_GPS_DEST_BEARING,
                    ExifInterface.TAG_GPS_DEST_BEARING_REF,
                    ExifInterface.TAG_GPS_DEST_DISTANCE,
                    ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
                    ExifInterface.TAG_GPS_DEST_LATITUDE,
                    ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
                    ExifInterface.TAG_GPS_DEST_LONGITUDE,
                    ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_DIFFERENTIAL,
                    ExifInterface.TAG_GPS_IMG_DIRECTION,
                    ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                    ExifInterface.TAG_GPS_MAP_DATUM,
                    ExifInterface.TAG_GPS_MEASURE_MODE,
                    ExifInterface.TAG_GPS_SATELLITES,
                    ExifInterface.TAG_GPS_SPEED,
                    ExifInterface.TAG_GPS_SPEED_REF,
                    ExifInterface.TAG_GPS_STATUS,
                    ExifInterface.TAG_GPS_TRACK,
                    ExifInterface.TAG_GPS_TRACK_REF,
                    ExifInterface.TAG_GPS_VERSION_ID));
        }

        ExifInterface exif = new ExifInterface(target.getAbsolutePath());
        boolean changed = false;
        for (String tag : tags) {
            if (exif.getAttribute(tag) == null) {
                continue;
            }
            try {
                exif.setAttribute(tag, null);
                changed = true;
            } catch (Exception ignored) {
                // Тег есть, но записи не поддаётся — остальные всё равно чистим.
            }
        }
        if (!changed) {
            return false;
        }
        try {
            exif.saveAttributes();
            return true;
        } catch (IOException e) {
            // Копия осталась с координатами — отдавать её нельзя.
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw e;
        }
    }

    public static byte[] readFileHeader(String path, int length) {
        File file = new File(path);
        if (!file.exists() || length <= 0) {
            return null;
        }
        byte[] buffer = new byte[length];
        try (InputStream in = new FileInputStream(file)) {
            int read = 0;
            while (read < length) {
                int count = in.read(buffer, read, length - read);
                if (count <= 0) {
                    break;
                }
                read += count;
            }
            if (read <= 0) {
                return null;
            }
            return read == length ? buffer : Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source);
             java.io.OutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
        }
    }

    private static byte[] hex(String data) {
        int length = data.length() / 2;
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            int index = i * 2;
            out[i] = (byte) ((Character.digit(data.charAt(index), 16) << 4)
                    + Character.digit(data.charAt(index + 1), 16));
        }
        return out;
    }
}
