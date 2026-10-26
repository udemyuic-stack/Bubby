import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:buddy/l10n/l10n.dart';
import 'package:buddy/screens/agents_screen.dart';
import 'package:buddy/screens/ai_setup_screen.dart';
import 'package:buddy/screens/calendar_screen.dart';
import 'package:buddy/screens/fun_screen.dart';
import 'package:buddy/screens/home_screen.dart';
import 'package:buddy/screens/more_screen.dart';
import 'package:buddy/screens/music_screen.dart';
import 'package:buddy/screens/outfits_screen.dart';
import 'package:buddy/screens/settings_screen.dart';
import 'package:buddy/screens/setup_screen.dart';
import 'package:buddy/screens/tasks_screen.dart';
import 'package:buddy/theme.dart';
import 'package:buddy/widgets/widgets.dart';

const MethodChannel _channel = MethodChannel('buddy/native');

/// Fake native layer. Every scenario overrides only what it needs.
class Fake {
  Fake({
    this.running = true,
    this.keys = const [],
    this.tasks = 4,
    this.paired = true,
    this.perms = const {
      'overlay': true,
      'mic': true,
      'phone': false,
      'notifications': false,
      'calendar': true,
    },
    this.testOk = true,
    this.testMessage = '',
  });

  final bool running;
  final List<String> keys;
  final int tasks;
  final bool paired;
  final Map<String, bool> perms;
  final bool testOk;
  final String testMessage;

  /// User-authored / third-party data that is allowed to be Cyrillic in any UI language.
  static const userData = <String>[
    'Купить молоко',
    'Сдать отчёт',
    'Позвонить маме',
    'Записаться к врачу',
    'Встреча с командой',
    'Офис',
    'Задача',
    'Весёлый',
    'Рабочий ноутбук',
  ];

  Future<Object?> handle(MethodCall call) async {
    final now = DateTime.now();
    switch (call.method) {
      case 'getSettings':
        return <String, Object?>{
          'key': keys.isEmpty ? '' : keys.first,
          'keys': keys,
          'lang': 'auto',
          'running': running,
          'persona': 'Весёлый',
          'outfit': '',
          'lastCrash': '',
          'musicApp': 'spotify',
        };
      case 'permissionsStatus':
        return perms;
      case 'listTasks':
        return <Object?>[
          for (var i = 0; i < tasks; i++)
            <String, Object?>{
              'id': 't$i',
              'title': i < 4
                  ? userData[i]
                  : 'Очень длинная задача номер $i которую нужно сделать до конца недели обязательно',
              'notes': i % 2 == 0
                  ? 'Ещё хлеб и сыр, и ещё много всего другого'
                  : '',
              'dueMillis': i % 5 == 4
                  ? null
                  : now.add(Duration(hours: i * 7 - 20)).millisecondsSinceEpoch,
              'remind': i.isEven,
              'done': i % 4 == 3,
            },
        ];
      case 'listEvents':
        return <Object?>[
          for (var i = 0; i < 4; i++)
            <String, Object?>{
              'id': i + 1,
              'title': i == 3
                  ? 'Очень длинное название встречи для проверки переполнения строки'
                  : 'Встреча с командой',
              'startMillis': DateTime(
                now.year,
                now.month,
                now.day,
                9 + i * 2,
              ).millisecondsSinceEpoch,
              'endMillis': DateTime(
                now.year,
                now.month,
                now.day,
                10 + i * 2,
              ).millisecondsSinceEpoch,
              'location': i.isEven ? 'Офис' : '',
            },
        ];
      case 'batteryStatus':
        return <String, Object?>{'ignoring': false};
      case 'renderPreview':
      case 'renderSticker':
        return Uint8List(0);
      case 'agentStatus':
        return <String, Object?>{
          'paired': paired,
          'desktop': paired ? 'Рабочий ноутбук' : '',
          'connected': paired,
          'sessions': paired
              ? [
                  {
                    'pillId': 'p1',
                    'agent': 'claude',
                    'state': 'running',
                    'statusText': 'Editing a very long file name inside the repository src/main.dart',
                    'stepIndex': 2,
                    'stepCount': 5,
                  },
                  {'pillId': 'p2', 'agent': 'codex', 'state': 'idle'},
                ]
              : <Object?>[],
        };
      case 'musicApps':
        return <Object?>[
          {
            'id': 'spotify',
            'label': 'Spotify',
            'installed': true,
            'kind': 'streaming',
          },
          {'id': 'yt', 'label': 'YouTube', 'installed': true, 'kind': 'video'},
          {
            'id': 'local',
            'label': 'Local player',
            'installed': false,
            'kind': 'local',
          },
        ];
      case 'testAi':
        return <String, Object?>{'ok': testOk, 'message': testMessage};
      case 'listModels':
        return <String, Object?>{
          'ok': testOk,
          'models': ['llama-3.3-70b', 'gemma-2-9b'],
          'message': testMessage,
        };
      default:
        return null;
    }
  }
}

class Scenario {
  Scenario(this.name, this.build, [Fake? fake]) : fake = fake ?? Fake();
  final String name;
  final Widget Function() build;
  final Fake fake;
  // Stepper screen that pumps beyond the harness-supported tap check.
  bool checkTargets = true;
}

final RegExp _cyr = RegExp('[А-Яа-яЁё]');

/// Brand/documented allowlist (strings that stay Cyrillic by design).
bool _allowed(String s) =>
    Fake.userData.any(s.contains) ||
    s.startsWith('Очень длинн') ||
    s.startsWith('Ещё хлеб') ||
    s == 'Язык / Language / Til'; // intentionally trilingual label

List<String> _allTexts(WidgetTester tester) {
  final out = <String>[];
  for (final w in tester.widgetList<Text>(find.byType(Text))) {
    final d = w.data ?? w.textSpan?.toPlainText();
    if (d != null && d.isNotEmpty) out.add(d);
  }
  for (final w in tester.widgetList<RichText>(find.byType(RichText))) {
    final d = w.text.toPlainText();
    if (d.isNotEmpty) out.add(d);
  }
  return out;
}

Finder _targets() => find.byWidgetPredicate(
  (w) =>
      w is ButtonStyleButton ||
      w is IconButton ||
      w is ListTile ||
      w is Switch ||
      w is BuddyButton ||
      w is BuddyIconButton ||
      w is BuddySwitchTile ||
      w is FloatingActionButton,
);

Future<void> _pumpApp(
  WidgetTester tester,
  Widget screen, {
  required bool dark,
  required double scale,
}) async {
  await tester.pumpWidget(
    ValueListenableBuilder<String>(
      valueListenable: appLang,
      builder: (_, v, _) => MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: BuddyTheme.light,
        darkTheme: BuddyTheme.dark,
        themeMode: dark ? ThemeMode.dark : ThemeMode.light,
        builder: (ctx, child) => MediaQuery(
          data: MediaQuery.of(ctx).copyWith(
            textScaler: TextScaler.linear(scale),
            disableAnimations: true,
          ),
          child: child!,
        ),
        home: KeyedSubtree(
          key: ValueKey(v),
          child: Scaffold(body: screen),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
  await tester.pump(const Duration(milliseconds: 1500));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final keys3 = [
    'AIzaSyExampleKeyExampleKeyExample123',
    'https://api.groq.com/openai/v1|llama-3.3-70b-versatile|gsk_abcdefghijklmnop',
    'https://openrouter.ai/api/v1|google/gemma-3-27b-it:free|sk-or-abcdefghijkl',
  ];

  final scenarios = <Scenario>[
    Scenario(
      'home-onboarding',
      () => const HomeScreen(),
      Fake(running: false, perms: const {'overlay': false, 'mic': false}),
    ),
    Scenario(
      'home-running',
      () => const HomeScreen(),
      Fake(running: true, keys: keys3),
    ),
    Scenario('tasks-0', () => const TasksScreen(), Fake(tasks: 0)),
    Scenario('tasks-1', () => const TasksScreen(), Fake(tasks: 1)),
    Scenario('tasks-30', () => const TasksScreen(), Fake(tasks: 30)),
    Scenario('calendar', () => const CalendarScreen()),
    Scenario('outfits', () => const OutfitsScreen()),
    Scenario('fun', () => const FunScreen()),
    Scenario('music', () => const MusicScreen()),
    Scenario('agents-paired', () => const AgentsScreen(), Fake(paired: true)),
    Scenario(
      'agents-unpaired',
      () => const AgentsScreen(),
      Fake(paired: false),
    ),
    Scenario('ai-0', () => const AiSetupScreen()),
    Scenario('ai-3', () => const AiSetupScreen(), Fake(keys: keys3)),
    Scenario(
      'ai-3-errors',
      () => const AiSetupScreen(),
      Fake(
        keys: keys3,
        testOk: false,
        testMessage: '429 quota exceeded: rate limit',
      ),
    ),
    Scenario('more', () => const MoreScreen()),
    Scenario('permissions-list', () => const PermissionsListScreen()),
    Scenario('settings', () => const SettingsScreen(), Fake(keys: keys3)),
    Scenario('setup', () => const SetupScreen()),
  ];

  const langs = ['ru', 'uz', 'en'];
  const widths = [320.0, 411.0];
  const scales = [1.0, 1.5];

  for (final s in scenarios) {
    testWidgets('matrix: ${s.name}', (tester) async {
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        _channel,
        s.fake.handle,
      );
      addTearDown(() {
        tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          _channel,
          null,
        );
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
        appLang.value = 'auto';
      });
      final problems = <String>[];
      final prev = FlutterError.onError;
      String combo = '';
      FlutterError.onError = (d) {
        if (const bool.fromEnvironment('QA_VERBOSE')) {
          // ignore: avoid_print
          print('=== [$combo]\n${d.toString(minLevel: DiagnosticLevel.debug)}');
        }
        final msg = d.exceptionAsString().split('\n').first;
        final loc = RegExp(r'lib/[\w/]+\.dart:\d+')
            .allMatches(d.toString(minLevel: DiagnosticLevel.debug))
            .map((m) => m.group(0))
            .toSet()
            .take(2)
            .join(',');
        final cr =
            RegExp(r'creator: ([^\n]*)')
                .firstMatch(d.toString(minLevel: DiagnosticLevel.debug))
                ?.group(1) ??
            '';
        problems.add(
          '[$combo] ${msg.contains('overflowed') ? 'OVERFLOW' : 'EXCEPTION'}: $msg @ $loc ${cr.length > 160 ? cr.substring(0, 160) : cr}',
        );
      };
      var n = 0;
      try {
        for (final lang in langs) {
          for (final dark in [false, true]) {
            for (final scale in scales) {
              for (final w in widths) {
                combo =
                    '${s.name} $lang ${dark ? 'dark' : 'light'} x$scale ${w.toInt()}w';
                n++;
                appLang.value = lang;
                tester.view.devicePixelRatio = 2;
                tester.view.physicalSize = Size(w * 2, 800 * 2);
                await _pumpApp(tester, s.build(), dark: dark, scale: scale);
                final ex = tester.takeException();
                if (ex != null) problems.add('[$combo] takeException: $ex');

                if (lang != 'ru') {
                  for (final t in _allTexts(tester)) {
                    if (_cyr.hasMatch(t) && !_allowed(t)) {
                      problems.add(
                        '[$combo] CYRILLIC: "${t.replaceAll('\n', ' ')}"',
                      );
                    }
                  }
                }
                if (s.checkTargets) {
                  for (final e in _targets().evaluate()) {
                    final box = e.renderObject;
                    // A Switch inside a ListTile is operated through the whole tile.
                    var inTile = false;
                    if (e.widget is Switch) {
                      e.visitAncestorElements((a) {
                        if (a.widget is ListTile) inTile = true;
                        return !inTile;
                      });
                    }
                    if (!inTile && box is RenderBox && box.hasSize) {
                      final sz = box.size;
                      if (sz.width < 44 - 0.5 || sz.height < 44 - 0.5) {
                        problems.add(
                          '[$combo] SMALL TARGET ${e.widget.runtimeType} '
                          '${sz.width.toStringAsFixed(0)}x${sz.height.toStringAsFixed(0)}',
                        );
                      }
                    }
                  }
                }
              }
            }
          }
        }
      } finally {
        FlutterError.onError = prev;
      }
      // Group duplicates so the report stays readable.
      final uniq = <String, int>{};
      for (final p in problems) {
        final key = p.replaceFirst(RegExp(r'^\[[^\]]*\] '), '');
        uniq[key] = (uniq[key] ?? 0) + 1;
      }
      expect(n, 24);
      expect(
        uniq.isEmpty,
        isTrue,
        reason: uniq.entries
            .map(
              (e) =>
                  '${e.value}x ${e.key}\n  first: ${problems.firstWhere((p) => p.endsWith(e.key))}',
            )
            .join('\n'),
      );
    });
  }

  testWidgets('switching appLang at runtime rebuilds visible texts', (
    tester,
  ) async {
    final fake = Fake();
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _channel,
      fake.handle,
    );
    addTearDown(() {
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        _channel,
        null,
      );
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
      appLang.value = 'auto';
    });
    tester.view.devicePixelRatio = 2;
    tester.view.physicalSize = const Size(822, 1600);
    for (final make in <Widget Function()>[
      () => const HomeScreen(),
      () => const MoreScreen(),
      () => const TasksScreen(),
    ]) {
      appLang.value = 'ru';
      await _pumpApp(tester, make(), dark: false, scale: 1.0);
      final ru = _allTexts(tester).toSet();
      appLang.value = 'en';
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 1500));
      final en = _allTexts(tester).toSet();
      expect(en, isNot(equals(ru)));
      expect(en.where((t) => _cyr.hasMatch(t) && !_allowed(t)), isEmpty);
      appLang.value = 'uz';
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 1500));
      final uz = _allTexts(tester).toSet();
      expect(uz, isNot(equals(en)));
      expect(uz.where((t) => _cyr.hasMatch(t) && !_allowed(t)), isEmpty);
    }
  });
}
