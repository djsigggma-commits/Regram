package app.regram.badges;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Client-side supporter list; never changes Telegram's verification or premium status. */
public final class SupporterBadges {
    private static final String URL = "https://github.com/djsigggmagg-ui/regram-badges/raw/refs/heads/main/ids.txt";
    private static final long REFRESH_MS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_MS = 15 * 60 * 1000L;
    private static final int MAX_BYTES = 128 * 1024;
    private static final int MAX_MESSAGE_LENGTH = 200;
    private static final Pattern LINE = Pattern.compile(
            "([1-9][0-9]{0,18})(?:\\s*-\\s*tg://emoji\\?id=([1-9][0-9]{0,18})(?:\\s*-\\s*(\\S(?:.*\\S)?))?)?");
    // Emoji ID 0 means the default re:gram logo. Older lines have no custom message.
    private static volatile Map<Long, BadgeInfo> ids;

    private static final class BadgeInfo {
        final long emojiId;
        final String message;

        BadgeInfo(long emojiId, String message) {
            this.emojiId = emojiId;
            this.message = message;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BadgeInfo)) return false;
            BadgeInfo info = (BadgeInfo) other;
            return emojiId == info.emojiId && TextUtils.equals(message, info.message);
        }

        @Override public int hashCode() {
            return 31 * Long.hashCode(emojiId) + (message == null ? 0 : message.hashCode());
        }
    }
    private static long lastAttempt;
    private static boolean loading;
    private static final ArrayList<Runnable> listeners = new ArrayList<>();
    // A slow GitHub connection must not block the shared queue used by UI telemetry.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "regram-badges-fetch");
        thread.setDaemon(true);
        return thread;
    });

    private SupporterBadges() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("regram_supporter_badges", Context.MODE_PRIVATE);
    }

    private static Map<Long, BadgeInfo> parse(String text) {
        HashMap<Long, BadgeInfo> parsed = new HashMap<>();
        for (String line : text.split("\\R")) {
            Matcher match = LINE.matcher(line.trim());
            // Ignore comments and malformed lines, including invalid emoji links.
            if (!match.matches()) continue;
            try {
                String message = match.group(3);
                // Remote text is displayed as plain text, never as HTML or a URL.
                if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
                    message = null;
                }
                parsed.put(Long.parseLong(match.group(1)), new BadgeInfo(
                        match.group(2) == null ? 0L : Long.parseLong(match.group(2)), message));
            } catch (NumberFormatException ignored) {}
        }
        return Collections.unmodifiableMap(parsed);
    }

    private static void loadCache() {
        if (ids == null) ids = parse(prefs().getString("ids", ""));
    }

    public static boolean has(long userId) {
        loadCache();
        return userId > 0 && ids.containsKey(userId);
    }

    public static long emojiFor(long userId) {
        loadCache();
        BadgeInfo badge = ids.get(userId);
        return badge == null ? 0 : badge.emojiId;
    }

    /** Called on the UI thread when a screen opens; the network request runs in the background. */
    public static void refresh(Runnable onChange) {
        loadCache();
        long now = System.currentTimeMillis();
        if (loading) {
            if (onChange != null) listeners.add(onChange);
            return;
        }
        if (now - prefs().getLong("updated", 0) < REFRESH_MS || now - lastAttempt < RETRY_MS) return;
        lastAttempt = now;
        loading = true;
        if (onChange != null) listeners.add(onChange);
        IO.execute(() -> {
            String data = download();
            Map<Long, BadgeInfo> updated = data == null ? null : parse(data);
            AndroidUtilities.runOnUIThread(() -> {
                boolean changed = updated != null && !updated.equals(ids);
                if (updated != null) {
                    ids = updated;
                    prefs().edit().putString("ids", data).putLong("updated", System.currentTimeMillis()).apply();
                }
                loading = false;
                if (changed) for (Runnable listener : listeners) listener.run();
                listeners.clear();
            });
        });
    }

    private static String download() {
        String url = URL;
        for (int redirect = 0; redirect < 2; redirect++) {
            HttpURLConnection conn = null;
            try {
                URL target = new URL(url);
                if (!"https".equals(target.getProtocol())) return null;
                if (redirect == 0 && !"github.com".equals(target.getHost())) return null;
                if (redirect == 1 && !"raw.githubusercontent.com".equals(target.getHost())) return null;
                conn = (HttpURLConnection) target.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "text/plain");
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    url = conn.getHeaderField("Location");
                    if (url == null) return null;
                    continue;
                }
                if (code != 200 || conn.getContentLengthLong() > MAX_BYTES
                        || conn.getContentType() == null
                        || !conn.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("text/plain")) return null;
                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (out.size() + count > MAX_BYTES) return null;
                        out.write(buffer, 0, count);
                    }
                    return out.toString(StandardCharsets.UTF_8.name());
                }
            } catch (Exception e) {
                FileLog.e("Supporter badges: list unavailable", e);
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    /** Append the badge without replacing a premium/verified/mute icon. Call after title icons are updated. */
    public static void decorate(SimpleTextView name, long userId, int account) {
        if (name == null) return;
        Drawable current = name.getRightDrawable2();
        BadgeAndIcon previous = current instanceof BadgeAndIcon ? (BadgeAndIcon) current : null;
        Drawable old = previous != null ? previous.original : current;
        loadCache();
        BadgeInfo configured = userId > 0 ? ids.get(userId) : null;
        if (configured == null) {
            clear(name);
            return;
        }
        long emojiId = configured.emojiId;
        boolean dark = Theme.isCurrentThemeDark();
        if (previous != null && previous.emojiId == emojiId
                && previous.account == account && previous.dark == dark
                && TextUtils.equals(previous.message, configured.message)) return;
        BadgeAndIcon badge = new BadgeAndIcon(name, old, account, emojiId, dark, configured.message);
        name.setRightDrawable2(badge);
        if (previous != null) previous.release(name);
        name.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                    && event.getActionMasked() != MotionEvent.ACTION_UP) return false;
            Rect bounds = badge.getBounds();
            int badgeLeft = bounds.right - badge.iconWidth;
            boolean hit = event.getX() >= badgeLeft - AndroidUtilities.dp(6)
                    && event.getX() <= bounds.right + AndroidUtilities.dp(6)
                    && event.getY() >= bounds.top - AndroidUtilities.dp(10)
                    && event.getY() <= bounds.bottom + AndroidUtilities.dp(10);
            if (!hit) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                new AlertDialog.Builder(name.getContext())
                        .setTitle(name.getContext().getString(R.string.RegramSupporterTitle))
                        .setMessage(badge.message != null ? badge.message
                                : name.getContext().getString(R.string.RegramSupporterInfo))
                        .setPositiveButton(name.getContext().getString(R.string.OK), null)
                        .show();
            }
            return true;
        });
    }

    /** Don't share a badge drawable (and its view lifecycle) during chat-to-profile transitions. */
    public static Drawable withoutBadge(Drawable drawable) {
        return drawable instanceof BadgeAndIcon ? ((BadgeAndIcon) drawable).original : drawable;
    }

    /** Must run before upstream title code replaces rightDrawable2 to detach animated emoji. */
    public static void clear(SimpleTextView name) {
        if (name == null || !(name.getRightDrawable2() instanceof BadgeAndIcon)) return;
        BadgeAndIcon badge = (BadgeAndIcon) name.getRightDrawable2();
        name.setRightDrawable2(badge.original);
        badge.release(name);
        name.setOnTouchListener(null);
    }

    private static final class BadgeAndIcon extends Drawable implements Drawable.Callback, View.OnAttachStateChangeListener {
        final Drawable original;
        final long emojiId;
        final int account;
        final boolean dark;
        final String message;
        final int iconWidth = AndroidUtilities.dp(23);
        private final int iconHeight;
        private final Drawable icon;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable animatedIcon;
        private final int gap = AndroidUtilities.dp(5);

        BadgeAndIcon(SimpleTextView name, Drawable original, int account, long emojiId, boolean dark, String message) {
            this.original = original;
            this.emojiId = emojiId;
            this.account = account;
            this.dark = dark;
            this.message = message;
            if (original != null) original.setCallback(this);
            if (emojiId != 0) {
                iconHeight = AndroidUtilities.dp(23);
                animatedIcon = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(name, iconWidth);
                animatedIcon.setCurrentAccount(account);
                animatedIcon.set(emojiId, false);
                icon = animatedIcon;
                name.addOnAttachStateChangeListener(this);
                if (name.isAttachedToWindow()) animatedIcon.attach();
            } else {
                iconHeight = AndroidUtilities.dp(17);
                animatedIcon = null;
                icon = name.getResources().getDrawable(dark
                        ? R.drawable.regram_supporter_dark : R.drawable.regram_supporter_light).mutate();
            }
        }

        void release(SimpleTextView name) {
            if (animatedIcon != null) {
                name.removeOnAttachStateChangeListener(this);
                animatedIcon.detach();
            }
        }

        @Override public void onViewAttachedToWindow(View view) {
            if (animatedIcon != null) animatedIcon.attach();
        }
        @Override public void onViewDetachedFromWindow(View view) {
            if (animatedIcon != null) animatedIcon.detach();
        }

        @Override public int getIntrinsicWidth() {
            return iconWidth + (original != null ? original.getIntrinsicWidth() + gap : 0);
        }
        @Override public int getIntrinsicHeight() {
            return Math.max(iconHeight, original != null ? original.getIntrinsicHeight() : 0);
        }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (original != null) {
                int h = original.getIntrinsicHeight();
                original.setBounds(b.left, b.centerY() - h / 2,
                        b.left + original.getIntrinsicWidth(), b.centerY() - h / 2 + h);
                original.draw(canvas);
            }
            icon.setBounds(b.right - iconWidth, b.centerY() - iconHeight / 2,
                    b.right, b.centerY() - iconHeight / 2 + iconHeight);
            icon.draw(canvas);
        }
        @Override public void setAlpha(int alpha) { icon.setAlpha(alpha); if (original != null) original.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) {
            // Telegram custom emoji may have its own colors; don't tint the document.
            if (animatedIcon == null) icon.setColorFilter(filter);
            if (original != null) original.setColorFilter(filter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
    }
}
