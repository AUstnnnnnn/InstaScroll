package com.instascroll;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class ScrollService extends AccessibilityService {

    private static final String TAG = "InstaScroll";
    public static boolean isRunning = false;

    private boolean isInstagramForeground = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Double-click detection
    private long lastVolDownTime = 0;
    private static final long DOUBLE_CLICK_MS = 400;
    private Runnable pendingScroll = null;

    // Screen dimensions
    private int screenW = 1080;
    private int screenH = 2410;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        isRunning = true;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        Log.d(TAG, "Service connected. Screen: " + screenW + "x" + screenH);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                boolean wasInsta = isInstagramForeground;
                isInstagramForeground = pkg.toString().equals("com.instagram.android");
                if (wasInsta != isInstagramForeground) {
                    Log.d(TAG, "Instagram foreground: " + isInstagramForeground + " (pkg=" + pkg + ")");
                }
            }
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isInstagramForeground) {
            return false; // Don't intercept outside Instagram
        }

        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true; // Consume UP events too, but only act on DOWN
        }

        // Avoid repeat events from long-press
        if (event.getRepeatCount() > 0) {
            return true;
        }

        int cx = screenW / 2;
        int cy = screenH / 2;

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // Cancel any pending scroll-down
            if (pendingScroll != null) {
                handler.removeCallbacks(pendingScroll);
                pendingScroll = null;
            }
            Log.d(TAG, "VOL UP -> scroll up (prev post)");
            // Swipe DOWN to scroll up: finger moves from low Y to high Y
            doSwipe(cx, cy - 400, cx, cy + 400, 250);

        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            long now = System.currentTimeMillis();

            if (now - lastVolDownTime < DOUBLE_CLICK_MS) {
                // Double click = like
                if (pendingScroll != null) {
                    handler.removeCallbacks(pendingScroll);
                    pendingScroll = null;
                }
                lastVolDownTime = 0;
                Log.d(TAG, "VOL DOWN x2 -> LIKE");
                doDoubleTap(cx, cy);
            } else {
                // First press - delayed scroll
                lastVolDownTime = now;
                pendingScroll = () -> {
                    Log.d(TAG, "VOL DOWN -> scroll down (next post)");
                    // Swipe UP to scroll down: finger moves from high Y to low Y
                    doSwipe(cx, cy + 400, cx, cy - 400, 250);
                    pendingScroll = null;
                };
                handler.postDelayed(pendingScroll, DOUBLE_CLICK_MS);
            }
        }

        return true; // Consume the event - prevents volume change
    }

    private void doSwipe(int x1, int y1, int x2, int y2, long durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
    }

    private void doDoubleTap(int x, int y) {
        // Both taps in a single GestureDescription so Android sees them as a true double-tap
        Path tap1 = new Path();
        tap1.moveTo(x, y);
        Path tap2 = new Path();
        tap2.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        // First tap: starts at 0ms, lasts 50ms
        builder.addStroke(new GestureDescription.StrokeDescription(tap1, 0, 50));
        // Second tap: starts at 100ms, lasts 50ms (50ms gap between taps)
        builder.addStroke(new GestureDescription.StrokeDescription(tap2, 100, 50));
        dispatchGesture(builder.build(), null, null);
    }

    @Override
    public void onInterrupt() {
        isRunning = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }
}
