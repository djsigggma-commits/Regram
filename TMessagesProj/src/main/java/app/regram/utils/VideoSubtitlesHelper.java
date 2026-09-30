package app.regram.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

/**
 * Внешние субтитры к видео в просмотрщике.
 *
 * Перенос exteraGram 12.9.0:utils/VideoSubtitlesHelper.java.
 *
 * Выбранный пользователем файл .srt/.vtt запоминается для конкретного видео:
 * ключ считается по документу, пути, uri или id блока статьи, значение лежит
 * в отдельном SharedPreferences. Файл из content-провайдера копируется в кэш,
 * потому что ExoPlayer читает дорожку субтитров как обычный файл.
 */
public final class VideoSubtitlesHelper {

    public static final int SUBTITLE_PICKER_REQUEST_CODE = 231;

    public static final String MIME_SRT = "application/x-subrip";
    public static final String MIME_VTT = "text/vtt";

    private static final String EXTENSION_SRT = "srt";
    private static final String EXTENSION_VTT = "vtt";

    private static final String PREFS_NAME = "video_external_subtitles";
    private static final String KEY_PATH = "path_";
    private static final String KEY_MIME = "mime_";
    private static final String KEY_LABEL = "label_";

    private VideoSubtitlesHelper() {
    }

    public enum LoadError {
        NONE,
        UNSUPPORTED_FORMAT,
        LOAD_FAILED
    }

    public static final class SubtitleState {

        private final String path;
        private final String mimeType;
        private final String label;

        public SubtitleState(String path, String mimeType, String label) {
            this.path = path;
            this.mimeType = mimeType;
            this.label = label;
        }

        public String path() {
            return path;
        }

        public String mimeType() {
            return mimeType;
        }

        public String label() {
            return label;
        }

        public String getDisplayName() {
            return !TextUtils.isEmpty(label) ? label : new File(path).getName();
        }

        public boolean isValid() {
            if (TextUtils.isEmpty(path) || TextUtils.isEmpty(mimeType)) {
                return false;
            }
            File file = new File(path);
            return file.exists() && file.length() > 0;
        }

        public VideoPlayer.ExternalSubtitle toExternalSubtitle() {
            return new VideoPlayer.ExternalSubtitle(Uri.fromFile(new File(path)), mimeType, getDisplayName());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SubtitleState)) {
                return false;
            }
            SubtitleState other = (SubtitleState) obj;
            return Objects.equals(path, other.path)
                    && Objects.equals(mimeType, other.mimeType)
                    && Objects.equals(label, other.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, mimeType, label);
        }

        @Override
        public String toString() {
            return "SubtitleState[path=" + path + ", mimeType=" + mimeType + ", label=" + label + "]";
        }
    }

    public static final class SubtitleLoadResult {

        private final SubtitleState subtitleState;
        private final LoadError error;

        public SubtitleLoadResult(SubtitleState subtitleState, LoadError error) {
            this.subtitleState = subtitleState;
            this.error = error;
        }

        public SubtitleState subtitleState() {
            return subtitleState;
        }

        public LoadError error() {
            return error;
        }

        public boolean isSuccess() {
            return subtitleState != null;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SubtitleLoadResult)) {
                return false;
            }
            SubtitleLoadResult other = (SubtitleLoadResult) obj;
            return Objects.equals(subtitleState, other.subtitleState) && error == other.error;
        }

        @Override
        public int hashCode() {
            return Objects.hash(subtitleState, error);
        }

        @Override
        public String toString() {
            return "SubtitleLoadResult[subtitleState=" + subtitleState + ", error=" + error + "]";
        }
    }

    public static boolean areSame(SubtitleState first, SubtitleState second) {
        if (first == second) {
            return true;
        }
        return first != null && second != null
                && TextUtils.equals(first.path, second.path)
                && TextUtils.equals(first.mimeType, second.mimeType)
                && TextUtils.equals(first.label, second.label);
    }

    public static String buildVideoKey(MessageObject messageObject, String path, Uri uri, TL_iv.PageBlock pageBlock) {
        if (messageObject != null && messageObject.getDocument() != null) {
            return "doc_" + messageObject.getDocument().id;
        }
        if (!TextUtils.isEmpty(path)) {
            return makePathKey(path);
        }
        if (uri != null) {
            return makeUriKey(uri);
        }
        if (pageBlock instanceof TL_iv.pageBlockVideo) {
            return "page_video_" + ((TL_iv.pageBlockVideo) pageBlock).video_id;
        }
        return null;
    }

    public static String makePathKey(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        return "uri_" + Utilities.MD5(path);
    }

    public static String makeUriKey(Uri uri) {
        if (uri == null) {
            return null;
        }
        return makePathKey(uri.toString());
    }

    public static Intent createPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{MIME_SRT, MIME_VTT});
        return intent;
    }

    public static TextView createSubtitlesView(Context context) {
        TextView textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(0xffffffff);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.isTablet() ? 20 : 16);
        textView.setMaxLines(3);
        textView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), 0xcc000000));
        textView.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(1), 0x99000000);
        textView.setVisibility(View.GONE);
        return textView;
    }

    public static SubtitleLoadResult loadFromPickerIntent(Intent intent) {
        return loadFromUri(intent != null ? intent.getData() : null);
    }

    public static SubtitleLoadResult loadFromUri(Uri uri) {
        if (uri == null) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }
        String mimeType = resolveMimeType(uri);
        if (TextUtils.isEmpty(mimeType)) {
            return new SubtitleLoadResult(null, LoadError.UNSUPPORTED_FORMAT);
        }
        String extension = MIME_VTT.equals(mimeType) ? EXTENSION_VTT : EXTENSION_SRT;
        String resolvedPath;
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                resolvedPath = uri.getPath();
            } else {
                String path = AndroidUtilities.getPath(uri);
                if (TextUtils.isEmpty(path) || path.startsWith("content://")) {
                    resolvedPath = MediaController.copyFileToCache(uri, extension);
                } else {
                    resolvedPath = path;
                }
            }
        } catch (Exception ignored) {
            resolvedPath = null;
        }
        if (TextUtils.isEmpty(resolvedPath)) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }
        File file = new File(resolvedPath);
        if (!file.exists() || file.length() <= 0) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }
        return new SubtitleLoadResult(new SubtitleState(resolvedPath, mimeType, file.getName()), LoadError.NONE);
    }

    public static SubtitleState restore(String videoKey) {
        if (TextUtils.isEmpty(videoKey)) {
            return null;
        }
        SharedPreferences preferences = getPreferences();
        String path = preferences.getString(KEY_PATH + videoKey, null);
        String mimeType = preferences.getString(KEY_MIME + videoKey, null);
        String label = preferences.getString(KEY_LABEL + videoKey, null);
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(mimeType)) {
            return null;
        }
        SubtitleState state = new SubtitleState(path, mimeType, label);
        if (state.isValid()) {
            return state;
        }
        clear(videoKey);
        return null;
    }

    public static void save(String videoKey, SubtitleState state) {
        if (TextUtils.isEmpty(videoKey) || state == null || !state.isValid()) {
            return;
        }
        getPreferences().edit()
                .putString(KEY_PATH + videoKey, state.path())
                .putString(KEY_MIME + videoKey, state.mimeType())
                .putString(KEY_LABEL + videoKey, state.getDisplayName())
                .apply();
    }

    public static void clear(String videoKey) {
        if (TextUtils.isEmpty(videoKey)) {
            return;
        }
        getPreferences().edit()
                .remove(KEY_PATH + videoKey)
                .remove(KEY_MIME + videoKey)
                .remove(KEY_LABEL + videoKey)
                .apply();
    }

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String resolveMimeType(Uri uri) {
        String type;
        try {
            type = ApplicationLoader.applicationContext.getContentResolver().getType(uri);
        } catch (Exception ignored) {
            type = null;
        }
        if (!TextUtils.isEmpty(type)) {
            String lower = type.toLowerCase(Locale.US);
            if (MIME_VTT.equals(lower) || lower.contains(EXTENSION_VTT)) {
                return MIME_VTT;
            }
            if (MIME_SRT.equals(lower) || lower.contains("subrip") || lower.contains(EXTENSION_SRT)) {
                return MIME_SRT;
            }
        }
        String extension = getSubtitleExtension(uri);
        if (EXTENSION_VTT.equals(extension)) {
            return MIME_VTT;
        }
        if (EXTENSION_SRT.equals(extension)) {
            return MIME_SRT;
        }
        return null;
    }

    private static String getSubtitleExtension(Uri uri) {
        String path = AndroidUtilities.getPath(uri);
        if (TextUtils.isEmpty(path)) {
            path = uri.getPath();
        }
        if (TextUtils.isEmpty(path)) {
            path = uri.toString();
        }
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith("." + EXTENSION_SRT)) {
            return EXTENSION_SRT;
        }
        if (lower.endsWith("." + EXTENSION_VTT)) {
            return EXTENSION_VTT;
        }
        return null;
    }
}
