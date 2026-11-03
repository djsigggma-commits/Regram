package app.regram.termux;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Components.BulletinFactory;
import java.util.*;

/** UI owned by the client: real Android listeners, never intercepted Telegram methods. */
final class TermuxUi {
    private final TermuxBridge bridge;
    private final Map<String, Panel> panels = new HashMap<>();
    private final Map<View, OwnedView> javaViews = new IdentityHashMap<>();
    private final ArrayList<String> lines = new ArrayList<>();
    private JSONObject options = new JSONObject();
    private View overlay, ball;
    private int overlayGeneration;
    private boolean silent;
    TermuxUi(TermuxBridge bridge) { this.bridge = bridge; }
    int panelCount() { return panels.size(); }
    void selfTest() throws Exception {
        options = new JSONObject(); lines.clear();
        append(new JSONObject().put("text", "Termux Bridge · re:gram").put("timeout", 3).put("mono", false));
        render();
    }
    private static final class Panel {
        View root;
        String owner;
        boolean keep;
        final Map<String, Item> items = new HashMap<>();
    }
    private static final class Item {
        String kind, text;
        View view, root;
        TextView label;
        int lo, step;
    }
    private static final class OwnedView {
        final String owner;
        final boolean keep;
        OwnedView(String owner, boolean keep) { this.owner = owner; this.keep = keep; }
    }
    JSONObject command(TermuxBridge.Session session, JSONObject msg) throws Exception {
        String owner = session.sid;
        switch (msg.optString("cmd")) {
            case "show": options = new JSONObject(); lines.clear(); append(msg); render(); break;
            case "append": append(msg); render(); break;
            case "clear": lines.clear(); options = new JSONObject(); removeOverlay(); break;
            case "notify":
                if (LaunchActivity.getLastFragment() == null) throw new IllegalStateException("open re:gram first");
                BulletinFactory factory = BulletinFactory.of(LaunchActivity.getLastFragment());
                String note = TermuxBridge.truncate(msg.optString("text"), 4096);
                if ("error".equals(msg.optString("kind"))) factory.createErrorBulletin(note).show();
                else factory.createSimpleBulletin("success".equals(msg.optString("kind"))
                        ? org.telegram.messenger.R.raw.done : org.telegram.messenger.R.raw.info, note).show();
                break;
            case "clip": AndroidUtilities.addToClipboard(msg.optString("text")); break;
            case "ball": detach(ball); ball = null; if (!msg.optBoolean("off")) buildBall(msg); break;
            case "panel": buildPanel(owner, msg); return TermuxBridge.ok().put("id", msg.optString("id", "panel"));
            case "set": set(owner, msg); break;
            case "close": close(key(owner, msg.optString("panel")), false); break;
            default: return TermuxBridge.error("unknown cmd");
        }
        return TermuxBridge.ok();
    }
    private void append(JSONObject msg) throws Exception {
        for (String name : new String[]{"title", "pos", "fg", "bg", "size", "mono", "timeout", "max_lines"})
            if (msg.has(name)) options.put(name, msg.get(name));
        lines.addAll(Arrays.asList(TermuxBridge.truncate(msg.optString("text"), 65536).split("\n", -1)));
        int max = clamp(options.optInt("max_lines", 30), 1, 200);
        if (lines.size() > max) lines.subList(0, lines.size() - max).clear();
    }
    private void render() {
        Activity act = bridge.activity();
        removeOverlay();
        TextView text = text(act, String.join("\n", lines));
        if (!options.optString("title").isEmpty()) text.setText(options.optString("title") + "\n" + text.getText());
        text.setTextColor(color(options.optString("fg"), Color.WHITE));
        text.setTextSize(clamp(options.optInt("size", 13), 8, 48));
        if (options.optBoolean("mono", true)) text.setTypeface(Typeface.MONOSPACE);
        text.setMaxWidth(Math.max(dp(60), act.getResources().getDisplayMetrics().widthPixels - dp(24)));
        text.setPadding(dp(10), dp(10), dp(10), dp(10));
        text.setBackground(shape(color(options.optString("bg"), 0xcc101418), 12));
        text.setOnClickListener(v -> removeOverlay());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, gravity(options.optString("pos", "top")));
        lp.setMargins(dp(12), dp(48), dp(12), dp(72));
        decor().addView(text, lp); overlay = text;
        int generation = overlayGeneration;
        double timeout = options.optDouble("timeout", 0);
        if (timeout > 0 && Double.isFinite(timeout)) AndroidUtilities.runOnUIThread(() -> {
            if (generation == overlayGeneration) removeOverlay();
        }, (long) (Math.min(86400, timeout) * 1000));
    }
    private void removeOverlay() { overlayGeneration++; detach(overlay); overlay = null; }
    private void buildBall(JSONObject msg) {
        Activity act = bridge.activity();
        FrameLayout root = new FrameLayout(act);
        int size = dp(clamp(msg.optInt("size", 56), 24, 200));
        View circle = new View(act);
        GradientDrawable background = shape(color(msg.optString("color"), 0xffffd600), 200);
        circle.setBackground(background);
        root.addView(circle, new FrameLayout.LayoutParams(size, size));
        TextView close = text(act, "✕");
        close.setGravity(Gravity.CENTER); close.setBackground(shape(0xe61e2328, 200));
        close.setOnClickListener(v -> { detach(ball); ball = null; });
        root.addView(close, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP | Gravity.RIGHT));
        circle.setOnTouchListener(drag(root));
        decor().addView(root, new FrameLayout.LayoutParams(size + dp(12), size + dp(12), Gravity.TOP | Gravity.LEFT));
        root.setX(Math.max(0, act.getResources().getDisplayMetrics().widthPixels - size - dp(24)));
        root.setY(act.getResources().getDisplayMetrics().heightPixels * .45f);
        ball = root;
    }
    private void buildPanel(String owner, JSONObject msg) throws Exception {
        String id = msg.optString("id", "panel");
        if (id.length() > 128) throw new IllegalArgumentException("panel id too long");
        String key = key(owner, id);
        Panel previous = panels.get(key);
        if (previous == null && panels.size() >= 32) throw new IllegalStateException("too many panels");
        Float x = previous == null ? null : previous.root.getX();
        Float y = previous == null ? null : previous.root.getY();
        Activity act = bridge.activity();
        Panel panel = new Panel(); panel.owner = owner; panel.keep = msg.optBoolean("keep");
        LinearLayout root = new LinearLayout(act); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10)); root.setBackground(shape(0xf01c2127, 16)); root.setElevation(dp(24));
        panel.root = root;
        LinearLayout header = new LinearLayout(act); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(act, msg.optString("title", " ")); title.setTextSize(12); title.setTextColor(0xff9aa4ad);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text(act, "✕"); close.setPadding(dp(10), dp(4), dp(4), dp(4));
        close.setOnClickListener(v -> close(key, true)); header.addView(close);
        header.setOnTouchListener(drag(root)); root.addView(header);
        LinearLayout body = new LinearLayout(act); body.setOrientation(LinearLayout.VERTICAL);
        JSONArray items = msg.optJSONArray("items");
        int[] budget = {128};
        if (items != null) for (int i = 0; i < items.length(); i++) {
            View view = item(act, panel, id, items.getJSONObject(i), 0, budget);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
            body.addView(view, lp);
        }
        ScrollView scroll = new ScrollView(act); scroll.addView(body);
        int sw = act.getResources().getDisplayMetrics().widthPixels;
        int sh = act.getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(Math.max(dp(80), sw - dp(24)), dp(clamp(msg.optInt("width", 260), 80, 800)));
        body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(body.getMeasuredHeight(), Math.max(dp(40), sh - dp(180)))));
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = root.getMeasuredHeight();
        String pos = msg.optString("pos", "bottom_end");
        float px = x != null ? x : (msg.has("x") ? dpFloat(msg.optDouble("x")) :
                pos.contains("start") ? dp(12) : pos.contains("end") ? sw - width - dp(12) : (sw - width) / 2f);
        float py = y != null ? y : (msg.has("x") ? dpFloat(msg.optDouble("y", 0)) :
                pos.contains("top") ? dp(48) : pos.contains("bottom") ? sh - height - dp(72) : (sh - height) / 2f);
        close(key, false);
        decor().addView(root, new FrameLayout.LayoutParams(width, -2, Gravity.TOP | Gravity.LEFT));
        root.setX(Math.max(0, Math.min(px, sw - width))); root.setY(Math.max(0, Math.min(py, sh - height)));
        panels.put(key, panel);
    }
    private View item(Activity act, Panel panel, String pid, JSONObject spec, int depth, int[] budget) throws Exception {
        if (depth > 4 || --budget[0] < 0) throw new IllegalArgumentException("too many panel items");
        String kind = spec.optString("type", "text"), id = spec.optString("id");
        if (id.length() > 128) throw new IllegalArgumentException("item id too long");
        if (kind.equals("row")) {
            LinearLayout row = new LinearLayout(act);
            JSONArray items = spec.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++)
                row.addView(item(act, panel, pid, items.getJSONObject(i), depth + 1, budget), new LinearLayout.LayoutParams(0, -2, 1));
            return row;
        }
        Item entry = new Item(); entry.kind = kind; entry.text = spec.optString("text");
        switch (kind) {
            case "button": {
                TextView button = button(act, entry.text, color(spec.optString("color"), 0xff2f80ed));
                button.setOnClickListener(v -> event(panel.owner, pid, id, "click", null)); entry.view = button; break;
            }
            case "toggle": {
                Switch toggle = new Switch(act); toggle.setText(entry.text); toggle.setTextColor(Color.WHITE);
                toggle.setChecked(spec.optBoolean("value"));
                toggle.setOnCheckedChangeListener((view, value) -> { if (!silent) event(panel.owner, pid, id, "toggle", value); });
                entry.view = toggle; break;
            }
            case "slider": {
                entry.lo = clamp(spec.optInt("min", 0), -1000000, 1000000);
                int hi = clamp(spec.optInt("max", 100), entry.lo, 1000000);
                entry.step = clamp(spec.optInt("step", 1), 1, 1000000);
                LinearLayout wrap = new LinearLayout(act); wrap.setOrientation(LinearLayout.VERTICAL);
                entry.label = text(act, ""); wrap.addView(entry.label);
                SeekBar slider = new SeekBar(act); slider.setMax(Math.max(0, (hi - entry.lo) / entry.step));
                slider.setProgress(clamp((spec.optInt("value", entry.lo) - entry.lo) / entry.step, 0, slider.getMax()));
                wrap.addView(slider); entry.root = wrap; entry.view = slider; updateLabel(entry);
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    long last;
                    public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                        updateLabel(entry);
                        if (user && !silent && SystemClock.elapsedRealtime() - last >= 80) {
                            last = SystemClock.elapsedRealtime(); event(panel.owner, pid, id, "slide", entry.lo + progress * entry.step);
                        }
                    }
                    public void onStartTrackingTouch(SeekBar bar) { }
                    public void onStopTrackingTouch(SeekBar bar) { if (!silent) event(panel.owner, pid, id, "slide", entry.lo + bar.getProgress() * entry.step); }
                }); break;
            }
            case "input": {
                LinearLayout row = new LinearLayout(act); row.setGravity(Gravity.CENTER_VERTICAL);
                EditText input = new EditText(act); input.setSingleLine(); input.setTextColor(Color.WHITE);
                input.setHintTextColor(0x77ffffff); input.setHint(spec.optString("hint")); input.setTextSize(14);
                row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
                TextView send = button(act, spec.optString("button", "OK"), 0xff2f80ed);
                send.setOnClickListener(v -> { event(panel.owner, pid, id, "submit", input.getText().toString()); if (spec.optBoolean("clear", true)) input.setText(""); });
                row.addView(send); entry.view = input; entry.root = row; break;
            }
            default: {
                TextView text = text(act, entry.text); text.setTextColor(color(spec.optString("color"), Color.WHITE));
                text.setTextSize(clamp(spec.optInt("size", 14), 8, 48));
                if (spec.optBoolean("mono")) text.setTypeface(Typeface.MONOSPACE);
                entry.view = text; break;
            }
        }
        if (entry.root == null) entry.root = entry.view;
        if (!id.isEmpty() && panel.items.put(id, entry) != null) throw new IllegalArgumentException("duplicate item id");
        return entry.root;
    }
    private void updateLabel(Item item) {
        item.label.setText(item.text + ": " + (item.lo + ((SeekBar) item.view).getProgress() * item.step));
    }
    private void set(String owner, JSONObject msg) {
        Panel panel = panels.get(key(owner, msg.optString("panel")));
        if (panel == null) throw new IllegalArgumentException("panel not found");
        Item item = panel.items.get(msg.optString("id"));
        if (item == null) throw new IllegalArgumentException("item not found");
        if (msg.has("text")) {
            item.text = msg.optString("text");
            if (item.kind.equals("slider")) updateLabel(item);
            else if (item.view instanceof TextView) ((TextView) item.view).setText(item.text);
        }
        if (msg.has("hint") && item.view instanceof EditText) ((EditText) item.view).setHint(msg.optString("hint"));
        silent = true;
        try {
            if (msg.has("value")) {
                if (item.view instanceof Switch) ((Switch) item.view).setChecked(msg.optBoolean("value"));
                else if (item.view instanceof SeekBar) ((SeekBar) item.view).setProgress(clamp((msg.optInt("value") - item.lo) / item.step, 0, ((SeekBar) item.view).getMax()));
                else if (item.view instanceof EditText) ((EditText) item.view).setText(msg.optString("value"));
            }
        } finally { silent = false; }
        if (msg.has("enabled")) { enableTree(item.root, msg.optBoolean("enabled")); item.root.setAlpha(msg.optBoolean("enabled") ? 1 : .4f); }
        if (msg.has("visible")) item.root.setVisibility(msg.optBoolean("visible") ? View.VISIBLE : View.GONE);
    }
    private static void enableTree(View root, boolean enabled) {
        root.setEnabled(enabled);
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) enableTree(((ViewGroup) root).getChildAt(i), enabled);
    }
    private void event(String owner, String panel, String id, String name, Object value) {
        try {
            JSONObject event = new JSONObject().put("event", name).put("panel", panel).put("id", id);
            if (value != null) event.put("value", value);
            bridge.emit(owner, event);
        } catch (Exception ignored) { }
    }
    private void close(String key, boolean notify) {
        Panel panel = panels.remove(key);
        if (panel == null) return;
        detach(panel.root);
        if (notify) event(panel.owner, key.substring(panel.owner.length() + 1), "", "close", null);
    }
    void addJavaView(String owner, View view, JSONObject msg) {
        if (!javaViews.containsKey(view) && javaViews.size() >= 128) throw new IllegalStateException("too many views");
        OwnedView existing = javaViews.get(view);
        if (existing != null && !existing.owner.equals(owner)) throw new IllegalArgumentException("view belongs to another session");
        detach(view);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(clamp(msg.optInt("w", -2), -2, 4096), clamp(msg.optInt("ht", -2), -2, 4096), msg.optInt("gravity", 51));
        decor().addView(view, lp);
        if (msg.has("x") && !msg.isNull("x")) { view.setX((float) msg.optDouble("x")); view.setY((float) msg.optDouble("y", 0)); }
        javaViews.put(view, new OwnedView(owner, msg.optBoolean("keep")));
    }
    void removeJavaView(String owner, View view) {
        OwnedView existing = javaViews.get(view);
        if (existing == null || !existing.owner.equals(owner)) throw new IllegalArgumentException("view not owned by session");
        javaViews.remove(view); detach(view);
    }
    void cleanupOwner(String owner) {
        for (String key : new ArrayList<>(panels.keySet())) if (panels.get(key).owner.equals(owner) && !panels.get(key).keep) close(key, false);
        for (View view : new ArrayList<>(javaViews.keySet())) if (javaViews.get(view).owner.equals(owner) && !javaViews.get(view).keep) { detach(view); javaViews.remove(view); }
    }
    void clearAll() {
        removeOverlay(); detach(ball); ball = null; lines.clear(); options = new JSONObject();
        for (String key : new ArrayList<>(panels.keySet())) close(key, false);
        for (View view : javaViews.keySet()) detach(view);
        javaViews.clear();
    }
    private ViewGroup decor() { return (ViewGroup) bridge.activity().getWindow().getDecorView(); }
    private View.OnTouchListener drag(View target) {
        float[] start = new float[4];
        return (view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                start[0] = event.getRawX(); start[1] = event.getRawY(); start[2] = target.getX(); start[3] = target.getY();
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                View parent = (View) target.getParent();
                target.setX(Math.max(0, Math.min(start[2] + event.getRawX() - start[0], parent.getWidth() - target.getWidth())));
                target.setY(Math.max(0, Math.min(start[3] + event.getRawY() - start[1], parent.getHeight() - target.getHeight())));
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
            return true;
        };
    }
    private static TextView text(Activity act, String text) { TextView view = new TextView(act); view.setText(text); view.setTextColor(Color.WHITE); view.setTextSize(14); return view; }
    private static TextView button(Activity act, String text, int color) {
        TextView view = text(act, text); view.setGravity(Gravity.CENTER); view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackground(shape(color, 10)); return view;
    }
    static void detach(View view) { if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view); }
    private static String key(String owner, String id) { return owner + ":" + id; }
    static int dp(int value) { return AndroidUtilities.dp(value); }
    private static float dpFloat(double value) { return Double.isFinite(value) ? (float) Math.max(-100000, Math.min(100000, value)) * AndroidUtilities.density : 0; }
    static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static int color(String value, int fallback) { try { return Color.parseColor(value); } catch (Exception ignored) { return fallback; } }
    private static GradientDrawable shape(int color, int radius) { GradientDrawable out = new GradientDrawable(); out.setColor(color); out.setCornerRadius(dp(radius)); return out; }
    private static int gravity(String pos) {
        switch (pos) {
            case "bottom": return Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            case "center": return Gravity.CENTER;
            case "top_start": return Gravity.TOP | Gravity.START;
            case "top_end": return Gravity.TOP | Gravity.END;
            case "bottom_start": return Gravity.BOTTOM | Gravity.START;
            case "bottom_end": return Gravity.BOTTOM | Gravity.END;
            default: return Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        }
    }
}
