package com.Yubai.TouchDX;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class TouchView extends View {
    private float touchRadius = 22.0f;
    private boolean isCalibrationMode = false;
    private boolean isInGame = false;
    private float userScale = 1.0f;

    public void setTouchRadius(float radius) { this.touchRadius = radius; invalidate(); }
    public void setCalibrationMode(boolean mode) { this.isCalibrationMode = mode; invalidate(); }
    public void setInGame(boolean inGame) { this.isInGame = inGame; invalidate(); }
    public void setUserScale(float scale) { this.userScale = scale; invalidate(); }
    public float getUserScale() { return this.userScale; }

    public interface OnScaleChangeListener {
        void onScaleChanged(float newScale);
    }
    public interface OnDragChangeListener {
        void onDragChanged(float dx, float dy);
    }
    private OnScaleChangeListener scaleChangeListener;
    private OnDragChangeListener dragChangeListener;
    public void setOnScaleChangeListener(OnScaleChangeListener listener) {
        this.scaleChangeListener = listener;
    }
    public void setOnDragChangeListener(OnDragChangeListener listener) {
        this.dragChangeListener = listener;
    }
    public void setOffset(float ox, float oy) {
        this.offsetX = ox;
        this.offsetY = oy;
        invalidate();
    }
    
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }

    private Paint activePaint;
    private Paint circlePaint;
    private float dx;
    private float dy;
    private float lastTouchX;
    private float lastTouchY;
    private List<TouchRegion> regions;
    private float scale;
    private float scaleX;
    private float scaleY;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private Paint strokePaint;
    private MaimaiTouchClient touchClient;
    private float[] pointerX = new float[10];
    private float[] pointerY = new float[10];
    private boolean[] pointerActive = new boolean[10];
    private boolean[] regionActiveBuffer;
    private float customAlpha = 1.0f;
    
    private ScaleGestureDetector scaleDetector;

    public void setCustomAlpha(float a) {
        this.customAlpha = a;
        invalidate();
    }

    public void setTouchClient(MaimaiTouchClient maimaiTouchClient) {
        this.touchClient = maimaiTouchClient;
    }

    public static class TouchRegion {
        int fillColor;
        String id;
        boolean isActive;
        boolean isInteractive;
        Path path;
        Region region;
        Rect bounds;

        TouchRegion(Path path, String str, int i) {
            this.path = path;
            this.id = str;
            this.fillColor = i;
            boolean z = str != null && str.matches("^[A-E][1-8]?$");
            this.isInteractive = z;
            if (z) {
                RectF rectF = new RectF();
                path.computeBounds(rectF, true);
                Region region = new Region((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                Region region2 = new Region();
                this.region = region2;
                region2.setPath(path, region);
                this.bounds = region2.getBounds();
            }
        }
    }

    public TouchView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        Paint paint = new Paint();
        this.strokePaint = paint;
        paint.setColor(Color.parseColor("#FFFFFF"));
        this.strokePaint.setStyle(Paint.Style.STROKE);
        this.strokePaint.setStrokeWidth(2.0f);
        this.strokePaint.setAntiAlias(true);
        
        Paint paint2 = new Paint();
        this.activePaint = paint2;
        paint2.setColor(Color.parseColor("#FF0000"));
        this.activePaint.setStyle(Paint.Style.FILL);
        this.activePaint.setAntiAlias(true);
        
        Paint paint3 = new Paint();
        this.circlePaint = paint3;
        paint3.setColor(Color.parseColor("#88FFFFFF"));
        this.circlePaint.setStyle(Paint.Style.FILL);
        this.circlePaint.setAntiAlias(true);

        this.regions = new ArrayList();
        
        this.scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (isCalibrationMode) {
                    float factor = detector.getScaleFactor();
                    userScale *= factor;
                    userScale = Math.max(0.1f, Math.min(userScale, 3.0f));
                    
                    if (scaleChangeListener != null) {
                        scaleChangeListener.onScaleChanged(userScale);
                    }
                    invalidate();
                    return true;
                }
                return false;
            }
        });
    }

    public void addRegion(Path path, String str, int i) {
        this.regions.add(new TouchRegion(path, str, i));
        this.regionActiveBuffer = new boolean[this.regions.size()];
    }

    @Override
    protected void onSizeChanged(int i, int i2, int i3, int i4) {
        super.onSizeChanged(i, i2, i3, i4);
        float f = i;
        float f2 = f / 896.0f;
        this.scaleX = f2;
        float f3 = i2;
        float f4 = f3 / 898.0f;
        this.scaleY = f4;
        
        float min = Math.min(f2, f4);
        
        this.scale = min;
        this.dx = (f - (896.0f * min)) / 2.0f;
        this.dy = (f3 - (min * 898.0f)) / 2.0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        
        float currentTotalScale = this.scale * this.userScale;
        float scaledWidth = 896.0f * currentTotalScale;
        float scaledHeight = 898.0f * currentTotalScale;
        
        float centerX = dx + offsetX + (896.0f * this.scale) / 2.0f;
        float centerY = dy + offsetY + (898.0f * this.scale) / 2.0f;
        
        canvas.translate(centerX - scaledWidth / 2.0f, centerY - scaledHeight / 2.0f);
        canvas.scale(currentTotalScale, currentTotalScale);
        
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        for (TouchRegion touchRegion : this.regions) {
            if (touchRegion.isActive && touchRegion.isInteractive) {
                this.activePaint.setAlpha((int)(255 * customAlpha));
                canvas.drawPath(touchRegion.path, this.activePaint);
            } else {
                paint.setColor(touchRegion.fillColor);
                paint.setAlpha((int)(Color.alpha(touchRegion.fillColor) * customAlpha));
                canvas.drawPath(touchRegion.path, paint);
            }
            this.strokePaint.setAlpha((int)(255 * customAlpha));
            canvas.drawPath(touchRegion.path, this.strokePaint);
        }
        canvas.restore();
        
        for (int i = 0; i < 10; i++) {
            if (pointerActive[i]) {
                canvas.drawCircle(pointerX[i], pointerY[i], touchRadius, circlePaint);
            }
        }
    }

    private boolean intersects(TouchRegion touchRegion, int x, int y, int radius) {
        Region region = touchRegion.region;
        if (region == null) return false;
        
        Rect bounds = touchRegion.bounds;
        if (x + radius < bounds.left || x - radius > bounds.right || y + radius < bounds.top || y - radius > bounds.bottom) {
            return false;
        }

        if (region.contains(x, y)) return true;
        
        for (int i = 0; i < 8; i++) {
            double angle = i * 0.7853981633974483; // PI/4
            int cx = x + (int)(radius * Math.cos(angle));
            int cy = y + (int)(radius * Math.sin(angle));
            if (cx >= bounds.left && cx <= bounds.right && cy >= bounds.top && cy <= bounds.bottom) {
                if (region.contains(cx, cy)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (isCalibrationMode) {
            scaleDetector.onTouchEvent(motionEvent);

            int action = motionEvent.getActionMasked();

            if (motionEvent.getPointerCount() > 1) {
                // This is a multi-touch gesture, so we shouldn't treat it as a drag.
                // Reset last touch coordinates to prevent jumping when one finger is lifted.
                lastTouchX = -1;
                lastTouchY = -1;
            } else {
                // This is a single-touch gesture, handle dragging.
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = motionEvent.getX();
                        lastTouchY = motionEvent.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (lastTouchX != -1 && lastTouchY != -1) {
                            float x = motionEvent.getX();
                            float y = motionEvent.getY();
                            offsetX += x - lastTouchX;
                            offsetY += y - lastTouchY;
                            if (dragChangeListener != null) {
                                dragChangeListener.onDragChanged(offsetX, offsetY);
                            }
                            invalidate();
                            lastTouchX = x;
                            lastTouchY = y;
                        } else {
                            // After a multi-touch, the first move event should just set the anchor point.
                            lastTouchX = motionEvent.getX();
                            lastTouchY = motionEvent.getY();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Reset on gesture end.
                        lastTouchX = -1;
                        lastTouchY = -1;
                        break;
                }
            }
            return true;
        }

        if (this.regionActiveBuffer == null) {
            this.regionActiveBuffer = new boolean[this.regions.size()];
        }
        boolean[] zArr = this.regionActiveBuffer;
        for (int i = 0; i < zArr.length; i++) zArr[i] = false;

        int actionMasked = motionEvent.getActionMasked();
        int pointerCount = motionEvent.getPointerCount();

        for (int i = 0; i < 10; i++) pointerActive[i] = false;

        float currentTotalScale = this.scale * this.userScale;
        float scaledWidth = 896.0f * currentTotalScale;
        float scaledHeight = 898.0f * currentTotalScale;
        float centerX = dx + offsetX + (896.0f * this.scale) / 2.0f;
        float centerY = dy + offsetY + (898.0f * this.scale) / 2.0f;
        float offsetXScreen = centerX - scaledWidth / 2.0f;
        float offsetYScreen = centerY - scaledHeight / 2.0f;

        for (int i = 0; i < pointerCount; i++) {
            if ((actionMasked != MotionEvent.ACTION_POINTER_UP && actionMasked != MotionEvent.ACTION_UP && actionMasked != MotionEvent.ACTION_CANCEL) || i != motionEvent.getActionIndex()) {
                float x = motionEvent.getX(i);
                float y = motionEvent.getY(i);
                int pid = motionEvent.getPointerId(i);
                if (pid >= 0 && pid < 10) {
                    pointerX[pid] = x;
                    pointerY[pid] = y;
                    pointerActive[pid] = true;
                }

                int i2 = (int) ((x - offsetXScreen) / currentTotalScale);
                int i3 = (int) ((y - offsetYScreen) / currentTotalScale);
                int scaledRadius = isInGame ? (int) (touchRadius / currentTotalScale) : 0;

                boolean foundOutGameHit = false;
                for (int i4 = 0; i4 < this.regions.size(); i4++) {
                    TouchRegion touchRegion = this.regions.get(i4);
                    if (touchRegion.isInteractive && !zArr[i4]) {
                        if (isInGame) {
                            if (intersects(touchRegion, i2, i3, scaledRadius)) {
                                zArr[i4] = true;
                            }
                        } else {
                            if (!foundOutGameHit && touchRegion.region != null && touchRegion.region.contains(i2, i3)) {
                                zArr[i4] = true;
                                foundOutGameHit = true;
                            }
                        }
                    }
                }
            }
        }
        boolean z = false;
        for (int i5 = 0; i5 < this.regions.size(); i5++) {
            TouchRegion touchRegion2 = this.regions.get(i5);
            if (touchRegion2.isInteractive) {
                boolean z2 = touchRegion2.isActive;
                boolean z3 = zArr[i5];
                if (z2 != z3) {
                    touchRegion2.isActive = z3;
                    if (this.touchClient != null && touchRegion2.id != null) {
                        this.touchClient.setButtonState(touchRegion2.id, touchRegion2.isActive);
                    }
                    z = true;
                }
            }
        }
        if (z || actionMasked == MotionEvent.ACTION_MOVE || actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_POINTER_DOWN || actionMasked == MotionEvent.ACTION_POINTER_UP) {
            invalidate();
        }
        return true;
    }
}
