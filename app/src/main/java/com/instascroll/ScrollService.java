package com.instascroll;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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

    // Long-press detection for Volume Up
    private static final long LONG_PRESS_MS = 500;
    private boolean volUpHeld = false;
    private Runnable pendingLongPress = null;

    // Comments state
    private boolean inComments = false;

    // Screen dimensions
    private int screenW = 1080;
    private int screenH = 2410;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        isRunning = true;
        refreshServiceInfo();
        updateScreenDimensions();
        Log.d(TAG, "Service connected. Screen: " + screenW + "x" + screenH);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateScreenDimensions();
        refreshServiceInfo();
        Log.d(TAG, "Config changed, refreshed. Screen: " + screenW + "x" + screenH);
    }

    private void updateScreenDimensions() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
    }

    /** Re-apply service info to refresh gesture dispatch capability after Doze/sleep. */
    private void refreshServiceInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            setServiceInfo(info);
            Log.d(TAG, "Service info refreshed");
        }
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
            return false;
        }

        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return handleVolumeUp(event);
        } else {
            return handleVolumeDown(event);
        }
    }

    private boolean handleVolumeUp(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            // First press — schedule long-press action
            volUpHeld = false;
            pendingLongPress = () -> {
                volUpHeld = true;
                toggleComments();
            };
            handler.postDelayed(pendingLongPress, LONG_PRESS_MS);

        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            // Released — if it was a short press, do scroll
            if (pendingLongPress != null) {
                handler.removeCallbacks(pendingLongPress);
                pendingLongPress = null;
            }
            if (!volUpHeld && !inComments) {
                int cx = screenW / 2;
                int cy = screenH / 2;
                // Cancel any pending scroll-down
                if (pendingScroll != null) {
                    handler.removeCallbacks(pendingScroll);
                    pendingScroll = null;
                }
                Log.d(TAG, "VOL UP -> scroll up (prev post)");
                doSwipe(cx, cy - 400, cx, cy + 400, 250);
            }
            volUpHeld = false;
        }
        return true;
    }

    private boolean handleVolumeDown(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getRepeatCount() > 0) {
            return true;
        }

        int cx = screenW / 2;
        int cy = screenH / 2;

        if (inComments) {
            // Scroll comments: small swipe in the lower half of screen
            Log.d(TAG, "VOL DOWN -> scroll comments");
            int commentAreaY = screenH * 3 / 4;
            doSwipe(cx, commentAreaY + 150, cx, commentAreaY - 150, 200);
            return true;
        }

        // Normal post scrolling with double-click detection
        long now = System.currentTimeMillis();
        if (now - lastVolDownTime < DOUBLE_CLICK_MS) {
            if (pendingScroll != null) {
                handler.removeCallbacks(pendingScroll);
                pendingScroll = null;
            }
            lastVolDownTime = 0;
            Log.d(TAG, "VOL DOWN x2 -> LIKE");
            doDoubleTap(cx, cy);
        } else {
            lastVolDownTime = now;
            pendingScroll = () -> {
                Log.d(TAG, "VOL DOWN -> scroll down (next post)");
                doSwipe(cx, cy + 400, cx, cy - 400, 250);
                pendingScroll = null;
            };
            handler.postDelayed(pendingScroll, DOUBLE_CLICK_MS);
        }
        return true;
    }

    private void toggleComments() {
        int cx = screenW / 2;
        if (inComments) {
            // Close comments: swipe down from comment sheet to dismiss
            Log.d(TAG, "HOLD VOL UP -> close comments");
            int commentTop = screenH / 2;
            doSwipe(cx, commentTop, cx, screenH, 200);
            inComments = false;
        } else {
            // Open comments: tap the comment icon (right side, Reels layout)
            int commentIconX = screenW - 90;
            int commentIconY = screenH * 3 / 5;
            Log.d(TAG, "HOLD VOL UP -> open comments at " + commentIconX + "," + commentIconY);
            doTap(commentIconX, commentIconY);
            inComments = true;
        }
    }

    private void doSwipe(int x1, int y1, int x2, int y2, long durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        GestureDescription gesture = builder.build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCancelled(GestureDescription g) {
                Log.w(TAG, "Swipe CANCELLED — refreshing service and retrying");
                refreshServiceInfo();
                updateScreenDimensions();
                // Retry once after refresh
                dispatchGesture(gesture, new GestureResultCallback() {
                    @Override
                    public void onCancelled(GestureDescription g2) {
                        Log.e(TAG, "Swipe retry CANCELLED — gesture dispatch broken");
                    }
                }, null);
            }
        }, null);
    }

    private void doTap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        GestureDescription gesture = builder.build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCancelled(GestureDescription g) {
                Log.w(TAG, "Tap CANCELLED — refreshing service and retrying");
                refreshServiceInfo();
                dispatchGesture(gesture, null, null);
            }
        }, null);
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
        GestureDescription gesture = builder.build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCancelled(GestureDescription g) {
                Log.w(TAG, "DoubleTap CANCELLED — refreshing service and retrying");
                refreshServiceInfo();
                updateScreenDimensions();
                dispatchGesture(gesture, new GestureResultCallback() {
                    @Override
                    public void onCancelled(GestureDescription g2) {
                        Log.e(TAG, "DoubleTap retry CANCELLED — gesture dispatch broken");
                    }
                }, null);
            }
        }, null);
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
