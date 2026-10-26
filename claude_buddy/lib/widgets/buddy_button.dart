import 'package:flutter/material.dart';

import 'press_scale.dart';

enum BuddyButtonKind { filled, tonal, elevated, outline, text }

/// Button family with the Buddy press feel: spring scale, inner shadow,
/// light sweep (filled kinds), haptic tick and an optional loading state.
class BuddyButton extends StatelessWidget {
  const BuddyButton({
    super.key,
    required this.onPressed,
    required this.child,
    this.kind = BuddyButtonKind.filled,
    this.icon,
    this.loading = false,
    this.style,
    this.primary = false,
  });

  const BuddyButton.filled({
    super.key,
    required this.onPressed,
    required this.child,
    this.icon,
    this.loading = false,
    this.style,
    this.primary = false,
  }) : kind = BuddyButtonKind.filled;

  const BuddyButton.tonal({
    super.key,
    required this.onPressed,
    required this.child,
    this.icon,
    this.loading = false,
    this.style,
    this.primary = false,
  }) : kind = BuddyButtonKind.tonal;

  const BuddyButton.elevated({
    super.key,
    required this.onPressed,
    required this.child,
    this.icon,
    this.loading = false,
    this.style,
    this.primary = false,
  }) : kind = BuddyButtonKind.elevated;

  const BuddyButton.outline({
    super.key,
    required this.onPressed,
    required this.child,
    this.icon,
    this.loading = false,
    this.style,
    this.primary = false,
  }) : kind = BuddyButtonKind.outline;

  const BuddyButton.text({
    super.key,
    required this.onPressed,
    required this.child,
    this.icon,
    this.loading = false,
    this.style,
    this.primary = false,
  }) : kind = BuddyButtonKind.text;

  final VoidCallback? onPressed;
  final Widget child;
  final BuddyButtonKind kind;
  final Widget? icon;
  final bool loading;
  final ButtonStyle? style;

  /// Primary actions get a stronger haptic.
  final bool primary;

  @override
  Widget build(BuildContext context) {
    final cb = onPressed;
    final enabled = cb != null;
    final VoidCallback? tap = !enabled
        ? null
        : loading
            ? () {}
            : () {
                buddyHaptic(primary ? BuddyHaptic.light : BuddyHaptic.selection);
                cb();
              };
    final Widget? lead = loading
        ? const SizedBox(width: 18, height: 18, child: _Spinner())
        : icon;
    final Widget button;
    switch (kind) {
      case BuddyButtonKind.filled:
        button = lead == null
            ? FilledButton(onPressed: tap, style: style, child: child)
            : FilledButton.icon(
                onPressed: tap, style: style, icon: lead, label: child);
      case BuddyButtonKind.tonal:
        button = lead == null
            ? FilledButton.tonal(onPressed: tap, style: style, child: child)
            : FilledButton.tonalIcon(
                onPressed: tap, style: style, icon: lead, label: child);
      case BuddyButtonKind.elevated:
        button = lead == null
            ? ElevatedButton(onPressed: tap, style: style, child: child)
            : ElevatedButton.icon(
                onPressed: tap, style: style, icon: lead, label: child);
      case BuddyButtonKind.outline:
        button = lead == null
            ? OutlinedButton(onPressed: tap, style: style, child: child)
            : OutlinedButton.icon(
                onPressed: tap, style: style, icon: lead, label: child);
      case BuddyButtonKind.text:
        button = lead == null
            ? TextButton(onPressed: tap, style: style, child: child)
            : TextButton.icon(
                onPressed: tap, style: style, icon: lead, label: child);
    }
    return PressScale(
      enabled: enabled,
      shimmer: kind == BuddyButtonKind.filled ||
          kind == BuddyButtonKind.tonal ||
          kind == BuddyButtonKind.elevated,
      haptic: BuddyHaptic.none,
      child: button,
    );
  }
}

class _Spinner extends StatelessWidget {
  const _Spinner();

  @override
  Widget build(BuildContext context) {
    return CircularProgressIndicator(
      strokeWidth: 2.5,
      color: IconTheme.of(context).color,
    );
  }
}

/// Icon button with the Buddy press feel.
class BuddyIconButton extends StatelessWidget {
  const BuddyIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.color,
    this.tonal = false,
  });

  final Widget icon;
  final VoidCallback? onPressed;
  final String? tooltip;
  final Color? color;
  final bool tonal;

  @override
  Widget build(BuildContext context) {
    final cb = onPressed;
    final VoidCallback? tap = cb == null
        ? null
        : () {
            buddyHaptic(BuddyHaptic.selection);
            cb();
          };
    final button = tonal
        ? IconButton.filledTonal(
            onPressed: tap, tooltip: tooltip, color: color, icon: icon)
        : IconButton(onPressed: tap, tooltip: tooltip, color: color, icon: icon);
    return PressScale(
      enabled: cb != null,
      scale: 0.86,
      haptic: BuddyHaptic.none,
      child: button,
    );
  }
}

/// Extended floating action button with the Buddy press feel.
class BuddyFab extends StatelessWidget {
  const BuddyFab.extended({
    super.key,
    required this.onPressed,
    required this.icon,
    required this.label,
  });

  final VoidCallback onPressed;
  final Widget icon;
  final Widget label;

  @override
  Widget build(BuildContext context) {
    return PressScale(
      shimmer: true,
      haptic: BuddyHaptic.none,
      child: FloatingActionButton.extended(
        onPressed: () {
          buddyHaptic(BuddyHaptic.light);
          onPressed();
        },
        icon: icon,
        label: label,
      ),
    );
  }
}

/// Switch tile with the Buddy press feel and a haptic tick on toggle.
class BuddySwitchTile extends StatelessWidget {
  const BuddySwitchTile({
    super.key,
    required this.title,
    required this.value,
    required this.onChanged,
    this.subtitle,
    this.contentPadding,
  });

  final Widget title;
  final Widget? subtitle;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final EdgeInsetsGeometry? contentPadding;

  @override
  Widget build(BuildContext context) {
    final cb = onChanged;
    return PressScale(
      enabled: cb != null,
      scale: 0.985,
      haptic: BuddyHaptic.none,
      radius: const BorderRadius.all(Radius.circular(16)),
      child: SwitchListTile(
        contentPadding: contentPadding,
        title: title,
        subtitle: subtitle,
        value: value,
        onChanged: cb == null
            ? null
            : (v) {
                buddyHaptic(BuddyHaptic.selection);
                cb(v);
              },
      ),
    );
  }
}
