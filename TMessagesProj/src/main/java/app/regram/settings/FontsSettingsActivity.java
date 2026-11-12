package app.regram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import app.regram.fonts.ApplicationFonts;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.PaintTypeface;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

import java.util.ArrayList;
import java.util.List;

/** Import and application-wide UI selection, plus the existing media editor font catalog. */
public final class FontsSettingsActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {
    private static final int IMPORT_FONT = 24110, TYPE_FONT = 100;
    private int applicationRow, applicationInfoRow, headerRow, systemRow, fontsStart, fontsEnd;
    private int editorInfoRow, addRow, resetRow, importInfoRow;
    private List<PaintTypeface> fonts = new ArrayList<>();
    private ItemTouchHelper touchHelper;
    private boolean importing, reordering, refreshPending;

    @Override public boolean onFragmentCreate() {
        boolean result = super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.customTypefacesLoaded);
        return result;
    }
    @Override public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.customTypefacesLoaded);
        super.onFragmentDestroy();
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.customTypefacesLoaded) refresh();
    }
    private void refresh() {
        if (reordering) { refreshPending = true; return; }
        updateRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }
    @Override protected void updateRows() {
        super.updateRows();
        applicationRow = addRow("regramApplicationFont", "RegramFontsApplication");
        applicationInfoRow = addRow("RegramFontsApplicationInfo");
        headerRow = addRow("RegramFontsAvailable");
        systemRow = addRow("regramSystemFonts", "RegramFontsSystem");
        fonts = new ArrayList<>(ApplicationFonts.catalog(PaintTypeface.getAll(), true));
        fontsStart = rowCount;
        for (PaintTypeface font : fonts) addRow("regramFont_" + font.getKey());
        fontsEnd = rowCount;
        editorInfoRow = addRow("RegramFontsEditorInfo");
        addRow = addRow("regramImportFont", "RegramFontsAdd");
        resetRow = addRow("regramResetFontOrder", "RegramFontsReset");
        importInfoRow = addRow("RegramFontsImportInfo");
    }
    @Override protected String getActionBarTitle() { return getString(R.string.RegramFontsTitle); }
    @Override protected String getKey() { return "regram_fonts"; }
    @Override public int getSearchGuid() { return 23110; }
    @Override public int getSearchIcon() { return R.drawable.msg_settings; }
    @Override public String getSearchPrefix() { return "RegramFonts"; }
    @Override protected BaseListAdapter createAdapter(Context context) { return new ListAdapter(context); }

    @Override public View createView(Context context) {
        View view = super.createView(context);
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean isLongPressDragEnabled() { return false; }
            @Override public int getMovementFlags(RecyclerView recycler, RecyclerView.ViewHolder holder) {
                int p = holder.getAdapterPosition();
                return p >= fontsStart && p < fontsEnd ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
            }
            @Override public boolean onMove(RecyclerView recycler, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
                int from = source.getAdapterPosition(), to = target.getAdapterPosition();
                if (from < fontsStart || from >= fontsEnd || to < fontsStart || to >= fontsEnd) return false;
                fonts.add(to - fontsStart, fonts.remove(from - fontsStart));
                listAdapter.notifyItemMoved(from, to);
                return true;
            }
            @Override public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
                if (state == ItemTouchHelper.ACTION_STATE_DRAG) reordering = true;
                super.onSelectedChanged(holder, state);
            }
            @Override public void clearView(RecyclerView recycler, RecyclerView.ViewHolder holder) {
                super.clearView(recycler, holder);
                ApplicationFonts.setOrder(fonts);
                reordering = false;
                if (refreshPending) { refreshPending = false; refresh(); }
                else updateRows(); // Keep row keys/deep links consistent with the moved entries.
            }
            @Override public void onSwiped(RecyclerView.ViewHolder holder, int direction) {}
        });
        touchHelper.attachToRecyclerView(listView);
        return view;
    }
    @Override protected void onItemClick(View view, int position, float x, float y) {
        if (position == applicationRow) {
            chooseApplicationFont();
        } else if (position == systemRow) {
            ApplicationFonts.setSystemFonts(!ApplicationFonts.systemFonts());
            refresh();
        } else if (position == addRow && !importing) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(intent, IMPORT_FONT); } catch (Exception e) { error(e); }
        } else if (position == resetRow) {
            ApplicationFonts.resetOrder();
            refresh();
        } else if (position >= fontsStart && position < fontsEnd) {
            PaintTypeface font = fonts.get(position - fontsStart);
            if (ApplicationFonts.isImported(font.getKey())) {
                showDialog(new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                        .setTitle(font.getName()).setMessage(getString(R.string.RegramFontsDelete))
                        .setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                            boolean selected = ApplicationFonts.selected().equals(font.getKey());
                            try {
                                ApplicationFonts.delete(font.getKey());
                                refresh();
                                if (selected) restartHint();
                            } catch (Exception e) { error(e); }
                        }).setNegativeButton(getString(R.string.Cancel), null).create());
            }
        }
    }
    private void chooseApplicationFont() {
        List<PaintTypeface> imported = ApplicationFonts.imported();
        CharSequence[] names = new CharSequence[imported.size() + 1];
        names[0] = getString(R.string.RegramFontsDefault);
        for (int i = 0; i < imported.size(); i++) names[i + 1] = imported.get(i).getName();
        showDialog(new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                .setTitle(getString(R.string.RegramFontsApplication))
                .setItems(names, (dialog, which) -> {
                    String key = which == 0 ? "" : imported.get(which - 1).getKey();
                    if (key.equals(ApplicationFonts.selected())) return;
                    try { ApplicationFonts.select(key); refresh(); restartHint(); } catch (Exception e) { error(e); }
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }
    private void restartHint() {
        if (getParentActivity() == null) return;
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.OEAppearanceNeedRestart),
                getString(R.string.OEAppearanceRestartNow), () -> {
                    Activity activity = getParentActivity();
                    if (activity != null) AppRestartHelper.triggerRebirth(activity, new Intent(activity, LaunchActivity.class));
                }).show();
    }
    private void error(Exception e) {
        FileLog.e(e);
        if (getParentActivity() != null) showDialog(new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                .setTitle(getString(R.string.RegramFontsTitle)).setMessage(getString(R.string.RegramFontsError))
                .setPositiveButton(getString(R.string.OK), null).create());
    }
    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != IMPORT_FONT) { super.onActivityResultFragment(requestCode, resultCode, data); return; }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || importing) return;
        importing = true;
        Utilities.globalQueue.postRunnable(() -> {
            Exception error = null;
            try { ApplicationFonts.importFont(data.getData()); } catch (Exception e) { error = e; }
            Exception result = error;
            AndroidUtilities.runOnUIThread(() -> {
                importing = false;
                if (getParentActivity() == null) return;
                if (result != null) { error(result); return; }
                refresh();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.RegramFontsImportedDone)).show();
            });
        });
    }

    private final class FontRow extends FrameLayout {
        final TextView name, kind;
        final ImageView eye, handle;
        FontRow(Context context) {
            super(context);
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            handle = new ImageView(context); handle.setImageResource(R.drawable.list_reorder);
            handle.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
            handle.setContentDescription(getString(R.string.RegramFontsReorder));
            addView(handle, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
            LinearLayout text = new LinearLayout(context); text.setOrientation(LinearLayout.VERTICAL);
            name = new TextView(context); name.setTextSize(18); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
            name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setTag(R.id.regram_font_preview, true);
            kind = new TextView(context); kind.setTextSize(10); kind.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            text.addView(name); text.addView(kind);
            addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 64, 0, 56, 0));
            eye = new ImageView(context); eye.setImageResource(R.drawable.regram_font_eye); eye.setPadding(dp(12), dp(12), dp(12), dp(12));
            eye.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
            addView(eye, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        }
        @Override protected void onMeasure(int width, int height) { super.onMeasure(width, MeasureSpec.makeMeasureSpec(dp(72), MeasureSpec.EXACTLY)); }
        void bind(PaintTypeface font, RecyclerView.ViewHolder holder) {
            name.setText(font.getName()); name.setTypeface(font.getTypeface());
            kind.setText(getString(ApplicationFonts.isImported(font.getKey()) ? R.string.RegramFontsImported
                    : PaintTypeface.BUILT_IN_FONTS.contains(font) ? R.string.RegramFontsBuiltin : R.string.RegramFontsSystemLabel));
            boolean hidden = ApplicationFonts.hidden(font.getKey());
            name.setAlpha(hidden ? 0.45f : 1f); eye.setAlpha(hidden ? 0.35f : 1f);
            eye.setContentDescription(getString(hidden ? R.string.RegramFontsShow : R.string.RegramFontsHide));
            eye.setOnClickListener(v -> { ApplicationFonts.toggleHidden(font.getKey()); refresh(); });
            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) touchHelper.startDrag(holder);
                return true;
            });
        }
    }
    private final class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) { super(context); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_FONT || super.isEnabled(holder);
        }
        @Override public int getItemViewType(int position) {
            if (position == applicationRow) return TYPE_SETTINGS;
            if (position == headerRow) return TYPE_HEADER;
            if (position == systemRow) return TYPE_CHECK;
            if (position >= fontsStart && position < fontsEnd) return TYPE_FONT;
            if (position == addRow || position == resetRow) return TYPE_TEXT;
            return TYPE_INFO_PRIVACY;
        }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            return type == TYPE_FONT ? new RecyclerListView.Holder(new FontRow(parent.getContext())) : super.onCreateViewHolder(parent, type);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (position == applicationRow) {
                ((TextSettingsCell) holder.itemView).setTextAndValue(getString(R.string.RegramFontsApplication),
                        ApplicationFonts.selectedName(getString(R.string.RegramFontsDefault)), false);
            } else if (position == headerRow) {
                ((HeaderCell) holder.itemView).setText(getString(R.string.RegramFontsAvailable));
            } else if (position == systemRow) {
                ((TextCheckCell) holder.itemView).setTextAndValueAndCheck(getString(R.string.RegramFontsSystem),
                        getString(R.string.RegramFontsSystemInfo), ApplicationFonts.systemFonts(), true, true);
            } else if (position >= fontsStart && position < fontsEnd) {
                ((FontRow) holder.itemView).bind(fonts.get(position - fontsStart), holder);
            } else if (position == addRow || position == resetRow) {
                ((TextCell) holder.itemView).setTextAndIcon(getString(position == addRow ? R.string.RegramFontsAdd : R.string.RegramFontsReset),
                        position == addRow ? R.drawable.msg_add : R.drawable.msg_reset, position == addRow);
            } else {
                ((TextInfoPrivacyCell) holder.itemView).setText(getString(position == applicationInfoRow ? R.string.RegramFontsApplicationInfo
                        : position == editorInfoRow ? R.string.RegramFontsEditorInfo : R.string.RegramFontsImportInfo));
            }
        }
    }
}
