import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'ar_tryon_view_method_channel.dart';

abstract class ArTryonViewPlatform extends PlatformInterface {
  /// Constructs a ArTryonViewPlatform.
  ArTryonViewPlatform() : super(token: _token);

  static final Object _token = Object();

  static ArTryonViewPlatform _instance = MethodChannelArTryonView();

  /// The default instance of [ArTryonViewPlatform] to use.
  ///
  /// Defaults to [MethodChannelArTryonView].
  static ArTryonViewPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [ArTryonViewPlatform] when
  /// they register themselves.
  static set instance(ArTryonViewPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
