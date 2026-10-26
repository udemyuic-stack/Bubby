import 'package:flutter/material.dart';

import '../theme.dart';

class SectionTitle extends StatelessWidget {
  const SectionTitle(
    this.text, {
    super.key,
    this.icon,
    this.trailing,
    this.padding = const EdgeInsets.fromLTRB(4, 0, 4, Sp.s),
  });

  final String text;
  final IconData? icon;
  final Widget? trailing;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Padding(
      padding: padding,
      child: Row(
        children: [
          Container(
            width: 4,
            height: 18,
            decoration: BoxDecoration(
              color: cs.primary,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: Sp.s),
          if (icon != null) ...[
            Icon(icon, size: 18, color: cs.onSurfaceVariant),
            const SizedBox(width: 6),
          ],
          Expanded(child: Text(text, style: context.tt.titleMedium)),
          ?trailing,
        ],
      ),
    );
  }
}
