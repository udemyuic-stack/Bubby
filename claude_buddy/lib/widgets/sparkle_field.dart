import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';

/// Floating sparkles and bubbles with an optional pulsing glow in the middle.
/// Draws a single static frame when motion is reduced.
class SparkleField extends StatefulWidget {
  const SparkleField({super.key, this.active = false});

  /// When true the central glow pulses (the mascot is "running").
  final bool active;

  @override
  State<SparkleField> createState() => _SparkleFieldState();
}

class _SparkleFieldState extends State<SparkleField>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 12),
    value: 0.3,
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (context.noMotion) {
      if (_c.isAnimating) _c.stop();
    } else if (!_c.isAnimating) {
      _c.repeat();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final dark = context.isDark;
    return IgnorePointer(
      child: RepaintBoundary(
        child: CustomPaint(
          painter: _SparklePainter(
            anim: _c,
            dark: dark,
            active: widget.active,
          ),
          size: Size.infinite,
        ),
      ),
    );
  }
}

class _SparklePainter extends CustomPainter {
  _SparklePainter({
    required this.anim,
    required this.dark,
    required this.active,
  }) : super(repaint: anim);

  final Animation<double> anim;
  final bool dark;
  final bool active;

  static double _frac(double v) => v - v.floorToDouble();

  void _star(Canvas canvas, Offset c, double r, Paint paint) {
    final path = Path()
      ..moveTo(c.dx, c.dy - r)
      ..quadraticBezierTo(c.dx, c.dy, c.dx + r, c.dy)
      ..quadraticBezierTo(c.dx, c.dy, c.dx, c.dy + r)
      ..quadraticBezierTo(c.dx, c.dy, c.dx - r, c.dy)
      ..quadraticBezierTo(c.dx, c.dy, c.dx, c.dy - r)
      ..close();
    canvas.drawPath(path, paint);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final t = anim.value;
    final w = size.width;
    final h = size.height;
    if (w <= 0 || h <= 0) return;

    // Central glow.
    final pulse = active ? 0.5 + 0.5 * math.sin(t * 2 * math.pi * 6) : 0.0;
    final glowR = math.min(w, h) * (0.46 + 0.10 * pulse);
    final glowColor = active ? BuddyColors.orange : BuddyColors.rose;
    final glowA = active ? 0.34 + 0.22 * pulse : 0.16;
    final center = Offset(w / 2, h * 0.46);
    canvas.drawCircle(
      center,
      glowR,
      Paint()
        ..shader = RadialGradient(
          colors: [
            glowColor.withAlpha((glowA * 255).round()),
            glowColor.withAlpha(0),
          ],
        ).createShader(Rect.fromCircle(center: center, radius: glowR)),
    );

    final sparkColors = dark
        ? const [Color(0xFFFFD9A8), Color(0xFFB4A9F0), Color(0xFFF5A0BF)]
        : const [Color(0xFFFFFFFF), Color(0xFFF7B267), Color(0xFFEE7BA3)];
    final bubbleColor = dark ? const Color(0xFFB4A9F0) : const Color(0xFF7A6BC4);

    // Bubbles rise and loop seamlessly (integer cycles per 12 s).
    for (var i = 0; i < 9; i++) {
      final cycles = 1 + i % 3;
      final life = _frac(i * 0.37 + t * cycles);
      final x = _frac(i * 0.73 + 0.11) + math.sin((life + i) * 2 * math.pi * 2) * 0.02;
      final y = 1.02 - life * 1.1;
      final r = 4.0 + (i % 4) * 3.0;
      final a = math.sin(life * math.pi);
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.6
        ..color = bubbleColor.withAlpha((a * 120).round());
      canvas.drawCircle(Offset(x * w, y * h), r, paint);
      canvas.drawCircle(
        Offset(x * w - r * 0.3, y * h - r * 0.3),
        r * 0.18,
        Paint()..color = bubbleColor.withAlpha((a * 140).round()),
      );
    }

    // Twinkling sparkles.
    for (var i = 0; i < 14; i++) {
      final tw = 2 + i % 3;
      final phase = i * 0.37;
      final k = 0.5 + 0.5 * math.sin((t * tw + phase) * 2 * math.pi);
      final x = _frac(i * 0.618034 + 0.07) + math.sin((t * 2 + phase) * 2 * math.pi) * 0.012;
      final y = 0.08 + _frac(i * 0.381966 + 0.2) * 0.78;
      final r = (3.5 + (i % 4) * 2.4) * (0.55 + 0.6 * k);
      final c = sparkColors[i % sparkColors.length];
      _star(canvas, Offset(x * w, y * h), r, Paint()..color = c.withAlpha((90 + 150 * k).round()));
    }
  }

  @override
  bool shouldRepaint(_SparklePainter old) =>
      old.dark != dark || old.active != active || old.anim != anim;
}
