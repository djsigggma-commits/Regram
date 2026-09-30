package app.regram.icons.picker;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.List;

import app.regram.icons.IconPack;
import app.regram.icons.IconPackManager;
import app.regram.icons.IconPackStorage;
import app.regram.icons.IconPacksConfig;

/**
 * Плавающий пикер иконок (порт {@code com.exteragram.messenger.icons.ui.picker.IconPickerView}).
 *
 * Живёт поверх интерфейса: перетаскиваемая кнопка, по нажатию раскрывающаяся в панель со списком
 * иконок, которые запросил текущий экран ({@link IconObserver}). Тап по иконке открывает
 * {@link ReplaceIconBottomSheet} — точечную замену внутри редактируемого пака.
 *
 * exteraGram использует пружинные анимации {@code DynamicAnimation} и {@code UniversalRecyclerView};
 * здесь то же поведение собрано на {@code ViewPropertyAnimator} и {@link RecyclerListView},
 * чтобы не тянуть зависимостей за пределы модуля.
 *
 * Пустой список — не ошибка: значит, текущий экран ещё не запрашивал ни одной заменяемой иконки.
 */
@SuppressLint("ViewConstructor")
public class IconPickerView extends FrameLayout {

    private static final String PREFS = "oe_icon_picker";
    private static final String KEY_FAB_X = "fabX";
    private static final String KEY_FAB_Y = "fabY";

    private final SharedPreferences prefs;
    private final int touchSlop;

    private final FrameLayout fab;
    private final ImageView fabIcon;

    private final FrameLayout panel;
    private final TextView titleView;
    private final TextView subtitleView;
    private final RecyclerListView listView;
    private final ListAdapter adapter;

    private final List<Integer> items = new ArrayList<>();

    private boolean expanded;
    private boolean dismissing;

    public IconPickerView(@NonNull Context context) {
        super(context);
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setWillNotDraw(false);

        // ---- панель ----
        panel = new FrameLayout(context) {
            private final RectF rect = new RectF();
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                rect.set(0, 0, getWidth(), getHeight());
                paint.setColor(Theme.getColor(Theme.key_dialogBackground));
                canvas.drawRoundRect(rect, dp(16), dp(16), paint);
            }
        };
        panel.setWillNotDraw(false);
        panel.setVisibility(GONE);
        panel.setClickable(true);
        panel.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        panel.setElevation(dp(6));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT, 16, 12, 96, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT, 16, 34, 96, 0));

        TextView doneButton = new TextView(context);
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButton.setTypeface(AndroidUtilities.bold());
        doneButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setText(getString(R.string.IconPickerFinish));
        doneButton.setPadding(dp(12), 0, dp(12), 0);
        doneButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        doneButton.setOnClickListener(v -> IconPickerController.finishEditing());
        ScaleStateListAnimator.apply(doneButton);
        panel.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28,
                Gravity.TOP | Gravity.RIGHT, 0, 16, 12, 0));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, 4));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(dp(4), 0, dp(4), dp(8));
        adapter = new ListAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            openReplaceSheet(items.get(position));
        });
        panel.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.TOP, 0, 58, 0, 0));

        addView(panel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 320, Gravity.BOTTOM, 8, 0, 8, 8));

        // ---- кнопка ----
        fabIcon = new ImageView(context);
        fabIcon.setScaleType(ImageView.ScaleType.CENTER);
        fabIcon.setImageResource(R.drawable.msg_photoeditor);
        fabIcon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));

        fab = new FrameLayout(context);
        fab.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(52),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        fab.setElevation(dp(4));
        fab.addView(fabIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fab.setAlpha(0f);
        fab.setScaleX(0.6f);
        fab.setScaleY(0.6f);
        fab.setOnTouchListener(new DragTouchListener());
        addView(fab, LayoutHelper.createFrame(52, 52, Gravity.TOP | Gravity.LEFT));
    }

    // ---- прокидывание касаний вниз ----

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // фон пикера прозрачен для касаний: под ним обычный интерфейс, им надо пользоваться
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (fab.getTranslationX() == 0 && fab.getTranslationY() == 0) {
            restoreFabPosition();
        }
    }

    private void restoreFabPosition() {
        float x = prefs.getFloat(KEY_FAB_X, -1);
        float y = prefs.getFloat(KEY_FAB_Y, -1);
        int maxX = Math.max(0, getWidth() - fab.getWidth());
        int maxY = Math.max(0, getHeight() - fab.getHeight());
        if (x < 0 || y < 0) {
            x = maxX - dp(16);
            y = maxY - dp(96);
        }
        fab.setTranslationX(Math.max(0, Math.min(maxX, x)));
        fab.setTranslationY(Math.max(0, Math.min(maxY, y)));
    }

    public void saveConfig() {
        try {
            prefs.edit()
                    .putFloat(KEY_FAB_X, fab.getTranslationX())
                    .putFloat(KEY_FAB_Y, fab.getTranslationY())
                    .apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ---- показ/скрытие ----

    public void showFab() {
        fab.animate().cancel();
        fab.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).setDuration(220).start();
        updateItems();
    }

    public void dismiss(Runnable after) {
        if (dismissing) {
            return;
        }
        dismissing = true;
        saveConfig();
        showIconList(false);
        animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (after != null) {
                after.run();
            }
        }).start();
    }

    public boolean onBackPressed(boolean invoked) {
        if (!expanded) {
            return false;
        }
        if (invoked) {
            showIconList(false);
        }
        return true;
    }

    public void showIconList(boolean show) {
        if (expanded == show) {
            return;
        }
        expanded = show;
        if (show) {
            updateItems();
            panel.setVisibility(VISIBLE);
            panel.setAlpha(0f);
            panel.setTranslationY(dp(24));
            panel.animate().alpha(1f).translationY(0)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(220).start();
            fabIcon.setImageResource(R.drawable.msg_close);
        } else {
            panel.animate().alpha(0f).translationY(dp(24))
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(180)
                    .withEndAction(() -> panel.setVisibility(GONE)).start();
            fabIcon.setImageResource(R.drawable.msg_photoeditor);
        }
    }

    // ---- содержимое ----

    /** Пересобирает список иконок, замеченных на текущем экране. */
    public void updateItems() {
        IconObserver.setSuspended(true);
        try {
            items.clear();
            IconPackManager manager = IconPackManager.getInstance();
            for (Integer resId : IconObserver.getUsedIcons()) {
                String name = manager.getResourceName(resId);
                if (name == null || IconPackManager.isBlacklisted(name)) {
                    continue;
                }
                items.add(resId);
            }
            adapter.notifyDataSetChanged();
            updateTitle();
        } finally {
            IconObserver.setSuspended(false);
        }
    }

    private void updateTitle() {
        IconPack pack = IconPackStorage.findPackById(IconPacksConfig.currentEditingPackId());
        titleView.setText(pack == null ? getString(R.string.IconPacks) : pack.getName());
        subtitleView.setText(org.telegram.messenger.LocaleController.formatPluralString("IconPackIconCount", items.size()));
    }

    private void openReplaceSheet(int resId) {
        String packId = IconPacksConfig.currentEditingPackId();
        if (packId == null) {
            return;
        }
        try {
            ReplaceIconBottomSheet sheet = new ReplaceIconBottomSheet(getContext(), packId, resId, this::updateItems);
            sheet.show();
        } catch (Throwable t) {
            FileLog.e("openExtera: failed to open replace icon sheet", t);
        }
    }

    // ---- перетаскивание кнопки ----

    private class DragTouchListener implements OnTouchListener {

        private float startX, startY, startTranslationX, startTranslationY;
        private boolean dragging;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    startTranslationX = fab.getTranslationX();
                    startTranslationY = fab.getTranslationY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                        dragging = true;
                    }
                    if (dragging) {
                        int maxX = Math.max(0, getWidth() - fab.getWidth());
                        int maxY = Math.max(0, getHeight() - fab.getHeight());
                        fab.setTranslationX(Math.max(0, Math.min(maxX, startTranslationX + dx)));
                        fab.setTranslationY(Math.max(0, Math.min(maxY, startTranslationY + dy)));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        showIconList(!expanded);
                    } else {
                        saveConfig();
                    }
                    dragging = false;
                    return true;
            }
            return false;
        }
    }

    // ---- список ----

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            IconCell cell = new IconCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((IconCell) holder.itemView).bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** Ячейка сетки: сама иконка (уже с учётом активных паков) и имя ресурса под ней. */
    private static class IconCell extends FrameLayout {

        private final ImageView imageView;
        private final TextView nameView;

        IconCell(Context context) {
            super(context);
            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(imageView, LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            nameView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            nameView.setGravity(Gravity.CENTER);
            nameView.setMaxLines(2);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL, 2, 46, 2, 0));
        }

        void bind(int resId) {
            IconPackManager manager = IconPackManager.getInstance();
            String name = manager.getResourceName(resId);
            nameView.setText(name == null ? "" : name);
            Drawable drawable = null;
            try {
                drawable = getContext().getResources().getDrawable(resId).mutate();
            } catch (Throwable t) {
                FileLog.e(t);
            }
            if (drawable != null) {
                drawable.setColorFilter(new PorterDuffColorFilter(
                        Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.SRC_IN));
            }
            imageView.setImageDrawable(drawable);
            imageView.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
