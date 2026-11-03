package app.regram.chats;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.MsgClockDrawable;
import tw.nekomimi.nekogram.NekoConfig;

/** Native adaptation of AllLeft 2.0.11 by @limeplug (GPL-3.0).
 * Source: https://gitlab.com/limezsh/allleft, 7bdbeef90eac9432955d0fe2b8ed7294a90ca6ea.
 * Modified for re:gram: direct layout/drawing APIs instead of hooks/reflection.
 * Ownership, send_state, peer IDs and read receipts are never changed. */
public final class AllLeft {
    private AllLeft() {}
    private static MsgClockDrawable statusClock;
    private static int tintColor;
    private static ColorFilter tintFilter;
    private static ColorFilter tint(int color) {
        if (tintFilter == null || tintColor != color) {
            tintColor = color;
            tintFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
        return tintFilter;
    }
    public static boolean active(MessageObject message) {
        if (!NekoConfig.regramAllLeft.Bool() || message == null || message.messageOwner == null
                || message.isSaved || message.isSponsored() || message.preview || message.previewForward || message.sendPreview
                || message.isRepostPreview || message.isQuickReply()) return false;
        long dialog = message.getDialogId();
        if (dialog == 0 || dialog == UserConfig.getInstance(message.currentAccount).getClientUserId()) return false;
        return !NekoConfig.regramAllLeftPrivate.Bool() || DialogObject.isUserDialog(dialog) || DialogObject.isEncryptedDialog(dialog);
    }
    public static boolean privateChat(MessageObject message) {
        return active(message) && (DialogObject.isUserDialog(message.getDialogId()) || DialogObject.isEncryptedDialog(message.getDialogId()));
    }
    public static boolean statusVisible(MessageObject message) {
        return active(message) && message.isOutOwner() && NekoConfig.regramAllLeftStatus.Bool()
                && !message.notime && !message.isSponsored() && message.type != MessageObject.TYPE_PHONE_CALL;
    }
    public static float pinnedBottomProgress(MessageObject message, float progress) {
        return active(message) && message.isOutOwner() ? 1f : progress;
    }
    /** -1: none, 0: pending, 1: sent, 2: read, 3: error. Never writes read state. */
    public static int status(MessageObject message) {
        if (message == null || message.messageOwner == null || message.notime) return -1;
        if (message.isSendError()) return 3;
        if (message.isSending() || message.isEditing()) return 0;
        if (!message.isSent()) return -1;
        if (message.scheduled) return 1;
        // Secret messages use local/negative IDs; their protocol read flag remains authoritative.
        if (DialogObject.isEncryptedDialog(message.getDialogId())) return message.isUnread() ? 1 : 2;
        if (message.getId() <= 0) return 1;
        Integer read = MessagesController.getInstance(message.currentAccount).dialogs_read_outbox_max.get(message.getDialogId());
        return read != null && read >= message.getId() ? 2 : 1;
    }
    public static void drawStatus(Canvas canvas, ChatMessageCell cell, MessageObject message,
                                  float alpha, float timeLeft, float timeTop, float timeWidth, int height) {
        if (!statusVisible(message) || height <= 0) return;
        int status = status(message);
        if (status < 0) return;
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        if (a == 0) return;
        boolean media = cell.shouldDrawTimeOnMedia();
        int color = cell.getThemedColor(media ? Theme.key_chat_mediaSentCheck : Theme.key_chat_inTimeText);
        float right = timeLeft + timeWidth;
        if (status == 3) {
            blit(canvas, Theme.chat_msgErrorDrawable, cell.getThemedColor(Theme.key_chat_sentErrorIcon), false, right, timeTop, height, a);
        } else if (status == 0) {
            if (statusClock == null) statusClock = new MsgClockDrawable();
            statusClock.setColor(color);
            blit(canvas, statusClock, color, false, right, timeTop, height, a);
            cell.invalidate();
        } else {
            if (status == 2) {
                blit(canvas, media ? Theme.chat_msgMediaCheckDrawable : Theme.chat_msgOutCheckReadDrawable, color,
                        media, right - AndroidUtilities.dp(media ? 4.8f : 5f), timeTop, height, a);
                blit(canvas, media ? Theme.chat_msgMediaHalfCheckDrawable : Theme.chat_msgOutHalfCheckDrawable,
                        color, media, right, timeTop, height, a);
            } else {
                blit(canvas, media ? Theme.chat_msgMediaCheckDrawable : Theme.chat_msgOutCheckDrawable,
                        color, media, right, timeTop, height, a);
            }
        }
    }
    private static void blit(Canvas canvas, Drawable drawable, int color, boolean media,
                             float right, float top, int height, int alpha) {
        if (drawable == null) return;
        int left = drawable.getBounds().left, topBound = drawable.getBounds().top;
        int rightBound = drawable.getBounds().right, bottom = drawable.getBounds().bottom;
        int oldAlpha = drawable.getAlpha();
        ColorFilter oldFilter = drawable.getColorFilter();
        try {
            int w = drawable.getIntrinsicWidth(), h = drawable.getIntrinsicHeight();
            if (w <= 0) w = AndroidUtilities.dp(11);
            if (h <= 0) h = w;
            float y = media ? top + height - h - AndroidUtilities.dp(1.2f) : top + (height - h) / 2f;
            drawable.setColorFilter(tint(color));
            drawable.setAlpha(alpha);
            drawable.setBounds(Math.round(right) - w, Math.round(y), Math.round(right), Math.round(y) + h);
            drawable.draw(canvas);
        } finally {
            drawable.setBounds(left, topBound, rightBound, bottom);
            drawable.setAlpha(oldAlpha);
            drawable.setColorFilter(oldFilter);
        }
    }
}
