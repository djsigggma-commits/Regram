package app.regram.documents;

import static org.telegram.messenger.LocaleController.getString;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.net.Uri;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

/** Native fullscreen viewer. Work, PdfRenderer and FD disposal share one background owner. */
final class DocumentViewerDialog extends Dialog {
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
        return app.regram.input.SPenInput.hover(getWindow().getDecorView(), event)
                || super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        app.regram.input.SPenInput.contact(getWindow().getDecorView(), event);
        return super.dispatchTouchEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) {
            app.regram.input.SPenInput.invalidateHoverWindows();
            app.regram.input.SPenInput.resetHover(getWindow().getDecorView());
        }
        super.onWindowFocusChanged(focused);
    }

    private static final int MAX_PIXELS = 3_000_000;
    private final Activity activity;
    private final File file;
    private final String name, mime, format;
    private final boolean restricted;
    private final int account;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> { Thread t = new Thread(r, "document-viewer"); t.setDaemon(true); return t; });
    private final Handler main = new Handler(Looper.getMainLooper());
    private Future<?> pending;
    private volatile boolean closed;
    private volatile int generation;
    private PdfRenderer pdf; // worker thread only
    private ParcelFileDescriptor descriptor; // worker thread only
    private DocumentReader.Result document;
    private final LinearLayout root, content;
    private final TextView status, counter, searchCounter;
    private final ScrollView scroll;
    private final TextView text;
    private final DocumentImageView image;
    private WebView web;
    private final LinearLayout pager, searchBar;
    private int page, count = 1, font, theme, hit = -1, rotation;
    private float textSize;
    private boolean wrap = true, invert, fitWidth, mirror, loaded;
    private String encoding, positionKey;
    private List<int[]> hits = Collections.emptyList();
    private final Set<AlertDialog> childDialogs = new HashSet<>();

    DocumentViewerDialog(Activity activity, File file, String name, String mime, String format, boolean restricted, int account) throws Exception {
        super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.activity = activity; this.file = file; this.name = name; this.mime = mime == null || mime.isEmpty() ? "*/*" : mime;
        this.format = format; this.restricted = restricted; this.account = account; setOwnerActivity(activity);
        theme = Math.floorMod(NativeDocViewer.settings().getInt("theme", 0), 3);
        textSize = Math.max(8, Math.min(34, NativeDocViewer.settings().getFloat("text_size", 15)));
        invert = NativeDocViewer.option("invert", false); fitWidth = NativeDocViewer.option("fitwidth", false);
        positionKey = NativeDocViewer.positionKey(file, account, format);
        if (NativeDocViewer.option("keepon", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (restricted || (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(activity); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(8), dp(4), dp(8), dp(4)); bar.setBackgroundColor(0xff16161e);
        bar.addView(button("✕", this::dismiss));
        TextView title = label(name); title.setSingleLine(); title.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1)); bar.addView(button("⋮", this::menu)); root.addView(bar);
        content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        status = label(getString(R.string.Loading)); status.setGravity(Gravity.CENTER); status.setTextIsSelectable(!restricted); root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(activity); text = label(""); text.setPadding(dp(18), dp(16), dp(18), dp(16)); text.setTextIsSelectable(!restricted); text.setLineSpacing(0, 1.25f);
        image = new DocumentImageView(activity); image.setBackgroundColor(0xff09090d); image.fitWidth(fitWidth); image.invert(invert);
        pager = new LinearLayout(activity); pager.setGravity(Gravity.CENTER); pager.setBackgroundColor(0xff16161e); pager.addView(button("‹", () -> go(page - 1)));
        counter = label("1 / 1"); counter.setGravity(Gravity.CENTER); counter.setOnClickListener(v -> jump()); pager.addView(counter, new LinearLayout.LayoutParams(dp(100), dp(48)));
        pager.addView(button("›", () -> go(page + 1))); root.addView(pager);
        searchBar = new LinearLayout(activity); searchBar.setGravity(Gravity.CENTER); searchBar.setBackgroundColor(0xff16161e); searchBar.addView(button("✕", () -> { hits = Collections.emptyList(); hit = -1; searchBar.setVisibility(View.GONE); if (web != null) web.clearMatches(); else showText(); }));
        searchBar.addView(button("‹", () -> stepHit(-1))); searchCounter = label(""); searchBar.addView(searchCounter); searchBar.addView(button("›", () -> stepHit(1))); searchBar.setVisibility(View.GONE); root.addView(searchBar);
        if (format.equals("pdf") || format.equals("image")) content.addView(image, new LinearLayout.LayoutParams(-1, -1));
        else if (format.equals("web")) buildWeb(); else attachText();
        pager.setVisibility(format.equals("image") || format.equals("web") ? View.GONE : View.VISIBLE);
        colors(); setContentView(root); setOnDismissListener(dialog -> dispose());
        setOnKeyListener((dialog, key, event) -> {
            if (!NativeDocViewer.option("volume", true) || (format.equals("web") || format.equals("image")) || (key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN)) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && loaded) { int direction = key == KeyEvent.KEYCODE_VOLUME_DOWN ? 1 : -1;
                if (!format.equals("pdf") && scroll.canScrollVertically(direction)) scroll.smoothScrollBy(0, (int) (direction * scroll.getHeight() * .85f)); else go(page + direction); }
            return true;
        });
    }
    private void buildWeb() {
        web = new WebView(activity); WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBlockNetworkLoads(true); settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setDomStorageEnabled(false); settings.setDatabaseEnabled(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false); settings.setGeolocationEnabled(false); settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (closed) return true;
                generation++; if (pending != null) pending.cancel(true);
                content.removeView(view); view.destroy(); web = null;
                error(getString(R.string.RegramDocMemory)); return true;
            }
            private WebResourceResponse blocked() { return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0])); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return blocked(); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) { return blocked(); }
        });
        web.setWebChromeClient(new WebChromeClient() { @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); } });
        web.setDownloadListener((url, userAgent, disposition, type, length) -> { });
        if (restricted) web.setOnLongClickListener(v -> true);
        web.setFindListener((active, total, done) -> { if (done && !closed) { searchCounter.setText((total == 0 ? 0 : active + 1) + " / " + total); searchBar.setVisibility(View.VISIBLE); } });
        content.addView(web, new LinearLayout.LayoutParams(-1, -1));
    }
    private void attachText() {
        if (text.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) text.getParent()).removeView(text);
        scroll.removeAllViews(); content.removeAllViews(); text.setHorizontallyScrolling(!wrap);
        if (wrap) scroll.addView(text, new ScrollView.LayoutParams(-1, -2));
        else { HorizontalScrollView horizontal = new HorizontalScrollView(activity); horizontal.addView(text, new HorizontalScrollView.LayoutParams(-2, -2)); scroll.addView(horizontal); }
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }
    interface Work { void run(int token) throws Exception; }
    private void job(Work work) {
        if (closed) return;
        int token = ++generation;
        if (pending != null) pending.cancel(true); worker.purge();
        pending = worker.submit(() -> {
            try { if (!closed) work.run(token); }
            catch (InterruptedIOException ignored) { }
            catch (Exception e) { ui(token, () -> error(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()))); }
            catch (OutOfMemoryError e) { ui(token, () -> error(getString(R.string.RegramDocMemory))); }
        });
    }
    private void ui(int token, Runnable action) { main.post(() -> { if (!closed && token == generation) action.run(); }); }
    void load() {
        status.setText(getString(R.string.Loading)); status.setVisibility(View.VISIBLE);
        hits = Collections.emptyList(); hit = -1; searchBar.setVisibility(View.GONE);
        final int savedPage = loaded ? page : NativeDocViewer.position(positionKey);
        final int selectedTheme = theme; final String selectedEncoding = encoding;
        final DocumentReader.Options options = new DocumentReader.Options(); options.wrap = NativeDocViewer.option("wrap", true);
        options.prettyJson = NativeDocViewer.option("prettyjson", true); options.csvTable = NativeDocViewer.option("csv_table", true);
        job(token -> {
            if (format.equals("pdf")) {
                try { descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); pdf = new PdfRenderer(descriptor); }
                catch (Exception e) { closePdf(); throw e; }
                int pages = pdf.getPageCount(); if (pages < 1) throw new IOException("Empty PDF");
                ui(token, () -> { count = pages; loaded = true; go(Math.max(0, Math.min(count - 1, savedPage))); });
            } else if (format.equals("image")) {
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException(getString(R.string.RegramDocUnsupportedImage));
                int sample = 1;
                while ((long) bounds.outWidth / sample * (bounds.outHeight / sample) > MAX_PIXELS || Math.max(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2;
                BitmapFactory.Options decode = new BitmapFactory.Options(); decode.inSampleSize = sample; decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), decode);
                if (bitmap == null) throw new IOException(getString(R.string.RegramDocUnsupportedImage));
                if ((long) bitmap.getWidth() * bitmap.getHeight() > MAX_PIXELS) { bitmap.recycle(); throw new IOException(getString(R.string.RegramDocMemory)); }
                int orientation = ExifInterface.ORIENTATION_NORMAL;
                try { orientation = new ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL); } catch (Exception ignored) { }
                final int degrees = orientation == 3 || orientation == 4 ? 180 : orientation == 6 || orientation == 7 ? 90 : orientation == 5 || orientation == 8 ? 270 : 0;
                final boolean flipped = orientation == 2 || orientation == 4 || orientation == 5 || orientation == 7;
                deliverBitmap(token, bitmap, degrees, flipped);
            } else if (format.equals("web")) {
                String markup = DocumentWeb.read(file, name, selectedTheme);
                ui(token, () -> { if (web != null) { web.loadDataWithBaseURL("https://regram.invalid/document/", markup, "text/html", "UTF-8", null); status.setVisibility(View.GONE); loaded = true; } });
            } else {
                DocumentReader.Result result = DocumentReader.load(file, format, name, options, selectedEncoding);
                ui(token, () -> { document = result; count = result.pages.size(); font = result.font; wrap = result.wrap; loaded = true;
                    attachText(); status.setVisibility(View.GONE); go(Math.max(0, Math.min(count - 1, savedPage))); });
            }
        });
    }
    private void go(int requested) {
        if (!loaded || requested < 0 || requested >= count || closed) return;
        page = requested; counter.setText((page + 1) + " / " + count);
        if (count > 1) NativeDocViewer.savePosition(positionKey, page);
        if (format.equals("pdf")) {
            status.setText(getString(R.string.Loading)); status.setVisibility(View.VISIBLE);
            final int selectedPage = page, width = Math.max(1, activity.getResources().getDisplayMetrics().widthPixels);
            job(token -> {
                DocumentText.check();
                try (PdfRenderer.Page rendered = pdf.openPage(selectedPage)) {
                    int pw = rendered.getWidth(), ph = rendered.getHeight();
                    int[] size = DocumentText.bitmapSize(pw, ph, width, MAX_PIXELS);
                    Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.WHITE);
                    try { rendered.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); }
                    catch (Exception e) { bitmap.recycle(); throw e; }
                    deliverBitmap(token, bitmap, -1, false);
                }
            });
        } else if (document != null) showText();
    }
    private void deliverBitmap(int token, Bitmap bitmap, int rotation, boolean mirror) {
        main.post(() -> {
            if (closed || token != generation) { bitmap.recycle(); return; }
            if (rotation >= 0) { this.rotation = rotation; this.mirror = mirror; }
            image.display(bitmap, this.rotation, this.mirror); status.setVisibility(View.GONE); loaded = true;
        });
    }
    private void showText() {
        if (document == null || closed) return;
        String value = document.pages.get(page); SpannableString highlighted = new SpannableString(value);
        int spans = 0;
        for (int i = 0; i < hits.size(); i++) { int[] match = hits.get(i); if (match[0] == page && (spans++ < 1500 || i == hit)) highlighted.setSpan(new BackgroundColorSpan(i == hit ? 0xffd67800 : theme == 0 ? 0xff78620a : 0xffffe678), match[1], match[2], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); }
        text.setTypeface(font == 1 ? Typeface.SERIF : font == 2 ? Typeface.MONOSPACE : Typeface.DEFAULT); text.setTextSize(textSize); text.setText(highlighted); colors(); scroll.scrollTo(0, 0);
        if (hit >= 0 && hits.get(hit)[0] == page) { int offset = hits.get(hit)[1]; text.post(() -> { if (!closed && text.getLayout() != null) scroll.scrollTo(0, text.getLayout().getLineTop(text.getLayout().getLineForOffset(Math.min(offset, text.length())))); }); }
    }
    private void search(String query) {
        if (web != null) { web.findAllAsync(query); return; }
        if (document == null) return;
        if (query.isEmpty()) { hits = Collections.emptyList(); hit = -1; searchBar.setVisibility(View.GONE); showText(); return; }
        List<String> pages = document.pages;
        job(token -> {
            Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            ArrayList<int[]> found = new ArrayList<>();
            for (int p = 0; p < pages.size() && found.size() < 5000; p++) {
                DocumentText.check(); Matcher matcher = pattern.matcher(pages.get(p));
                while (matcher.find() && found.size() < 5000) found.add(new int[]{p, matcher.start(), matcher.end()});
            }
            ui(token, () -> { hits = found; hit = found.isEmpty() ? -1 : 0; searchBar.setVisibility(found.isEmpty() ? View.GONE : View.VISIBLE); if (found.isEmpty()) toast(getString(R.string.RegramDocNoMatches)); else stepHit(0); showText(); });
        });
    }
    private void stepHit(int delta) {
        if (web != null) { web.findNext(delta >= 0); return; }
        if (hits.isEmpty()) return; hit = Math.floorMod(hit + delta, hits.size()); searchCounter.setText((hit + 1) + " / " + hits.size() + (hits.size() == 5000 ? "+" : "")); go(hits.get(hit)[0]);
    }
    private void input(int title, boolean numeric, String initial, java.util.function.Consumer<String> result) {
        EditText edit = new EditText(activity); edit.setText(initial); edit.setSingleLine(); edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1024)});
        if (numeric) edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        alert().setTitle(getString(title)).setView(edit).setPositiveButton(getString(R.string.OK), (d, which) -> { if (!closed) result.accept(edit.getText().toString()); }).setNegativeButton(getString(R.string.Cancel), null).show();
    }
    private void jump() { input(R.string.RegramDocJump, true, String.valueOf(page + 1), value -> { try { go(Integer.parseInt(value) - 1); } catch (Exception ignored) { } }); }
    private void menu() {
        ArrayList<String> labels = new ArrayList<>(); ArrayList<Runnable> actions = new ArrayList<>();
        java.util.function.BiConsumer<Integer, Runnable> add = (title, action) -> { labels.add(getString(title)); actions.add(action); };
        if (document != null || web != null) add.accept(R.string.RegramDocSearch, () -> input(R.string.RegramDocSearch, false, "", this::search));
        if (count > 1) add.accept(R.string.RegramDocJump, this::jump);
        if (format.equals("pdf") || format.equals("image")) {
            add.accept(R.string.RegramDocInvert, () -> { invert = !invert; NativeDocViewer.settings().edit().putBoolean("invert", invert).apply(); image.invert(invert); });
            add.accept(R.string.RegramDocRotate, () -> { rotation = (rotation + 90) % 360; image.rotate(); });
            add.accept(R.string.RegramDocFit, () -> { fitWidth = !fitWidth; NativeDocViewer.settings().edit().putBoolean("fitwidth", fitWidth).apply(); image.fitWidth(fitWidth); });
        }
        if (document != null) {
            add.accept(R.string.RegramDocFont, () -> { font = (font + 1) % 3; showText(); });
            add.accept(R.string.RegramDocWrap, () -> { wrap = !wrap; attachText(); showText(); });
            if (document.raw) add.accept(R.string.RegramDocEncoding, this::chooseEncoding);
            if (!restricted) { add.accept(R.string.RegramDocCopyPage, () -> copy(document.pages.get(page))); add.accept(R.string.RegramDocCopyAll, this::copyAll); }
        }
        if (document != null || web != null) {
            add.accept(R.string.RegramDocLarger, () -> resize(1)); add.accept(R.string.RegramDocSmaller, () -> resize(-1));
            add.accept(R.string.RegramDocTheme, () -> { theme = (theme + 1) % 3; NativeDocViewer.settings().edit().putInt("theme", theme).apply(); colors(); if (web != null) load(); else showText(); });
        }
        if (web != null) add.accept(R.string.RegramDocSource, () -> NativeDocViewer.show(activity, file, name, mime, "text", restricted, account));
        if (!restricted) { add.accept(R.string.RegramDocShare, () -> external(true)); add.accept(R.string.RegramDocExternal, () -> external(false)); }
        add.accept(R.string.RegramDocInfo, () -> alert().setTitle(getString(R.string.RegramDocInfo)).setMessage(name + "\n" + mime + "\n" + file.length() + " B\n" + format + (document == null ? "" : "\n" + document.encoding)).setPositiveButton(getString(R.string.OK), null).show());
        alert().setItems(labels.toArray(new String[0]), (d, which) -> { if (!closed) actions.get(which).run(); }).show();
    }
    private void resize(int direction) {
        textSize = Math.max(8, Math.min(34, textSize + direction)); NativeDocViewer.settings().edit().putFloat("text_size", textSize).apply();
        if (web != null) web.getSettings().setTextZoom((int) (textSize / 15 * 100)); else text.setTextSize(textSize);
    }
    private void chooseEncoding() {
        String[] encodings = {null, "UTF-8", "windows-1251", "KOI8-R", "IBM866", "UTF-16", "windows-1252"};
        String[] names = {getString(R.string.RegramDocAuto), "UTF-8", "Windows-1251", "KOI8-R", "CP866", "UTF-16", "Windows-1252"};
        alert().setTitle(getString(R.string.RegramDocEncoding)).setItems(names, (d, which) -> { if (!closed) { encoding = encodings[which]; load(); } }).show();
    }
    private void copy(String value) { if (!restricted) { if (value.length() > 900000) toast(getString(R.string.RegramDocCopyLimit)); else AndroidUtilities.addToClipboard(value); } }
    private void copyAll() { int length = 0; for (String page : document.pages) { length += page.length() + document.separator.length(); if (length > 900000) { toast(getString(R.string.RegramDocCopyLimit)); return; } } copy(String.join(document.separator, document.pages)); }
    private void external(boolean share) {
        if (restricted) return;
        try {
            Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            Intent intent = new Intent(share ? Intent.ACTION_SEND : Intent.ACTION_VIEW); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (share) { intent.setType(mime); intent.putExtra(Intent.EXTRA_STREAM, uri); intent.setClipData(ClipData.newRawUri(name, uri)); }
            else intent.setDataAndType(uri, mime);
            activity.startActivity(Intent.createChooser(intent, getString(share ? R.string.RegramDocShare : R.string.RegramDocExternal)));
        } catch (Exception e) { toast(getString(R.string.RegramDocExternalError)); }
    }
    private void colors() { int bg = theme == 1 ? 0xfffafaf7 : theme == 2 ? 0xfff4ecd8 : 0xff09090d; int fg = theme == 1 ? 0xff1c1c20 : theme == 2 ? 0xff433422 : 0xffe8e8ee;
        root.setBackgroundColor(bg); text.setTextColor(fg); status.setTextColor(fg); counter.setTextColor(0xffe8e8ee); searchCounter.setTextColor(0xffe8e8ee); if (web != null) web.setBackgroundColor(bg); }
    private void error(String message) { status.setText(getString(restricted ? R.string.RegramDocReadProtectedError : R.string.RegramDocReadError) + "\n" + (message.length() > 300 ? message.substring(0, 300) : message)); status.setVisibility(View.VISIBLE); }
    private TextView label(String value) { TextView view = new TextView(activity); view.setText(value); view.setTextColor(0xffe8e8ee); view.setTextSize(15); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView button(String value, Runnable action) { TextView view = label(value); view.setGravity(Gravity.CENTER); view.setPadding(dp(14), dp(6), dp(14), dp(6)); view.setMinHeight(dp(48)); view.setOnClickListener(v -> action.run()); return view; }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
    private void toast(String value) { Toast.makeText(activity, value, Toast.LENGTH_LONG).show(); }
    private AlertDialog.Builder alert() {
        return new AlertDialog.Builder(activity) {
            @Override public AlertDialog show() {
                AlertDialog child = create(); if (closed) return child;
                if ((DocumentViewerDialog.this.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0)
                    child.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                child.setOnDismissListener(d -> childDialogs.remove(child));
                childDialogs.add(child); child.show(); return child;
            }
        };
    }
    private void closePdf() {
        PdfRenderer old = pdf; pdf = null;
        try { if (old != null) old.close(); }
        finally { ParcelFileDescriptor fd = descriptor; descriptor = null; if (fd != null) try { fd.close(); } catch (IOException ignored) { } }
    }
    void dispose() {
        if (closed) return; closed = true; generation++;
        NativeDocViewer.removed(this); if (loaded && count > 1) NativeDocViewer.savePosition(positionKey, page);
        if (pending != null) pending.cancel(true); worker.purge();
        // Never acquire a render lock or close a page/renderer on the UI thread.
        worker.execute(this::closePdf); worker.shutdown();
        for (AlertDialog child : new ArrayList<>(childDialogs)) child.dismiss(); childDialogs.clear();
        image.release(); document = null; hits = Collections.emptyList();
        if (web != null) { web.stopLoading(); content.removeView(web); web.destroy(); web = null; }
    }
}
