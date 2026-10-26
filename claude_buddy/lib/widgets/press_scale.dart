import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';
import 'package:flutter/services.dart';

import '../theme.dart';

/// Haptic strength used by the press system.
enum BuddyHaptic { none, selection, light }

/// Fires a haptic tick. Haptics stay on even when animations are reduced.
void buddyHaptic(BuddyHaptic h) {
  switch (h) {
    case BuddyHaptic.none:
      break;
    case BuddyHaptic.selection:
      unawaited(HapticFeedback.selectionClick());
    case BuddyHaptic.light:
      unawaited(HapticFeedback.lightImpact());
  }
}

/// Press feedback: quick shrink with a soft inner shadow on pointer down, a
/// springy overshoot on release, an optional light sweep and pulse ring.
/// Uses raw pointer events for the visuals, so it never swallows taps of
/// nested widgets (buttons, switches, ...).
class PressScale extends StatefulWidget {
  const PressScale({
    super.key,
    required this.child,
    this.onTap,
    this.onLongPress,
    this.scale = 0.94,
    this.enabled = true,
    this.haptic,
    this.shimmer = false,
    this.ring = false,
    this.ringColor,
    this.radius = Rad.pillRadius,
  });

  final Widget child;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final double scale;
  final bool enabled;

  /// Defaults to a selection tick when [onTap] is set, otherwise none.
  final BuddyHaptic? haptic;

  /// Diagonal light sweep over the child after a tap (for filled surfaces).
  final bool shimmer;

  /// Expanding pulse ring on press.
  final bool ring;
  final Color? ringColor;

  /// Shape used by the inner shadow, sweep and ring.
  final BorderRadius radius;

  @override
  State<PressScale> createState() => _PressScaleState();
}

class _PressScaleState extends State<PressScale> with TickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    lowerBound: 0.7,
    upperBound: 1.3,
    value: 1,
  );
  late final AnimationController _s = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 560),
  );
  late final AnimationController _r = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 650),
  );
  bool _down = false;
  Offset _origin = Offset.zero;

  static final SpringDescription _spring =
      SpringDescription.withDampingRatio(mass: 1, stiffness: 520, ratio: 0.55);

  void _press() {
    if (_down || !widget.enabled) return;
    _down = true;
    if (context.noMotion) return;
    _c.animateTo(
      widget.scale,
      duration: const Duration(milliseconds: 150),
      curve: Curves.easeOut,
    );
    if (widget.ring) _r.forward(from: 0);
  }

  void _release({bool tapped = false}) {
    if (!_down) return;
    _down = false;
    if (context.noMotion) {
      _c.value = 1;
      return;
    }
    _c.animateWith(SpringSimulation(_spring, _c.value, 1, _c.velocity + 1.5));
    if (tapped && widget.shimmer) _s.forward(from: 0);
  }

  void _tap() {
    final cb = widget.onTap;
    if (cb == null || !widget.enabled) return;
    buddyHaptic(widget.haptic ?? BuddyHaptic.selection);
    cb();
  }

  void _long() {
    final cb = widget.onLongPress;
    if (cb == null || !widget.enabled) return;
    cb();
  }

  @override
  void dispose() {
    _c.dispose();
    _s.dispose();
    _r.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    Widget body = widget.child;
    if (!context.noMotion) {
      body = AnimatedBuilder(
        animation: Listenable.merge([_c, _s, _r]),
        child: widget.child,
        builder: (context, child) {
          final span = 1 - widget.scale;
          final dip = span <= 0
              ? 0.0
              : ((1 - _c.value) / span).clamp(0.0, 1.0).toDouble();
          return Transform.scale(
            scale: _c.value,
            child: Stack(
              fit: StackFit.passthrough,
              clipBehavior: Clip.none,
              children: [
                child!,
                if (dip > 0.003)
                  Positioned.fill(
                    child: IgnorePointer(
                      child: CustomPaint(
                        painter: _DipPainter(dip, widget.radius),
                      ),
                    ),
                  ),
                if (_s.isAnimating)
                  Positioned.fill(
                    child: IgnorePointer(
                      child: CustomPaint(
                        painter: _SweepPainter(_s.value, widget.radius),
                      ),
                    ),
                  ),
                if (_r.isAnimating)
                  Positioned.fill(
                    child: IgnorePointer(
                      child: CustomPaint(
                        painter: _RingPainter(
                          _r.value,
                          widget.radius,
                          widget.ringColor ?? context.cs.primary,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      );
    }
    final hasTap = widget.onTap != null || widget.onLongPress != null;
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (e) {
        _origin = e.position;
        _press();
      },
      onPointerMove: (e) {
        if ((e.position - _origin).distance > 20) _release();
      },
      onPointerUp: (_) => _release(tapped: true),
      onPointerCancel: (_) => _release(),
      child: hasTap
          ? GestureDetector(
              behavior: HitTestBehavior.translucent,
              onTap: widget.onTap == null ? null : _tap,
              onLongPress: widget.onLongPress == null ? null : _long,
              child: body,
            )
          : body,
    );
  }
}

class _DipPainter extends CustomPainter {
  _DipPainter(this.t, this.radius);

  final double t;
  final BorderRadius radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rr = radius.toRRect(Offset.zero & size);
    canvas.drawRRect(
      rr,
      Paint()..color = Color.fromRGBO(0, 0, 0, 0.07 * t),
    );
    canvas.save();
    canvas.clipRRect(rr);
    canvas.drawRRect(
      rr,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 12
        ..color = Color.fromRGBO(0, 0, 0, 0.16 * t)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 7),
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(_DipPainter old) => old.t != t || old.radius != radius;
}

class _SweepPainter extends CustomPainter {
  _SweepPainter(this.t, this.radius);

  final double t;
  final BorderRadius radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rr = radius.toRRect(Offset.zero & size);
    final band = size.width * 0.6;
    final dx = -band + (size.width + band) * Curves.easeInOut.transform(t);
    final a = math.sin(t * math.pi);
    final white = Color.fromRGBO(255, 255, 255, 0.42 * a);
    final clear = Color.fromRGBO(255, 255, 255, 0);
    canvas.save();
    canvas.clipRRect(rr);
    canvas.drawRect(
      Offset.zero & size,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [clear, white, clear],
          stops: const [0, 0.5, 1],
        ).createShader(Rect.fromLTWH(dx, 0, band, size.height)),
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(_SweepPainter old) => old.t != t || old.radius != radius;
}

class _RingPainter extends CustomPainter {
  _RingPainter(this.t, this.radius, this.color);

  final double t;
  final BorderRadius radius;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final e = Curves.easeOutCubic.transform(t);
    final rr = radius.toRRect(Offset.zero & size).inflate(2 + 16 * e);
    canvas.drawRRect(
      rr,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3.5 * (1 - e) + 0.8
        ..color = color.withAlpha((150 * (1 - e)).round().clamp(0, 255)),
    );
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.t != t || old.radius != radius || old.color != color;
}
