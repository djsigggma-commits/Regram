package app.regram.icons.picker;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DocumentSelectActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import app.regram.icons.IconPack;
import app.regram.icons.IconPackManager;
import app.regram.icons.IconPackStorage;
import tw.nekomimi.nekogram.ui.icons.IconsResources;

/**
 * Лист точечной замены одной иконки (порт
 * {@code com.exteragram.messenger.icons.ui.components.ReplaceIconBottomSheet}).
 *
 * Показывает, как иконка выглядит сейчас и как выглядела бы без пака, и даёт три действия:
 * выбрать картинку файлом, вставить из буфера обмена, сбросить замену.
 */
public class ReplaceIconBottomSheet extends BottomSheet {

    private final String packId;
    private final int resId;
    private final String resourceName;
    private final Runnable onChanged;

    private final ImageView currentPreview;
    private final TextCell resetCell;

    public ReplaceIconBottomSheet(Context context, String packId, int resId, Runnable onChanged) {
        super(context, false);
        this.packId = packId;
        this.resId = resId;
        this.onChanged = onChanged;
        this.resourceName = IconPackManager.getInstance().getResourceName(resId);

        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setText(getString(R.string.IconPickerReplaceIcon));
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                22, 18, 22, 0));

        TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        subtitle.setText(resourceName == null ? "" : resourceName);
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                22, 4, 22, 0));

        // ---- превью: слева оригинал, справа текущая иконка ----
        LinearLayout previews = new LinearLayout(context);
        previews.setOrientation(LinearLayout.HORIZONTAL);
        previews.setGravity(Gravity.CENTER);
        root.addView(previews, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                0, 18, 0, 12));

        previews.addView(buildPreview(context, getString(R.string.IconPickerOriginal), originalDrawable(), null),
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        currentPreview = new ImageView(context);
        previews.addView(buildPreview(context, getString(R.string.IconPickerCurrent), currentDrawable(), currentPreview),
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextCell fromFilesCell = new TextCell(context);
        fromFilesCell.setTextAndIcon(getString(R.string.IconPickerSelectImage), R.drawable.msg_photos, true);
        fromFilesCell.setOnClickListener(v -> selectFromFiles());
        root.addView(fromFilesCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        TextCell fromClipboardCell = new TextCell(context);
        fromClipboardCell.setTextAndIcon(getString(R.string.PasteFromClipboard), R.drawable.msg_copy, true);
        fromClipboardCell.setOnClickListener(v -> pasteFromClipboard());
        root.addView(fromClipboardCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        resetCell = new TextCell(context);
        resetCell.setTextAndIcon(getString(R.string.Reset), R.drawable.msg_reset, false);
        resetCell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
        resetCell.setOnClickListener(v -> resetIcon());
        resetCell.setVisibility(hasReplacement() ? android.view.View.VISIBLE : android.view.View.GONE);
        root.addView(resetCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        setCustomView(root);
    }

    private FrameLayout buildPreview(Context context, String label, Drawable drawable, ImageView reuse) {
        FrameLayout container = new FrameLayout(context);
        ImageView image = reuse == null ? new ImageView(context) : reuse;
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        applyDrawable(image, drawable);
        container.addView(image, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        text.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        text.setGravity(Gravity.CENTER);
        text.setText(label);
        container.addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 4, 62, 4, 8));
        return container;
    }

    private static void applyDrawable(ImageView view, Drawable drawable) {
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.SRC_IN));
        }
        view.setImageDrawable(drawable);
    }

    private boolean hasReplacement() {
        IconPack pack = IconPackStorage.findPackById(packId);
        return pack != null && resourceName != null && pack.getIcons().containsKey(resourceName);
    }

    private Drawable originalDrawable() {
        try {
            android.content.res.Resources res = ApplicationLoader.applicationContext.getResources();
            if (res instanceof IconsResources) {
                return ((IconsResources) res).getOriginalDrawableForDensity(resId, 0, null);
            }
            return res.getDrawable(resId);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private Drawable currentDrawable() {
        try {
            return ApplicationLoader.applicationContext.getResources().getDrawable(resId);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    // ---- действия ----

    private void selectFromFiles() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return;
        }
        dismiss();
        DocumentSelectActivity selector = new DocumentSelectActivity(false);
        selector.setMaxSelectedFiles(1);
        selector.setAllowPhoto(true);
        selector.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                if (!files.isEmpty()) {
                    apply(new File(files.get(0)), false);
                }
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                if (photos.isEmpty()) {
                    return;
                }
                String path = photos.get(0).path;
                if (path != null) {
                    apply(new File(path), false);
                }
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        fragment.presentFragment(selector);
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            Uri uri = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).getUri();
            if (uri == null) {
                BulletinFactory.of(getContainer(), null)
                        .createErrorBulletin(getString(R.string.IconPickerClipboardEmpty)).show();
                return;
            }
            File temp = new File(ApplicationLoader.applicationContext.getCacheDir(), "oe_icon_clipboard.png");
            try (InputStream in = getContext().getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(temp)) {
                if (in == null) {
                    return;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            apply(temp, true);
        } catch (Throwable t) {
            FileLog.e("openExtera: failed to paste icon from clipboard", t);
        }
    }

    private void apply(File source, boolean deleteSource) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = IconPackManager.getInstance().saveCustomIcon(packId, resId, source);
            if (deleteSource) {
                //noinspection ResultOfMethodCallIgnored
                source.delete();
            }
            AndroidUtilities.runOnUIThread(() -> finish(ok));
        });
    }

    private void resetIcon() {
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = IconPackManager.getInstance().resetCustomIcon(packId, resId);
            AndroidUtilities.runOnUIThread(() -> finish(ok));
        });
    }

    private void finish(boolean changed) {
        if (changed) {
            applyDrawable(currentPreview, currentDrawable());
            resetCell.setVisibility(hasReplacement() ? android.view.View.VISIBLE : android.view.View.GONE);
            if (onChanged != null) {
                onChanged.run();
            }
        }
        try {
            dismiss();
        } catch (Exception ignore) {
        }
    }
}
