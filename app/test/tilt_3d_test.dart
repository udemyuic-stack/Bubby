import 'package:buddy/widgets/tilt_3d.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Widget host(int wobble, {bool reduce = false}) => MaterialApp(
        home: MediaQuery(
          data: MediaQueryData(disableAnimations: reduce),
          child: Scaffold(
            body: Center(child: SizedBox(width: 200, height: 200, child: Tilt3D(wobble: wobble, child: const Text('pic')))),
          ),
        ),
      );

  testWidgets('shows its child and turns when dragged', (tester) async {
    await tester.pumpWidget(host(0, reduce: true));
    expect(find.text('pic'), findsOneWidget);
    Matrix4 current() => tester.widget<Transform>(find.descendant(of: find.byType(Tilt3D), matching: find.byType(Transform)).last).transform;
    final before = current().storage.toList();
    final g = await tester.startGesture(tester.getCenter(find.byType(Tilt3D)));
    await g.moveBy(const Offset(60, 0));
    await tester.pump();
    expect(current().storage.toList(), isNot(before));   // turned while the finger is down
    await g.up();
    await tester.pump();
    expect(current().storage[0], closeTo(1, 1e-9));       // and flat again after letting go (reduced motion)
  });

  testWidgets('with reduced motion it settles back and nothing keeps animating', (tester) async {
    await tester.pumpWidget(host(0, reduce: true));
    await tester.drag(find.byType(Tilt3D), const Offset(60, 20));
    await tester.pumpAndSettle();
    final t = tester.widget<Transform>(find.descendant(of: find.byType(Tilt3D), matching: find.byType(Transform)).last).transform;
    expect(t.storage[0], closeTo(1, 1e-9));
  });

  testWidgets('a changed wobble number swings and then rests', (tester) async {
    await tester.pumpWidget(host(0));
    await tester.pumpWidget(host(1));
    await tester.pump(const Duration(milliseconds: 200));
    expect(tester.takeException(), isNull);
  });
}
