

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


```
## Usage

Take permission from AndroidManifest.xml file

```xml
<uses-permission android:name="android.permission.CAMERA" />

```

Make sure the /android/app/build.gradle.kts file contain this

```kotlin

android {
    // Recommended to avoid NDK mismatch with some plugins
    ndkVersion = "27.0.12077973"

    defaultConfig {
        // Required for this plugin (CameraX / platform view pipeline)
        minSdk = 24
    }
}


```



Set .png assets here


```dart

@override
Widget build(BuildContext context) {
  return Scaffold(
    body: ElevatedButton(
      onPressed: () => controller?.setEffectAsset('assets/glasses_01.png'),
      child: const Text('Effect'),
    ),
  );
}



```

Just like this: 

```dart

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:ar_tryon_view/ar_tryon_view.dart';

class TryOnScreen extends StatefulWidget {
  const TryOnScreen({super.key});

  @override
  State<TryOnScreen> createState() => _TryOnScreenState();
}

class _TryOnScreenState extends State<TryOnScreen> {
  ArTryOnController? controller;

  Future<void> _startSafely() async {
    final status = await Permission.camera.request();
    if (!status.isGranted) return;
    await controller?.start();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('AR Try-on Demo')),
      body: Column(
        children: [
          Expanded(
            child: ArTryOnView(
              onCreated: (c) async {
                controller = c;
                await _startSafely();
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Wrap(
              spacing: 10,
              children: [
                ElevatedButton(
                  onPressed: _startSafely,
                  child: const Text('Start'),
                ),
                ElevatedButton(
                  onPressed: () => controller?.stop(),
                  child: const Text('Stop'),
                ),
                ElevatedButton(
                  onPressed: () => controller?.setEffectAsset('assets/glasses_01.png'),
                  child: const Text('Effect'),
                ),
                ElevatedButton(
                  onPressed: () => controller?.clearEffect(),
                  child: const Text('Clear'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}



```



