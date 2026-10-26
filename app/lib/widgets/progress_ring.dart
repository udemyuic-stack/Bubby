import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';

/// Circular progress with an animated sweep and an optional centre widget.
class ProgressRing extends StatelessWidget {
  const ProgressRing({
    super.key,
    required this.value,
    this.size = 56,
    this.strokeWidth = 6,
    this.color,
    this.child,
  });

  final double value;
  final double size;
  final double strokeWidth;
  final Color? color;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final v = value.clamp(0.0, 1.0);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: v),
      duration: context.noMotion ? Duration.zero : const Duration(milliseconds: 800),
      curve: Curves.easeOutCubic,
      builder: (context, a, child) => SizedBox(
        width: size,
        height: size,
        child: CustomPaint(
          painter: _RingPainter(
            value: a,
            stroke: strokeWidth,
            track: cs.onSurface.withAlpha(30),
            color: color ?? cs.primary,
          ),
          child: Center(child: child),
        ),
      ),
      child: child,
    );
  }
}

class _RingPainter extends CustomPainter {
  const _RingPainter({
    required this.value,
    required this.stroke,
    required this.track,
    required this.color,
  });

  final double value;
  final double stroke;
  final Color track;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final r = rect.deflate(stroke / 2);
    final p = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(r, 0, math.pi * 2, false, p..color = track);
    if (value > 0) {
      canvas.drawArc(r, -math.pi / 2, math.pi * 2 * value, false, p..color = color);
    }
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.value != value || old.color != color || old.track != track || old.stroke != stroke;
}
