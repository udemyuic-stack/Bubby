import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:buddy/l10n/l10n.dart';
import 'package:buddy/screens/home_screen.dart';
import 'package:buddy/screens/more_screen.dart';
import 'package:buddy/screens/setup_screen.dart';
import 'package:buddy/theme.dart';

const MethodChannel _channel = MethodChannel('buddy/native');

Future<Object?> _fake(MethodCall call) async {
  switch (call.method) {
    case 'getSettings':
      return <String, Object?>{
        'key': 'AIzaSyExampleKey',
        'lang': 'ru',
        'running': true,
        'lastCrash': '',
      };
    case 'permissionsStatus':
      return <String, Object?>{'overlay': true, 'mic': true, 'phone': false};
    case 'batteryStatus':
      return <String, Object?>{'ignoring': false};
    default:
      return null;
  }
}

final RegExp _cyr = RegExp('[А-Яа-яЁё]');

Future<List<String>> _texts(
  WidgetTester tester,
  String lang,
  Widget screen,
) async {
  appLang.value = lang;
  await tester.pumpWidget(
    MaterialApp(
      theme: BuddyTheme.light,
      home: Scaffold(
        body: KeyedSubtree(key: ValueKey(lang), child: screen),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
  await tester.pump(const Duration(milliseconds: 1500));
  final out = <String>[];
  for (final w in tester.widgetList<Text>(find.byType(Text))) {
    final d = w.data ?? w.textSpan?.toPlainText();
    if (d != null) out.add(d);
  }
  return out;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('screens are fully translated', (tester) async {
    tester.view.physicalSize = const Size(780, 3000);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _channel,
      _fake,
    );
    addTearDown(() {
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        _channel,
        null,
      );
      appLang.value = 'auto';
    });

    final screens = <Widget Function()>[
      () => const HomeScreen(),
      () => const MoreScreen(),
      () => const PermissionsListScreen(),
    ];
    for (final make in screens) {
      final ru = await _texts(tester, 'ru', make());
      final uz = await _texts(tester, 'uz', make());
      final en = await _texts(tester, 'en', make());
      expect(ru.any(_cyr.hasMatch), isTrue);
      expect(ru, isNot(equals(uz)));
      expect(ru, isNot(equals(en)));
      expect(uz, isNot(equals(en)));
      expect(en.where(_cyr.hasMatch), isEmpty);
      expect(uz.where(_cyr.hasMatch), isEmpty);
    }
  });
}
