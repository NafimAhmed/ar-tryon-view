

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

Then use this code


```dart

import 'package:flutter/material.dart';
import 'package:firebase_notification_helper/firebase_notification_helper.dart';

class NotificationSenderPage extends StatefulWidget {
  const NotificationSenderPage({super.key});

  @override
  State<NotificationSenderPage> createState() => _NotificationSenderPageState();
}

class _NotificationSenderPageState extends State<NotificationSenderPage> {
  String token = "";
  String response = "";
  final keyController = TextEditingController();
  final titleController = TextEditingController(text: "Test Notification");
  final bodyController = TextEditingController(
    text: "Hello from firebase_notification_helper!",
  );

  @override
  void initState() {
    super.initState();
    _loadToken();
  }

  Future<void> _loadToken() async {
    final t = await FirebaseNotificationHelper.getToken();
    setState(() => token = t ?? "");
  }

  Future<void> _sendNotification() async {
    if (keyController.text.isEmpty) return;

    final res = await FirebaseNotificationHelper.sendNotification(
      serverKey: keyController.text.trim(),
      targetToken: token,
      title: titleController.text.trim(),
      body: bodyController.text.trim(),
    );

    setState(() => response = res.toString());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("FCM Sender")),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          FirebaseNotificationHelper.showLocalNotification(
            title: titleController.text.trim(),
            body: bodyController.text.trim(),
          );
        },
        child: const Icon(Icons.notifications),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            const Text("Your FCM Token:"),
            SelectableText(token),
            const SizedBox(height: 16),

            TextField(
              controller: keyController,
              decoration: const InputDecoration(
                labelText: "Server Key",
                border: OutlineInputBorder(),
              ),
            ),

            const SizedBox(height: 12),

            TextField(
              controller: titleController,
              decoration: const InputDecoration(
                labelText: "Notification Title",
                border: OutlineInputBorder(),
              ),
            ),

            const SizedBox(height: 12),

            TextField(
              controller: bodyController,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: "Notification Body",
                border: OutlineInputBorder(),
              ),
            ),

            const SizedBox(height: 12),

            ElevatedButton(
              onPressed: _sendNotification,
              child: const Text("Send Notification"),
            ),

            const SizedBox(height: 16),
            const Text("Response:"),
            Expanded(
              child: SingleChildScrollView(child: Text(response)),
            ),
          ],
        ),
      ),
    );
  }
}





```

## Upgrade this android part in /android/build.gradle.kts

Then import

```kotlin

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.2")
    }
}



```




## Upgrade this android part in /android/app/build.gradle.kts

Then import

```kotlin

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")   // REQUIRED
    id("dev.flutter.flutter-gradle-plugin")
}


android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}


```

## Initialization

Then import

```dart
import 'package:flutter/material.dart';
import 'package:firebase_notification_helper/firebase_notification_helper.dart';

Future<void> main() async {
  await FirebaseNotificationHelper.initialize();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: NotificationSenderPage(),
    );
  }
}

```












