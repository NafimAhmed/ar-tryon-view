import 'package:ar_tryon_view/ar_tryon_view.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('ArGlbFile formats megabyte sizes', () {
    const file = ArGlbFile(
      uri: 'content://example/model.glb',
      name: 'model.glb',
      sizeBytes: 2 * 1024 * 1024,
    );

    expect(file.name, 'model.glb');
    expect(file.readableSize, '2.0 MB');
  });

  test('ArGlbFile formats kilobyte sizes', () {
    const file = ArGlbFile(
      uri: 'content://example/model.glb',
      name: 'model.glb',
      sizeBytes: 512 * 1024,
    );

    expect(file.readableSize, '512.0 KB');
  });
}
