package app.regram.icons;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/** Причины отказа при установке пака иконок (порт IconPackStorageError). */
public enum IconPackStorageError {

    INVALID_ARCHIVE,
    MISSING_METADATA,
    METADATA_TOO_LARGE,
    INVALID_METADATA,
    TOO_MANY_FILES,
    ARCHIVE_TOO_LARGE,
    FILE_TOO_LARGE,
    COMPRESSION_RATIO_TOO_HIGH,
    STORAGE_ERROR,
    UNKNOWN;

    public String getLocalizedMessage() {
        int res;
        switch (this) {
            case INVALID_ARCHIVE:
                res = R.string.IconPackErrorInvalidArchive;
                break;
            case MISSING_METADATA:
                res = R.string.IconPackErrorMissingMetadata;
                break;
            case METADATA_TOO_LARGE:
                res = R.string.IconPackErrorMetadataTooLarge;
                break;
            case INVALID_METADATA:
                res = R.string.IconPackErrorInvalidMetadata;
                break;
            case TOO_MANY_FILES:
                res = R.string.IconPackErrorTooManyFiles;
                break;
            case ARCHIVE_TOO_LARGE:
                res = R.string.IconPackErrorArchiveTooLarge;
                break;
            case FILE_TOO_LARGE:
                res = R.string.IconPackErrorFileTooLarge;
                break;
            case COMPRESSION_RATIO_TOO_HIGH:
                res = R.string.IconPackErrorCompressionRatioTooHigh;
                break;
            case STORAGE_ERROR:
                res = R.string.IconPackErrorStorage;
                break;
            default:
                res = R.string.IconPackErrorUnknown;
                break;
        }
        return LocaleController.getString(res);
    }
}
