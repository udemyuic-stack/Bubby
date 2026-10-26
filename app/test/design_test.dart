import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:buddy/screens/home_screen.dart';
import 'package:buddy/screens/more_screen.dart';
import 'package:buddy/screens/settings_screen.dart';
import 'package:buddy/screens/tasks_screen.dart';
import 'package:buddy/theme.dart';

const MethodChannel _channel = MethodChannel('buddy/native');

Future<Object?> _fake(MethodCall call) async {
  final now = DateTime.now();
  switch (call.method) {
    case 'getSettings':
      return <String, Object?>{
        'key': 'AIzaSyExampleKey',
        'lang': 'ru',
        'tts': true,
        'conv': false,
        'study': true,
        'voice': true,
        'persona': 'Весёлый',
        'running': true,
        'outfit': '',
        'lastCrash': '',
      };
    case 'permissionsStatus':
      return <String, Object?>{
        'overlay': true,
        'mic': true,
        'phone': false,
        'notifications': false,
        'calendar': true,
      };
    case 'listTasks':
      return <Object?>[
        <String, Object?>{
          'id': 'a',
          'title': 'Купить молоко',
          'notes': 'Ещё хлеб и сыр',
          'dueMillis': now.add(const Duration(hours: 3)).millisecondsSinceEpoch,
          'remind': true,
          'done': false,
        },
        <String, Object?>{
          'id': 'b',
          'title': 'Сдать отчёт',
          'notes': '',
          'dueMillis': now
              .subtract(const Duration(days: 1))
              .millisecondsSinceEpoch,
          'remind': false,
          'done': false,
        },
        <String, Object?>{
          'id': 'c',
          'title': 'Позвонить маме',
          'notes': '',
          'dueMillis': now.add(const Duration(days: 2)).millisecondsSinceEpoch,
          'remind': true,
          'done': false,
        },
        <String, Object?>{
          'id': 'd',
          'title': 'Записаться к врачу',
          'notes': '',
          'dueMillis': null,
          'remind': false,
          'done': true,
        },
      ];
    case 'batteryStatus':
      return <String, Object?>{'ignoring': false};
    case 'renderSticker':
      return Uint8List(0);
    default:
      return null;
  }
}

Future<void> _golden(
  WidgetTester tester,
  Widget screen,
  Brightness brightness,
  String name,
) async {
  tester.view.physicalSize = const Size(780, 1688);
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    _channel,
    _fake,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _channel,
      null,
    ),
  );

  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: BuddyTheme.light,
      darkTheme: BuddyTheme.dark,
      themeMode: brightness == Brightness.dark
          ? ThemeMode.dark
          : ThemeMode.light,
      home: Scaffold(body: screen),
    ),
  );
  // Loops never settle, so advance the clock manually.
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
  await tester.pump(const Duration(milliseconds: 1500));

  await expectLater(
    find.byType(MaterialApp),
    matchesGoldenFile('goldens/$name.png'),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final screens = <String, Widget Function()>{
    'home': () => const HomeScreen(),
    'tasks': () => const TasksScreen(),
    'settings': () => const SettingsScreen(),
    'more': () => const MoreScreen(),
  };

  for (final e in screens.entries) {
    for (final b in [Brightness.light, Brightness.dark]) {
      final suffix = b == Brightness.dark ? 'dark' : 'light';
      testWidgets('${e.key} $suffix golden', (tester) async {
        await _golden(tester, e.value(), b, '${e.key}_$suffix');
      });
    }
  }
}
