

library ar_tryon_view;

import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

class ArTryOnView extends StatefulWidget {
  const ArTryOnView({
    super.key,
    this.onCreated,
    this.creationParams,
  });

  final void Function(ArTryOnController controller)? onCreated;

  /// Optional params to pass when creating the native view
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

class ArTryOnController {
  ArTryOnController._(int id)
      : _id = id,
        _channel = MethodChannel('ar_tryon_view/method_$id');

  final int _id;
  final MethodChannel _channel;

  int get id => _id;

  /// Start native session (Camera/AR)
  Future<void> start() => _channel.invokeMethod<void>('start');

  /// Stop native session (Camera/AR)
  Future<void> stop() => _channel.invokeMethod<void>('stop');

  /// Example: "glasses_01" / "makeup_lip_02"
  /// Native side-এ তুমি string ধরেই model/overlay change করবে
  Future<void> setEffect(String effectId) =>
      _channel.invokeMethod<void>('setEffect', {'effectId': effectId});

  /// PNG/JPG bytes পাঠিয়ে native overlay দেখাতে চাইলে (ImageView)
  Future<void> setEffectBytes(Uint8List bytes) =>
      _channel.invokeMethod<void>('setEffectBytes', bytes);

  /// Flutter asset path থেকে bytes লোড করে পাঠায় (সবচেয়ে easy)
  /// Example: 'assets/glasses_01.png'
  Future<void> setEffectAsset(String assetPath) async {
    final data = await rootBundle.load(assetPath);
    final bytes = data.buffer.asUint8List();
    await setEffectBytes(bytes);
  }

  /// Overlay clear
  Future<void> clearEffect() => _channel.invokeMethod<void>('clearEffect');

  /// (Optional) If later you want to release native resources per-view
  Future<void> dispose() => _channel.invokeMethod<void>('dispose');
}
