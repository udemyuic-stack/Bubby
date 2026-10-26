import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'l10n/l10n.dart';
import 'native.dart';
import 'screens/calendar_screen.dart';
import 'screens/home_screen.dart';
import 'screens/more_screen.dart';
import 'screens/settings_screen.dart';
import 'screens/tasks_screen.dart';
import 'theme.dart';
import 'widgets/widgets.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    final s = await Native.getSettings();
    appLang.value = s.lang;
  } catch (_) {}
  runApp(const BuddyApp());
}

class BuddyApp extends StatelessWidget {
  const BuddyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<String>(
      valueListenable: appLang,
      builder: (_, v, _) => MaterialApp(
        title: 'Bubby',
        debugShowCheckedModeBanner: false,
        theme: BuddyTheme.light,
        darkTheme: BuddyTheme.dark,
        // Material has no Uzbek: framework widgets use English there, our own strings come from tr().
        locale: Locale(currentLang == 'uz' ? 'en' : currentLang),
        supportedLocales: const [Locale('en'), Locale('ru')],
        home: KeyedSubtree(key: ValueKey(v), child: const RootShell()),
      ),
    );
  }
}

class RootShell extends StatefulWidget {
  const RootShell({super.key});

  @override
  State<RootShell> createState() => _RootShellState();
}

class _RootShellState extends State<RootShell> {
  int _index = 0;
  int _prev = 0;
  Timer? _clock;
  bool? _splash;

  static const List<Widget> _pages = [
    HomeScreen(),
    TasksScreen(),
    CalendarScreen(),
    SettingsScreen(),
    MoreScreen(),
  ];

  @override
  void initState() {
    super.initState();
    hourNow.value = DateTime.now();
    _clock = Timer.periodic(const Duration(minutes: 1), (_) {
      hourNow.value = DateTime.now();
    });
  }

  @override
  void dispose() {
    _clock?.cancel();
    super.dispose();
  }

  void _select(int i) {
    if (i == _index) return;
    HapticFeedback.selectionClick();
    setState(() {
      _prev = _index;
      _index = i;
    });
  }

  @override
  Widget build(BuildContext context) {
    _splash ??= shouldShowLaunchSplash(context);
    return Stack(
      fit: StackFit.expand,
      children: [
        Scaffold(
          body: Stack(
            fit: StackFit.expand,
            children: [
              for (var i = 0; i < _pages.length; i++)
                _TabPage(
                  index: i,
                  current: _index,
                  previous: _prev,
                  child: _pages[i],
                ),
            ],
          ),
          bottomNavigationBar: _BubbyNavBar(index: _index, onSelect: _select),
        ),
        if (_splash == true)
          LaunchSplash(onDone: () {
            if (mounted) setState(() => _splash = false);
          }),
      ],
    );
  }
}

class _NavItem {
  const _NavItem(this.icon, this.selectedIcon, this.label);
  final IconData icon;
  final IconData selectedIcon;
  final String label;
}

List<_NavItem> get _navItems => [
  _NavItem(Icons.home_outlined, Icons.home_rounded, tr('Главная', en: 'Home', uz: 'Bosh sahifa')),
  _NavItem(Icons.check_circle_outline_rounded, Icons.check_circle_rounded,
      tr('Задачи', en: 'Tasks', uz: 'Vazifalar')),
  _NavItem(Icons.calendar_month_outlined, Icons.calendar_month_rounded,
      tr('Календарь', en: 'Calendar', uz: 'Kalendar')),
  _NavItem(Icons.settings_outlined, Icons.settings_rounded, tr('Настройки', en: 'Settings', uz: 'Sozlamalar')),
  _NavItem(Icons.apps_outlined, Icons.apps_rounded, tr('Ещё', en: 'More', uz: 'Yana')),
];

/// Bottom bar with a selection pill that slides and stretches between items
/// and takes its colour from the hour of the day.
class _BubbyNavBar extends StatefulWidget {
  const _BubbyNavBar({required this.index, required this.onSelect});

  final int index;
  final ValueChanged<int> onSelect;

  @override
  State<_BubbyNavBar> createState() => _BubbyNavBarState();
}

class _BubbyNavBarState extends State<_BubbyNavBar>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 460),
    value: 1,
  );
  int _from = 0;
  int _to = 0;

  @override
  void initState() {
    super.initState();
    _from = _to = widget.index;
  }

  @override
  void didUpdateWidget(_BubbyNavBar old) {
    super.didUpdateWidget(old);
    if (old.index != widget.index) {
      _from = old.index;
      _to = widget.index;
      if (context.noMotion) {
        _c.value = 1;
      } else {
        _c.forward(from: 0);
      }
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final dark = context.isDark;
    final bottom = MediaQuery.paddingOf(context).bottom;
    return ValueListenableBuilder<DateTime>(
      valueListenable: hourNow,
      builder: (context, now, _) {
        final pill = hourContainer(now, dark);
        final on = hourOnContainer(now, dark);
        return DecoratedBox(
          decoration: BoxDecoration(
            color: cs.surfaceContainerLow,
            border: Border(top: BorderSide(color: cs.outlineVariant)),
          ),
          child: Padding(
            padding: EdgeInsets.only(bottom: bottom),
            child: SizedBox(
              height: 72,
              child: LayoutBuilder(
                builder: (context, box) {
                  final w = box.maxWidth / _navItems.length;
                  return Stack(
                    children: [
                      AnimatedBuilder(
                        animation: _c,
                        builder: (context, _) {
                          final t = _c.value;
                          final right = _to >= _from;
                          // The leading edge moves first, the trailing edge
                          // follows: the pill stretches then settles.
                          final lead = Curves.easeOutCubic.transform(t);
                          final trail = Curves.easeInOutCubic
                              .transform(((t - 0.12) / 0.88).clamp(0.0, 1.0));
                          double l(int i) => w * i + w / 2 - 32;
                          double r(int i) => w * i + w / 2 + 32;
                          final left = right
                              ? l(_from) + (l(_to) - l(_from)) * trail
                              : l(_from) + (l(_to) - l(_from)) * lead;
                          final rgt = right
                              ? r(_from) + (r(_to) - r(_from)) * lead
                              : r(_from) + (r(_to) - r(_from)) * trail;
                          return Positioned(
                            left: left,
                            top: 12,
                            width: math.max(rgt - left, 40),
                            height: 32,
                            child: child_(pill),
                          );
                        },
                      ),
                      Row(
                        children: [
                          for (var i = 0; i < _navItems.length; i++)
                            Expanded(
                              child: _NavButton(
                                item: _navItems[i],
                                selected: i == widget.index,
                                selectedColor: on,
                                onTap: () => widget.onSelect(i),
                              ),
                            ),
                        ],
                      ),
                    ],
                  );
                },
              ),
            ),
          ),
        );
      },
    );
  }

  Widget child_(Color c) => AnimatedContainer(
        duration: const Duration(milliseconds: 900),
        decoration: BoxDecoration(color: c, borderRadius: Rad.pillRadius),
      );
}

class _NavButton extends StatelessWidget {
  const _NavButton({
    required this.item,
    required this.selected,
    required this.selectedColor,
    required this.onTap,
  });

  final _NavItem item;
  final bool selected;
  final Color selectedColor;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final color = selected ? selectedColor : cs.onSurfaceVariant;
    return Semantics(
      button: true,
      selected: selected,
      label: item.label,
      child: ExcludeSemantics(
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onTap,
          child: Column(
            children: [
              const SizedBox(height: 12),
              SizedBox(
                height: 32,
                child: Center(
                  child: _NavIcon(
                    selected ? item.selectedIcon : item.icon,
                    selected: selected,
                    color: color,
                  ),
                ),
              ),
              const SizedBox(height: 4),
              AnimatedDefaultTextStyle(
                duration: Dur.med,
                style: (context.tt.labelMedium ?? const TextStyle()).copyWith(
                  fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
                  color: selected ? cs.onSurface : cs.onSurfaceVariant,
                ),
                child: Text(item.label, maxLines: 1),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Icon that pops in with an elastic scale when it becomes the selected one.
class _NavIcon extends StatelessWidget {
  const _NavIcon(this.icon, {required this.selected, required this.color});

  final IconData icon;
  final bool selected;
  final Color color;

  @override
  Widget build(BuildContext context) {
    if (!selected || context.noMotion) return Icon(icon, size: 26, color: color);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: const Duration(milliseconds: 560),
      builder: (context, t, child) {
        final hop = -9 * math.sin(math.min(t * 2, 1) * math.pi) * (1 - t);
        final s = 1 + 0.3 * math.sin(t * math.pi * 2.5) * (1 - t);
        return Transform.translate(
          offset: Offset(0, hop),
          child: Transform.scale(scale: s, child: child),
        );
      },
      child: Icon(icon, size: 26, color: color),
    );
  }
}

/// One tab. Keeps its state while hidden and cross-fades / slides in and out.
class _TabPage extends StatefulWidget {
  const _TabPage({
    required this.index,
    required this.current,
    required this.previous,
    required this.child,
  });

  final int index;
  final int current;
  final int previous;
  final Widget child;

  @override
  State<_TabPage> createState() => _TabPageState();
}

class _TabPageState extends State<_TabPage>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 320),
    value: widget.index == widget.current ? 1 : 0,
  )..addStatusListener((_) {
      if (mounted) setState(() {});
    });
  double _side = 1;

  bool get _active => widget.index == widget.current;

  @override
  void didUpdateWidget(_TabPage old) {
    super.didUpdateWidget(old);
    final was = old.index == old.current;
    if (was == _active) return;
    if (_active) {
      _side = widget.index > widget.previous ? 1 : -1;
      if (context.noMotion) {
        _c.value = 1;
      } else {
        _c.forward();
      }
    } else {
      _side = widget.current > widget.index ? -1 : 1;
      if (context.noMotion) {
        _c.value = 0;
      } else {
        _c.reverse();
      }
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final curved = _c.drive(CurveTween(curve: Curves.easeInOutCubic));
    return Offstage(
      offstage: _c.isDismissed && !_active,
      child: TickerMode(
        enabled: _active || !_c.isDismissed,
        child: IgnorePointer(
          ignoring: !_active,
          child: RepaintBoundary(
            child: TabParallax(
              progress: curved,
              side: _side,
              child: FadeTransition(
                opacity: _c.drive(
                  CurveTween(curve: const Interval(0.1, 1, curve: Curves.easeOut)),
                ),
                child: SlideTransition(
                  position: curved.drive(
                    Tween<Offset>(
                      begin: Offset(_side * 0.22, 0),
                      end: Offset.zero,
                    ),
                  ),
                  child: ScaleTransition(
                    scale: curved.drive(Tween<double>(begin: 0.97, end: 1)),
                    child: widget.child,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
