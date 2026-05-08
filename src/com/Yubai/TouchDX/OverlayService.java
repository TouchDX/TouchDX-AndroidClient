package com.Yubai.TouchDX;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private TouchView touchView;
    private FrameLayout uiContainer;
    private LinearLayout panelContainer;
    private ScrollView scrollView;
    private Button floatBtn;
    private Button calibrateButton;
    private Button latencyButton;
    private Button lockTouchButton;
    private SeekBar radiusSeekBar;
    private SeekBar alphaSeekBar;
    private SeekBar scaleSeekBar;
    private TextView statusTextView;
    
    private boolean isCalibrationMode = false;
    private boolean isTouchLocked = false;
    private MaimaiTouchClient touchClient;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isPanelOpen = false;
    private float playingAlpha = 0.0f;
    private DisplayMetrics displayMetrics;

    private WindowManager.LayoutParams touchParams;
    private WindowManager.LayoutParams uiParams;
    private SharedPreferences prefs;

    public IBinder onBind(Intent intent) {
        return null;
    }

    private int px(float val) {
        if (displayMetrics == null) return (int)val;
        float scale = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels) / 1000f;
        return (int) (val * scale);
    }

    private Button createStyledButton(String text, String bgColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16f));
        btn.setPadding(px(12f), px(12f), px(12f), px(12f));
        btn.setAllCaps(false);

        int normalColor = Color.parseColor(bgColor);
        int pressedColor = Color.argb(
            Color.alpha(normalColor),
            Math.max(0, Color.red(normalColor) - 20),
            Math.max(0, Color.green(normalColor) - 20),
            Math.max(0, Color.blue(normalColor) - 20)
        );

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(px(12f));

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setCornerRadius(px(12f));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);

        btn.setBackground(states);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setElevation(px(2f));
            btn.setStateListAnimator(null); // Remove default elevation animation
        }
        return btn;
    }

    private void updateScale(int progress) {
        int newProgress = Math.max(0, Math.min(500, progress));
        float userScale = Math.max(0.1f, newProgress / 100.0f);
        touchView.setUserScale(userScale);
        scaleSeekBar.setProgress(newProgress);
        prefs.edit().putFloat("userScale", userScale).apply();
    }

    private void setSeekBarStyle(SeekBar seekBar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int activeColor = Color.parseColor("#0A84FF");
            int inactiveColor = Color.parseColor("#555558");
            ColorStateList progressTint = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_enabled}}, 
                new int[]{activeColor}
            );
            ColorStateList thumbTint = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_enabled}}, 
                new int[]{activeColor}
            );

            seekBar.setProgressTintList(progressTint);
            seekBar.setSecondaryProgressTintList(ColorStateList.valueOf(inactiveColor));
            seekBar.setThumbTintList(thumbTint);
        }
    }

    public void onCreate() {
        super.onCreate();
        this.prefs = getSharedPreferences("TouchDX_Prefs", Context.MODE_PRIVATE);
        this.windowManager = (WindowManager)this.getSystemService("window");
        this.touchClient = new MaimaiTouchClient();
        
        displayMetrics = new DisplayMetrics();
        this.windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        // User reports 2x resolution, so we divide by 2 here to correct it.
        displayMetrics.widthPixels /= 2;
        displayMetrics.heightPixels /= 2;

        // 1. Touch Window Setup
        this.touchView = new TouchView(this);
        this.touchView.setTouchClient(this.touchClient);
        this.touchView.setCustomAlpha(0.0f);
        this.touchView.setTouchRadius(prefs.getFloat("touchRadius", 22f));
        this.touchView.setUserScale(prefs.getFloat("userScale", 1.0f));
        this.touchView.setOffset(prefs.getFloat("offsetX", 0f), prefs.getFloat("offsetY", 0f));
        this.playingAlpha = prefs.getFloat("playingAlpha", 0.0f);
        
        SvgLoader.load(this.touchView);
        
        int n = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        int flags = 66312 | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL; // 0x10308 | 0x20
        
        this.touchParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 
            WindowManager.LayoutParams.MATCH_PARENT, 
            n, 
            flags, 
            -3);
        if (Build.VERSION.SDK_INT >= 28) {
            this.touchParams.layoutInDisplayCutoutMode = 1; // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        this.touchView.setSystemUiVisibility(5894);
        this.windowManager.addView(this.touchView, this.touchParams);

        // 2. UI Window Setup
        this.uiContainer = new FrameLayout(this);
        
        this.uiParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, 
            WindowManager.LayoutParams.WRAP_CONTENT, 
            n, 
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, 
            -3);
        this.uiParams.gravity = Gravity.TOP | Gravity.LEFT;
        this.uiParams.x = prefs.getInt("uiX", px(20f));
        this.uiParams.y = prefs.getInt("uiY", px(20f));
        this.windowManager.addView(this.uiContainer, this.uiParams);

        // Floating Button
        this.floatBtn = new Button(this);
        this.floatBtn.setText("M");
        this.floatBtn.setTextColor(Color.WHITE);
        this.floatBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(20f));
        this.floatBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.floatBtn.setElevation(px(8f));
            this.floatBtn.setStateListAnimator(null);
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.parseColor("#E62C2C2E"));
        shape.setStroke(px(1f), Color.parseColor("#44FFFFFF"));
        this.floatBtn.setBackground(shape);
        
        FrameLayout.LayoutParams floatBtnParams = new FrameLayout.LayoutParams(px(56f), px(56f));
        floatBtnParams.gravity = Gravity.TOP | Gravity.LEFT;
        this.uiContainer.addView(this.floatBtn, floatBtnParams);

        // Settings Panel
        this.scrollView = new ScrollView(this);
        this.scrollView.setVerticalScrollBarEnabled(false);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(px(320f), -2);
        scrollParams.gravity = Gravity.TOP | Gravity.LEFT;
        scrollParams.topMargin = px(70f);
        scrollParams.bottomMargin = px(20f);
        
        this.panelContainer = new LinearLayout(this);
        this.panelContainer.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#D91C1C1E")); // Modern glass dark
        panelBg.setCornerRadius(px(24f));
        this.panelContainer.setBackground(panelBg);
        this.panelContainer.setPadding(px(24f), px(24f), px(24f), px(24f));
        
        this.statusTextView = new TextView(this);
        this.statusTextView.setText("\u4ea4\u4e92\u6a21\u5f0f");
        this.statusTextView.setTextColor(Color.parseColor("#32D74B")); // Modern green
        this.statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(18f));
        this.statusTextView.setGravity(Gravity.CENTER);
        this.statusTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.bottomMargin = px(20f);
        this.panelContainer.addView(this.statusTextView, statusParams);

        this.lockTouchButton = createStyledButton("\u9501\u5b9a\u89e6\u6478 (\u7a7f\u900f\u5230\u684c\u9762)", "#0A84FF"); // Modern Blue
        LinearLayout.LayoutParams lockParams = new LinearLayout.LayoutParams(-1, -2);
        lockParams.bottomMargin = px(12f);
        this.panelContainer.addView(this.lockTouchButton, lockParams);

        this.calibrateButton = createStyledButton("\u6821\u51c6 Touch", "#3A3A3C");
        LinearLayout.LayoutParams calibParams = new LinearLayout.LayoutParams(-1, -2);
        calibParams.bottomMargin = px(12f);
        this.panelContainer.addView(this.calibrateButton, calibParams);
        
        Button resetPosButton = createStyledButton("\u91cd\u7f6e\u5e03\u5c40\u4f4d\u7f6e", "#3A3A3C");
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, -2);
        resetParams.bottomMargin = px(12f);
        this.panelContainer.addView(resetPosButton, resetParams);
        resetPosButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OverlayService.this.touchView.setOffset(0f, 0f);
                prefs.edit().putFloat("offsetX", 0f).putFloat("offsetY", 0f).apply();
                Toast.makeText(OverlayService.this, "\u5e03\u5c40\u5df2\u5c45\u4e2d", Toast.LENGTH_SHORT).show();
            }
        });
        
        this.latencyButton = createStyledButton("\u6d4b\u7f51\u7edc\u5ef6\u8fdf", "#3A3A3C");
        this.latencyButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams latParams = new LinearLayout.LayoutParams(-1, -2);
        latParams.bottomMargin = px(12f);
        this.panelContainer.addView(this.latencyButton, latParams);
        
        // Radius
        TextView radiusLabel = new TextView(this);
        radiusLabel.setText("\u89e6\u6478\u534a\u5f84");
        radiusLabel.setTextColor(Color.WHITE);
        radiusLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16f));
        this.panelContainer.addView(radiusLabel);
        
        this.radiusSeekBar = new SeekBar(this);
        this.radiusSeekBar.setMax(50);
        this.radiusSeekBar.setProgress(17);
        setSeekBarStyle(this.radiusSeekBar);
        LinearLayout.LayoutParams radiusParams = new LinearLayout.LayoutParams(-1, -2);
        radiusParams.bottomMargin = px(15f);
        this.panelContainer.addView(this.radiusSeekBar, radiusParams);
        this.radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar seekBar, int n, boolean bl) {
                float r = 5.0f + (float)n;
                OverlayService.this.touchView.setTouchRadius(r);
                prefs.edit().putFloat("touchRadius", r).apply();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Scale
        TextView scaleLabel = new TextView(this);
        scaleLabel.setText("\u753b\u9762\u7f29\u653e");
        scaleLabel.setTextColor(Color.WHITE);
        scaleLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16f));
        this.panelContainer.addView(scaleLabel);

        LinearLayout scaleControlLayout = new LinearLayout(this);
        scaleControlLayout.setOrientation(LinearLayout.HORIZONTAL);
        scaleControlLayout.setGravity(Gravity.CENTER_VERTICAL);

        Button minusButton = createStyledButton("-", "#3A3A3C");
        minusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int progress = scaleSeekBar.getProgress();
                updateScale(progress - 1);
            }
        });
        LinearLayout.LayoutParams minusParams = new LinearLayout.LayoutParams(px(48), px(48));
        scaleControlLayout.addView(minusButton, minusParams);

        this.scaleSeekBar = new SeekBar(this);
        this.scaleSeekBar.setMax(500); // Increased precision
        this.scaleSeekBar.setProgress((int)(prefs.getFloat("userScale", 1.0f) * 100));
        setSeekBarStyle(this.scaleSeekBar);
        LinearLayout.LayoutParams scaleParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        scaleParams.leftMargin = px(10f);
        scaleParams.rightMargin = px(10f);
        scaleControlLayout.addView(this.scaleSeekBar, scaleParams);

        Button plusButton = createStyledButton("+", "#3A3A3C");
        plusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int progress = scaleSeekBar.getProgress();
                updateScale(progress + 1);
            }
        });
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(px(48), px(48));
        scaleControlLayout.addView(plusButton, plusParams);

        LinearLayout.LayoutParams scaleControlLayoutParams = new LinearLayout.LayoutParams(-1, -2);
        scaleControlLayoutParams.bottomMargin = px(15f);
        this.panelContainer.addView(scaleControlLayout, scaleControlLayoutParams);
        
        this.touchView.setOnScaleChangeListener(new TouchView.OnScaleChangeListener() {
            @Override
            public void onScaleChanged(float newScale) {
                int progress = (int)(newScale * 100);
                if (progress < 0) progress = 0;
                if (progress > 300) progress = 300;
                OverlayService.this.scaleSeekBar.setProgress(progress);
                prefs.edit().putFloat("userScale", newScale).apply();
            }
        });
        
        this.touchView.setOnDragChangeListener(new TouchView.OnDragChangeListener() {
            @Override
            public void onDragChanged(float dx, float dy) {
                prefs.edit().putFloat("offsetX", dx).putFloat("offsetY", dy).apply();
            }
        });

        this.scaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar seekBar, int n, boolean bl) {
                if (bl) {
                    updateScale(n);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Alpha
        TextView alphaLabel = new TextView(this);
        alphaLabel.setText("\u6e38\u73a9\u900f\u660e\u5ea6");
        alphaLabel.setTextColor(Color.WHITE);
        alphaLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16f));
        this.panelContainer.addView(alphaLabel);

        this.alphaSeekBar = new SeekBar(this);
        this.alphaSeekBar.setMax(100);
        this.alphaSeekBar.setProgress((int)(this.playingAlpha * 100.0f));
        setSeekBarStyle(this.alphaSeekBar);
        LinearLayout.LayoutParams alphaParams = new LinearLayout.LayoutParams(-1, -2);
        alphaParams.bottomMargin = px(10f);
        this.panelContainer.addView(this.alphaSeekBar, alphaParams);
        this.alphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar seekBar, int n, boolean bl) {
                OverlayService.this.playingAlpha = (float)n / 100.0f;
                prefs.edit().putFloat("playingAlpha", OverlayService.this.playingAlpha).apply();
                if (!OverlayService.this.isCalibrationMode) {
                    OverlayService.this.touchView.setCustomAlpha(OverlayService.this.playingAlpha);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        scrollView.addView(this.panelContainer);
        this.uiContainer.addView(scrollView, scrollParams);
        
        scrollView.setVisibility(View.GONE);
        scrollView.setPivotX(0f);
        scrollView.setPivotY(0f);
        setSlidersVisibility(View.GONE);
        
        // Touch events for FloatBtn dragging
        this.floatBtn.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging = false;
            private long touchStartTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = uiParams.x;
                        initialY = uiParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = (event.getRawX() - initialTouchX) / 2.0f;
                        float dy = (event.getRawY() - initialTouchY) / 2.0f;
                        if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            uiParams.x = initialX + (int) dx;
                            uiParams.y = initialY + (int) dy;
                            windowManager.updateViewLayout(uiContainer, uiParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        if (!isDragging && duration < 200) {
                            togglePanel();
                        } else if (isDragging) {
                            prefs.edit().putInt("uiX", uiParams.x).putInt("uiY", uiParams.y).apply();
                        }
                        return true;
                }
                return false;
            }
        });

        this.lockTouchButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                OverlayService.this.isTouchLocked = !OverlayService.this.isTouchLocked;
                if (OverlayService.this.isTouchLocked) {
                    OverlayService.this.lockTouchButton.setText("\u89e3\u9501\u89e6\u6478 (\u6062\u590d\u6e38\u620f)");
                    OverlayService.this.lockTouchButton.setBackgroundColor(Color.parseColor("#55FF0000"));
                    OverlayService.this.touchView.setVisibility(View.GONE);
                    OverlayService.this.touchParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                } else {
                    OverlayService.this.lockTouchButton.setText("\u9501\u5b9a\u89e6\u6478 (\u7a7f\u900f\u5230\u684c\u9762)");
                    OverlayService.this.lockTouchButton.setBackgroundColor(Color.parseColor("#550000FF"));
                    OverlayService.this.touchView.setVisibility(View.VISIBLE);
                    OverlayService.this.touchParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                }
                OverlayService.this.windowManager.updateViewLayout(OverlayService.this.touchView, OverlayService.this.touchParams);
                OverlayService.this.windowManager.updateViewLayout(OverlayService.this.uiContainer, OverlayService.this.uiParams);
            }
        });
        
        this.calibrateButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                OverlayService.this.isCalibrationMode = !OverlayService.this.isCalibrationMode;
                OverlayService.this.touchView.setCalibrationMode(OverlayService.this.isCalibrationMode);
                if (OverlayService.this.isCalibrationMode) {
                    OverlayService.this.calibrateButton.setText("\u5b8c\u6210\u6821\u51c6");
                    OverlayService.this.calibrateButton.setBackgroundColor(Color.parseColor("#88008800"));
                    OverlayService.this.touchView.setCustomAlpha(0.6f);
                    OverlayService.this.setSlidersVisibility(View.VISIBLE);
                } else {
                    OverlayService.this.calibrateButton.setText("\u6821\u51c6 Touch");
                    OverlayService.this.calibrateButton.setBackgroundColor(Color.parseColor("#55FFFFFF"));
                    OverlayService.this.touchView.setCustomAlpha(OverlayService.this.playingAlpha);
                    OverlayService.this.setSlidersVisibility(View.GONE);
                }
            }
        });
        
        this.touchClient.setDiagnosticListener(new MaimaiTouchClient.OnDiagnosticListener(){
            @Override
            public void onDiagnosticConnected() {
                OverlayService.this.mainHandler.post(new Runnable(){
                    @Override
                    public void run() {
                        OverlayService.this.latencyButton.setVisibility(View.VISIBLE);
                        OverlayService.this.statusTextView.setText("\u6d4b\u8bd5\u6a21\u5f0f");
                        OverlayService.this.statusTextView.setTextColor(Color.YELLOW);
                    }
                });
            }

            @Override
            public void onLatencyResult(final long l) {
                OverlayService.this.mainHandler.post(new Runnable(){
                    @Override
                    public void run() {
                        OverlayService.this.latencyButton.setText("\u5ef6\u8fdf: " + l + "ms");
                    }
                });
            }

            @Override
            public void onDisconnected() {
                OverlayService.this.mainHandler.post(new Runnable(){
                    @Override
                    public void run() {
                        OverlayService.this.latencyButton.setVisibility(View.GONE);
                        OverlayService.this.latencyButton.setText("\u6d4b\u7f51\u7edc\u5ef6\u8fdf");
                        OverlayService.this.statusTextView.setText("\u4ea4\u4e92\u6a21\u5f0f");
                        OverlayService.this.statusTextView.setTextColor(Color.GREEN);
                    }
                });
            }

            @Override
            public void onGameStatusChanged(final boolean isInGame) {
                OverlayService.this.mainHandler.post(new Runnable(){
                    @Override
                    public void run() {
                        OverlayService.this.touchView.setInGame(isInGame);
                        if (!OverlayService.this.touchClient.isDiagnostic) {
                            OverlayService.this.statusTextView.setText(isInGame ? "\u6e38\u620f\u6a21\u5f0f" : "\u4ea4\u4e92\u6a21\u5f0f");
                            OverlayService.this.statusTextView.setTextColor(isInGame ? Color.CYAN : Color.GREEN);
                        }
                    }
                });
            }
        });
        
        this.latencyButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                OverlayService.this.latencyButton.setText("\u6d4b\u8bd5\u4e2d...");
                OverlayService.this.touchClient.testLatency();
            }
        });
    }

    private void togglePanel() {
        isPanelOpen = !isPanelOpen;
        if (isPanelOpen) {
            scrollView.setVisibility(View.VISIBLE);
            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(250);
            anim.setInterpolator(new OvershootInterpolator(1.2f));
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float val = (float) animation.getAnimatedValue();
                    scrollView.setScaleX(val);
                    scrollView.setScaleY(val);
                    scrollView.setAlpha(val);
                }
            });
            anim.start();
        } else {
            ValueAnimator anim = ValueAnimator.ofFloat(1f, 0f);
            anim.setDuration(200);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float val = (float) animation.getAnimatedValue();
                    scrollView.setScaleX(val);
                    scrollView.setScaleY(val);
                    scrollView.setAlpha(val);
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scrollView.setVisibility(View.GONE);
                }
            });
            anim.start();
        }
    }
    
    private void setSlidersVisibility(int visibility) {
        for (int i = 0; i < panelContainer.getChildCount(); i++) {
            View child = panelContainer.getChildAt(i);
            if (child == calibrateButton || child == statusTextView || child == latencyButton || child == lockTouchButton) {
                continue;
            }
            child.setVisibility(visibility);
        }
    }

    public int onStartCommand(Intent intent, int n, int n2) {
        if (intent != null && intent.hasExtra("ip")) {
            String string = intent.getStringExtra("ip");
            this.touchClient.connect(string, 4321);
        }
        return Service.START_STICKY;
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.touchView != null) {
            this.windowManager.removeView(this.touchView);
        }
        if (this.uiContainer != null) {
            this.windowManager.removeView(this.uiContainer);
        }
        if (this.touchClient != null) {
            this.touchClient.disconnect();
        }
    }
}
