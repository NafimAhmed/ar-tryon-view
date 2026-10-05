import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

export 'ar_glb_placement.dart';

/// Front-camera preview with a transparent PNG overlay.
///
/// This is the first of the package's two supported features.
/// The PNG can be changed or cleared at runtime through [ArTryOnController].
class ArTryOnView extends StatefulWidget {
  const ArTryOnView({
    super.key,
    this.onCreated,
    this.creationParams,
  });

  final void Function(ArTryOnController controller)? onCreated;

  /// Optional parameters forwarded to the native platform view.
  final Map<String, dynamic>? creationParams;

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

    if (Platform.isAndroid) {
      return AndroidView(
        viewType: _viewType,
        onPlatformViewCreated: _onPlatformViewCreated,
        creationParams: params,
        creationParamsCodec: const StandardMessageCodec(),
      );
    }

    if (Platform.isIOS) {
      return UiKitView(
        viewType: _viewType,
        onPlatformViewCreated: _onPlatformViewCreated,
        creationParams: params,
        creationParamsCodec: const StandardMessageCodec(),
      );
    }

    return const SizedBox();
  }
}

/// Controller for the PNG camera-overlay feature.
class ArTryOnController {
  ArTryOnController._(int id)
      : _id = id,
        _channel = MethodChannel('ar_tryon_view/method_$id');

  final int _id;
  final MethodChannel _channel;

  int get id => _id;

  /// Starts the native front camera.
  Future<void> start() => _channel.invokeMethod<void>('start');

  /// Stops the native front camera.
  Future<void> stop() => _channel.invokeMethod<void>('stop');

  /// Shows PNG/JPG bytes over the camera preview.
  Future<void> setEffectBytes(Uint8List bytes) =>
      _channel.invokeMethod<void>('setEffectBytes', bytes);

  /// Loads a Flutter PNG/JPG asset and shows it over the camera preview.
  Future<void> setEffectAsset(String assetPath) async {
    final data = await rootBundle.load(assetPath);
    final bytes = data.buffer.asUint8List();
    await setEffectBytes(bytes);
  }

  /// Clears the current PNG/JPG overlay.
  Future<void> clearEffect() => _channel.invokeMethod<void>('clearEffect');

  /// Releases native resources for this platform view.
  Future<void> dispose() => _channel.invokeMethod<void>('dispose');
}
