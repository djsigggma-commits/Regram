package app.regram.icons;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Провайдер файлов иконок (порт {@code com.exteragram.messenger.icons.IconPackProvider}).
 *
 * Системе уведомлений нельзя отдать {@code Drawable} — только ресурс или {@code content://} URI.
 * Провайдер отдаёт растеризованный PNG иконки из пака по адресу
 * {@code content://<applicationId>.icon_pack_provider/icon/<packId>/<resourceName>}.
 * Растр кладётся в {@code cacheDir/notification_icons} и переиспользуется, пока файл иконки
 * не изменился (в имени кэша — хэш пути, mtime, размера и плотности).
 *
 * ВНИМАНИЕ: провайдер работает только после объявления в AndroidManifest, см. отчёт.
 */
public class IconPackProvider extends ContentProvider {

    private static final List<String> SUPPORTED = Arrays.asList("png", "webp", "jpg", "jpeg", "svg");
    private static final String CACHE_DIR = "notification_icons";

    public static String getAuthority() {
        return ApplicationLoader.getApplicationId() + ".icon_pack_provider";
    }

    /** URI иконки из пака или null, если такой замены нет. */
    @Nullable
    public static Uri getIconUri(String packId, String resourceName) {
        IconPack pack = IconPackStorage.findPackById(packId);
        if (pack == null || resourceName == null) {
            return null;
        }
        String relative = pack.getIcons().get(resourceName);
        File file = relative == null ? null : IconPackStorage.resolveIconFile(pack, relative);
        if (file == null || !SUPPORTED.contains(extensionOf(file.getName()))) {
            return null;
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(getAuthority())
                .appendPath("icon")
                .appendPath(packId)
                .appendPath(resourceName)
                // версия в query — чтобы система не отдала устаревший закэшированный растр
                .appendQueryParameter("v", file.lastModified() + "_" + file.length())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) {
        File source = resolveSource(uri);
        if (source == null) {
            return null;
        }
        File rasterized = getRasterizedIcon(source);
        File result = rasterized != null ? rasterized : getFallbackIcon();
        if (result == null) {
            return null;
        }
        try {
            return ParcelFileDescriptor.open(result, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            FileLog.e("openExtera: cannot open icon for notification", e);
            return null;
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (!isIconUri(uri)) {
            return null;
        }
        String name = uri.getLastPathSegment() + ".png";
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        File source = resolveSource(uri);
        File rasterized = source == null ? null : getRasterizedIcon(source);
        cursor.addRow(new Object[]{name, rasterized == null ? 0L : rasterized.length()});
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return isIconUri(uri) ? "image/png" : null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    // ---- внутреннее ----

    private boolean isIconUri(Uri uri) {
        List<String> segments = uri.getPathSegments();
        return segments.size() == 3 && "icon".equals(segments.get(0));
    }

    @Nullable
    private File resolveSource(Uri uri) {
        if (!isIconUri(uri)) {
            return null;
        }
        List<String> segments = uri.getPathSegments();
        IconPack pack = IconPackStorage.findPackById(segments.get(1));
        if (pack == null) {
            return null;
        }
        String relative = pack.getIcons().get(segments.get(2));
        File file = relative == null ? null : IconPackStorage.resolveIconFile(pack, relative);
        if (file == null || !SUPPORTED.contains(extensionOf(file.getName()))) {
            return null;
        }
        return file;
    }

    @Nullable
    private File getRasterizedIcon(File source) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        int density = context.getResources().getDisplayMetrics().densityDpi;
        String key;
        try {
            String seed = source.getCanonicalPath() + ':' + source.lastModified() + ':' + source.length() + ':' + density;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            key = sb + ".png";
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        return materialize(key, () -> IconPackManager.getInstance()
                .decodeForNotification(source.getAbsolutePath(), R.drawable.notification, density));
    }

    @Nullable
    private File getFallbackIcon() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        int density = context.getResources().getDisplayMetrics().densityDpi;
        return materialize("default_" + density + ".png",
                () -> BitmapFactory.decodeResource(context.getResources(), R.drawable.notification));
    }

    private interface BitmapSource {
        @Nullable
        Bitmap get();
    }

    /** Пишет растр в кэш атомарно: сначала во временный файл, затем переименование. */
    @Nullable
    private File materialize(String name, BitmapSource source) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        File dir = new File(context.getCacheDir(), CACHE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File target = new File(dir, name);
        if (target.isFile() && target.length() > 0) {
            return target;
        }
        synchronized (IconPackProvider.class) {
            if (target.isFile() && target.length() > 0) {
                return target;
            }
            File tmp = new File(dir, name + ".tmp");
            Bitmap bitmap = null;
            try {
                bitmap = source.get();
                if (bitmap == null) {
                    return null;
                }
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        return null;
                    }
                }
                if (!tmp.renameTo(target)) {
                    return null;
                }
                return target;
            } catch (Exception e) {
                FileLog.e("openExtera: failed to rasterize notification icon", e);
                return null;
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
                if (tmp.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            }
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
