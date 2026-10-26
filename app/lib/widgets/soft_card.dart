import 'package:flutter/material.dart';

import '../theme.dart';
import 'press_scale.dart';
import 'tap_ripple.dart';

/// Rounded elevated card with a subtle border and a tap ripple.
class SoftCard extends StatelessWidget {
  const SoftCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(Sp.l),
    this.margin = EdgeInsets.zero,
    this.color,
    this.gradient,
    this.borderColor,
    this.borderWidth = 1,
    this.onTap,
    this.onLongPress,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;
  final Color? color;
  final Gradient? gradient;
  final Color? borderColor;
  final double borderWidth;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final dark = context.isDark;
    final tappable = onTap != null || onLongPress != null;
    Widget card = DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: Rad.cardRadius,
        boxShadow: [
          BoxShadow(
            color: dark ? const Color(0x59000000) : const Color(0x1F7A6BC4),
            blurRadius: 24,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Material(
        color: gradient == null ? (color ?? cs.surfaceContainerLow) : Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: Rad.cardRadius,
          side: BorderSide(
            color: borderColor ?? cs.outlineVariant,
            width: borderWidth,
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: DecoratedBox(
          decoration: BoxDecoration(gradient: gradient),
          child: tappable
              ? TapRipple(
                  onTap: onTap,
                  onLongPress: onLongPress,
                  color: color == null ? null : cs.onSurface,
                  child: Padding(padding: padding, child: child),
                )
              : Padding(padding: padding, child: child),
        ),
      ),
    );
    if (tappable) card = PressScale(scale: 0.98, radius: Rad.cardRadius, child: card);
    return Padding(padding: margin, child: card);
  }
}
