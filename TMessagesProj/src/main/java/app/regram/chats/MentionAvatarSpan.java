package app.regram.chats;

import android.graphics.Canvas;
import android.graphics.Paint;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.AvatarSpan;

/** Decorates an existing edge grapheme/word; adds width, not characters, to the display. */
public final class MentionAvatarSpan extends AvatarSpan {
    private final boolean before;
    public MentionAvatarSpan(int account, float size, boolean before, TLObject peer) {
        super(null, account, size);
        this.before = before;
        needDrawShadow = false;
        setLoadOnlyWhenAttached(true);
        setObject(peer);
    }
    @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        int avatar = super.getSize(paint, text, start, end, null);
        if (fm != null) {
            paint.getFontMetricsInt(fm);
            int center = (fm.ascent + fm.descent) / 2;
            fm.ascent = Math.min(fm.ascent, center - avatar / 2);
            fm.descent = Math.max(fm.descent, center + (avatar + 1) / 2);
            fm.top = Math.min(fm.top, fm.ascent);
            fm.bottom = Math.max(fm.bottom, fm.descent);
        }
        return (int) Math.ceil(paint.measureText(text, start, end)) + avatar + AndroidUtilities.dp(3);
    }
    @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                               int top, int y, int bottom, Paint paint) {
        float textWidth = paint.measureText(text, start, end);
        int avatarWidth = super.getSize(paint, text, start, end, null);
        float gap = AndroidUtilities.dp(3);
        canvas.drawText(text, start, end, before ? x + avatarWidth + gap : x, y, paint);
        super.draw(canvas, text, start, end, before ? x : x + textWidth + gap, top, y, bottom, paint);
    }
}
