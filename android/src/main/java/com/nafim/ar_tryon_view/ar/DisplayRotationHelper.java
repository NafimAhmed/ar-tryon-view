package com.nafim.ar_tryon_view.ar;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.WindowManager;

import com.google.ar.core.Session;

public final class DisplayRotationHelper implements DisplayManager.DisplayListener {
    private final Context context;
    private final Display display;
    private boolean viewportChanged;
    private int viewportWidth;
    private int viewportHeight;

    public DisplayRotationHelper(Activity activity) {
        context = activity;
        WindowManager windowManager =
                (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        display = windowManager.getDefaultDisplay();
    }

    public void onResume() {
        context.getSystemService(DisplayManager.class)
                .registerDisplayListener(this, null);
    }

    public void onPause() {
        context.getSystemService(DisplayManager.class)
                .unregisterDisplayListener(this);
    }

    public void onSurfaceChanged(int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        viewportChanged = true;
    }

    public void updateSessionIfNeeded(Session session) {
        if (viewportChanged) {
            session.setDisplayGeometry(
                    display.getRotation(), viewportWidth, viewportHeight);
            viewportChanged = false;
        }
    }

    @Override public void onDisplayAdded(int displayId) {}
    @Override public void onDisplayRemoved(int displayId) {}
    @Override public void onDisplayChanged(int displayId) { viewportChanged = true; }
}
