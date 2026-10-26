import 'package:flutter/material.dart';

import '../theme.dart';

class StatusPill extends StatelessWidget {
  const StatusPill({
    super.key,
    required this.label,
    required this.color,
    this.icon,
    this.dot = false,
    this.pulse = false,
    this.dense = false,
  });

  final String label;
  final Color color;
  final IconData? icon;
  final bool dot;
  final bool pulse;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final fg = Color.lerp(color, cs.onSurface, 0.4) ?? color;
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: dense ? 8 : 12,
        vertical: dense ? 3 : 6,
      ),
      decoration: BoxDecoration(
        color: color.withAlpha(36),
        borderRadius: Rad.pillRadius,
        border: Border.all(color: color.withAlpha(96)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (dot) ...[
            _PulseDot(color: color, pulse: pulse),
            const SizedBox(width: 8),
          ],
          if (icon != null) ...[
            Icon(icon, size: dense ? 12 : 15, color: fg),
            const SizedBox(width: 5),
          ],
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: (dense ? context.tt.labelSmall : context.tt.labelMedium)
                  ?.copyWith(color: fg, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }
}

class _PulseDot extends StatefulWidget {
  const _PulseDot({required this.color, required this.pulse});

  final Color color;
  final bool pulse;

  @override
  State<_PulseDot> createState() => _PulseDotState();
}

class _PulseDotState extends State<_PulseDot>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1400),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(_PulseDot old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    if (widget.pulse && !context.noMotion) {
      if (!_c.isAnimating) _c.repeat();
    } else {
      if (_c.isAnimating) _c.stop();
      _c.value = 0;
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 14,
      height: 14,
      child: AnimatedBuilder(
        animation: _c,
        builder: (context, _) {
          final v = _c.value;
          return Stack(
            alignment: Alignment.center,
            children: [
              if (widget.pulse)
                Transform.scale(
                  scale: 1 + v * 0.9,
                  child: Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: widget.color.withAlpha(((1 - v) * 110).round()),
                    ),
                  ),
                ),
              Container(
                width: 9,
                height: 9,
                decoration: BoxDecoration(shape: BoxShape.circle, color: widget.color),
              ),
            ],
          );
        },
      ),
    );
  }
}
