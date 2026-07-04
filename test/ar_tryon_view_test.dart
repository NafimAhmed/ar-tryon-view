import 'package:flutter/widgets.dart';
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
    MockArTryonViewPlatform fakePlatform = MockArTryonViewPlatform();
    ArTryonViewPlatform.instance = fakePlatform;

    // expect(await arTryonViewPlugin.getPlatformVersion(), '42');
  });

  test('glb mask asset source keeps placement options', () {
    const mask = ArTryOnGlbMask.asset(
      'assets/face_mask.glb',
      widthFactor: 0.6,
      heightFactor: 0.4,
      alignment: Alignment(0, -0.2),
      cameraOrbit: '0deg 75deg 2m',
    );

    expect(mask.src, 'assets/face_mask.glb');
    expect(mask.widthFactor, 0.6);
    expect(mask.heightFactor, 0.4);
    expect(mask.alignment, const Alignment(0, -0.2));
    expect(mask.cameraOrbit, '0deg 75deg 2m');
  });

  test('glb mask file source is converted to a file uri', () {
    final mask = ArTryOnGlbMask.file('C:\\masks\\face_mask.glb');

    expect(mask.src, startsWith('file:'));
    expect(mask.src, contains('face_mask.glb'));
  });
}
