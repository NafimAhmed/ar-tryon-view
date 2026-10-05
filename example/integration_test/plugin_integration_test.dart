import 'package:ar_tryon_view/ar_tryon_view.dart';
import 'package:flutter/material.dart';

void main() => runApp(const IntegrationDemo());

class IntegrationDemo extends StatefulWidget {
  const IntegrationDemo({super.key});

  @override
  State<IntegrationDemo> createState() => _IntegrationDemoState();
}

class _IntegrationDemoState extends State<IntegrationDemo> {
  ArTryOnController? controller;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('AR Try-On Plugin Demo')),
        body: Column(
          children: [
            Expanded(
              child: ArTryOnView(
                onCreated: (c) async {
                  controller = c;
                  await controller!.start();
                },
              ),
            ),
            Wrap(
              spacing: 8,
              children: [
                ElevatedButton(
                  onPressed: () =>
                      controller?.setEffectAsset('assets/glasses_01.png'),
                  child: const Text('Show PNG'),
                ),
                ElevatedButton(
                  onPressed: () => controller?.clearEffect(),
                  child: const Text('Clear PNG'),
                ),
                ElevatedButton(
                  onPressed: () => ArGlbPlacement.pickAndOpen(),
                  child: const Text('Place GLB'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
