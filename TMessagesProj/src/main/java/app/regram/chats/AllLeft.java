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
import tw.nekomimi.nekogram.NekoConfig;

/** Native presentation policy inspired by AllLeft 2.0.6 by @limeplug.
 * Ownership, send_state, peer IDs and read receipts are never changed. */
public final class AllLeft {
    private AllLeft() {}
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
    public static void drawStatus(Canvas canvas, ChatMessageCell cell, MessageObject message,
                                  float alpha, float timeLeft, float timeTop, float timeWidth, int height) {
        if (!statusVisible(message) || height <= 0) return;
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        if (a == 0) return;
        boolean media = cell.shouldDrawTimeOnMedia();
        int color = cell.getThemedColor(media ? Theme.key_chat_mediaSentCheck : Theme.key_chat_inTimeText);
        float right = timeLeft + timeWidth;
        if (message.isSendError()) {
            blit(canvas, Theme.chat_msgErrorDrawable, cell.getThemedColor(Theme.key_chat_sentErrorIcon), false, right, timeTop, height, a);
        } else if (message.isSending() || message.isEditing()) {
            blit(canvas, Theme.chat_msgClockDrawable, color, false, right, timeTop, height, a);
            cell.invalidate();
        } else if (message.isSent()) {
            Integer read = MessagesController.getInstance(message.currentAccount).dialogs_read_outbox_max.get(message.getDialogId());
            boolean seen = !message.scheduled && (DialogObject.isEncryptedDialog(message.getDialogId())
                    ? !message.isUnread() : message.getId() > 0 && (read != null ? read >= message.getId() : !message.isUnread()));
            if (seen) {
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
            float y = top + (height - h) / 2f + AndroidUtilities.dp(media ? 1 : 0);
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
