package com.nafim.ar_tryon_view

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.NonNull
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.nafim.ar_tryon_view.ar.ArPlacementActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class ArTryonViewPlugin :
  FlutterPlugin,
  ActivityAware,
  PluginRegistry.ActivityResultListener,
  MethodChannel.MethodCallHandler {

  companion object {
    private const val AR_CHANNEL = "ar_tryon_view/ar"
    private const val PICK_GLB_REQUEST = 4011
  }

  private var activity: Activity? = null
  private var activityBinding: ActivityPluginBinding? = null
  private var arChannel: MethodChannel? = null
  private var pendingPickResult: MethodChannel.Result? = null

  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    binding.platformViewRegistry.registerViewFactory(
      "ar_tryon_view/native_view",
      ArTryOnViewFactory(binding.binaryMessenger) { activity }
    )

    arChannel = MethodChannel(binding.binaryMessenger, AR_CHANNEL).also {
      it.setMethodCallHandler(this)
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    arChannel?.setMethodCallHandler(null)
    arChannel = null
    pendingPickResult = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    activityBinding = binding
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    detachActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivity() {
    detachActivity()
  }

  private fun detachActivity() {
    activityBinding?.removeActivityResultListener(this)
    activityBinding = null
    activity = null
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "pickGlb" -> pickGlb(result)
      "openAr" -> openAr(call, result)
      else -> result.notImplemented()
    }
  }

  private fun pickGlb(result: MethodChannel.Result) {
    val currentActivity = activity
    if (currentActivity == null) {
      result.error("NO_ACTIVITY", "Android Activity is not available.", null)
      return
    }

    if (pendingPickResult != null) {
      result.error("PICKER_BUSY", "A GLB picker is already open.", null)
      return
    }

    pendingPickResult = result

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)
      type = "*/*"
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    try {
      currentActivity.startActivityForResult(intent, PICK_GLB_REQUEST)
    } catch (e: Exception) {
      pendingPickResult = null
      result.error("PICKER_OPEN_FAILED", e.message, null)
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode != PICK_GLB_REQUEST) return false

    val result = pendingPickResult
    pendingPickResult = null

    if (result == null) return true

    if (resultCode != Activity.RESULT_OK || data?.data == null) {
      result.success(null)
      return true
    }

    val currentActivity = activity
    if (currentActivity == null) {
      result.error("NO_ACTIVITY", "Android Activity is not available.", null)
      return true
    }

    val uri = data.data!!
    val metadata = readMetadata(currentActivity, uri)
    val name = metadata.first

    if (!name.lowercase().endsWith(".glb")) {
      result.error(
        "INVALID_FILE_TYPE",
        "Only .glb 3D model files are supported.",
        null
      )
      return true
    }

    try {
      currentActivity.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
      )
    } catch (_: SecurityException) {
      // Some document providers grant only a temporary URI permission.
    }

    result.success(
      mapOf(
        "uri" to uri.toString(),
        "name" to name,
        "sizeBytes" to metadata.second
      )
    )
    return true
  }

  private fun openAr(call: MethodCall, result: MethodChannel.Result) {
    val currentActivity = activity
    if (currentActivity == null) {
      result.error("NO_ACTIVITY", "Android Activity is not available.", null)
      return
    }

    val args = call.arguments as? Map<*, *>
    val modelUri = args?.get("modelUri") as? String
    val modelSizeM = (args?.get("modelSizeM") as? Number)?.toFloat() ?: 0.55f

    if (modelUri.isNullOrBlank()) {
      result.error("MODEL_REQUIRED", "Choose a .glb model first.", null)
      return
    }

    // This plugin also owns a CameraX front-camera preview. ARCore needs
    // exclusive access to the camera when the placement Activity starts.
    // Explicitly unbind CameraX first; relying only on Activity lifecycle pause
    // can leave ARCore stuck in Camera: PAUSED on some devices.
    try {
      val providerFuture = ProcessCameraProvider.getInstance(currentActivity)
      providerFuture.addListener({
        try {
          providerFuture.get().unbindAll()
        } catch (cameraReleaseError: Exception) {
          Log.w("ArTryOn", "Could not explicitly release CameraX before AR", cameraReleaseError)
        }

        // Give CameraX a short moment to release the camera device before ARCore
        // opens the rear camera.
        currentActivity.window.decorView.postDelayed({
          launchArActivity(
            currentActivity = currentActivity,
            modelUri = modelUri,
            modelSizeM = modelSizeM,
            result = result
          )
        }, 300L)
      }, ContextCompat.getMainExecutor(currentActivity))
    } catch (e: Exception) {
      launchArActivity(
        currentActivity = currentActivity,
        modelUri = modelUri,
        modelSizeM = modelSizeM,
        result = result
      )
    }
  }

  private fun launchArActivity(
    currentActivity: Activity,
    modelUri: String,
    modelSizeM: Float,
    result: MethodChannel.Result
  ) {
    try {
      val intent = Intent(currentActivity, ArPlacementActivity::class.java).apply {
        putExtra(ArPlacementActivity.EXTRA_MODEL_URI, modelUri)
        putExtra(ArPlacementActivity.EXTRA_MODEL_SIZE_M, modelSizeM)
      }
      currentActivity.startActivity(intent)
      result.success(true)
    } catch (e: Exception) {
      result.error("AR_OPEN_ERROR", e.message, null)
    }
  }

  private fun readMetadata(activity: Activity, uri: Uri): Pair<String, Long> {
    var name = "model.glb"
    var size = -1L

    activity.contentResolver.query(
      uri,
      arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
      null,
      null,
      null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

        if (nameIndex >= 0) {
          cursor.getString(nameIndex)?.let { name = it }
        }
        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
          size = cursor.getLong(sizeIndex)
        }
      }
    }

    return name to size
  }
}

private class ArTryOnViewFactory(
  private val messenger: BinaryMessenger,
  private val activityProvider: () -> Activity?
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

  override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
    return ArTryOnPlatformView(context, messenger, viewId, activityProvider)
  }
}

private class ArTryOnPlatformView(
  private val context: Context,
  messenger: BinaryMessenger,
  viewId: Int,
  private val activityProvider: () -> Activity?
) : PlatformView, MethodChannel.MethodCallHandler {

  private val TAG = "ArTryOn"

  private val container = FrameLayout(context).apply {
    setBackgroundColor(0xFF000000.toInt())
  }

  private val previewView = PreviewView(context).apply {
    scaleType = PreviewView.ScaleType.FILL_CENTER
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
  }

  private val effectView = ImageView(context).apply {
    visibility = View.GONE
    alpha = 1.0f
    scaleType = ImageView.ScaleType.FIT_CENTER
    setBackgroundColor(0x00000000)
  }

  private val channel = MethodChannel(messenger, "ar_tryon_view/method_$viewId")
  private var cameraProvider: ProcessCameraProvider? = null

  init {
    channel.setMethodCallHandler(this)

    container.addView(
      previewView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )

    container.addView(
      effectView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )
  }

  override fun getView(): View = container

  override fun dispose() {
    channel.setMethodCallHandler(null)
    try {
      cameraProvider?.unbindAll()
    } catch (_: Exception) {
    }
    cameraProvider = null
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "start" -> startCamera(result)

      "stop" -> {
        cameraProvider?.unbindAll()
        result.success(null)
      }

      "setEffect" -> result.success(null)

      "setEffectBytes" -> {
        try {
          val bytes = call.arguments as ByteArray
          val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          if (bmp != null) {
            effectView.setImageBitmap(bmp)
            effectView.visibility = View.VISIBLE
          } else {
            effectView.visibility = View.GONE
          }
          result.success(null)
        } catch (e: Exception) {
          result.error("EFFECT_BYTES_FAILED", e.message, null)
        }
      }

      "clearEffect" -> {
        effectView.setImageDrawable(null)
        effectView.visibility = View.GONE
        result.success(null)
      }

      "dispose" -> {
        dispose()
        result.success(null)
      }

      else -> result.notImplemented()
    }
  }

  private fun startCamera(result: MethodChannel.Result) {
    Log.d(TAG, "startCamera() called")

    val activity = activityProvider()
    if (activity == null) {
      result.error("NO_ACTIVITY", "Activity is null.", null)
      return
    }

    val lifecycleOwner = activity as? LifecycleOwner
    if (lifecycleOwner == null) {
      result.error("NO_LIFECYCLE", "Activity is not a LifecycleOwner.", null)
      return
    }

    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED
    if (!granted) {
      result.error("NO_CAMERA_PERMISSION", "Camera permission not granted.", null)
      return
    }

    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
      try {
        cameraProvider = future.get()

        val preview = Preview.Builder().build().also {
          it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val selector = CameraSelector.Builder()
          .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
          .build()

        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(lifecycleOwner, selector, preview)

        result.success(null)
      } catch (e: Exception) {
        result.error("CAMERA_START_FAILED", e.message, null)
      }
    }, ContextCompat.getMainExecutor(context))
  }
}
