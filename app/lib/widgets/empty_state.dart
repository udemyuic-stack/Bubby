import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../mascot.dart';
import '../theme.dart';
import 'motion.dart';

/// Friendly empty placeholder with a little mascot.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.title,
    this.message,
    this.mood = MascotMood.sleepy,
    this.action,
    this.size = 120,
    this.bubble,
  });

  /// Optional speech bubble above the floating mascot.
  final String? bubble;

  final String title;
  final String? message;
  final MascotMood mood;
  final Widget? action;
  final double size;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(Sp.xxl),
        child: FadeSlideIn(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (bubble != null) ...[
                _SpeechBubble(bubble!),
                const SizedBox(height: Sp.s),
              ],
              Container(
                width: size * 1.4,
                height: size * 1.4,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: RadialGradient(
                    colors: [cs.primary.withAlpha(60), cs.primary.withAlpha(0)],
                  ),
                ),
                child: _Floating(child: Mascot(mood: mood, size: size)),
              ),
              const SizedBox(height: Sp.l),
              Text(title, textAlign: TextAlign.center, style: context.tt.titleLarge),
              if (message != null) ...[
                const SizedBox(height: Sp.s),
                Text(
                  message!,
                  textAlign: TextAlign.center,
                  style: context.tt.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
                ),
              ],
              if (action != null) ...[
                const SizedBox(height: Sp.l),
                action!,
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Gentle up-and-down float; the controller stops off-screen / reduced motion.
class _Floating extends StatefulWidget {
  const _Floating({required this.child});

  final Widget child;

  @override
  State<_Floating> createState() => _FloatingState();
}

class _FloatingState extends State<_Floating> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 2800),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (context.noMotion) {
      if (_c.isAnimating) _c.stop();
    } else if (!_c.isAnimating) {
      _c.repeat(reverse: true);
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
      builder: (context, child) => Transform.translate(
        offset: Offset(0, -6 * math.sin(Curves.easeInOut.transform(_c.value) * math.pi - math.pi / 2)),
        child: child,
      ),
    );
  }
}

class _SpeechBubble extends StatelessWidget {
  const _SpeechBubble(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          constraints: const BoxConstraints(maxWidth: 260),
          padding: const EdgeInsets.symmetric(horizontal: Sp.l, vertical: Sp.m),
          decoration: BoxDecoration(
            color: cs.secondaryContainer,
            borderRadius: BorderRadius.circular(20),
          ),
          child: Text(
            text,
            textAlign: TextAlign.center,
            style: context.tt.bodyMedium?.copyWith(
              color: cs.onSecondaryContainer,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        CustomPaint(
          size: const Size(18, 9),
          painter: _TailPainter(cs.secondaryContainer),
        ),
      ],
    );
  }
}

class _TailPainter extends CustomPainter {
  const _TailPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawPath(
      Path()
        ..moveTo(0, 0)
        ..lineTo(size.width, 0)
        ..lineTo(size.width / 2, size.height)
        ..close(),
      Paint()..color = color,
    );
  }

  @override
  bool shouldRepaint(_TailPainter old) => old.color != color;
}
