package com.nafim.ar_tryon_view.ar;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

public final class CameraPermissionHelper {
    private static final int CAMERA_PERMISSION_CODE = 1001;

    private CameraPermissionHelper() {}

    public static boolean hasCameraPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCameraPermission(Activity activity) {
        activity.requestPermissions(
                new String[] {Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    public static boolean isCameraPermissionRequest(int requestCode) {
        return requestCode == CAMERA_PERMISSION_CODE;
    }

    public static boolean shouldShowRequestPermissionRationale(Activity activity) {
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
    }

    public static void launchPermissionSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
}
