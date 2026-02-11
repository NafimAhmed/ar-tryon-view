

# AR Try-On View (Flutter)

A lightweight Flutter plugin that embeds a native Android camera preview using **PlatformView + CameraX** and supports **transparent PNG overlay effects** (e.g., glasses, masks, accessories). This is a great starting point for building **virtual try-on** experiences for e-commerce apps.

![Linear Date Picker Demo](https://media.giphy.com/media/iEhGnZhkzoSOF8JNyD/giphy.gif)

![Linear Date Picker Demo](https://media.giphy.com/media/h6A88MsuZWu7mTN8Zy/giphy.gif)

## Features

- Embedded native Android camera preview using PlatformView + CameraX.
- Overlay transparent PNG effects on top of the camera feed.
- Dart controller API: start/stop, setEffect, setEffectBytes, setEffectAsset, clearEffect.
- Uses PreviewView ImplementationMode.COMPATIBLE for proper alpha blending (TextureView).
- Works well with permission_handler for camera permission.

## Platform Support

- Android: ✅ Supported
- iOS: ⏳ Not implemented yet (planned)

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  ar_tryon_view: ^0.0.2


