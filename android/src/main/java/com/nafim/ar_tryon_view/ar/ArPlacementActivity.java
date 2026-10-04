package com.nafim.ar_tryon_view.ar;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.io.InputStream;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Native ARCore screen launched from Flutter.
 *
 * Flutter is only the app/UI layer. ARCore owns camera tracking and anchors.
 * Placement readiness is intentionally simple: as soon as ARCore returns a valid
 * center hit on a tracked horizontal plane, the + becomes green and the user can place.
 */
public final class ArPlacementActivity extends Activity implements GLSurfaceView.Renderer {

    public static final String EXTRA_MODEL_SIZE_M = "modelSizeM";
    public static final String EXTRA_MODEL_URI = "modelUri";

    private static final String TAG = "JAVA_HELLO_AR";

    // Masks only very short TRACKING -> PAUSED -> TRACKING transitions so the model
    // does not blink because of a single dropped tracking frame.
    private static final long TRACKING_GRACE_NS = 220_000_000L;

    private GLSurfaceView surfaceView;
    private DisplayRotationHelper displayRotationHelper;
    private TapHelper tapHelper;
    private TextView statusText;
    private TextView reticle;
    private Button resetButton;

    private Session session;
    private boolean installRequested;
    private boolean sessionRunning;
    private boolean cameraTextureSet;

    private BackgroundRenderer backgroundRenderer;
    private PlaneRenderer planeRenderer;
    private GlbRenderer glbRenderer;

    private Anchor placedAnchor;
    private float modelSizeMeters = 0.55f;
    private String modelUriString;

    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] lastGoodViewMatrix = new float[16];
    private final float[] lastGoodProjectionMatrix = new float[16];

    private long lastGoodTrackingNs;
    private boolean hasLastGoodMatrices;

    private int frameCounter;
    private boolean lastReticleReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        modelSizeMeters = getIntent().getFloatExtra(EXTRA_MODEL_SIZE_M, 0.55f);
        modelUriString = getIntent().getStringExtra(EXTRA_MODEL_URI);

        displayRotationHelper = new DisplayRotationHelper(this);
        tapHelper = new TapHelper(this);

        FrameLayout root = new FrameLayout(this);

        surfaceView = new GLSurfaceView(this);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setOnTouchListener(tapHelper);

        root.addView(
                surfaceView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        reticle = new TextView(this);
        reticle.setText("+");
        reticle.setTextSize(44f);
        reticle.setGravity(Gravity.CENTER);
        reticle.setTextColor(Color.RED);
        reticle.setShadowLayer(6f, 0f, 0f, Color.BLACK);

        FrameLayout.LayoutParams reticleParams = new FrameLayout.LayoutParams(130, 130);
        reticleParams.gravity = Gravity.CENTER;
        root.addView(reticle, reticleParams);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15f);
        statusText.setPadding(20, 14, 20, 14);
        statusText.setBackgroundColor(Color.argb(165, 0, 0, 0));
        statusText.setText("Starting AR camera…");

        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        statusParams.gravity = Gravity.TOP;
        statusParams.leftMargin = 12;
        statusParams.rightMargin = 12;
        statusParams.topMargin = 25;
        root.addView(statusText, statusParams);

        resetButton = new Button(this);
        resetButton.setText("REPOSITION");
        resetButton.setVisibility(View.GONE);
        resetButton.setOnClickListener(v -> surfaceView.queueEvent(this::resetAnchor));

        FrameLayout.LayoutParams resetParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        resetParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        resetParams.bottomMargin = 50;
        root.addView(resetButton, resetParams);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }

        resumeAr();
    }

    private void resumeAr() {
        if (sessionRunning) return;

        if (session == null) {
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        showStatus("Google Play Services for AR needs installation/update.");
                        return;
                    case INSTALLED:
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "ARCore install error", e);
                showStatus("Could not prepare ARCore: " + e.getMessage());
                return;
            }

            try {
                session = new Session(this);
                configureSession();
            } catch (Exception e) {
                Log.e(TAG, "Session creation failed", e);
                showStatus("Could not start AR camera: " + e.getMessage());
                return;
            }
        } else {
            configureSession();
        }

        // Same lifecycle ordering used by Google's Hello AR sample.
        try {
            session.resume();
            surfaceView.onResume();
            displayRotationHelper.onResume();
            sessionRunning = true;
            cameraTextureSet = false;
            resetTrackingQualityState();
            showStatus("Move the phone slowly and point at a well-lit floor.");
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available", e);
            showStatus("Camera unavailable. Close other camera apps and try again.");
            session.close();
            session = null;
        }
    }

    private void configureSession() {
        if (session == null) return;

        Config config = session.getConfig();
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
        config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);
        config.setDepthMode(Config.DepthMode.DISABLED);
        session.configure(config);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (sessionRunning && session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
            sessionRunning = false;
        }
    }

    @Override
    protected void onDestroy() {
        if (placedAnchor != null) {
            placedAnchor.detach();
            placedAnchor = null;
        }

        if (session != null) {
            session.close();
            session = null;
        }

        tapHelper.clear();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (!CameraPermissionHelper.isCameraPermissionRequest(requestCode)) return;

        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            resumeAr();
        } else {
            Toast.makeText(
                    this,
                    "Camera permission is required for AR",
                    Toast.LENGTH_LONG).show();

            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        backgroundRenderer = new BackgroundRenderer();
        backgroundRenderer.createOnGlThread();

        planeRenderer = new PlaneRenderer();
        planeRenderer.createOnGlThread();

        glbRenderer = new GlbRenderer();

        if (modelUriString == null || modelUriString.trim().isEmpty()) {
            showStatus("No .glb model selected. Go back and choose a model.");
        } else {
            try {
                Uri modelUri = Uri.parse(modelUriString);
                InputStream inputStream = getContentResolver().openInputStream(modelUri);
                glbRenderer.createOnGlThread(inputStream, modelSizeMeters);

                if (!glbRenderer.isReady()) {
                    showStatus("3D model could not be loaded: " + glbRenderer.getErrorMessage());
                }
            } catch (Throwable modelError) {
                Log.e(TAG, "Could not open selected GLB", modelError);
                showStatus(
                        "Could not open selected .glb file: "
                                + (modelError.getMessage() == null
                                ? modelError.getClass().getSimpleName()
                                : modelError.getMessage()));
            }
        }

        cameraTextureSet = false;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        displayRotationHelper.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null || backgroundRenderer == null) return;

        try {
            displayRotationHelper.updateSessionIfNeeded(session);

            if (!cameraTextureSet) {
                session.setCameraTextureName(backgroundRenderer.getTextureId());
                cameraTextureSet = true;
            }

            Frame frame = session.update();
            Camera camera = frame.getCamera();

            if (frame.getTimestamp() != 0) {
                backgroundRenderer.draw(frame);
            }

            if (camera.getTrackingState() == TrackingState.TRACKING) {
                onTrackingFrame(frame, camera);
            } else {
                onTrackingPaused(camera);
            }

        } catch (Throwable t) {
            Log.e(TAG, "Exception on OpenGL thread", t);
        }
    }

    private void onTrackingFrame(Frame frame, Camera camera) {
        camera.getProjectionMatrix(projectionMatrix, 0, 0.05f, 100.0f);
        camera.getViewMatrix(viewMatrix, 0);

        System.arraycopy(viewMatrix, 0, lastGoodViewMatrix, 0, 16);
        System.arraycopy(projectionMatrix, 0, lastGoodProjectionMatrix, 0, 16);
        lastGoodTrackingNs = System.nanoTime();
        hasLastGoodMatrices = true;

        if (placedAnchor == null) {
            HitResult centerHit = findCenterFloorHit(frame);

            // IMPORTANT: do not add artificial 8/12 frame gates here.
            // If ARCore says the center ray is on a valid tracked plane, let the user place.
            // Surface readiness must not depend on model loading. A bad GLB should
            // never make the reticle look like ARCore cannot find the floor.
            boolean ready = centerHit != null;

            setReticleReady(ready);
            handleTap(camera, centerHit, ready);

            if (planeRenderer != null) {
                planeRenderer.draw(session, viewMatrix, projectionMatrix);
            }
        } else {
            MotionEvent ignored = tapHelper.poll();
            if (ignored != null) ignored.recycle();
        }

        if (placedAnchor != null
                && placedAnchor.getTrackingState() == TrackingState.TRACKING
                && glbRenderer != null
                && glbRenderer.isReady()) {
            glbRenderer.draw(placedAnchor, viewMatrix, projectionMatrix);
        }

        frameCounter++;
        if (frameCounter % 15 == 0) {
            updateStatus(camera, countTrackedPlanes());
        }
    }

    private void onTrackingPaused(Camera camera) {
        if (placedAnchor == null) {
            setReticleReady(false);
        }

        long elapsed = System.nanoTime() - lastGoodTrackingNs;
        if (placedAnchor != null
                && hasLastGoodMatrices
                && elapsed >= 0
                && elapsed <= TRACKING_GRACE_NS
                && placedAnchor.getTrackingState() != TrackingState.STOPPED
                && glbRenderer != null
                && glbRenderer.isReady()) {
            glbRenderer.draw(placedAnchor, lastGoodViewMatrix, lastGoodProjectionMatrix);
        }

        frameCounter++;
        if (frameCounter % 10 == 0) {
            updateStatus(camera, countTrackedPlanes());
        }
    }

    private HitResult findCenterFloorHit(Frame frame) {
        float centerX = surfaceView.getWidth() / 2f;
        float centerY = surfaceView.getHeight() / 2f;

        // Prefer a real tracked horizontal plane whenever ARCore has one.
        List<HitResult> hits = frame.hitTest(centerX, centerY);
        for (HitResult hit : hits) {
            if (hit.getTrackable() instanceof Plane) {
                Plane plane = (Plane) hit.getTrackable();
                if (plane.getTrackingState() == TrackingState.TRACKING
                        && plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING
                        && plane.getSubsumedBy() == null
                        && plane.isPoseInPolygon(hit.getHitPose())) {
                    return hit;
                }
            }
        }

        // User-friendly fallback: ARCore Instant Placement can provide a usable
        // placement hit while plane discovery is still warming up. When ARCore
        // later understands the real geometry it refines the tracking.
        List<HitResult> instantHits =
                frame.hitTestInstantPlacement(centerX, centerY, 1.2f);
        for (HitResult hit : instantHits) {
            if (hit.getTrackable() != null
                    && hit.getTrackable().getTrackingState() != TrackingState.STOPPED) {
                return hit;
            }
        }

        return null;
    }

    private void setReticleReady(boolean ready) {
        if (lastReticleReady == ready) return;
        lastReticleReady = ready;

        runOnUiThread(() -> {
            if (reticle.getVisibility() == View.VISIBLE) {
                reticle.setTextColor(ready ? Color.GREEN : Color.RED);
            }
        });
    }

    private void handleTap(Camera camera, HitResult centerHit, boolean placementReady) {
        MotionEvent tap = tapHelper.poll();
        if (tap == null) return;

        try {
            if (camera.getTrackingState() != TrackingState.TRACKING) {
                showStatus("Tracking is not ready yet. Move the phone slowly.");
                return;
            }

            if (!placementReady || centerHit == null) {
                showStatus("Aim the + at the detected floor. When it turns GREEN, tap once.");
                return;
            }

            if (glbRenderer == null || !glbRenderer.isReady()) {
                showStatus(
                        "3D model is not ready: "
                                + (glbRenderer == null
                                ? "renderer missing"
                                : glbRenderer.getErrorMessage()));
                return;
            }

            placedAnchor = centerHit.createAnchor();
            setReticleReady(false);

            runOnUiThread(() -> {
                reticle.setVisibility(View.GONE);
                resetButton.setVisibility(View.VISIBLE);
                statusText.setText("Placed ✓  Move around slowly to view it from different angles.");
            });
        } finally {
            tap.recycle();
        }
    }

    private int countTrackedPlanes() {
        if (session == null) return 0;

        int planes = 0;
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING
                    && plane.getSubsumedBy() == null
                    && plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                planes++;
            }
        }
        return planes;
    }

    private void updateStatus(Camera camera, int planeCount) {
        TrackingState state = camera.getTrackingState();
        TrackingFailureReason reason = camera.getTrackingFailureReason();

        String message;

        if (glbRenderer != null && !glbRenderer.isReady()) {
            message = "Selected GLB could not be loaded: "
                    + glbRenderer.getErrorMessage()
                    + "\nChoose another/optimized .glb file.";
        } else if (state == TrackingState.TRACKING) {
            if (placedAnchor != null) {
                if (placedAnchor.getTrackingState() == TrackingState.TRACKING) {
                    message = "Placed ✓  Walk around slowly to inspect the model.";
                } else {
                    message = "Hold the phone steady — recovering object tracking…";
                }
            } else if (planeCount == 0) {
                message = "Scan the floor slowly. Good lighting and floor texture help.";
            } else if (lastReticleReady) {
                message = "Surface ready ✓  Tap once to place.";
            } else {
                message = "Surface found. Aim the + directly at the floor.";
            }
        } else {
            if (reason == TrackingFailureReason.INSUFFICIENT_LIGHT) {
                message = "Need more light. Point at a brighter, textured floor.";
            } else if (reason == TrackingFailureReason.EXCESSIVE_MOTION) {
                message = "Move the phone more slowly.";
            } else if (reason == TrackingFailureReason.INSUFFICIENT_FEATURES) {
                message = "Point at a textured area — avoid blank/shiny surfaces.";
            } else if (reason == TrackingFailureReason.BAD_STATE) {
                message = "Tracking is recovering. Hold the phone steady.";
            } else if (reason == TrackingFailureReason.CAMERA_UNAVAILABLE) {
                message = "Camera tracking is unavailable. Reopen AR if this continues.";
            } else {
                message = "Initializing tracking… move the phone slowly.";
            }
        }

        String anchorState = placedAnchor == null
                ? "NONE"
                : placedAnchor.getTrackingState().toString();
        String modelState = glbRenderer == null
                ? "LOADING"
                : (glbRenderer.isReady() ? "READY" : "ERROR");

        String cameraDebug = state.toString();
        if (state != TrackingState.TRACKING) {
            cameraDebug += " (" + reason + ")";
        }

        String text = message
                + "\n\nCamera: " + cameraDebug
                + "  Anchor: " + anchorState
                + "  Model: " + modelState
                + "  Planes: " + planeCount;

        showStatus(text);
    }

    private void resetAnchor() {
        if (placedAnchor != null) {
            placedAnchor.detach();
            placedAnchor = null;
        }

        tapHelper.clear();
        resetTrackingQualityState();

        runOnUiThread(() -> {
            reticle.setVisibility(View.VISIBLE);
            lastReticleReady = false;
            reticle.setTextColor(Color.RED);
            resetButton.setVisibility(View.GONE);
            statusText.setText("Model removed. Scan the floor again.");
        });
    }

    private void resetTrackingQualityState() {
        hasLastGoodMatrices = false;
        lastGoodTrackingNs = 0L;
        lastReticleReady = false;
    }

    private void showStatus(String text) {
        runOnUiThread(() -> statusText.setText(text));
    }
}
