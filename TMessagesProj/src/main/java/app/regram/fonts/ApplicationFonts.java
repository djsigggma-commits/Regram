package app.regram.fonts;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.Paint.PaintTypeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Global, account-independent font library. UI selection is a process-start snapshot. */
public final class ApplicationFonts {
    private static final String PREFIX = "regram_import_";
    private static final int MAX_FONTS = 32;
    private static boolean initialized;
    private static Typeface active;
    private static final HashMap<Integer, Typeface> styles = new HashMap<>();
    private static List<PaintTypeface> importedCache;
    private static Set<Typeface> frameworkUi;
    private ApplicationFonts() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("regram_fonts", Context.MODE_PRIVATE);
    }
    private static File directory() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "regram_fonts");
    }
    private static File file(String key) {
        if (!isImported(key)) return null;
        String id = key.substring(PREFIX.length());
        try {
            if (!UUID.fromString(id).toString().equals(id)) return null;
            return new File(directory(), id + ".font");
        } catch (IllegalArgumentException e) { return null; }
    }
    public static boolean isImported(String key) { return key != null && key.startsWith(PREFIX); }
    public static String selected() { return prefs().getString("application", ""); }
    public static boolean systemFonts() { return prefs().getBoolean("system", true); }
    public static void setSystemFonts(boolean value) { prefs().edit().putBoolean("system", value).apply(); }
    public static boolean hidden(String key) { return prefs().getStringSet("hidden", Collections.emptySet()).contains(key); }
    public static void toggleHidden(String key) {
        Set<String> hidden = new HashSet<>(prefs().getStringSet("hidden", Collections.emptySet()));
        if (!hidden.remove(key)) hidden.add(key);
        prefs().edit().putStringSet("hidden", hidden).apply();
    }
    public static void resetOrder() { prefs().edit().remove("order").apply(); }
    public static void setOrder(List<PaintTypeface> fonts) {
        JSONArray order = new JSONArray();
        for (PaintTypeface font : fonts) order.put(font.getKey());
        prefs().edit().putString("order", order.toString()).apply();
    }
    private static JSONArray metadata() {
        try { return new JSONArray(prefs().getString("imports", "[]")); }
        catch (Exception e) { FileLog.e(e); return new JSONArray(); }
    }

    public static synchronized List<PaintTypeface> imported() {
        if (importedCache == null) {
            ArrayList<PaintTypeface> result = new ArrayList<>();
            JSONArray data = metadata();
            for (int i = 0; i < Math.min(data.length(), MAX_FONTS); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                String key = item.optString("key");
                File file = file(key);
                if (file == null || !file.isFile()) continue;
                try {
                    result.add(PaintTypeface.imported(key, item.optString("name", "Font"), Typeface.createFromFile(file)));
                } catch (Exception e) { FileLog.e(e); }
            }
            importedCache = Collections.unmodifiableList(result);
        }
        return importedCache;
    }
    public static String selectedName(String defaultName) {
        for (PaintTypeface font : imported()) if (font.getKey().equals(selected())) return font.getName();
        return defaultName;
    }
    public static synchronized void select(String key) {
        if (key.isEmpty()) {
            if (!prefs().edit().remove("application").commit()) throw new IllegalStateException("Cannot save font selection");
            return;
        }
        for (PaintTypeface font : imported()) {
            if (font.getKey().equals(key)) {
                if (!prefs().edit().putString("application", key).commit()) throw new IllegalStateException("Cannot save font selection");
                return;
            }
        }
        throw new IllegalArgumentException("Only imported fonts can be used for the UI");
    }
    public static synchronized void delete(String key) throws IOException {
        File file = file(key);
        if (file == null) throw new IOException("Not an imported font");
        JSONArray result = new JSONArray(), data = metadata();
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item != null && !key.equals(item.optString("key"))) result.put(item);
        }
        SharedPreferences.Editor edit = prefs().edit().putString("imports", result.toString());
        if (selected().equals(key)) edit.remove("application");
        Set<String> hidden = new HashSet<>(prefs().getStringSet("hidden", Collections.emptySet()));
        hidden.remove(key);
        edit.putStringSet("hidden", hidden);
        if (!edit.commit()) throw new IOException("Cannot save font library");
        importedCache = null;
        if (file.exists() && !file.delete()) FileLog.e("Cannot delete imported font");
    }
    public static String importFont(Uri uri) throws Exception {
        JSONArray data = metadata();
        if (data.length() >= MAX_FONTS) throw new IOException("Font library full (32 fonts)");
        String name = null;
        try (Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        }
        name = FontFileImport.displayName(name);
        File dir = directory();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create font directory");
        File temp = File.createTempFile("import-", ".tmp", dir);
        File destination = null;
        boolean saved = false;
        try {
            try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(temp)) {
                FontFileImport.copy(in, out);
                out.getFD().sync();
            }
            Typeface validated = Build.VERSION.SDK_INT >= 26 ? new Typeface.Builder(temp).build() : Typeface.createFromFile(temp);
            if (validated == null || validated == Typeface.DEFAULT) throw new IOException("Unsupported font");
            String key = PREFIX + UUID.randomUUID();
            destination = file(key);
            if (!temp.renameTo(destination)) throw new IOException("Cannot store imported font");
            synchronized (ApplicationFonts.class) {
                data = metadata(); // Another import or deletion may have completed while the stream was copying.
                if (data.length() >= MAX_FONTS) throw new IOException("Font library full (32 fonts)");
                JSONObject item = new JSONObject();
                item.put("key", key); item.put("name", name); data.put(item);
                if (!prefs().edit().putString("imports", data.toString()).commit()) throw new IOException("Cannot save font library");
                saved = true;
                importedCache = null;
            }
            return key;
        } finally {
            if (temp.exists()) temp.delete();
            if (!saved && destination != null) destination.delete();
        }
    }

    /** Includes hidden entries on the settings screen; hiding never breaks saved editor text. */
    public static List<PaintTypeface> catalog(List<PaintTypeface> original, boolean includeHidden) {
        ArrayList<PaintTypeface> result = new ArrayList<>();
        for (PaintTypeface font : original) {
            if (systemFonts() || PaintTypeface.BUILT_IN_FONTS.contains(font)) result.add(font);
        }
        result.addAll(imported());
        HashMap<String, Integer> ranks = new HashMap<>();
        try {
            JSONArray order = new JSONArray(prefs().getString("order", "[]"));
            for (int i = 0; i < order.length(); i++) ranks.putIfAbsent(order.optString(i), i);
        } catch (Exception e) { FileLog.e(e); }
        result.sort((a, b) -> Integer.compare(ranks.getOrDefault(a.getKey(), Integer.MAX_VALUE), ranks.getOrDefault(b.getKey(), Integer.MAX_VALUE)));
        if (!includeHidden) result.removeIf(font -> hidden(font.getKey()));
        // Keep the text tool usable even if every font is hidden. Settings still shows their real flags.
        if (!includeHidden && result.isEmpty()) result.add(PaintTypeface.ROBOTO_MEDIUM);
        return result;
    }

    public static synchronized Typeface ui(int weight, boolean italic) {
        if (!initialized) {
            if (ApplicationLoader.applicationContext == null) return null;
            initialized = true;
            File file = file(selected());
            if (file != null && file.isFile()) {
                try { active = Typeface.createFromFile(file); } catch (Exception e) { FileLog.e(e); }
            }
        }
        if (active == null) return null;
        int key = weight * 2 + (italic ? 1 : 0);
        Typeface result = styles.get(key);
        if (result == null) {
            result = Build.VERSION.SDK_INT >= 28 ? Typeface.create(active, weight, italic)
                    : Typeface.create(active, (weight >= 500 ? Typeface.BOLD : Typeface.NORMAL) | (italic ? Typeface.ITALIC : 0));
            styles.put(key, result);
        }
        return result;
    }
    public static Typeface forAsset(String path) {
        switch (path) {
            case "fonts/rregular.ttf": return ui(400, false);
            case "fonts/rmedium.ttf": return ui(500, false);
            case "fonts/rextrabold.ttf": return ui(800, false);
            case "fonts/rmediumitalic.ttf": return ui(500, true);
            case "fonts/ritalic.ttf": return ui(400, true);
            case "fonts/rcondensedbold.ttf": return ui(700, false);
            default: return null; // code, icon, avatar and decorative fonts are not UI font substitutions
        }
    }
    public static synchronized Typeface replacement(Typeface current) {
        if (ui(400, false) == null || styles.containsValue(current)) return null;
        if (frameworkUi == null) {
            frameworkUi = new HashSet<>();
            Collections.addAll(frameworkUi, null, Typeface.DEFAULT, Typeface.DEFAULT_BOLD, Typeface.SANS_SERIF);
            for (String family : new String[]{"sans-serif", "sans-serif-medium", "sans-serif-light", "sans-serif-thin", "sans-serif-black", "sans-serif-condensed"}) {
                for (int style = 0; style < 4; style++) frameworkUi.add(Typeface.create(family, style));
            }
        }
        if (!frameworkUi.contains(current)) return null;
        int weight = current == null ? 400 : Build.VERSION.SDK_INT >= 28 ? current.getWeight() : current.isBold() ? 700 : 400;
        return ui(weight, current != null && current.isItalic());
    }
    public static void applyUi(Paint... paints) {
        if (ui(400, false) == null) return;
        for (Paint paint : paints) if (paint != null) {
            Typeface replacement = replacement(paint.getTypeface());
            if (replacement != null) paint.setTypeface(replacement);
        }
    }
}
