package app.regram.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import app.regram.icons.BaseIconPacks;
import app.regram.icons.IconPackManager;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Статичные иконки поля ввода вместо анимаций RLottie.
 *
 * Анимацию иконпаком не подменишь — в паке лежат обычные картинки, поэтому при активном
 * наборе иконок кнопка эмодзи и кнопка голоса/видео собираются из этого класса: две
 * наложенные ImageView, между которыми состояние перетекает масштабом и прозрачностью.
 */
@SuppressLint("ViewConstructor")
public class ChatActivityEnterViewStaticIconView extends FrameLayout {

    private static final int TRANSITION_DURATION = 200;
    private static final float HIDDEN_SCALE = 0.1f;

    public enum State {
        VOICE(R.drawable.input_mic_pressed),
        VIDEO(R.drawable.input_video_pressed),
        STICKER(R.drawable.msg_sticker),
        KEYBOARD(R.drawable.input_keyboard),
        SMILE(R.drawable.input_smile),
        GIF(R.drawable.msg_gif),
        MENU(R.drawable.ic_ab_other);

        final int resource;

        State(int resource) {
            this.resource = resource;
        }
    }

    private final ImageView[] buttonViews = new ImageView[2];
    private AnimatorSet buttonsAnimation;
    private State currentState;

    public ChatActivityEnterViewStaticIconView(Context context, ChatActivityEnterView enterView) {
        this(context, enterView, 32);
    }

    public ChatActivityEnterViewStaticIconView(Context context, ChatActivityEnterView enterView, int sizeDp) {
        super(context);
        setWillNotDraw(false);
        for (int i = 0; i < buttonViews.length; i++) {
            buttonViews[i] = new ImageView(context);
            buttonViews[i].setColorFilter(new PorterDuffColorFilter(enterView.getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            buttonViews[i].setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(buttonViews[i], LayoutHelper.createFrame(sizeDp, sizeDp, Gravity.CENTER));
        }
        buttonViews[0].setVisibility(VISIBLE);
        buttonViews[1].setVisibility(GONE);
        buttonViews[1].setScaleX(HIDDEN_SCALE);
        buttonViews[1].setScaleY(HIDDEN_SCALE);
    }

    /**
     * Нужны ли статичные иконки: порт {@code IconManager.isBasePackOnly(IconPackType.DEFAULT)}.
     * Анимации остаются, только пока не выбран ни встроенный набор, ни пользовательский пак.
     */
    public static boolean isStaticIconsEnabled() {
        try {
            return BaseIconPacks.getSelected() != BaseIconPacks.BASE_DEFAULT
                    || IconPackManager.getInstance().hasReplacements();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static State fromAnimatedState(ChatActivityEnterViewAnimatedIconView.State state) {
        if (state == null) {
            return null;
        }
        switch (state) {
            case VOICE: return State.VOICE;
            case VIDEO: return State.VIDEO;
            case STICKER: return State.STICKER;
            case KEYBOARD: return State.KEYBOARD;
            case GIF: return State.GIF;
            case MENU: return State.MENU;
            default: return State.SMILE;
        }
    }

    public static ChatActivityEnterViewAnimatedIconView.State toAnimatedState(State state) {
        if (state == null) {
            return null;
        }
        switch (state) {
            case VOICE: return ChatActivityEnterViewAnimatedIconView.State.VOICE;
            case VIDEO: return ChatActivityEnterViewAnimatedIconView.State.VIDEO;
            case STICKER: return ChatActivityEnterViewAnimatedIconView.State.STICKER;
            case KEYBOARD: return ChatActivityEnterViewAnimatedIconView.State.KEYBOARD;
            case GIF: return ChatActivityEnterViewAnimatedIconView.State.GIF;
            case MENU: return ChatActivityEnterViewAnimatedIconView.State.MENU;
            default: return ChatActivityEnterViewAnimatedIconView.State.SMILE;
        }
    }

    public State getCurrentState() {
        return currentState;
    }

    public void setColorFilter(ColorFilter colorFilter) {
        buttonViews[0].setColorFilter(colorFilter);
        buttonViews[1].setColorFilter(colorFilter);
    }

    public void setState(State state, boolean animate) {
        if (animate && state == currentState) {
            return;
        }
        State fromState = currentState;
        currentState = state;
        if (!animate || fromState == null) {
            if (buttonsAnimation != null) {
                buttonsAnimation.cancel();
                buttonsAnimation = null;
            }
            buttonViews[0].setImageResource(currentState.resource);
            buttonViews[0].setAlpha(1f);
            buttonViews[0].setScaleX(1f);
            buttonViews[0].setScaleY(1f);
            buttonViews[0].setVisibility(VISIBLE);
            buttonViews[1].setVisibility(GONE);
        } else {
            if (buttonsAnimation != null) {
                buttonsAnimation.cancel();
            }
            buttonViews[1].setVisibility(VISIBLE);
            buttonViews[1].setImageResource(currentState.resource);
            buttonViews[0].setAlpha(1f);
            buttonViews[0].setScaleX(1f);
            buttonViews[0].setScaleY(1f);
            buttonViews[1].setAlpha(0f);
            buttonViews[1].setScaleX(HIDDEN_SCALE);
            buttonViews[1].setScaleY(HIDDEN_SCALE);

            buttonsAnimation = new AnimatorSet();
            buttonsAnimation.playTogether(
                    ObjectAnimator.ofFloat(buttonViews[0], View.SCALE_X, HIDDEN_SCALE),
                    ObjectAnimator.ofFloat(buttonViews[0], View.SCALE_Y, HIDDEN_SCALE),
                    ObjectAnimator.ofFloat(buttonViews[0], View.ALPHA, 0f),
                    ObjectAnimator.ofFloat(buttonViews[1], View.SCALE_X, 1f),
                    ObjectAnimator.ofFloat(buttonViews[1], View.SCALE_Y, 1f),
                    ObjectAnimator.ofFloat(buttonViews[1], View.ALPHA, 1f)
            );
            buttonsAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(buttonsAnimation)) {
                        buttonsAnimation = null;
                        ImageView shown = buttonViews[1];
                        buttonViews[1] = buttonViews[0];
                        buttonViews[0] = shown;
                        buttonViews[1].setVisibility(INVISIBLE);
                        buttonViews[1].setAlpha(0f);
                        buttonViews[1].setScaleX(HIDDEN_SCALE);
                        buttonViews[1].setScaleY(HIDDEN_SCALE);
                    }
                }
            });
            buttonsAnimation.setDuration(TRANSITION_DURATION);
            buttonsAnimation.start();
        }

        switch (state) {
            case VOICE:
                setContentDescription(LocaleController.getString(R.string.AccDescrVoiceMessage));
                break;
            case VIDEO:
                setContentDescription(LocaleController.getString(R.string.AccDescrVideoMessage));
                break;
        }
    }
}
