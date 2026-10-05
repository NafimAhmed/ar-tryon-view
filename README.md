# AR Try-On View

A Flutter plugin with exactly two user-facing features:

1. **PNG face/camera overlay** using the native front camera.
2. **Native Android ARCore GLB placement** for placing a user-selected `.glb` model on a floor/surface.

The old ModelViewer-based 3D mask overlay has been removed.

## Platform support

| Feature | Android | iOS |
| --- | --- | --- |
| PNG camera overlay | ✅ | ✅ |
| Native GLB AR placement | ✅ | ❌ |

## Installation

```yaml
dependencies:
  ar_tryon_view: ^0.0.7
```

For testing the development branch directly:

```yaml
dependencies:
  ar_tryon_view:
    git:
      url: https://github.com/NafimAhmed/ar-tryon-view.git
      ref: native-ar-glb-placement
```

Android apps should use at least:

```kotlin
android {
    defaultConfig {
        minSdk = 24
    }
}
```

Add camera permission to the host Android app:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

For iOS, add:

```xml
<key>NSCameraUsageDescription</key>
<string>This app needs camera access for the try-on preview.</string>
```

## 1. PNG face/camera overlay

Add a transparent PNG asset:

```yaml
flutter:
  assets:
    - assets/glasses_01.png
```

Then use:

```dart
import 'package:ar_tryon_view/ar_tryon_view.dart';

ArTryOnController? controller;

ArTryOnView(
  onCreated: (c) async {
    controller = c;
    await controller!.start();
    await controller!.setEffectAsset('assets/glasses_01.png');
  },
);
```

Available PNG overlay controls:

```dart
await controller?.setEffectAsset('assets/glasses_01.png');
await controller?.setEffectBytes(bytes);
await controller?.clearEffect();
await controller?.start();
await controller?.stop();
```

This feature is a native front-camera preview with a transparent image overlay. It does not add a second 3D mask system.

## 2. Native GLB AR placement

Android only.

Pick a `.glb` file and open the native ARCore placement screen:

```dart
final file = await ArGlbPlacement.pickGlb();

if (file != null) {
  await ArGlbPlacement.open(
    modelUri: file.uri,
    modelSizeM: 0.55,
  );
}
```

Or:

```dart
await ArGlbPlacement.pickAndOpen(
  modelSizeM: 0.55,
);
```

The AR placement flow:

```text
Choose .glb
   ↓
Native ARCore camera
   ↓
Real horizontal plane preferred
   ↓
Instant Placement fallback
   ↓
+ turns GREEN
   ↓
Tap once
   ↓
ARCore anchor + GLB model
```

Current GLB renderer support includes:

- `.glb` only
- embedded base-color textures
- per-material `baseColorFactor`
- multiple materials
- multiple mesh primitives
- `EXT_texture_webp` base-color sources
- unsigned byte/short indices
- 32-bit indices on supported devices
- ARCore horizontal-plane detection
- Instant Placement fallback
- repositioning after placement

Advanced PBR effects such as full sheen, specular, clearcoat, transmission, and normal-map lighting are not fully reproduced by the current lightweight renderer.

## Example

The bundled example intentionally demonstrates only these two features:

- **Show/Clear PNG**
- **Choose .glb and place in AR**

Repository: https://github.com/NafimAhmed/ar-tryon-view
