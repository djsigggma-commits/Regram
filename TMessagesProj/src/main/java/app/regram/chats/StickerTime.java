package app.regram.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;

public final class StickerTime {

    public static final class Row {
        public MessageObject message;
        public float overStickerOffsetX;
        public float timeWidth;
        public float stickerLeft;
        public float stickerRight;
        public float stickerWidth;
        public float anchorY;
        public float cellWidth;
        public boolean sideButton;
        public boolean hasName;
        public float nameLeft;
        public float nameWidth;
        public float nameTop;
        public float nameHeight;
    }

    private StickerTime() {
    }

    public static boolean isHidden(MessageObject message) {
        return message != null && message.isAnyKindOfSticker() && ChatsConfig.stickerTimeMode() == ChatsConfig.STICKER_TIME_HIDDEN;
    }

    public static float getTimeOffsetX(Row row, float timeX) {
        if (row.message == null || !row.message.isAnyKindOfSticker()) {
            return 0;
        }
        return isBeside(row) ? getTimeLeft(row) + dp(6) - timeX : row.overStickerOffsetX;
    }

    public static float getSideButtonX(Row row, float x) {
        if (!isBeside(row)) {
            return x;
        }
        if (row.message.isOutOwner()) {
            return Math.min(x, getTimeLeft(row) - dp(8 + 32));
        }
        return Math.max(x, getTimeLeft(row) + getTimeWidth(row) + dp(8));
    }

    public static float getSideButtonTouchLeft(Row row, float left) {
        return isBeside(row) && !row.message.isOutOwner() ? dp(8) : left;
    }

    public static float getSideButtonTouchRight(Row row, float right) {
        return isBeside(row) && row.message.isOutOwner() ? dp(8 + 32) : right;
    }

    private static boolean isBeside(Row row) {
        MessageObject message = row.message;
        if (message == null || !message.isAnyKindOfSticker() || message.type == MessageObject.TYPE_EMOJIS
                || row.stickerWidth <= 0 || ChatsConfig.stickerTimeMode() != ChatsConfig.STICKER_TIME_SIDE) {
            return false;
        }
        float margin = dp(8);
        float left = getTimeLeft(row);
        float right = left + getTimeWidth(row);
        if (row.sideButton) {
            if (message.isOutOwner()) {
                left -= dp(32) + margin;
            } else {
                right += dp(32) + margin;
            }
        }
        return left >= margin && right <= row.cellWidth - margin;
    }

    private static float getTimeLeft(Row row) {
        float margin = dp(8);
        boolean nameInRow = row.hasName && overlapsRow(row, row.nameTop, row.nameTop + row.nameHeight);
        if (row.message.isOutOwner()) {
            float right = row.stickerLeft - margin;
            if (nameInRow) {
                right = Math.min(right, row.nameLeft - margin);
            }
            return right - getTimeWidth(row);
        }
        float left = row.stickerRight + margin;
        if (nameInRow) {
            left = Math.max(left, row.nameLeft + row.nameWidth + margin);
        }
        return left;
    }

    private static float getTimeWidth(Row row) {
        return row.timeWidth + dp(12) + (row.message.isOutOwner() ? dp(20) : 0);
    }

    private static boolean overlapsRow(Row row, float top, float bottom) {
        float rowTop = row.anchorY - dp(23);
        float rowHeight = Math.max(dp(17), Theme.chat_timePaint.getTextSize() + dp(5));
        return top < rowTop + rowHeight && bottom > rowTop;
    }
}
