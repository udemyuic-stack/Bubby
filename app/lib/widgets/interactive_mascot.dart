import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/l10n.dart';
import '../mascot.dart';
import '../theme.dart';
import 'motion.dart';

enum _R { none, bounce, wink, laugh, heart, spin, purr }

/// The home hero mascot: reacts to touch and has a quiet idle life.
///
/// tap: random cute reaction; press and hold: pupils follow the finger;
/// double tap: spin and get dizzy; long press: purr with floating hearts.
/// Idle blinks / look-arounds / yawns are driven by one-shot timers (no
/// permanent 60 fps loop); the 60 fps controller only runs during a reaction.
/// Everything stops when the route is off-screen, the app is not resumed or
/// the system asks for reduced motion.
class InteractiveMascot extends StatefulWidget {
  const InteractiveMascot({
    super.key,
    required this.mood,
    this.size = 190,
    this.species = 'cat',
  });

  final MascotMood mood;
  final double size;
  final String species;

  @override
  State<InteractiveMascot> createState() => _InteractiveMascotState();
}

class _InteractiveMascotState extends State<InteractiveMascot>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  late final AnimationController _r = AnimationController(vsync: this);
  final math.Random _rng = math.Random();
  _R _reaction = _R.none;
  Offset? _look;
  MascotMood? _idleMood;
  bool _holding = false;
  bool _resumed = true;
  Timer? _holdTimer;
  Timer? _lookTimer;
  Timer? _yawnTimer;
  Timer? _clearTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _scheduleLook();
    _scheduleYawn();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _resumed = state == AppLifecycleState.resumed;
    if (!_resumed) _r.stop();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _holdTimer?.cancel();
    _lookTimer?.cancel();
    _yawnTimer?.cancel();
    _clearTimer?.cancel();
    _r.dispose();
    super.dispose();
  }

  bool get _canIdle =>
      mounted &&
      _resumed &&
      TickerMode.valuesOf(context).enabled &&
      !context.noMotion &&
      _reaction == _R.none &&
      !_holding;

  void _scheduleLook() {
    _lookTimer?.cancel();
    _lookTimer = Timer(Duration(milliseconds: 4000 + _rng.nextInt(5000)), () {
      if (_canIdle) {
        setState(() => _look = Offset(_rng.nextDouble() * 2 - 1, _rng.nextDouble() * 1.2 - 0.6));
        _clearTimer?.cancel();
        _clearTimer = Timer(const Duration(milliseconds: 1300), () {
          if (mounted && !_holding) setState(() => _look = null);
        });
      }
      if (mounted) _scheduleLook();
    });
  }

  void _scheduleYawn() {
    _yawnTimer?.cancel();
    _yawnTimer = Timer(Duration(milliseconds: 12000 + _rng.nextInt(13000)), () {
      if (_canIdle) {
        setState(() => _idleMood = MascotMood.yawn);
        Timer(const Duration(milliseconds: 1700), () {
          if (mounted) setState(() => _idleMood = null);
        });
      }
      if (mounted) _scheduleYawn();
    });
  }

  void _play(_R r, int ms) {
    if (context.noMotion) {
      HapticFeedback.lightImpact();
      return;
    }
    setState(() {
      _reaction = r;
      _look = null;
      _idleMood = null;
    });
    _r.duration = Duration(milliseconds: ms);
    _r.forward(from: 0).whenComplete(() {
      if (mounted && _reaction == r && r != _R.purr) setState(() => _reaction = _R.none);
    });
  }

  void _onTap() {
    if (_reaction == _R.purr) return;
    HapticFeedback.selectionClick();
    const picks = [_R.bounce, _R.wink, _R.laugh, _R.heart];
    _play(picks[_rng.nextInt(picks.length)], 1100);
  }

  void _onDouble() {
    HapticFeedback.lightImpact();
    _play(_R.spin, 2200);
  }

  void _onLongPress() {
    HapticFeedback.lightImpact();
    _holdTimer?.cancel();
    _holding = false;
    if (context.noMotion) return;
    setState(() {
      _reaction = _R.purr;
      _look = null;
    });
    _r.duration = const Duration(milliseconds: 1800);
    _r.repeat();
  }

  void _endPurr() {
    if (_reaction != _R.purr) return;
    _r.stop();
    setState(() => _reaction = _R.none);
  }

  Offset _toLook(Offset local) {
    final h = widget.size / 2;
    return Offset(
      ((local.dx - h) / h * 1.3).clamp(-1.0, 1.0),
      ((local.dy - h) / h * 1.3).clamp(-1.0, 1.0),
    );
  }

  void _down(PointerDownEvent e) {
    final p = e.localPosition;
    _holdTimer?.cancel();
    _holdTimer = Timer(const Duration(milliseconds: 180), () {
      if (!mounted || _reaction != _R.none) return;
      _holding = true;
      setState(() => _look = _toLook(p));
    });
  }

  void _move(PointerMoveEvent e) {
    if (_holding) setState(() => _look = _toLook(e.localPosition));
  }

  void _up(PointerEvent e) {
    _holdTimer?.cancel();
    if (_holding) {
      _holding = false;
      setState(() => _look = null);
    }
  }

  MascotMood _moodFor(double v) {
    switch (_reaction) {
      case _R.wink:
        return v > 0.1 && v < 0.85 ? MascotMood.wink : widget.mood;
      case _R.laugh:
        return v < 0.92 ? MascotMood.laugh : widget.mood;
      case _R.heart:
        return v < 0.85 ? MascotMood.love : widget.mood;
      case _R.bounce:
        return MascotMood.happy;
      case _R.spin:
        return v > 0.33 ? MascotMood.dizzy : MascotMood.excited;
      case _R.purr:
        return MascotMood.pet;
      case _R.none:
        return _idleMood ?? widget.mood;
    }
  }

  @override
  Widget build(BuildContext context) {
    final size = widget.size;
    return Semantics(
      button: true,
      label: tr('Bubby — нажмите, чтобы поиграть', en: 'Bubby — tap to play', uz: 'Bubby — oʻynash uchun bosing'),
      child: ExcludeSemantics(
        child: Listener(
          onPointerDown: _down,
          onPointerMove: _move,
          onPointerUp: _up,
          onPointerCancel: _up,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: _onTap,
            onDoubleTap: _onDouble,
            onLongPress: _onLongPress,
            onLongPressEnd: (_) => _endPurr(),
            onLongPressCancel: _endPurr,
            child: SizedBox(
              width: size,
              height: size,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  AnimatedBuilder(
                    animation: _r,
                    builder: (context, _) {
                      final v = _reaction == _R.none ? 0.0 : _r.value;
                      return Transform(
                        alignment: Alignment.bottomCenter,
                        transform: _matrix(v, size),
                        child: Bounce(
                          trigger: widget.mood,
                          child: Mascot(
                            mood: _moodFor(v),
                            size: size,
                            species: widget.species,
                            lookAt: _look,
                          ),
                        ),
                      );
                    },
                  ),
                  if (_reaction == _R.heart || _reaction == _R.purr)
                    Positioned.fill(
                      child: IgnorePointer(
                        child: RepaintBoundary(
                          child: CustomPaint(
                            painter: _HeartsPainter(_r, _reaction == _R.purr),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Matrix4 _matrix(double v, double size) {
    var dx = 0.0, dy = 0.0, rot = 0.0, sc = 1.0;
    switch (_reaction) {
      case _R.none:
        break;
      case _R.bounce:
        dy = -(math.sin(v * math.pi * 3)).abs() * (1 - v) * size * 0.14;
        sc = 1 + 0.04 * math.sin(v * math.pi * 6) * (1 - v);
      case _R.wink:
        sc = 1 + 0.07 * math.sin(v * math.pi);
        rot = 0.08 * math.sin(v * math.pi);
      case _R.laugh:
        dx = math.sin(v * 44) * size * 0.012 * (1 - v);
        dy = -(math.sin(v * math.pi * 5)).abs() * (1 - v) * size * 0.04;
      case _R.heart:
        dy = -math.sin(v * math.pi) * size * 0.05;
        sc = 1 + 0.05 * math.sin(v * math.pi);
      case _R.spin:
        if (v < 0.4) {
          rot = Curves.easeInOutCubic.transform(v / 0.4) * math.pi * 2;
          dy = -math.sin(v / 0.4 * math.pi) * size * 0.08;
        } else {
          final k = (v - 0.4) / 0.6;
          rot = math.sin(k * 20) * 0.1 * (1 - k);
          dx = math.sin(k * 14) * size * 0.015 * (1 - k);
        }
      case _R.purr:
        dx = math.sin(v * math.pi * 2 * 16) * size * 0.007;
        sc = 1 + 0.02 * math.sin(v * math.pi * 2 * 2);
    }
    return Matrix4.identity()
      ..translateByDouble(dx, dy, 0, 1)
      ..rotateZ(rot)
      ..scaleByDouble(sc, sc, 1, 1);
  }
}

class _HeartsPainter extends CustomPainter {
  _HeartsPainter(this.t, this.loop) : super(repaint: t);

  final Animation<double> t;
  final bool loop;

  void _heart(Canvas canvas, Offset c, double r, Color col) {
    final p = Path()
      ..moveTo(c.dx, c.dy + r)
      ..cubicTo(c.dx - r * 1.8, c.dy - r * 0.2, c.dx - r * 0.8, c.dy - r * 1.5, c.dx, c.dy - r * 0.6)
      ..cubicTo(c.dx + r * 0.8, c.dy - r * 1.5, c.dx + r * 1.8, c.dy - r * 0.2, c.dx, c.dy + r);
    canvas.drawPath(p, Paint()..color = col);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final n = loop ? 5 : 3;
    for (var i = 0; i < n; i++) {
      final life = loop ? ((t.value * 1.0 + i / n) % 1.0) : ((t.value - i * 0.12) / 0.7).clamp(0.0, 1.0);
      if (life <= 0 || life >= 1) continue;
      final x = size.width * (0.2 + 0.6 * ((i * 0.37 + 0.1) % 1)) + math.sin(life * 6 + i) * 8;
      final y = size.height * (0.55 - life * 0.55);
      final a = math.sin(life * math.pi);
      _heart(canvas, Offset(x, y), 6 + (i % 3) * 2.5,
          const Color(0xFFE5365B).withAlpha((a * 230).round()));
    }
  }

  @override
  bool shouldRepaint(_HeartsPainter old) => old.t != t || old.loop != loop;
}
