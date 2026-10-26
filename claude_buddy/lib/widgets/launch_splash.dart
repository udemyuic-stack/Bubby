import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../mascot.dart';
import '../theme.dart';

/// Marks the Home hero mascot so the splash can fly into it.
final GlobalKey kHeroMascotKey = GlobalKey(debugLabel: 'heroMascot');

/// The splash is shown once per process, in release builds only (debug hot
/// restarts would replay it every time); `--dart-define=BUBBY_SPLASH=true`
/// forces it on.
const bool _splashEnabled =
    bool.fromEnvironment('BUBBY_SPLASH', defaultValue: !kDebugMode);
bool _splashShown = false;

bool shouldShowLaunchSplash(BuildContext context) {
  if (!_splashEnabled || _splashShown || context.noMotion) return false;
  _splashShown = true;
  return true;
}

/// In-app launch: the mascot sleeps (eyes shut, floating "z"), opens its
/// eyes, hops and waves; then the cream backdrop fades away while the mascot
/// glides into the Home hero position (shared-element style).
class LaunchSplash extends StatefulWidget {
  const LaunchSplash({super.key, required this.onDone});

  final VoidCallback onDone;

  @override
  State<LaunchSplash> createState() => _LaunchSplashState();
}

class _LaunchSplashState extends State<LaunchSplash> with SingleTickerProviderStateMixin {
  static const double _size = 190;
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1600),
  )..addStatusListener((s) {
      if (s == AnimationStatus.completed) widget.onDone();
    });
  Offset? _target; // hero mascot centre, global

  @override
  void initState() {
    super.initState();
    _c.forward();
    _c.addListener(_findTarget);
  }

  void _findTarget() {
    // Resolve the hero position once the exit phase starts.
    if (_target != null || _c.value < 0.62) return;
    final box = kHeroMascotKey.currentContext?.findRenderObject();
    if (box is RenderBox && box.attached && box.hasSize) {
      _target = box.localToGlobal(box.size.center(Offset.zero));
    } else {
      _target = const Offset(-1, -1);
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bg = Theme.of(context).scaffoldBackgroundColor;
    final screen = MediaQuery.sizeOf(context);
    final home = Offset(screen.width / 2, screen.height / 2);
    return IgnorePointer(
      ignoring: _c.value > 0.75,
      child: AnimatedBuilder(
        animation: _c,
        builder: (context, _) {
          final t = _c.value;
          // Phases: 0-.28 asleep, .28-.4 wake, .4-.62 hop/wave, .62-1 exit.
          final asleep = t < 0.28;
          final mood = t < 0.28
              ? MascotMood.sleepy
              : (t < 0.62 ? MascotMood.excited : MascotMood.happy);
          final exit = Curves.easeInOutCubic.transform(((t - 0.62) / 0.38).clamp(0.0, 1.0));
          final tgt = (_target == null || _target!.dx < 0) ? home : _target!;
          final pos = Offset.lerp(home, tgt, exit)!;
          final hop = t > 0.36 && t < 0.62
              ? -math.sin((t - 0.36) / 0.26 * math.pi * 2).abs() * 26
              : 0.0;
          final wake = Curves.easeOutBack.transform(((t - 0.26) / 0.14).clamp(0.0, 1.0));
          final scale = 0.9 + 0.1 * wake;
          final wave = t > 0.4 && t < 0.62 ? math.sin((t - 0.4) * 60) * 0.09 : 0.0;
          return Stack(
            children: [
              Positioned.fill(
                child: Opacity(
                  opacity: (1 - exit).clamp(0.0, 1.0),
                  child: ColoredBox(color: bg),
                ),
              ),
              Positioned(
                left: pos.dx - _size / 2,
                top: pos.dy - _size / 2 + hop,
                width: _size,
                height: _size,
                child: Opacity(
                  opacity: t > 0.97 ? 0 : 1,
                  child: Transform.rotate(
                    angle: wave,
                    child: Transform.scale(
                      scale: scale,
                      child: Stack(
                        clipBehavior: Clip.none,
                        children: [
                          Mascot(mood: mood, size: _size, eyesShut: asleep),
                          if (asleep) _Zzz(t: t / 0.28),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _Zzz extends StatelessWidget {
  const _Zzz({required this.t});

  final double t;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Positioned(
      right: 10,
      top: 8 - 10 * t,
      child: Opacity(
        opacity: (math.sin(t * math.pi)).clamp(0.0, 1.0),
        child: Text(
          'z',
          style: TextStyle(
            fontSize: 20 + 8 * t,
            fontWeight: FontWeight.w800,
            color: cs.primary,
          ),
        ),
      ),
    );
  }
}
