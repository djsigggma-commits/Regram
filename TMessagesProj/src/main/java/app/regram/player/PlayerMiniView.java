package app.regram.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;

import app.regram.appearance.AppearanceConfig;

public class PlayerMiniView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int HEIGHT_DP = 64;
    private static final int COMPACT_HEIGHT_DP = 52;
    private static final ArrayList<PlayerMiniView> attachedViews = new ArrayList<>();

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final boolean preview;
    private boolean dark;
    private int seed;
    private final LinearLayout card;
    private final GradientDrawable cardBg = new GradientDrawable();
    private final CoverImage cover;
    private final LinearLayout texts;
    private final TextView titleView;
    private final TextView artistView;
    private final RingPlayButton playButton;
    private final ImageView prevButton;
    private final ImageView nextButton;
    private final ImageView closeButton;
    private final PlayerIcon prevIcon = PlayerIcon.fill(PlayerIcon.PREV, 24);
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 24);
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 22);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private PlayerColors colors;
    private ValueAnimator colorAnimator;
    private ValueAnimator showAnimator;
    private int heightDp = HEIGHT_DP;
    private float cardRadius = dp(20);
    private int coverRadius = dp(12);
    private int progressStyle;
    private float drawnLineProgress = -1f;
    private final Path borderPath = new Path();
    private final Path borderSegment = new Path();
    private final PathMeasure borderMeasure = new PathMeasure();
    private final RectF borderRect = new RectF();
    private int borderKey;
    private float borderLength;
    private CharSequence titleText;
    private CharSequence artistText;
    private final CharacterStyle artistSpan = new CharacterStyle() {
        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor(ColorUtils.setAlphaComponent(colors.onPrimaryContainer, 0xcc));
        }
    };
    private MessageObject current;
    private boolean shown;
    private float showProgress;
    private float hostFactor = 1f;
    private float baseTranslation;
    private Runnable offsetListener;
    private boolean dockTop;
    private float dockProgress;
    private float topTranslation;
    private boolean hasTopTranslation;
    private float topZone = 1f;
    private float topShift;
    private float topVisibility = 1f;
    private boolean dragging;
    private float dragStartY;
    private float dragOffset;
    private float lastTouchY;
    private VelocityTracker velocityTracker;
    private ValueAnimator dockAnimator;
    private ValueAnimator liftAnimator;
    private float lift;

    public PlayerMiniView(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        this(context, fragment, resourcesProvider, false);
    }

    public static PlayerMiniView preview(Context context) {
        return new PlayerMiniView(context, null, null, true);
    }

    private PlayerMiniView(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, boolean preview) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        this.preview = preview;
        dark = PlayerColors.isDark(resourcesProvider);
        seed = PlayerColors.fallbackSeed(resourcesProvider);
        colors = PlayerColors.fromSeed(seed, dark);
        setClipChildren(false);
        setClipToPadding(false);

        card = new LinearLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                drawProgressLine(canvas, getWidth(), getHeight());
            }
        };
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), 0, dp(6), 0);
        cardBg.setCornerRadius(cardRadius);
        card.setBackground(cardBg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardRadius);
            }
        });
        card.setClipToOutline(true);
        card.setElevation(dp(6));
        if (!preview) {
            card.setOnClickListener(v -> Md3Player.open(fragment, this));
            card.setOnLongClickListener(v -> startDrag());
            card.setOnTouchListener(this::onCardTouch);
            card.setContentDescription(getString(R.string.OEPlayerOpen));
            dockTop = PlayerStyle.miniTop();
            dockProgress = dockTop ? 1f : 0f;
        }
        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEIGHT_DP, Gravity.BOTTOM, 12, 0, 12, 12));

        cover = new CoverImage(context, 22);
        cover.setRadius(coverRadius);
        card.addView(cover, LayoutHelper.createLinear(44, 44));

        texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        artistView.setAlpha(0.8f);
        texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 4, 0));

        prevButton = addButton(context, prevIcon, R.string.AccDescrPrevious, () -> MediaController.getInstance().playPreviousMessage());

        playButton = new RingPlayButton(context, 20, 3, 22, 1.3f, 12);
        if (!preview) {
            playButton.setOnClickListener(v -> {
                MediaController mc = MediaController.getInstance();
                MessageObject mo = mc.getPlayingMessageObject();
                if (mo == null || mc.isDownloadingCurrentMessage()) {
                    return;
                }
                if (mc.isMessagePaused()) {
                    mc.playMessage(mo);
                } else {
                    mc.pauseMessage(mo);
                }
            });
        }
        card.addView(playButton, LayoutHelper.createLinear(48, 48));

        nextButton = addButton(context, nextIcon, R.string.Next, () -> MediaController.getInstance().playNextMessage());
        closeButton = addButton(context, closeIcon, R.string.AccDescrClosePlayer, () -> MediaController.getInstance().cleanupPlayer(true, true));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(dp(3));

        applyColors(colors);
        applyStyle(false);
        setVisibility(GONE);
    }

    private ImageView addButton(Context context, PlayerIcon icon, int descriptionRes, Runnable action) {
        ImageView button = new ImageView(context);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setImageDrawable(icon);
        button.setContentDescription(getString(descriptionRes));
        if (!preview) {
            button.setOnClickListener(v -> action.run());
        }
        card.addView(button, LayoutHelper.createLinear(44, 48));
        return button;
    }

    public static void styleChanged() {
        for (int i = 0; i < attachedViews.size(); i++) {
            attachedViews.get(i).applyStyle(true);
        }
    }

    public void applyStyle(boolean animated) {
        boolean compact = PlayerStyle.miniCompact();
        int height = compact ? COMPACT_HEIGHT_DP : HEIGHT_DP;
        int coverSize = compact ? 36 : 44;
        int coverMode = PlayerStyle.miniCover();
        int shape = PlayerStyle.miniShape();
        boolean heightChanged = height != heightDp;
        heightDp = height;
        FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) card.getLayoutParams();
        if (cardParams.height != dp(height)) {
            cardParams.height = dp(height);
            card.setLayoutParams(cardParams);
        }
        card.setPadding(dp((height - coverSize) / 2f), 0, dp(6), 0);
        cardRadius = shape == PlayerStyle.MINI_SHAPE_PILL ? dp(height) / 2f : dp(shape == PlayerStyle.MINI_SHAPE_SQUARE ? 10 : 20);
        cardBg.setCornerRadius(cardRadius);
        card.invalidateOutline();

        boolean coverVisible = coverMode != PlayerStyle.MINI_COVER_HIDDEN;
        cover.setVisibility(coverVisible ? VISIBLE : GONE);
        LinearLayout.LayoutParams coverParams = (LinearLayout.LayoutParams) cover.getLayoutParams();
        if (coverParams.width != dp(coverSize)) {
            coverParams.width = dp(coverSize);
            coverParams.height = dp(coverSize);
            cover.setLayoutParams(coverParams);
        }
        coverRadius = coverMode == PlayerStyle.MINI_COVER_ROUND ? dp(coverSize) / 2 : dp(compact ? 10 : 12);
        cover.setRadius(coverRadius);
        LinearLayout.LayoutParams textParams = (LinearLayout.LayoutParams) texts.getLayoutParams();
        int textStart = coverVisible ? dp(12) : dp(shape == PlayerStyle.MINI_SHAPE_PILL ? 10 : 6);
        if (textParams.leftMargin != textStart) {
            textParams.leftMargin = textStart;
            texts.setLayoutParams(textParams);
        }

        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, compact ? 14 : 15);
        bindTexts();
        prevButton.setVisibility(PlayerStyle.miniPrevious() ? VISIBLE : GONE);
        nextButton.setVisibility(PlayerStyle.miniNext() ? VISIBLE : GONE);
        closeButton.setVisibility(PlayerStyle.miniClose() ? VISIBLE : GONE);
        progressStyle = PlayerStyle.miniProgress();
        playButton.setRingVisible(progressStyle == PlayerStyle.MINI_PROGRESS_RING);
        card.invalidate();

        cover.setListener(seed, PlayerStyle.miniThemeColors() ? null : this::onSeed);
        refreshColors(animated);
        if (heightChanged) {
            notifyOffset();
        }
    }

    private void refreshColors(boolean animated) {
        if (PlayerStyle.miniThemeColors() || current == null) {
            setTargetColors(PlayerColors.fromSeed(seed, dark), animated);
            return;
        }
        Integer cached = PlayerArt.cachedSeed(current);
        if (cached != null) {
            setTargetColors(PlayerColors.fromSeed(cached, dark), animated);
            return;
        }
        Bitmap bitmap = cover.getImageReceiver().getBitmap();
        if (bitmap != null) {
            MessageObject target = current;
            PlayerArt.requestSeed(target, bitmap, seed, value -> onSeed(target, value));
        }
    }

    private void onSeed(MessageObject mo, int value) {
        if (mo == current && !PlayerStyle.miniThemeColors()) {
            setTargetColors(PlayerColors.fromSeed(value, dark), isAttachedToWindow());
        }
    }

    private void setTargetColors(PlayerColors target, boolean animated) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        if (target.primaryContainer == colors.primaryContainer && target.onPrimaryContainer == colors.onPrimaryContainer) {
            return;
        }
        if (!animated || !isAttachedToWindow()) {
            applyColors(target);
            return;
        }
        PlayerColors from = colors;
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.addUpdateListener(a -> applyColors(PlayerColors.lerp(from, target, (float) a.getAnimatedValue())));
        colorAnimator.setDuration(320);
        colorAnimator.start();
    }

    private void bindTexts() {
        CharSequence title = titleText != null ? titleText : "";
        boolean showArtist = PlayerStyle.miniArtist() && !TextUtils.isEmpty(artistText);
        if (PlayerStyle.miniCompact()) {
            SpannableStringBuilder text = new SpannableStringBuilder(title);
            text.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (showArtist) {
                int start = text.length();
                text.append(" · ").append(artistText);
                text.setSpan(artistSpan, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            titleView.setTypeface(null);
            titleView.setText(text);
            artistView.setVisibility(GONE);
        } else {
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(title);
            artistView.setText(artistText);
            artistView.setVisibility(showArtist ? VISIBLE : GONE);
        }
    }

    private void drawProgressLine(Canvas canvas, int w, int h) {
        if (progressStyle != PlayerStyle.MINI_PROGRESS_LINE && progressStyle != PlayerStyle.MINI_PROGRESS_BORDER) {
            return;
        }
        MessageObject mo = current;
        float progress = mo != null ? Utilities.clamp01(mo.audioProgress) : preview ? 0.36f : 0f;
        drawnLineProgress = progress;
        if (progressStyle == PlayerStyle.MINI_PROGRESS_BORDER) {
            drawProgressBorder(canvas, w, h, progress);
            return;
        }
        float inset = Math.max(dp(16), cardRadius);
        float y = h - dp(4);
        float right = w - inset;
        linePaint.setColor(ColorUtils.setAlphaComponent(colors.onPrimaryContainer, 46));
        canvas.drawLine(inset, y, right, y, linePaint);
        if (progress > 0f) {
            linePaint.setColor(colors.onPrimaryContainer);
            canvas.drawLine(inset, y, inset + (right - inset) * progress, y, linePaint);
        }
    }

    private void drawProgressBorder(Canvas canvas, int w, int h, float progress) {
        float inset = dp(3) / 2f;
        int key = (w * 31 + h) * 31 + Float.floatToIntBits(cardRadius);
        if (key != borderKey) {
            borderKey = key;
            buildBorderPath(w, h, inset, Math.max(dp(2), cardRadius - inset));
            borderMeasure.setPath(borderPath, false);
            borderLength = borderMeasure.getLength();
        }
        linePaint.setColor(ColorUtils.setAlphaComponent(colors.onPrimaryContainer, 46));
        canvas.drawPath(borderPath, linePaint);
        if (progress > 0f) {
            borderSegment.rewind();
            borderMeasure.getSegment(0, borderLength * progress, borderSegment, true);
            linePaint.setColor(colors.onPrimaryContainer);
            canvas.drawPath(borderSegment, linePaint);
        }
    }

    private void buildBorderPath(int w, int h, float inset, float radius) {
        float left = inset;
        float top = inset;
        float right = w - inset;
        float bottom = h - inset;
        float r = Math.min(radius, Math.min(right - left, bottom - top) / 2f);
        float cx = w / 2f;
        borderPath.rewind();
        borderPath.moveTo(cx, top);
        borderPath.lineTo(right - r, top);
        borderRect.set(right - 2 * r, top, right, top + 2 * r);
        borderPath.arcTo(borderRect, -90, 90, false);
        borderPath.lineTo(right, bottom - r);
        borderRect.set(right - 2 * r, bottom - 2 * r, right, bottom);
        borderPath.arcTo(borderRect, 0, 90, false);
        borderPath.lineTo(left + r, bottom);
        borderRect.set(left, bottom - 2 * r, left + 2 * r, bottom);
        borderPath.arcTo(borderRect, 90, 90, false);
        borderPath.lineTo(left, top + r);
        borderRect.set(left, top, left + 2 * r, top + 2 * r);
        borderPath.arcTo(borderRect, 180, 90, false);
        borderPath.lineTo(cx, top);
    }

    private void lineProgressChanged() {
        MessageObject mo = current;
        if (mo == null || progressStyle != PlayerStyle.MINI_PROGRESS_LINE && progressStyle != PlayerStyle.MINI_PROGRESS_BORDER) {
            return;
        }
        float progress = Utilities.clamp01(mo.audioProgress);
        float length = progressStyle == PlayerStyle.MINI_PROGRESS_BORDER ? borderLength : card.getWidth();
        if (Math.abs(progress - drawnLineProgress) * length >= 1f) {
            card.invalidate();
        }
    }

    public boolean canTransition() {
        return !preview && isAttachedToWindow() && getVisibility() == VISIBLE && shown && showProgress >= 1f && visibility() >= 0.99f && card.getWidth() > 0 && cover.getVisibility() == VISIBLE
                && !dragging && dockAnimator == null && lift == 0f;
    }

    public void getCardRect(RectF out) {
        locate(card, out);
    }

    public void getCoverRect(RectF out) {
        locate(cover, out);
    }

    private static void locate(View view, RectF out) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        out.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
    }

    public float getCardRadius() {
        return cardRadius;
    }

    public float getCoverRadius() {
        return coverRadius;
    }

    public int getCardColor() {
        return colors.primaryContainer;
    }

    public Bitmap getCoverBitmap() {
        return cover.getImageReceiver().getBitmap();
    }

    public Bitmap captureCard() {
        int w = card.getWidth();
        int h = card.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int visibility = cover.getVisibility();
        try {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            cover.setVisibility(INVISIBLE);
            card.setPressed(false);
            card.jumpDrawablesToCurrentState();
            card.draw(canvas);
            return bitmap;
        } catch (Throwable e) {
            return null;
        } finally {
            cover.setVisibility(visibility);
        }
    }

    public void setTransitionHidden(boolean hidden) {
        card.setAlpha(hidden ? 0f : 1f);
    }

    public void setOffsetListener(Runnable listener) {
        offsetListener = listener;
    }

    public float getVisibleOffset() {
        return (dp(heightDp) + dp(8)) * showProgress * hostFactor * (1f - Utilities.clamp01(dockProgress) * topZone);
    }

    public float getVisibleOffset(boolean chats) {
        return (dp(heightDp) + dp(8)) * showProgress * hostFactor * (chats ? 1f - Utilities.clamp01(dockProgress) : 1f);
    }

    public int getTargetOffset(boolean chats) {
        return shown && hostFactor > 0.5f && !(chats && dockTop) ? dp(heightDp) + dp(8) : 0;
    }

    public int getTargetTopOffset(boolean chats) {
        return chats && shown && dockTop ? dp(heightDp) + dp(16) : 0;
    }

    public void setTopState(float anchor, float zone, float shift, float visibility) {
        if (preview || getHeight() == 0) {
            return;
        }
        float translation = anchor + dp(8) - getTop() - card.getTop();
        hasTopTranslation = true;
        if (topTranslation != translation || topZone != zone || topShift != shift || topVisibility != visibility) {
            topTranslation = translation;
            topZone = zone;
            topShift = shift;
            topVisibility = visibility;
            if (dockProgress != 0f || dragging) {
                applyTransform();
            }
        }
    }

    private boolean atTop() {
        return Utilities.clamp01(dockProgress) > 0f && topZone >= 0.5f;
    }

    private float visibility() {
        float dock = Utilities.clamp01(dockProgress);
        if (dock == 0f) {
            return hostFactor;
        }
        float swap = Math.min(1f, Math.abs(topZone - 0.5f) * 2f);
        return (atTop() ? AndroidUtilities.lerp(hostFactor, topVisibility, dock) : hostFactor) * swap;
    }

    private float dockTranslation() {
        return baseTranslation + (topTranslation - baseTranslation) * dockProgress;
    }

    private boolean onCardTouch(View v, MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            } else {
                velocityTracker.clear();
            }
        }
        if (velocityTracker != null) {
            MotionEvent copy = MotionEvent.obtain(ev);
            copy.setLocation(ev.getRawX(), ev.getRawY());
            velocityTracker.addMovement(copy);
            copy.recycle();
        }
        lastTouchY = ev.getRawY();
        if (!dragging) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            setDragOffset(lastTouchY - dragStartY);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            float velocity = 0f;
            if (action == MotionEvent.ACTION_UP && velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000);
                velocity = velocityTracker.getYVelocity();
            }
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            card.onTouchEvent(cancel);
            cancel.recycle();
            endDrag(velocity);
        }
        return true;
    }

    private boolean startDrag() {
        if (dragging || !shown || showProgress < 1f || hostFactor < 0.99f || topVisibility < 0.99f || topZone < 0.99f || !hasTopTranslation) {
            return false;
        }
        if (dockAnimator != null) {
            dockAnimator.cancel();
        }
        dragging = true;
        dragStartY = lastTouchY;
        dragOffset = 0f;
        card.setPressed(false);
        if (card.getParent() != null) {
            card.getParent().requestDisallowInterceptTouchEvent(true);
        }
        animateLift(true);
        return true;
    }

    private void setDragOffset(float offset) {
        float base = dockTranslation();
        float min = Math.min(baseTranslation, topTranslation) - dp(16);
        float max = Math.max(baseTranslation, topTranslation) + dp(16);
        dragOffset = Math.max(min, Math.min(max, base + offset)) - base;
        applyTransform();
    }

    private void endDrag(float velocity) {
        dragging = false;
        float range = baseTranslation - topTranslation;
        float position = dockTranslation() + dragOffset;
        float progress = range > 0 ? (baseTranslation - position) / range : dockProgress;
        boolean top = Math.abs(velocity) > dp(800) ? velocity < 0 : progress > 0.5f;
        dragOffset = 0f;
        dockProgress = progress;
        animateLift(false);
        setDockTop(top);
    }

    private void setDockTop(boolean top) {
        if (dockTop != top) {
            dockTop = top;
            AppearanceConfig.playerMiniTop.setConfigBool(top);
        }
        if (dockAnimator != null) {
            dockAnimator.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(dockProgress, top ? 1f : 0f);
        animator.addUpdateListener(a -> {
            dockProgress = (float) a.getAnimatedValue();
            applyTransform();
            notifyOffset();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (dockAnimator == animation) {
                    dockAnimator = null;
                }
            }
        });
        animator.setDuration(380);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        dockAnimator = animator;
        animator.start();
        notifyOffset();
    }

    private void animateLift(boolean up) {
        if (liftAnimator != null) {
            liftAnimator.cancel();
        }
        liftAnimator = ValueAnimator.ofFloat(lift, up ? 1f : 0f);
        liftAnimator.addUpdateListener(a -> setLift((float) a.getAnimatedValue()));
        liftAnimator.setDuration(up ? 180 : 260);
        liftAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        liftAnimator.start();
    }

    private void setLift(float value) {
        lift = value;
        float scale = 1f + 0.03f * value;
        card.setScaleX(scale);
        card.setScaleY(scale);
        card.setTranslationZ(dp(8) * value);
    }

    public void setHostPosition(float translation, float factor) {
        baseTranslation = translation;
        float old = hostFactor;
        hostFactor = factor;
        applyTransform();
        if (old != factor) {
            notifyOffset();
        }
    }

    private void applyTransform() {
        boolean top = atTop();
        float p = showProgress * visibility();
        float direction = top ? 1f - 2f * Utilities.clamp01(dockProgress) : 1f;
        setTranslationX(top ? topShift : 0f);
        setTranslationY((top ? dockTranslation() : baseTranslation) + dragOffset + dp(24) * (1f - p) * direction);
        setAlpha(p);
        setVisibility(p > 0f ? VISIBLE : GONE);
    }

    private void notifyOffset() {
        if (offsetListener != null) {
            offsetListener.run();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyStyle(false);
        if (preview) {
            bindPreview();
            return;
        }
        attachedViews.add(this);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(this, NotificationCenter.messagePlayingDidReset);
            nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.addObserver(this, NotificationCenter.fileLoaded);
            nc.addObserver(this, NotificationCenter.audioInfoLoaded);
        }
        update(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        if (dockAnimator != null) {
            dockAnimator.cancel();
            dockProgress = dockTop ? 1f : 0f;
        }
        if (liftAnimator != null) {
            liftAnimator.cancel();
            liftAnimator = null;
        }
        if (dragging) {
            dragging = false;
            dragOffset = 0f;
        }
        setLift(0f);
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        applyTransform();
        if (preview) {
            return;
        }
        attachedViews.remove(this);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidReset);
            nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.removeObserver(this, NotificationCenter.fileLoaded);
            nc.removeObserver(this, NotificationCenter.audioInfoLoaded);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            playButton.progressChanged();
            lineProgressChanged();
        } else if (id == NotificationCenter.audioInfoLoaded) {
            MessageObject mo = current;
            if (mo != null && PlayerArt.isPlaying(mo)) {
                cover.setMessage(mo);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            MessageObject mo = current;
            if (mo != null && mo.getDocument() != null && TextUtils.equals((String) args[0], FileLoader.getAttachFileName(mo.getDocument()))) {
                cover.setMessage(mo);
            }
        } else {
            update(true);
        }
    }

    private void checkThemeColors() {
        boolean themeDark = PlayerColors.isDark(resourcesProvider);
        int themeSeed = PlayerColors.fallbackSeed(resourcesProvider);
        if (themeDark != dark || themeSeed != seed) {
            dark = themeDark;
            seed = themeSeed;
            cover.setListener(seed, PlayerStyle.miniThemeColors() ? null : this::onSeed);
            refreshColors(false);
        }
    }

    public void bindPreview() {
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        if (mo != null && mo.isMusic() && mo.getId() != 0) {
            bind(mo);
        } else {
            current = null;
            titleText = getString(R.string.OEPlayerPreviewTitle);
            artistText = getString(R.string.OEPlayerPreviewArtist);
            bindTexts();
            cover.setMessage(null);
            playButton.setMessage(null);
        }
        playButton.setPlaying(false, false);
        shown = true;
        showProgress = 1f;
        applyTransform();
        refreshColors(false);
    }

    public void update(boolean animated) {
        checkThemeColors();
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        boolean want = Md3Player.miniEnabled() && mo != null && mo.isMusic() && mo.getId() != 0;
        if (want) {
            bind(mo);
        }
        playButton.setPlaying(want && !MediaController.getInstance().isMessagePaused(), animated);
        setShown(want, animated);
    }

    private void bind(MessageObject mo) {
        boolean changed = current != mo;
        current = mo;
        titleText = mo.getMusicTitle();
        artistText = mo.getMusicAuthor();
        bindTexts();
        cover.setMessage(mo);
        playButton.setMessage(mo);
        playButton.setContentDescription(getString(MediaController.getInstance().isMessagePaused() ? R.string.AccActionPlay : R.string.AccActionPause));
        if (changed) {
            drawnLineProgress = -1f;
            card.invalidate();
            refreshColors(isAttachedToWindow());
        }
    }

    private void setShown(boolean value, boolean animated) {
        if (shown == value && (showAnimator != null || showProgress == (value ? 1f : 0f))) {
            return;
        }
        shown = value;
        if (showAnimator != null) {
            showAnimator.cancel();
            showAnimator = null;
        }
        float target = value ? 1f : 0f;
        if (!animated || !isAttachedToWindow()) {
            showProgress = target;
            applyTransform();
            notifyOffset();
            return;
        }
        showAnimator = ValueAnimator.ofFloat(showProgress, target);
        showAnimator.addUpdateListener(a -> {
            showProgress = (float) a.getAnimatedValue();
            applyTransform();
            notifyOffset();
        });
        showAnimator.setDuration(value ? 450 : 300);
        showAnimator.setInterpolator(value ? new CubicBezierInterpolator(0.05, 0.7, 0.1, 1) : new CubicBezierInterpolator(0.3, 0, 0.8, 0.15));
        showAnimator.start();
    }

    private void applyColors(PlayerColors c) {
        colors = c;
        cardBg.setColor(c.primaryContainer);
        if (Build.VERSION.SDK_INT >= 28) {
            card.setOutlineSpotShadowColor(c.shadow());
            card.setOutlineAmbientShadowColor(c.shadow());
        }
        cover.setColors(c.secondaryContainer, c.onSecondaryContainer);
        titleView.setTextColor(c.onPrimaryContainer);
        artistView.setTextColor(c.onPrimaryContainer);
        prevIcon.setColor(c.onPrimaryContainer);
        nextIcon.setColor(c.onPrimaryContainer);
        closeIcon.setColor(c.onPrimaryContainer);
        int ripple = ColorUtils.setAlphaComponent(c.onPrimaryContainer, 0x1f);
        prevButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        nextButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        closeButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playButton.setColors(c.onPrimaryContainer, ColorUtils.setAlphaComponent(c.onPrimaryContainer, 46), c.onPrimaryContainer);
        card.setForeground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_ALL));
        card.invalidate();
    }
}
