import 'package:ar_tryon_view_example/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows the two supported feature controls', (tester) async {
    await tester.pumpWidget(const MyApp());

    expect(find.text('1. PNG face overlay'), findsOneWidget);
    expect(find.text('2. GLB floor placement'), findsOneWidget);
    expect(find.text('Show PNG'), findsOneWidget);
    expect(find.text('Choose .glb and place in AR'), findsOneWidget);
  });
}
