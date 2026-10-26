import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';
import 'press_scale.dart';

/// Lightweight tap highlight: a faint radial glow that expands from the touch
/// point and fades. Replaces the Material ink splash on big surfaces.
class TapRipple extends StatefulWidget {
  const TapRipple({
    super.key,
    required this.child,
    this.onTap,
    this.onLongPress,
    this.color,
    this.haptic = BuddyHaptic.selection,
  });

  final Widget child;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final Color? color;
  final BuddyHaptic haptic;

  @override
  State<TapRipple> createState() => _TapRippleState();
}

class _TapRippleState extends State<TapRipple>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 520),
  );
  Offset _origin = Offset.zero;

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = widget.color ?? context.cs.primary;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (d) {
        _origin = d.localPosition;
        if (!context.noMotion) _c.forward(from: 0);
      },
      onTap: widget.onTap == null
          ? null
          : () {
              buddyHaptic(widget.haptic);
              widget.onTap!();
            },
      onLongPress: widget.onLongPress,
      child: Stack(
        fit: StackFit.passthrough,
        children: [
          widget.child,
          Positioned.fill(
            child: IgnorePointer(
              child: AnimatedBuilder(
                animation: _c,
                builder: (context, _) => _c.isAnimating
                    ? CustomPaint(
                        painter: _RipplePainter(_c.value, _origin, color),
                      )
                    : const SizedBox.shrink(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _RipplePainter extends CustomPainter {
  _RipplePainter(this.t, this.origin, this.color);

  final double t;
  final Offset origin;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final far = math.max(
      math.max(origin.distance, (origin - Offset(size.width, 0)).distance),
      math.max(
        (origin - Offset(0, size.height)).distance,
        (origin - Offset(size.width, size.height)).distance,
      ),
    );
    final r = far * 1.1 * Curves.easeOutCubic.transform(t);
    final a = (46 * (1 - Curves.easeIn.transform(t))).round().clamp(0, 255);
    if (r <= 0 || a == 0) return;
    canvas.drawCircle(
      origin,
      r,
      Paint()
        ..shader = RadialGradient(
          colors: [color.withAlpha(a), color.withAlpha((a * 0.35).round()), color.withAlpha(0)],
          stops: const [0, 0.7, 1],
        ).createShader(Rect.fromCircle(center: origin, radius: r)),
    );
  }

  @override
  bool shouldRepaint(_RipplePainter old) =>
      old.t != t || old.origin != origin || old.color != color;
}
