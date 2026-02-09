import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'ar_tryon_view_platform_interface.dart';

/// An implementation of [ArTryonViewPlatform] that uses method channels.
class MethodChannelArTryonView extends ArTryonViewPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('ar_tryon_view');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
