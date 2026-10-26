import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';

/// Staggered entrance: fades in and slides up. Plays once when first built.
class FadeSlideIn extends StatefulWidget {
  const FadeSlideIn({
    super.key,
    required this.child,
    this.index = 0,
    this.delay = Duration.zero,
    this.step = const Duration(milliseconds: 55),
    this.duration = const Duration(milliseconds: 450),
    this.offset = const Offset(0, 0.12),
  });

  final Widget child;
  final int index;
  final Duration delay;
  final Duration step;
  final Duration duration;
  final Offset offset;

  @override
  State<FadeSlideIn> createState() => _FadeSlideInState();
}

class _FadeSlideInState extends State<FadeSlideIn>
    with SingleTickerProviderStateMixin {
  AnimationController? _c;
  Animation<double>? _a;
  bool _skip = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_c != null || _skip) return;
    if (context.noMotion) {
      _skip = true;
      return;
    }
    final lead = widget.delay + widget.step * math.min(widget.index, 10);
    final total = lead + widget.duration;
    final c = AnimationController(vsync: this, duration: total);
    _a = c.drive(CurveTween(
      curve: Interval(
        lead.inMicroseconds / total.inMicroseconds,
        1,
        curve: Curves.easeOutCubic,
      ),
    ));
    _c = c;
    c.forward();
  }

  @override
  void dispose() {
    _c?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final a = _a;
    if (_skip || a == null) return widget.child;
    return FadeTransition(
      opacity: a,
      child: SlideTransition(
        position: a.drive(Tween<Offset>(begin: widget.offset, end: Offset.zero)),
        child: widget.child,
      ),
    );
  }
}

/// Plays a damped bounce whenever [trigger] changes.
class Bounce extends StatefulWidget {
  const Bounce({
    super.key,
    required this.trigger,
    required this.child,
    this.amount = 0.18,
  });

  final Object? trigger;
  final Widget child;
  final double amount;

  @override
  State<Bounce> createState() => _BounceState();
}

class _BounceState extends State<Bounce> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 520),
  );

  @override
  void didUpdateWidget(Bounce old) {
    super.didUpdateWidget(old);
    if (old.trigger != widget.trigger && !context.noMotion) {
      _c.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      child: widget.child,
      builder: (context, child) {
        final v = _c.value;
        final s = 1 + widget.amount * math.sin(v * math.pi * 2.5) * (1 - v);
        return Transform.scale(scale: s, child: child);
      },
    );
  }
}
