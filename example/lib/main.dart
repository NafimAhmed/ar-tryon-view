import 'package:ar_tryon_view/ar_tryon_view.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'AR Try-on View',
      theme: ThemeData(useMaterial3: true),
      home: const DemoScreen(),
    );
  }
}

class DemoScreen extends StatefulWidget {
  const DemoScreen({super.key});

  @override
  State<DemoScreen> createState() => _DemoScreenState();
}

class _DemoScreenState extends State<DemoScreen> {
  ArTryOnController? _controller;
  bool _starting = false;

  Future<void> _startCamera() async {
    if (_starting) return;
    _starting = true;

    try {
      final status = await Permission.camera.request();
      if (!status.isGranted) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Camera permission denied')),
        );
        return;
      }

      await _controller?.start();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Camera start failed: $e')),
      );
    } finally {
      _starting = false;
    }
  }

  Future<void> _showPngFace() async {
    try {
      await _controller?.setEffectAsset('assets/glasses_01.png');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('PNG overlay failed: $e')),
      );
    }
  }

  Future<void> _placeGlb() async {
    try {
      final file = await ArGlbPlacement.pickGlb();
      if (file == null) return;

      await ArGlbPlacement.open(
        modelUri: file.uri,
        modelSizeM: 0.55,
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('AR placement failed: $e')),
      );
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AR Try-on View'),
        centerTitle: true,
      ),
      body: Column(
        children: [
          Expanded(
            child: ArTryOnView(
              onCreated: (controller) {
                _controller = controller;
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  _startCamera();
                });
              },
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    '1. PNG face overlay',
                    style: TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton(
                          onPressed: _showPngFace,
                          child: const Text('Show PNG'),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () => _controller?.clearEffect(),
                          child: const Text('Clear PNG'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    '2. GLB floor placement',
                    style: TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 8),
                  FilledButton.icon(
                    onPressed: _placeGlb,
                    icon: const Icon(Icons.view_in_ar),
                    label: const Text('Choose .glb and place in AR'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
