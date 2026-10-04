import 'dart:io';

import 'package:flutter/services.dart';

/// Metadata for a user-selected GLB file.
class ArGlbFile {
  const ArGlbFile({
    required this.uri,
    required this.name,
    required this.sizeBytes,
  });

  final String uri;
  final String name;
  final int sizeBytes;

  String get readableSize {
    if (sizeBytes < 0) return '';
    const kb = 1024;
    const mb = kb * 1024;

    if (sizeBytes >= mb) {
      return '${(sizeBytes / mb).toStringAsFixed(1)} MB';
    }
    return '${(sizeBytes / kb).toStringAsFixed(1)} KB';
  }
}

/// Native Android ARCore GLB placement API.
///
/// This is intentionally separate from the existing CameraX/front-camera
/// try-on pipeline so current try-on behavior remains unchanged.
class ArGlbPlacement {
  ArGlbPlacement._();

  static const MethodChannel _channel = MethodChannel('ar_tryon_view/ar');

  static void _ensureAndroid() {
    if (!Platform.isAndroid) {
      throw UnsupportedError(
        'ArGlbPlacement currently supports Android only.',
      );
    }
  }

  /// Opens the Android document picker and accepts only a .glb file.
  static Future<ArGlbFile?> pickGlb() async {
    _ensureAndroid();

    try {
      final result =
          await _channel.invokeMapMethod<String, dynamic>('pickGlb');

      if (result == null) return null;

      return ArGlbFile(
        uri: result['uri'] as String,
        name: result['name'] as String,
        sizeBytes: (result['sizeBytes'] as num?)?.toInt() ?? -1,
      );
    } on PlatformException catch (e) {
      throw Exception(e.message ?? 'Could not choose GLB file.');
    }
  }

  /// Opens the native ARCore placement screen for a selected GLB.
  static Future<void> open({
    required String modelUri,
    double modelSizeM = 0.55,
  }) async {
    _ensureAndroid();

    try {
      await _channel.invokeMethod<void>(
        'openAr',
        <String, dynamic>{
          'modelUri': modelUri,
          'modelSizeM': modelSizeM,
        },
      );
    } on PlatformException catch (e) {
      throw Exception(e.message ?? 'Could not open AR placement.');
    }
  }

  /// Picks a GLB and immediately opens it in AR.
  ///
  /// Returns the selected file, or null when the picker is cancelled.
  static Future<ArGlbFile?> pickAndOpen({
    double modelSizeM = 0.55,
  }) async {
    final file = await pickGlb();
    if (file == null) return null;

    await open(
      modelUri: file.uri,
      modelSizeM: modelSizeM,
    );
    return file;
  }
}
