//
//
// import 'package:flutter/material.dart';
// import 'package:ar_tryon_view/ar_tryon_view.dart';
// import 'package:permission_handler/permission_handler.dart';
// import 'package:flutter/services.dart';
//
// void main() => runApp(const MyApp());
//
// class MyApp extends StatefulWidget {
//   const MyApp({super.key});
//
//   @override
//   State<MyApp> createState() => _MyAppState();
// }
//
// class _MyAppState extends State<MyApp> {
//   ArTryOnController? controller;
//
//   Future<void> _startSafely() async {
//     final status = await Permission.camera.request();
//     if (!status.isGranted) {
//       if (!mounted) return;
//       ScaffoldMessenger.of(context).showSnackBar(
//         const SnackBar(content: Text('Camera permission denied')),
//       );
//       return;
//     }
//
//     try {
//       await controller?.start();
//     } on PlatformException catch (e) {
//       if (!mounted) return;
//       ScaffoldMessenger.of(context).showSnackBar(
//         SnackBar(content: Text('Start failed: ${e.code}')),
//       );
//     }
//   }
//
//   Future<void> _stopSafely() async {
//     try {
//       await controller?.stop();
//     } on PlatformException {}
//   }
//
//   @override
//   Widget build(BuildContext context) {
//     return MaterialApp(
//       home: Scaffold(
//         appBar: AppBar(title: const Text('AR Try-on Demo')),
//         body: Column(
//           children: [
//             Expanded(
//               child: ArTryOnView(
//                 onCreated: (c) async {
//                   controller = c;
//                   await _startSafely(); // auto start after permission
//                 },
//               ),
//             ),
//             Padding(
//               padding: const EdgeInsets.all(12),
//               child: Row(
//                 mainAxisAlignment: MainAxisAlignment.spaceAround,
//                 children: [
//                   ElevatedButton(
//                     onPressed: _startSafely,
//                     child: const Text('Start'),
//                   ),
//                   ElevatedButton(
//                     onPressed: _stopSafely,
//                     child: const Text('Stop'),
//                   ),
//                   ElevatedButton(
//                     onPressed: () async {
//                       await controller?.setEffectAsset('assets/glasses_01.png');
//                     },
//                     child: const Text('Effect'),
//                   ),
//                 ],
//               ),
//             ),
//           ],
//         ),
//       ),
//     );
//   }
// }












import 'package:ar_tryon_view/ar_tryon_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  ArTryOnController? controller;
  String statusText = 'Waiting...';
  bool _starting = false;

  Future<void> _showMessage(String message) async {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  Future<void> _startSafely() async {
    if (_starting) return;
    _starting = true;

    try {
      final status = await Permission.camera.request();
      if (!status.isGranted) {
        if (!mounted) return;
        setState(() => statusText = 'Camera permission denied');
        await _showMessage('Camera permission denied');
        return;
      }

      await controller?.start();

      if (!mounted) return;
      setState(() => statusText = 'AR started');
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() => statusText = 'Start failed: ${e.code}');
      await _showMessage('Start failed: ${e.code} ${e.message ?? ''}');
    } catch (e) {
      if (!mounted) return;
      setState(() => statusText = 'Start failed');
      await _showMessage('Start failed: $e');
    } finally {
      _starting = false;
    }
  }

  Future<void> _stopSafely() async {
    try {
      await controller?.stop();
      if (!mounted) return;
      setState(() => statusText = 'AR stopped');
    } on PlatformException catch (e) {
      await _showMessage('Stop failed: ${e.code}');
    } catch (e) {
      await _showMessage('Stop failed: $e');
    }
  }

  Future<void> _loadOnlineModel() async {
    try {
      await controller?.loadModelUrl(
        'https://your-domain.com/models/fox_mask.glb',
        scale: 1.0,
      );

      if (!mounted) return;
      setState(() => statusText = 'Online model loaded');
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() => statusText = 'Model load failed: ${e.code}');
      await _showMessage('Model load failed: ${e.code} ${e.message ?? ''}');
    } catch (e) {
      if (!mounted) return;
      setState(() => statusText = 'Model load failed');
      await _showMessage('Model load failed: $e');
    }
  }

  Future<void> _loadAssetModel() async {
    try {
      await controller?.loadModelAsset(
        'assets/models/fox_mask.glb',
        scale: 1.0,
      );

      if (!mounted) return;
      setState(() => statusText = 'Asset model loaded');
    } on PlatformException catch (e) {
      if (!mounted) return;
      setState(() => statusText = 'Asset load failed: ${e.code}');
      await _showMessage('Asset load failed: ${e.code} ${e.message ?? ''}');
    } catch (e) {
      if (!mounted) return;
      setState(() => statusText = 'Asset load failed');
      await _showMessage('Asset load failed: $e');
    }
  }

  Future<void> _clearModelSafely() async {
    try {
      await controller?.clearModel();
      if (!mounted) return;
      setState(() => statusText = 'Model cleared');
    } on PlatformException catch (e) {
      await _showMessage('Clear failed: ${e.code}');
    } catch (e) {
      await _showMessage('Clear failed: $e');
    }
  }

  @override
  void dispose() {
    controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        appBar: AppBar(
          title: const Text('AR Try-on Demo'),
        ),
        body: Column(
          children: [
            Expanded(
              child: ArTryOnView(
                onCreated: (c) async {
                  controller = c;
                  if (mounted) {
                    setState(() => statusText = 'View created');
                  }
                  await _startSafely();
                },
              ),
            ),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              color: Colors.black12,
              child: Text(
                statusText,
                textAlign: TextAlign.center,
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(12),
              child: Wrap(
                spacing: 10,
                runSpacing: 10,
                alignment: WrapAlignment.center,
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
                    onPressed: _loadOnlineModel,
                    child: const Text('Load URL'),
                  ),
                  ElevatedButton(
                    onPressed: _loadAssetModel,
                    child: const Text('Load Asset'),
                  ),
                  ElevatedButton(
                    onPressed: _clearModelSafely,
                    child: const Text('Clear'),
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