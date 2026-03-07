//
//package com.nafim.ar_tryon_view
//
//import android.app.Activity
//import android.content.Context
//import android.net.Uri
//import android.util.Log
//import android.view.View
//import android.widget.FrameLayout
//import androidx.annotation.NonNull
//import androidx.fragment.app.FragmentActivity
//import androidx.fragment.app.FragmentContainerView
//import com.google.ar.core.ArCoreApk
//import com.google.ar.core.AugmentedFace
//import com.google.ar.core.Config
//import com.google.ar.core.TrackingState
//import com.google.ar.core.exceptions.UnavailableException
//import com.google.ar.sceneform.Scene
//import com.google.ar.sceneform.math.Vector3
//import com.google.ar.sceneform.rendering.ModelRenderable
//import com.google.ar.sceneform.ux.ArFragment
//import com.google.ar.sceneform.ux.AugmentedFaceNode
//import io.flutter.embedding.engine.plugins.FlutterPlugin
//import io.flutter.embedding.engine.plugins.activity.ActivityAware
//import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
//import io.flutter.plugin.common.BinaryMessenger
//import io.flutter.plugin.common.MethodCall
//import io.flutter.plugin.common.MethodChannel
//import io.flutter.plugin.common.StandardMessageCodec
//import io.flutter.plugin.platform.PlatformView
//import io.flutter.plugin.platform.PlatformViewFactory
//import java.io.File
//import java.io.FileOutputStream
//
//class ArTryonViewPlugin : FlutterPlugin, ActivityAware {
//
//  private var activity: Activity? = null
//
//  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
//    binding.platformViewRegistry.registerViewFactory(
//      "ar_tryon_view/native_view",
//      ArTryOnViewFactory(
//        messenger = binding.binaryMessenger,
//        activityProvider = { activity },
//        assetLookup = { subPath ->
//          binding.flutterAssets.getAssetFilePathBySubpath(subPath)
//        }
//      )
//    )
//  }
//
//  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) = Unit
//
//  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
//    activity = binding.activity
//  }
//
//  override fun onDetachedFromActivityForConfigChanges() {
//    activity = null
//  }
//
//  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
//    activity = binding.activity
//  }
//
//  override fun onDetachedFromActivity() {
//    activity = null
//  }
//}
//
//private class ArTryOnViewFactory(
//  private val messenger: BinaryMessenger,
//  private val activityProvider: () -> Activity?,
//  private val assetLookup: (String) -> String,
//) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
//
//  override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
//    return ArTryOnPlatformView(
//      context = context,
//      messenger = messenger,
//      viewId = viewId,
//      activityProvider = activityProvider,
//      assetLookup = assetLookup,
//    )
//  }
//}
//
//private class ArTryOnPlatformView(
//  private val context: Context,
//  messenger: BinaryMessenger,
//  private val viewId: Int,
//  private val activityProvider: () -> Activity?,
//  private val assetLookup: (String) -> String,
//) : PlatformView, MethodChannel.MethodCallHandler {
//
//  private val tag = "ArTryOn3D"
//  private val fragmentTag = "ar_tryon_face_fragment_$viewId"
//
//  private val container = FrameLayout(context).apply {
//    setBackgroundColor(0xFF000000.toInt())
//    layoutParams = FrameLayout.LayoutParams(
//      FrameLayout.LayoutParams.MATCH_PARENT,
//      FrameLayout.LayoutParams.MATCH_PARENT
//    )
//  }
//
//  private val fragmentContainer = FragmentContainerView(context).apply {
//    id = View.generateViewId()
//    layoutParams = FrameLayout.LayoutParams(
//      FrameLayout.LayoutParams.MATCH_PARENT,
//      FrameLayout.LayoutParams.MATCH_PARENT
//    )
//  }
//
//  private val channel = MethodChannel(messenger, "ar_tryon_view/method_$viewId")
//
//  private var arFragment: ArFragment? = null
//  private var currentRenderable: ModelRenderable? = null
//  private var currentScale: Float = 1.0f
//  private var userRequestedInstall = true
//
//  private val faceNodes = linkedMapOf<AugmentedFace, AugmentedFaceNode>()
//
//  private val updateListener = Scene.OnUpdateListener {
//    syncTrackedFaces()
//  }
//
//  init {
//    container.addView(fragmentContainer)
//    channel.setMethodCallHandler(this)
//  }
//
//  override fun getView(): View = container
//
//  override fun dispose() {
//    channel.setMethodCallHandler(null)
//    removeSceneListener()
//    clearFaceNodes()
//    removeFragment()
//    currentRenderable = null
//  }
//
//  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
//    when (call.method) {
//      "start" -> startAr(result)
//      "stop" -> stopAr(result)
//      "loadModelAsset" -> loadModelFromFlutterAsset(call, result)
//      "loadModelUrl" -> loadModelFromUrl(call, result)
//      "clearModel" -> clearModel(result)
//      "dispose" -> {
//        dispose()
//        result.success(null)
//      }
//      else -> result.notImplemented()
//    }
//  }
//
//  private fun startAr(result: MethodChannel.Result) {
//    val activity = activityProvider() as? FragmentActivity
//    if (activity == null) {
//      result.error(
//        "NO_ACTIVITY",
//        "Host Activity must extend FlutterFragmentActivity / FragmentActivity.",
//        null
//      )
//      return
//    }
//
//    val availability = ArCoreApk.getInstance().checkAvailability(activity)
//    if (availability.isUnsupported) {
//      result.error("AR_NOT_SUPPORTED", "This device does not support ARCore face tracking.", null)
//      return
//    }
//
//    try {
//      when (ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)) {
//        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
//          userRequestedInstall = false
//          result.error(
//            "ARCORE_INSTALL_REQUESTED",
//            "Google Play Services for AR install/update was requested. Call start() again after returning.",
//            null
//          )
//          return
//        }
//
//        ArCoreApk.InstallStatus.INSTALLED -> {
//          attachFragmentIfNeeded(activity)
//          result.success(null)
//        }
//      }
//    } catch (e: UnavailableException) {
//      result.error("ARCORE_UNAVAILABLE", e.message, null)
//    } catch (e: Exception) {
//      result.error("ARCORE_START_FAILED", e.message, null)
//    }
//  }
//
//  private fun stopAr(result: MethodChannel.Result) {
//    removeSceneListener()
//    clearFaceNodes()
//    removeFragment()
//    result.success(null)
//  }
//
//  @Suppress("UNCHECKED_CAST")
//  private fun loadModelFromFlutterAsset(call: MethodCall, result: MethodChannel.Result) {
//    val args = call.arguments as? Map<String, Any?>
//    val asset = args?.get("asset") as? String
//    currentScale = (args?.get("scale") as? Number)?.toFloat() ?: 1.0f
//
//    if (asset.isNullOrBlank()) {
//      result.error("ARG_ERROR", "loadModelAsset requires {asset: 'assets/models/xxx.glb'}", null)
//      return
//    }
//
//    if (!isSupportedModel(asset)) {
//      result.error("MODEL_TYPE_UNSUPPORTED", "Only .glb and .gltf are supported.", null)
//      return
//    }
//
//    try {
//      val localFile = materializeFlutterAsset(asset)
//      val uri = Uri.fromFile(localFile)
//      buildRenderable(
//        uri = uri,
//        isFilamentGltf = true,
//        result = result
//      )
//    } catch (e: Exception) {
//      result.error("MODEL_ASSET_FAILED", e.message, null)
//    }
//  }
//
//  @Suppress("UNCHECKED_CAST")
//  private fun loadModelFromUrl(call: MethodCall, result: MethodChannel.Result) {
//    val args = call.arguments as? Map<String, Any?>
//    val url = args?.get("url") as? String
//    currentScale = (args?.get("scale") as? Number)?.toFloat() ?: 1.0f
//
//    if (url.isNullOrBlank()) {
//      result.error("ARG_ERROR", "loadModelUrl requires {url: 'https://...'}", null)
//      return
//    }
//
//    if (!isSupportedModel(url)) {
//      result.error("MODEL_TYPE_UNSUPPORTED", "Only .glb and .gltf are supported.", null)
//      return
//    }
//
//    try {
//      val uri = Uri.parse(url)
//      buildRenderable(
//        uri = uri,
//        isFilamentGltf = true,
//        result = result
//      )
//    } catch (e: Exception) {
//      result.error("MODEL_URL_FAILED", e.message, null)
//    }
//  }
//
//  private fun buildRenderable(
//    uri: Uri,
//    isFilamentGltf: Boolean,
//    result: MethodChannel.Result,
//  ) {
//    val builder = ModelRenderable.builder()
//      .setSource(context, uri)
//
//    if (isFilamentGltf) {
//      builder.setIsFilamentGltf(true)
//      builder.setAsyncLoadEnabled(true)
//    }
//
//    builder.build()
//      .thenAccept { renderable ->
//        currentRenderable = renderable
//        applyRenderableToExistingFaces()
//        result.success(null)
//      }
//      .exceptionally { throwable ->
//        result.error("MODEL_LOAD_FAILED", throwable.message, null)
//        null
//      }
//  }
//
//  private fun isSupportedModel(pathOrUrl: String): Boolean {
//    val clean = pathOrUrl.substringBefore("?").lowercase()
//    return clean.endsWith(".glb") || clean.endsWith(".gltf")
//  }
//
//  private fun clearModel(result: MethodChannel.Result) {
//    currentRenderable = null
//    faceNodes.values.forEach { node ->
//      node.setFaceRegionsRenderable(null)
//    }
//    result.success(null)
//  }
//
//
//
//
////  private fun attachFragmentIfNeeded(activity: FragmentActivity) {
////    val fm = activity.supportFragmentManager
////    val existing = fm.findFragmentByTag(fragmentTag) as? ArFragment
////
////    if (existing != null) {
////      arFragment = existing
////      ensureSceneListenerAttached()
////      return
////    }
////
////    val fragment = ArFragment().apply {
////      setOnSessionConfigurationListener { session, config ->
////        try {
////          val filter = CameraConfigFilter(session)
////            .setFacingDirection(CameraConfig.FacingDirection.FRONT)
////
////          val cameraConfigs = session.getSupportedCameraConfigs(filter)
////          if (cameraConfigs.isNotEmpty()) {
////            session.cameraConfig = cameraConfigs[0]
////          } else {
////            Log.e(tag, "No front camera config found for ARCore.")
////          }
////
////          config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
////          config.planeFindingMode = Config.PlaneFindingMode.DISABLED
////          config.lightEstimationMode = Config.LightEstimationMode.DISABLED
////        } catch (e: Exception) {
////          Log.e(tag, "Session config failed: ${e.message}", e)
////        }
////      }
////
////      setOnViewCreatedListener { arSceneView ->
////        try {
////          arSceneView.planeRenderer.isVisible = false
////        } catch (_: Exception) {
////        }
////      }
////    }
////
////    fm.beginTransaction()
////      .replace(fragmentContainer.id, fragment, fragmentTag)
////      .commitNowAllowingStateLoss()
////
////    arFragment = fragment
////    ensureSceneListenerAttached()
////  }
//
//
//
//
//  private fun attachFragmentIfNeeded(activity: FragmentActivity) {
//    val fm = activity.supportFragmentManager
//    val existing = fm.findFragmentByTag(fragmentTag) as? ArFragment
//
//    if (existing != null) {
//      arFragment = existing
//      ensureSceneListenerAttached()
//      return
//    }
//
//    val fragment = ArFragment().apply {
//      setOnSessionConfigurationListener { _, config ->
//        try {
//          config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
//          config.planeFindingMode = Config.PlaneFindingMode.DISABLED
//          config.lightEstimationMode = Config.LightEstimationMode.DISABLED
//        } catch (e: Exception) {
//          Log.e(tag, "Session config failed: ${e.message}", e)
//        }
//      }
//
//      setOnViewCreatedListener { arSceneView ->
//        try {
//          arSceneView.planeRenderer.isVisible = false
//        } catch (_: Exception) {
//        }
//      }
//    }
//
//    fm.beginTransaction()
//      .replace(fragmentContainer.id, fragment, fragmentTag)
//      .commitNowAllowingStateLoss()
//
//    arFragment = fragment
//    ensureSceneListenerAttached()
//  }
//
//
//
//
//
//  private fun removeFragment() {
//    val activity = activityProvider() as? FragmentActivity ?: return
//    val fm = activity.supportFragmentManager
//    val fragment = fm.findFragmentByTag(fragmentTag) ?: return
//
//    try {
//      fm.beginTransaction()
//        .remove(fragment)
//        .commitNowAllowingStateLoss()
//    } catch (_: Exception) {
//    }
//
//    arFragment = null
//  }
//
//  private fun ensureSceneListenerAttached() {
//    removeSceneListener()
//    arFragment?.arSceneView?.scene?.addOnUpdateListener(updateListener)
//  }
//
//  private fun removeSceneListener() {
//    try {
//      arFragment?.arSceneView?.scene?.removeOnUpdateListener(updateListener)
//    } catch (_: Exception) {
//    }
//  }
//
//  private fun syncTrackedFaces() {
//    val fragment = arFragment ?: return
//    val session = fragment.arSceneView?.session ?: return
//    val scene = fragment.arSceneView?.scene ?: return
//
//    val faces = session.getAllTrackables(AugmentedFace::class.java)
//
//    for (face in faces) {
//      when (face.trackingState) {
//        TrackingState.TRACKING -> {
//          val node = faceNodes[face] ?: createFaceNode(face, scene).also {
//            faceNodes[face] = it
//          }
//          currentRenderable?.let { renderable ->
//            node.setFaceRegionsRenderable(renderable)
//            node.localScale = Vector3(currentScale, currentScale, currentScale)
//          }
//        }
//
//        TrackingState.STOPPED -> removeFaceNode(face)
//        else -> Unit
//      }
//    }
//
//    val liveFaces = faces.toSet()
//    val toRemove = mutableListOf<AugmentedFace>()
//    for (face in faceNodes.keys) {
//      if (face !in liveFaces || face.trackingState == TrackingState.STOPPED) {
//        toRemove.add(face)
//      }
//    }
//    toRemove.forEach { removeFaceNode(it) }
//  }
//
//  private fun createFaceNode(face: AugmentedFace, scene: Scene): AugmentedFaceNode {
//    return AugmentedFaceNode(face).apply {
//      setParent(scene)
//      currentRenderable?.let { renderable ->
//        setFaceRegionsRenderable(renderable)
//      }
//      localScale = Vector3(currentScale, currentScale, currentScale)
//    }
//  }
//
//  private fun removeFaceNode(face: AugmentedFace) {
//    val node = faceNodes.remove(face) ?: return
//    try {
//      node.setParent(null)
//    } catch (_: Exception) {
//    }
//  }
//
//  private fun clearFaceNodes() {
//    val allFaces = faceNodes.keys.toList()
//    allFaces.forEach { removeFaceNode(it) }
//    faceNodes.clear()
//  }
//
//  private fun applyRenderableToExistingFaces() {
//    val renderable = currentRenderable ?: return
//    faceNodes.values.forEach { node ->
//      node.setFaceRegionsRenderable(renderable)
//      node.localScale = Vector3(currentScale, currentScale, currentScale)
//    }
//  }
//
//  private fun materializeFlutterAsset(assetPath: String): File {
//    val lookupKey = assetLookup(assetPath)
//    val safeName = "model_${lookupKey.hashCode()}_${assetPath.substringAfterLast('/')}"
//    val outDir = File(context.cacheDir, "ar_tryon_models").apply { mkdirs() }
//    val outFile = File(outDir, safeName)
//
//    if (outFile.exists() && outFile.length() > 0L) {
//      return outFile
//    }
//
//    context.resources.assets.open(lookupKey).use { input ->
//      FileOutputStream(outFile).use { output ->
//        input.copyTo(output)
//      }
//    }
//
//    Log.d(tag, "Materialized flutter asset: $assetPath -> ${outFile.absolutePath}")
//    return outFile
//  }
//}















package com.nafim.ar_tryon_view

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
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
      ArTryOnViewFactory(
        messenger = binding.binaryMessenger,
        activityProvider = { activity }
      )
    )
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) = Unit

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }
}

private class ArTryOnViewFactory(
  private val messenger: BinaryMessenger,
  private val activityProvider: () -> Activity?
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

  override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
    return ArTryOnPlatformView(
      context = context,
      messenger = messenger,
      viewId = viewId,
      activityProvider = activityProvider
    )
  }
}

private class ArTryOnPlatformView(
  private val context: Context,
  messenger: BinaryMessenger,
  viewId: Int,
  private val activityProvider: () -> Activity?
) : PlatformView, MethodChannel.MethodCallHandler {

  private val container = FrameLayout(context).apply {
    setBackgroundColor(0xFF000000.toInt())
    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    )
  }

  private val previewView = PreviewView(context).apply {
    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    )
    scaleType = PreviewView.ScaleType.FILL_CENTER
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
  }

  private val channel = MethodChannel(messenger, "ar_tryon_view/method_$viewId")

  private var cameraProvider: ProcessCameraProvider? = null
  private var isStarted = false

  init {
    container.addView(previewView)
    channel.setMethodCallHandler(this)
  }

  override fun getView(): View = container

  override fun dispose() {
    channel.setMethodCallHandler(null)
    stopCameraInternal()
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "start" -> startCamera(result)
      "stop" -> {
        stopCameraInternal()
        result.success(null)
      }
      "loadModelAsset" -> {
        result.error(
          "NOT_SUPPORTED_IN_FLUTTER_ACTIVITY_MODE",
          "This FlutterActivity-compatible version supports camera preview only. Face-anchored 3D model tracking needs a different native architecture.",
          null
        )
      }
      "loadModelUrl" -> {
        result.error(
          "NOT_SUPPORTED_IN_FLUTTER_ACTIVITY_MODE",
          "This FlutterActivity-compatible version supports camera preview only. Face-anchored 3D model tracking needs a different native architecture.",
          null
        )
      }
      "clearModel" -> {
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
    val activity = activityProvider()
    if (activity == null) {
      result.error("NO_ACTIVITY", "Activity is null.", null)
      return
    }

    val lifecycleOwner = activity as? LifecycleOwner
    if (lifecycleOwner == null) {
      result.error("NO_LIFECYCLE", "Host Activity is not a LifecycleOwner.", null)
      return
    }

    val granted = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (!granted) {
      result.error("NO_CAMERA_PERMISSION", "Camera permission not granted.", null)
      return
    }

    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
      {
        try {
          cameraProvider = cameraProviderFuture.get()

          val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
          }

          val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

          cameraProvider?.unbindAll()
          cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            selector,
            preview
          )

          isStarted = true
          result.success(null)
        } catch (e: Exception) {
          result.error("CAMERA_START_FAILED", e.message, null)
        }
      },
      ContextCompat.getMainExecutor(context)
    )
  }

  private fun stopCameraInternal() {
    try {
      cameraProvider?.unbindAll()
    } catch (_: Exception) {
    }
    cameraProvider = null
    isStarted = false
  }
}