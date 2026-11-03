package app.regram.documents;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ImageView;

/** Pinch, pan, double-tap and matrix rotation; rotation never allocates a second bitmap. */
final class DocumentImageView extends ImageView {
    private final Matrix transform = new Matrix();
    private final RectF bounds = new RectF();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private Bitmap bitmap;
    private float scale = 1, minimum = 1, fit = 1;
    private int rotation;
    private boolean fitWidth, mirror;
    DocumentImageView(Context context) {
        super(context); setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) { zoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY()); return true; }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onScroll(MotionEvent first, MotionEvent second, float dx, float dy) {
                if (!scaleDetector.isInProgress()) { transform.postTranslate(-dx, -dy); constrain(); } return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) { if (scale > fit * 1.1f) reset(); else zoom(2.6f, event.getX(), event.getY()); return true; }
        });
    }
    void display(Bitmap value, int rotation, boolean mirror) { bitmap = value; this.rotation = rotation; this.mirror = mirror; setImageBitmap(value); reset(); }
    void rotate() { rotation = (rotation + 90) % 360; reset(); }
    void fitWidth(boolean value) { fitWidth = value; reset(); }
    void invert(boolean value) {
        if (value) setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{-1,0,0,0,255, 0,-1,0,0,255, 0,0,-1,0,255, 0,0,0,1,0})));
        else clearColorFilter();
    }
    void release() { bitmap = null; setImageDrawable(null); }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { super.onSizeChanged(w, h, oldW, oldH); reset(); }
    private void reset() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
        transform.reset(); if (mirror) transform.postScale(-1, 1); transform.postRotate(rotation);
        bounds.set(0, 0, bitmap.getWidth(), bitmap.getHeight()); transform.mapRect(bounds);
        minimum = Math.min(getWidth() / bounds.width(), getHeight() / bounds.height());
        fit = fitWidth ? getWidth() / bounds.width() : minimum; scale = fit;
        transform.postScale(scale, scale); constrain();
    }
    private void zoom(float factor, float x, float y) {
        if (bitmap == null || !Float.isFinite(factor)) return;
        float next = Math.max(minimum, Math.min(fit * 6, scale * factor));
        transform.postScale(next / scale, next / scale, x, y); scale = next; constrain();
    }
    private void constrain() {
        if (bitmap == null) return;
        bounds.set(0, 0, bitmap.getWidth(), bitmap.getHeight()); transform.mapRect(bounds);
        float dx = bounds.width() <= getWidth() ? (getWidth() - bounds.width()) / 2 - bounds.left : bounds.left > 0 ? -bounds.left : bounds.right < getWidth() ? getWidth() - bounds.right : 0;
        float dy = bounds.height() <= getHeight() ? (getHeight() - bounds.height()) / 2 - bounds.top : bounds.top > 0 ? -bounds.top : bounds.bottom < getHeight() ? getHeight() - bounds.bottom : 0;
        transform.postTranslate(dx, dy); setImageMatrix(transform);
    }
    @Override public boolean onTouchEvent(MotionEvent event) { scaleDetector.onTouchEvent(event); gestures.onTouchEvent(event); return true; }
}
