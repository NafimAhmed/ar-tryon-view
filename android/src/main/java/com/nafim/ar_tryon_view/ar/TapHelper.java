package com.nafim.ar_tryon_view.ar;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class TapHelper implements View.OnTouchListener {
    private final GestureDetector gestureDetector;
    private final BlockingQueue<MotionEvent> queuedTaps = new ArrayBlockingQueue<>(8);

    public TapHelper(Context context) {
        gestureDetector = new GestureDetector(
                context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        queuedTaps.offer(MotionEvent.obtain(e));
                        return true;
                    }

                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }
                });
    }

    public MotionEvent poll() {
        return queuedTaps.poll();
    }

    public void clear() {
        MotionEvent event;
        while ((event = queuedTaps.poll()) != null) {
            event.recycle();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return gestureDetector.onTouchEvent(motionEvent);
    }
}
