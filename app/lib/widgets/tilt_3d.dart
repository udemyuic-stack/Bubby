import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';

import '../theme.dart';

/// Gives a flat picture depth: drag to turn it in perspective, it springs back, sways gently on its own and
/// swings when [wobble] changes. A soft highlight slides over it and a shadow moves under it.
/// With "reduce motion" on it stays still but can still be dragged.
class Tilt3D extends StatefulWidget {
  const Tilt3D({super.key, required this.child, this.wobble = 0, this.shadowWidth = 110});

  final Widget child;

  /// Change this number to make the picture swing once (for example after choosing an outfit).
  final int wobble;
  final double shadowWidth;

  @override
  State<Tilt3D> createState() => _Tilt3DState();
}

class _Tilt3DState extends State<Tilt3D> with TickerProviderStateMixin {
  static const _spring = SpringDescription(mass: 1, stiffness: 110, damping: 8);
  static const _maxY = 0.75;
  static const _maxX = 0.5;
  static const _perPixel = 0.011;

  late final AnimationController _x = AnimationController.unbounded(vsync: this);
  late final AnimationController _y = AnimationController.unbounded(vsync: this);
  late final AnimationController _idle = AnimationController(vsync: this, duration: const Duration(seconds: 7));
  bool _dragging = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (context.noMotion) {
      _idle.stop();
    } else if (!_idle.isAnimating) {
      _idle.repeat();
    }
  }

  @override
  void didUpdateWidget(Tilt3D old) {
    super.didUpdateWidget(old);
    if (old.wobble != widget.wobble && !context.noMotion && !_dragging) {
      _y.animateWith(SpringSimulation(_spring, _y.value, 0, 7));
    }
  }

  @override
  void dispose() {
    _x.dispose();
    _y.dispose();
    _idle.dispose();
    super.dispose();
  }

  void _start(DragStartDetails d) {
    _dragging = true;
    _x.stop();
    _y.stop();
  }

  void _update(DragUpdateDetails d) {
    _y.value = (_y.value + d.delta.dx * _perPixel).clamp(-_maxY, _maxY);
    _x.value = (_x.value - d.delta.dy * _perPixel).clamp(-_maxX, _maxX);
  }

  void _end(DragEndDetails d) {
    _dragging = false;
    final v = d.velocity.pixelsPerSecond;
    if (context.noMotion) {
      _x.value = 0;
      _y.value = 0;
      return;
    }
    _y.animateWith(SpringSimulation(_spring, _y.value, 0, (v.dx * _perPixel).clamp(-9.0, 9.0)));
    _x.animateWith(SpringSimulation(_spring, _x.value, 0, (-v.dy * _perPixel).clamp(-9.0, 9.0)));
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onPanStart: _start,
      onPanUpdate: _update,
      onPanEnd: _end,
      onPanCancel: () => _end(DragEndDetails()),
      child: RepaintBoundary(
        child: AnimatedBuilder(
          animation: Listenable.merge([_x, _y, _idle]),
          builder: (context, _) {
            final sway = _dragging ? 0.0 : math.sin(_idle.value * math.pi * 2);
            final ry = (_y.value + 0.16 * sway).clamp(-_maxY, _maxY);
            final rx = (_x.value + 0.05 * math.sin(_idle.value * math.pi * 4)).clamp(-_maxX, _maxX);
            final m = Matrix4.identity()
              ..setEntry(3, 2, 0.0016)
              ..rotateX(rx)
              ..rotateY(ry);
            return Stack(
              alignment: Alignment.center,
              children: [
                // contact shadow: slides against the turn and narrows when the picture leans away
                Align(
                  alignment: const Alignment(0, 0.92),
                  child: Transform.translate(
                    offset: Offset(-ry * 26, 0),
                    child: Container(
                      width: widget.shadowWidth * (1 - 0.18 * ry.abs()),
                      height: 12,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(12),
                        color: Colors.black.withAlpha(34),
                        boxShadow: [BoxShadow(color: Colors.black.withAlpha(34), blurRadius: 14, spreadRadius: 2)],
                      ),
                    ),
                  ),
                ),
                Transform(
                  alignment: Alignment.center,
                  transform: m,
                  child: ShaderMask(
                    blendMode: BlendMode.srcATop,
                    shaderCallback: (r) => RadialGradient(
                      center: Alignment((ry * 1.6).clamp(-1.0, 1.0), (-0.4 - rx * 1.6).clamp(-1.0, 1.0)),
                      radius: 0.75,
                      colors: [Colors.white.withAlpha(70), Colors.white.withAlpha(0)],
                    ).createShader(r),
                    child: widget.child,
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
