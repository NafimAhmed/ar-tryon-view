

import 'package:flutter/material.dart';
import 'package:ar_tryon_view/ar_tryon_view.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/services.dart';

void main() => runApp(const MyApp());

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  ArTryOnController? controller;

  Future<void> _startSafely() async {
    final status = await Permission.camera.request();
    if (!status.isGranted) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Camera permission denied')),
      );
      return;
    }

    try {
      await controller?.start();
    } on PlatformException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Start failed: ${e.code}')),
      );
    }
  }

  Future<void> _stopSafely() async {
    try {
      await controller?.stop();
    } on PlatformException {}
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('AR Try-on Demo')),
        body: Column(
          children: [
            Expanded(
              child: ArTryOnView(
                onCreated: (c) async {
                  controller = c;
                  await _startSafely(); // auto start after permission
                },
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  ElevatedButton(
                    onPressed: _startSafely,
                    child: const Text('Start'),
                  ),
                  ElevatedButton(
                    onPressed: _stopSafely,
                    child: const Text('Stop'),
                  ),
                  ElevatedButton(
                    onPressed: () async {
                      await controller?.setEffectAsset('assets/glasses_01.png');
                    },
                    child: const Text('Effect'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
