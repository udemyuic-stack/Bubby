import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme.dart';

/// One-shot confetti (<= 40 particles, 1.4 s). Once finished it removes
/// itself from the tree (renders nothing) and calls [onDone]. Does nothing
/// when motion is reduced. Place it in a Stack above the thing to celebrate.
class Confetti extends StatefulWidget {
  const Confetti({
    super.key,
    this.origin = const Alignment(0, -0.2),
    this.count = 36,
    this.spread = 1,
    this.onDone,
  });

  /// Where the burst starts inside the available box.
  final Alignment origin;
  final int count;

  /// Scale of the burst distance (1 = about 160 logical px).
  final double spread;
  final VoidCallback? onDone;

  /// Shows a burst centred on [context]'s widget in the root overlay and
  /// fires a light haptic. Removes itself afterwards.
  static void burst(BuildContext context, {bool haptic = true}) {
    if (haptic) HapticFeedback.lightImpact();
    if (context.noMotion) return;
    final box = context.findRenderObject();
    final overlay = Overlay.maybeOf(context, rootOverlay: true);
    if (box is! RenderBox || !box.attached || overlay == null) return;
    final c = box.localToGlobal(box.size.center(Offset.zero));
    late OverlayEntry entry;
    entry = OverlayEntry(
      builder: (ctx) {
        final size = MediaQuery.sizeOf(ctx);
        return Positioned.fill(
          child: Confetti(
            origin: Alignment(
              (c.dx / size.width) * 2 - 1,
              (c.dy / size.height) * 2 - 1,
            ),
            count: 30,
            onDone: () => entry.remove(),
          ),
        );
      },
    );
    overlay.insert(entry);
  }

  @override
  State<Confetti> createState() => _ConfettiState();
}

class _ConfettiState extends State<Confetti> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1400),
  )..addStatusListener((s) {
      if (s == AnimationStatus.completed && mounted) {
        setState(() => _gone = true);
        widget.onDone?.call();
      }
    });
  bool _gone = false;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (context.noMotion) {
      _gone = true;
      WidgetsBinding.instance.addPostFrameCallback((_) => widget.onDone?.call());
    } else {
      _c.forward();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_gone) return const SizedBox.shrink();
    return IgnorePointer(
      child: RepaintBoundary(
        child: CustomPaint(
          size: Size.infinite,
          painter: _ConfettiPainter(
            _c,
            widget.origin,
            math.min(widget.count, 40),
            widget.spread,
          ),
        ),
      ),
    );
  }
}

class _ConfettiPainter extends CustomPainter {
  _ConfettiPainter(this.t, this.origin, this.count, this.spread) : super(repaint: t);

  final Animation<double> t;
  final Alignment origin;
  final int count;
  final double spread;

  static const List<Color> _colors = [
    BuddyColors.orange,
    BuddyColors.rose,
    BuddyColors.violet,
    BuddyColors.teal,
    BuddyColors.sky,
    BuddyColors.warning,
  ];

  @override
  void paint(Canvas canvas, Size size) {
    final v = t.value;
    if (v <= 0 || v >= 1) return;
    final o = origin.alongSize(size);
    final secs = v * 1.4;
    final paint = Paint();
    for (var i = 0; i < count; i++) {
      // Deterministic pseudo-random spread so frames are stable.
      final h = math.sin(i * 12.9898) * 43758.5453;
      final r1 = h - h.floorToDouble();
      final h2 = math.sin(i * 78.233 + 1.3) * 12345.678;
      final r2 = h2 - h2.floorToDouble();
      final a = -math.pi / 2 + (r1 - 0.5) * math.pi * 1.5;
      final speed = (170 + 190 * r2) * spread;
      final dx = math.cos(a) * speed * secs * (1 - 0.35 * v);
      final dy = math.sin(a) * speed * secs + 420 * secs * secs * 0.5;
      final fade = v < 0.65 ? 1.0 : (1 - (v - 0.65) / 0.35);
      paint.color = _colors[i % _colors.length].withAlpha((255 * fade).round().clamp(0, 255));
      canvas.save();
      canvas.translate(o.dx + dx, o.dy + dy);
      canvas.rotate(r1 * 6 + v * (4 + r2 * 6));
      if (i % 3 == 0) {
        canvas.drawCircle(Offset.zero, 3, paint);
      } else {
        canvas.drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromCenter(center: Offset.zero, width: 8, height: 4.5),
            const Radius.circular(1.2),
          ),
          paint,
        );
      }
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(_ConfettiPainter old) => old.t != t;
}

/// Ring pulse + check mark that draws itself on. Plays once.
class SuccessPulse extends StatefulWidget {
  const SuccessPulse({super.key, this.size = 56, this.color, this.haptic = false});

  final double size;
  final Color? color;

  /// Fires a light haptic when the check lands.
  final bool haptic;

  @override
  State<SuccessPulse> createState() => _SuccessPulseState();
}

class _SuccessPulseState extends State<SuccessPulse> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  );
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (widget.haptic) HapticFeedback.lightImpact();
    if (context.noMotion) {
      _c.value = 1;
    } else {
      _c.forward();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = widget.color ?? BuddyColors.success;
    return SizedBox(
      width: widget.size,
      height: widget.size,
      child: RepaintBoundary(
        child: CustomPaint(painter: _PulsePainter(_c, color)),
      ),
    );
  }
}

class _PulsePainter extends CustomPainter {
  _PulsePainter(this.t, this.color) : super(repaint: t);

  final Animation<double> t;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final v = t.value;
    final c = size.center(Offset.zero);
    final r = size.shortestSide / 2;
    // Disc pops in.
    final pop = Curves.easeOutBack.transform((v / 0.35).clamp(0.0, 1.0));
    canvas.drawCircle(c, r * 0.72 * pop, Paint()..color = color);
    // Expanding ring (fades out).
    final rv = ((v - 0.15) / 0.85).clamp(0.0, 1.0);
    if (rv > 0 && rv < 1) {
      canvas.drawCircle(
        c,
        r * (0.72 + 0.28 * Curves.easeOut.transform(rv)),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3
          ..color = color.withAlpha((200 * (1 - rv)).round()),
      );
    }
    // Check mark draw-on.
    final cv = Curves.easeOutCubic.transform(((v - 0.3) / 0.45).clamp(0.0, 1.0));
    if (cv <= 0) return;
    final a = c + Offset(-r * 0.26, r * 0.02);
    final b = c + Offset(-r * 0.07, r * 0.22);
    final d = c + Offset(r * 0.30, -r * 0.2);
    final path = Path()..moveTo(a.dx, a.dy);
    final l1 = (b - a).distance, l2 = (d - b).distance;
    final len = (l1 + l2) * cv;
    if (len <= l1) {
      final p = a + (b - a) * (len / l1);
      path.lineTo(p.dx, p.dy);
    } else {
      final p = b + (d - b) * ((len - l1) / l2);
      path.lineTo(b.dx, b.dy);
      path.lineTo(p.dx, p.dy);
    }
    canvas.drawPath(
      path,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = r * 0.14
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..color = Colors.white,
    );
  }

  @override
  bool shouldRepaint(_PulsePainter old) => old.t != t || old.color != color;
}
