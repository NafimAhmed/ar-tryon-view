import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:model_viewer_plus/model_viewer_plus.dart';

class ArTryOnGlbMask {
  const ArTryOnGlbMask({
    required this.src,
    this.alt = '3D face mask',
    this.poster,
    this.iosSrc,
    this.alignment = Alignment.center,
    this.widthFactor = 0.72,
    this.heightFactor = 0.48,
    this.offset = Offset.zero,
    this.opacity = 1.0,
    this.cameraOrbit,
    this.cameraTarget,
    this.fieldOfView,
    this.exposure = 1.0,
    this.shadowIntensity = 0.0,
    this.autoRotate = false,
    this.cameraControls = false,
    this.disableZoom = true,
    this.ar = false,
    this.touchAction = TouchAction.none,
    this.interactionPrompt = InteractionPrompt.none,
    this.backgroundColor = const Color(0x00000000),
    this.loading = Loading.eager,
  }) : assert(widthFactor > 0),
       assert(heightFactor > 0),
       assert(opacity >= 0 && opacity <= 1);

  const ArTryOnGlbMask.asset(
    String assetPath, {
    this.alt = '3D face mask',
    this.poster,
    this.iosSrc,
    this.alignment = Alignment.center,
    this.widthFactor = 0.72,
    this.heightFactor = 0.48,
    this.offset = Offset.zero,
    this.opacity = 1.0,
    this.cameraOrbit,
    this.cameraTarget,
    this.fieldOfView,
    this.exposure = 1.0,
    this.shadowIntensity = 0.0,
    this.autoRotate = false,
    this.cameraControls = false,
    this.disableZoom = true,
    this.ar = false,
  }) : src = assetPath,
       touchAction = TouchAction.none,
       interactionPrompt = InteractionPrompt.none,
       backgroundColor = const Color(0x00000000),
       loading = Loading.eager,
       assert(widthFactor > 0),
       assert(heightFactor > 0),
       assert(opacity >= 0 && opacity <= 1);

  factory ArTryOnGlbMask.file(
    String absolutePath, {
    String alt = '3D face mask',
    String? poster,
    Alignment alignment = Alignment.center,
    double widthFactor = 0.72,
    double heightFactor = 0.48,
    Offset offset = Offset.zero,
    double opacity = 1.0,
    String? cameraOrbit,
    String? cameraTarget,
    String? fieldOfView,
    double exposure = 1.0,
    double shadowIntensity = 0.0,
    bool autoRotate = false,
    bool cameraControls = false,
    bool disableZoom = true,
    bool ar = false,
  }) {
    return ArTryOnGlbMask(
      src: Uri.file(absolutePath).toString(),
      alt: alt,
      poster: poster,
      alignment: alignment,
      widthFactor: widthFactor,
      heightFactor: heightFactor,
      offset: offset,
      opacity: opacity,
      cameraOrbit: cameraOrbit,
      cameraTarget: cameraTarget,
      fieldOfView: fieldOfView,
      exposure: exposure,
      shadowIntensity: shadowIntensity,
      autoRotate: autoRotate,
      cameraControls: cameraControls,
      disableZoom: disableZoom,
      ar: ar,
    );
  }

  const ArTryOnGlbMask.url(
    String url, {
    this.alt = '3D face mask',
    this.poster,
    this.iosSrc,
    this.alignment = Alignment.center,
    this.widthFactor = 0.72,
    this.heightFactor = 0.48,
    this.offset = Offset.zero,
    this.opacity = 1.0,
    this.cameraOrbit,
    this.cameraTarget,
    this.fieldOfView,
    this.exposure = 1.0,
    this.shadowIntensity = 0.0,
    this.autoRotate = false,
    this.cameraControls = false,
    this.disableZoom = true,
    this.ar = false,
  }) : src = url,
       touchAction = TouchAction.none,
       interactionPrompt = InteractionPrompt.none,
       backgroundColor = const Color(0x00000000),
       loading = Loading.eager,
       assert(widthFactor > 0),
       assert(heightFactor > 0),
       assert(opacity >= 0 && opacity <= 1);

  final String src;
  final String alt;
  final String? poster;
  final String? iosSrc;
  final Alignment alignment;
  final double widthFactor;
  final double heightFactor;
  final Offset offset;
  final double opacity;
  final String? cameraOrbit;
  final String? cameraTarget;
  final String? fieldOfView;
  final double exposure;
  final double shadowIntensity;
  final bool autoRotate;
  final bool cameraControls;
  final bool disableZoom;
  final bool ar;
  final TouchAction touchAction;
  final InteractionPrompt interactionPrompt;
  final Color backgroundColor;
  final Loading loading;

  ArTryOnGlbMask copyWith({
    String? src,
    String? alt,
    String? poster,
    String? iosSrc,
    Alignment? alignment,
    double? widthFactor,
    double? heightFactor,
    Offset? offset,
    double? opacity,
    String? cameraOrbit,
    String? cameraTarget,
    String? fieldOfView,
    double? exposure,
    double? shadowIntensity,
    bool? autoRotate,
    bool? cameraControls,
    bool? disableZoom,
    bool? ar,
    TouchAction? touchAction,
    InteractionPrompt? interactionPrompt,
    Color? backgroundColor,
    Loading? loading,
  }) {
    return ArTryOnGlbMask(
      src: src ?? this.src,
      alt: alt ?? this.alt,
      poster: poster ?? this.poster,
      iosSrc: iosSrc ?? this.iosSrc,
      alignment: alignment ?? this.alignment,
      widthFactor: widthFactor ?? this.widthFactor,
      heightFactor: heightFactor ?? this.heightFactor,
      offset: offset ?? this.offset,
      opacity: opacity ?? this.opacity,
      cameraOrbit: cameraOrbit ?? this.cameraOrbit,
      cameraTarget: cameraTarget ?? this.cameraTarget,
      fieldOfView: fieldOfView ?? this.fieldOfView,
      exposure: exposure ?? this.exposure,
      shadowIntensity: shadowIntensity ?? this.shadowIntensity,
      autoRotate: autoRotate ?? this.autoRotate,
      cameraControls: cameraControls ?? this.cameraControls,
      disableZoom: disableZoom ?? this.disableZoom,
      ar: ar ?? this.ar,
      touchAction: touchAction ?? this.touchAction,
      interactionPrompt: interactionPrompt ?? this.interactionPrompt,
      backgroundColor: backgroundColor ?? this.backgroundColor,
      loading: loading ?? this.loading,
    );
  }
}

class ArTryOnView extends StatefulWidget {
  const ArTryOnView({
    super.key,
    this.onCreated,
    this.creationParams,
    this.glbMask,
  });

  final void Function(ArTryOnController controller)? onCreated;

  /// Optional params to pass when creating the native view.
  final Map<String, dynamic>? creationParams;

  /// Optional 3D GLB/glTF mask rendered above the native camera preview.
  final ArTryOnGlbMask? glbMask;

  @override
  State<ArTryOnView> createState() => _ArTryOnViewState();
}

class _ArTryOnViewState extends State<ArTryOnView> {
  static const String _viewType = 'ar_tryon_view/native_view';

  void _onPlatformViewCreated(int id) {
    widget.onCreated?.call(ArTryOnController._(id));
  }

  @override
  Widget build(BuildContext context) {
    final params = widget.creationParams ?? const <String, dynamic>{};
    final Widget cameraView;

    if (Platform.isAndroid) {
      cameraView = AndroidView(
        viewType: _viewType,
        onPlatformViewCreated: _onPlatformViewCreated,
        creationParams: params,
        creationParamsCodec: const StandardMessageCodec(),
      );
    } else if (Platform.isIOS) {
      cameraView = UiKitView(
        viewType: _viewType,
        onPlatformViewCreated: _onPlatformViewCreated,
        creationParams: params,
        creationParamsCodec: const StandardMessageCodec(),
      );
    } else {
      cameraView = const SizedBox();
    }

    final glbMask = widget.glbMask;
    if (glbMask == null) return cameraView;

    return Stack(
      fit: StackFit.expand,
      children: [
        cameraView,
        ArTryOnGlbMaskView(mask: glbMask),
      ],
    );
  }
}

class ArTryOnGlbMaskView extends StatelessWidget {
  const ArTryOnGlbMaskView({super.key, required this.mask});

  final ArTryOnGlbMask mask;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      ignoring: !mask.cameraControls,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth * mask.widthFactor;
          final height = constraints.maxHeight * mask.heightFactor;

          return Align(
            alignment: mask.alignment,
            child: Transform.translate(
              offset: mask.offset,
              child: Opacity(
                opacity: mask.opacity,
                child: SizedBox(
                  width: width,
                  height: height,
                  child: ModelViewer(
                    src: mask.src,
                    alt: mask.alt,
                    poster: mask.poster,
                    iosSrc: mask.iosSrc,
                    ar: mask.ar,
                    autoRotate: mask.autoRotate,
                    cameraControls: mask.cameraControls,
                    disableZoom: mask.disableZoom,
                    cameraOrbit: mask.cameraOrbit,
                    cameraTarget: mask.cameraTarget,
                    fieldOfView: mask.fieldOfView,
                    exposure: mask.exposure,
                    shadowIntensity: mask.shadowIntensity,
                    interactionPrompt: mask.interactionPrompt,
                    loading: mask.loading,
                    backgroundColor: mask.backgroundColor,
                    touchAction: mask.touchAction,
                    reveal: Reveal.auto,
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class ArTryOnController {
  ArTryOnController._(int id)
    : _id = id,
      _channel = MethodChannel('ar_tryon_view/method_$id');

  final int _id;
  final MethodChannel _channel;

  int get id => _id;

  /// Start native camera/AR session.
  Future<void> start() => _channel.invokeMethod<void>('start');

  /// Stop native camera/AR session.
  Future<void> stop() => _channel.invokeMethod<void>('stop');

  /// Native string-based effect selection for implementations that map ids.
  Future<void> setEffect(String effectId) =>
      _channel.invokeMethod<void>('setEffect', {'effectId': effectId});

  /// Send PNG/JPG bytes to the native 2D overlay.
  Future<void> setEffectBytes(Uint8List bytes) =>
      _channel.invokeMethod<void>('setEffectBytes', bytes);

  /// Load a Flutter PNG/JPG asset and send it to the native 2D overlay.
  Future<void> setEffectAsset(String assetPath) async {
    final data = await rootBundle.load(assetPath);
    final bytes = data.buffer.asUint8List();
    await setEffectBytes(bytes);
  }

  /// Clear the native 2D overlay.
  Future<void> clearEffect() => _channel.invokeMethod<void>('clearEffect');

  /// Release native resources for this platform view.
  Future<void> dispose() => _channel.invokeMethod<void>('dispose');
}
