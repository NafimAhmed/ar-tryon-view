package com.nafim.ar_tryon_view

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class ArTryonViewPlugin : FlutterPlugin, ActivityAware {

  private var activity: Activity? = null

  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    binding.platformViewRegistry.registerViewFactory(
      "ar_tryon_view/native_view",
      ArTryOnViewFactory(binding.binaryMessenger) { activity }
    )
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {}

  override fun onAttachedToActivity(binding: ActivityPluginBinding) { activity = binding.activity }
  override fun onDetachedFromActivityForConfigChanges() { activity = null }
  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding.activity }
  override fun onDetachedFromActivity() { activity = null }
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
    // background black so you don't see flutter background
    setBackgroundColor(0xFF000000.toInt())
  }

  // ✅ IMPORTANT FIX: Use COMPATIBLE to ensure TextureView (so overlay alpha works)
  private val previewView = PreviewView(context).apply {
    scaleType = PreviewView.ScaleType.FILL_CENTER
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
  }

  // Overlay effect image (transparent PNG from Flutter bytes)
  private val effectView = ImageView(context).apply {
    visibility = View.GONE
    alpha = 1.0f
    scaleType = ImageView.ScaleType.FIT_CENTER
    // keep transparent areas truly transparent
    setBackgroundColor(0x00000000)
  }

  private val channel = MethodChannel(messenger, "ar_tryon_view/method_$viewId")
  private var cameraProvider: ProcessCameraProvider? = null

  init {
    channel.setMethodCallHandler(this)

    // 1) Camera preview
    container.addView(
      previewView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )

    // 2) Effect overlay on top
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
    try { cameraProvider?.unbindAll() } catch (_: Exception) {}
    cameraProvider = null
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "start" -> startCamera(result)

      "stop" -> {
        cameraProvider?.unbindAll()
        result.success(null)
      }

      // String-based effect id (optional)
      "setEffect" -> {
        // val effectId = call.argument<String>("effectId") ?: ""
        // You can map ids to assets later. For now just success.
        result.success(null)
      }

      // Bytes-based overlay (from Flutter)
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
