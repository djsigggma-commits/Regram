package app.regram.plugins.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import java.util.List;

/**
 * Строка разрешения: подпись, управляющий элемент и раскрывашка с уликами.
 *
 * Одна и та же строка нужна в двух местах — в листе установки (галочка) и на
 * экране разрешений (тумблер), — и в обоих человек спрашивает одно и то же:
 * «а почему приложение решило, что плагин это просит». Раньше улики были
 * свалены одной серой строкой над списком: по ней нельзя понять, какая находка
 * к какому разрешению относится. Здесь у каждой строки своя стрелка справа, а
 * под ней — те самые имена из исходника.
 *
 * Технические имена ({@code requests}, {@code SendMessagesHelper}) не
 * переводятся намеренно: их можно поискать в файле плагина и проверить руками.
 */
public class PluginPermissionCell extends FrameLayout {

    /** Галочка — лист установки: выбираем, что выдать. */
    public static final int TYPE_CHECKBOX = 0;
    /** Тумблер — экран разрешений: меняем уже выданное. */
    public static final int TYPE_SWITCH = 1;

    private final LinearLayout root;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView evidenceView;
    private final ImageView expandView;
    private final CheckBox2 checkBox;
    private final Switch switchView;

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean needDivider;

    private String permission;
    private boolean expanded;
    private boolean hasEvidence;
    private Runnable onExpandChanged;

    public PluginPermissionCell(Context context, int type) {
        super(context);

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (type == TYPE_CHECKBOX) {
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(Theme.key_checkbox, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(true);
            checkBox.setDrawBackgroundAsArc(10);
            switchView = null;
            row.addView(checkBox, LayoutHelper.createLinear(21, 21, Gravity.CENTER_VERTICAL,
                    21, 0, 13, 0));
        } else {
            checkBox = null;
            switchView = new Switch(context);
            switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
                    Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        }

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f,
                Gravity.CENTER_VERTICAL, type == TYPE_CHECKBOX ? 0 : 21, 10, 0, 10));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        subtitleView.setVisibility(GONE);
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        expandView = new ImageView(context);
        expandView.setScaleType(ImageView.ScaleType.CENTER);
        expandView.setImageResource(R.drawable.arrow_more);
        expandView.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        expandView.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(18)));
        expandView.setOnClickListener(v -> setExpanded(!expanded, true));
        row.addView(expandView, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL,
                4, 0, type == TYPE_SWITCH ? 6 : 12, 0));

        if (switchView != null) {
            // 21dp — как у штатного TextCheckCell: тумблер стоит на одной
            // вертикали с тумблерами остальных экранов, а не впритык к краю
            // карточки секции.
            row.addView(switchView, LayoutHelper.createLinear(37, 40, Gravity.CENTER_VERTICAL,
                    0, 0, 21, 0));
        }

        evidenceView = new TextView(context);
        evidenceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        evidenceView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        evidenceView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), 0.10f)));
        evidenceView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(9),
                AndroidUtilities.dp(12), AndroidUtilities.dp(9));
        evidenceView.setVisibility(GONE);
        root.addView(evidenceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, type == TYPE_CHECKBOX ? 21 : 21, 0, 21, 12));

        setWillNotDraw(false);
    }

    public void setOnExpandChanged(Runnable listener) {
        onExpandChanged = listener;
    }

    /** Клик по строке: у галочки — переключение, у тумблера — то же самое. */
    public void setOnToggle(Runnable listener) {
        setOnClickListener(listener == null ? null : v -> listener.run());
        setBackground(listener == null ? null
                : Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
    }

    public String getPermission() {
        return permission;
    }

    public boolean isChecked() {
        if (checkBox != null) {
            return checkBox.isChecked();
        }
        return switchView != null && switchView.isChecked();
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox != null) {
            checkBox.setChecked(checked, animated);
        } else if (switchView != null) {
            switchView.setChecked(checked, animated);
        }
    }

    public void setEnabledState(boolean enabled) {
        setEnabled(enabled);
        final float alpha = enabled ? 1f : 0.5f;
        titleView.setAlpha(alpha);
        subtitleView.setAlpha(alpha);
        if (checkBox != null) {
            checkBox.setAlpha(alpha);
        }
        if (switchView != null) {
            switchView.setAlpha(alpha);
        }
    }

    /**
     * @param evidence имена из исходника, по которым разрешение вообще попало
     *                 в список; пустой список — стрелки нет, раскрывать нечего.
     */
    public void set(String permission, CharSequence title, CharSequence subtitle,
                    List<String> evidence, boolean divider) {
        this.permission = permission;
        needDivider = divider;
        titleView.setText(title);
        if (TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(GONE);
        } else {
            subtitleView.setVisibility(VISIBLE);
            subtitleView.setText(subtitle);
        }
        hasEvidence = evidence != null && !evidence.isEmpty();
        expandView.setVisibility(hasEvidence ? VISIBLE : GONE);
        if (hasEvidence) {
            evidenceView.setText(LocaleController.formatString(R.string.PluginsPermissionFound,
                    TextUtils.join(", ", evidence)));
        }
        setExpanded(false, false);
    }

    public void setExpanded(boolean value, boolean animated) {
        expanded = value && hasEvidence;
        evidenceView.setVisibility(expanded ? VISIBLE : GONE);
        if (animated) {
            expandView.animate().rotation(expanded ? 180 : 0).setDuration(180).start();
        } else {
            expandView.setRotation(expanded ? 180 : 0);
        }
        if (animated && onExpandChanged != null) {
            onExpandChanged.run();
        }
        requestLayout();
    }

    public boolean isExpanded() {
        return expanded;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!needDivider) {
            return;
        }
        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        final int inset = AndroidUtilities.dp(21);
        canvas.drawRect(LocaleController.isRTL ? 0 : inset, getMeasuredHeight() - 1,
                LocaleController.isRTL ? getMeasuredWidth() - inset : getMeasuredWidth(),
                getMeasuredHeight(), dividerPaint);
    }
}
