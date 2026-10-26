import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../theme.dart';

/// Placeholder cards with a moving highlight (plain gradient sweep, no
/// ShaderMask). One controller, one painter; static when motion is reduced
/// and paused automatically when off-screen (TickerMode).
class SkeletonList extends StatefulWidget {
  const SkeletonList({super.key, this.count = 4, this.cardHeight = 76});

  final int count;
  final double cardHeight;

  @override
  State<SkeletonList> createState() => _SkeletonListState();
}

class _SkeletonListState extends State<SkeletonList> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1400),
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
    final cs = context.cs;
    return Semantics(
      label: tr('Загрузка…', en: 'Loading…', uz: 'Yuklanmoqda…'),
      child: ExcludeSemantics(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(Sp.l, Sp.m, Sp.l, 0),
          child: RepaintBoundary(
            child: CustomPaint(
              size: Size.infinite,
              painter: _SkeletonPainter(
                _c,
                cs.surfaceContainerHigh,
                cs.surface,
                widget.count,
                widget.cardHeight,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SkeletonPainter extends CustomPainter {
  _SkeletonPainter(this.t, this.base, this.hi, this.count, this.h) : super(repaint: t);

  final Animation<double> t;
  final Color base;
  final Color hi;
  final int count;
  final double h;

  @override
  void paint(Canvas canvas, Size size) {
    final sweep = -0.4 + t.value * 1.8; // highlight centre as fraction of width
    final w = size.width;
    for (var i = 0; i < count; i++) {
      final top = i * (h + Sp.m);
      if (top > size.height) break;
      final rect = Rect.fromLTWH(0, top, w, h);
      final rr = RRect.fromRectAndRadius(rect, const Radius.circular(20));
      canvas.drawRRect(
        rr,
        Paint()
          ..shader = LinearGradient(
            colors: [base, Color.lerp(base, hi, 0.85)!, base],
            stops: [
              (sweep - 0.25).clamp(0.0, 1.0),
              sweep.clamp(0.0, 1.0),
              (sweep + 0.25).clamp(0.0, 1.0),
            ],
          ).createShader(rect),
      );
      // Fake text lines.
      final line = Paint()..color = hi.withAlpha(150);
      canvas.drawRRect(
          RRect.fromRectAndRadius(
              Rect.fromLTWH(16, top + h * 0.3, w * 0.5, 10), const Radius.circular(5)),
          line);
      canvas.drawRRect(
          RRect.fromRectAndRadius(
              Rect.fromLTWH(16, top + h * 0.3 + 20, w * 0.3, 8), const Radius.circular(4)),
          line);
    }
  }

  @override
  bool shouldRepaint(_SkeletonPainter old) =>
      old.t != t || old.base != base || old.hi != hi || old.count != count;
}

/// Three bouncing dots: the mascot's "thinking" indicator.
class ThinkingDots extends StatefulWidget {
  const ThinkingDots({super.key, this.color, this.size = 8});

  final Color? color;
  final double size;

  @override
  State<ThinkingDots> createState() => _ThinkingDotsState();
}

class _ThinkingDotsState extends State<ThinkingDots> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1200),
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
    final color = widget.color ?? context.cs.primary;
    final s = widget.size;
    return Semantics(
      label: tr('Загрузка…', en: 'Loading…', uz: 'Yuklanmoqda…'),
      child: ExcludeSemantics(
        child: SizedBox(
          width: s * 5,
          height: s * 2.4,
          child: RepaintBoundary(
            child: CustomPaint(painter: _DotsPainter(_c, color, s)),
          ),
        ),
      ),
    );
  }
}

class _DotsPainter extends CustomPainter {
  _DotsPainter(this.t, this.color, this.s) : super(repaint: t);

  final Animation<double> t;
  final Color color;
  final double s;

  @override
  void paint(Canvas canvas, Size size) {
    for (var i = 0; i < 3; i++) {
      final k = ((t.value * 1.0 - i * 0.14) % 1.0);
      final up = k < 0.5 ? Curves.easeOut.transform(k * 2) : 1 - Curves.easeIn.transform((k - 0.5) * 2);
      canvas.drawCircle(
        Offset(size.width / 2 + (i - 1) * s * 1.7, size.height * 0.62 - up * s * 0.9),
        s * 0.5,
        Paint()..color = color.withAlpha((140 + 115 * up).round()),
      );
    }
  }

  @override
  bool shouldRepaint(_DotsPainter old) => old.t != t || old.color != color || old.s != s;
}
