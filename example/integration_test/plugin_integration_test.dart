


import 'package:flutter/material.dart';
import 'package:ar_tryon_view/ar_tryon_view.dart';

void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(home: Demo());
  }
}

class Demo extends StatefulWidget {
  const Demo({super.key});

  @override
  State<Demo> createState() => _DemoState();
}

class _DemoState extends State<Demo> {
  ArTryOnController? c;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('AR Try-On Plugin Demo')),
      body: Column(
        children: [
          Expanded(
            child: ArTryOnView(
              onCreated: (controller) async {
                c = controller;
                await c!.start();
              },
            ),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              ElevatedButton(
                onPressed: () => c?.start(),
                child: const Text('Start'),
              ),
              ElevatedButton(
                onPressed: () => c?.stop(),
                child: const Text('Stop'),
              ),
              ElevatedButton(
                onPressed: () => c?.setEffect('glasses_01'),
                child: const Text('Effect'),
              ),
            ],
          ),
          const SizedBox(height: 12),
        ],
      ),
    );
  }
}
