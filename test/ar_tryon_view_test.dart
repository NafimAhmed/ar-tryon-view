import 'package:flutter_test/flutter_test.dart';
import 'package:ar_tryon_view/ar_tryon_view.dart';
import 'package:ar_tryon_view/ar_tryon_view_platform_interface.dart';
import 'package:ar_tryon_view/ar_tryon_view_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockArTryonViewPlatform
    with MockPlatformInterfaceMixin
    implements ArTryonViewPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final ArTryonViewPlatform initialPlatform = ArTryonViewPlatform.instance;

  test('$MethodChannelArTryonView is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelArTryonView>());
  });

  test('getPlatformVersion', () async {
    ArTryonView arTryonViewPlugin = ArTryonView();
    MockArTryonViewPlatform fakePlatform = MockArTryonViewPlatform();
    ArTryonViewPlatform.instance = fakePlatform;

    expect(await arTryonViewPlugin.getPlatformVersion(), '42');
  });
}
