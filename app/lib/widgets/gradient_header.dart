import 'package:flutter/material.dart';

import '../theme.dart';

/// Exposes a tab's slide progress (0 hidden .. 1 shown) so headers can
/// move their decoration at a different speed (parallax).
class TabParallax extends InheritedWidget {
  const TabParallax({
    super.key,
    required this.progress,
    required this.side,
    required super.child,
  });

  final Animation<double> progress;
  final double side;

  static TabParallax? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<TabParallax>();

  @override
  bool updateShouldNotify(TabParallax old) =>
      old.progress != progress || old.side != side;
}

/// Soft gradient banner used at the top of every tab screen.
class GradientHeader extends StatelessWidget {
  const GradientHeader({
    super.key,
    required this.title,
    this.subtitle,
    this.trailing,
    this.bottom,
  });

  final String title;
  final String? subtitle;
  final Widget? trailing;

  /// Full-width area under the title (not horizontally padded).
  final Widget? bottom;

  @override
  Widget build(BuildContext context) {
    final dark = context.isDark;
    final top = MediaQuery.paddingOf(context).top;
    final blob = Colors.white.withAlpha(dark ? 14 : 110);
    final par = TabParallax.maybeOf(context);
    return ValueListenableBuilder<DateTime>(
      valueListenable: hourNow,
      builder: (context, now, _) {
        final base = BuddyGradients.header(dark).colors;
        final tint = hourContainer(now, dark);
        final c0 = Color.lerp(base[0], tint, 0.4)!;
        final c1 = Color.lerp(base[1], hueShift(tint, 40), 0.4)!;
        return AnimatedBuilder(
          animation: par?.progress ?? const AlwaysStoppedAnimation<double>(1),
          builder: (context, child) {
            final p = par?.progress.value ?? 1;
            final dx = (1 - p) * (par?.side ?? 0) * 70;
            return Container(
              clipBehavior: Clip.antiAlias,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment(-1 + dx / 200, -1),
                  end: Alignment(1 + dx / 200, 1),
                  colors: [c0, c1],
                ),
                borderRadius: const BorderRadius.vertical(
                  bottom: Radius.circular(Rad.sheet),
                ),
              ),
              child: Stack(
                children: [
                  Positioned(
                    right: -34 - dx * 0.8,
                    top: -34,
                    child: Container(
                      width: 140,
                      height: 140,
                      decoration:
                          BoxDecoration(shape: BoxShape.circle, color: blob),
                    ),
                  ),
                  Positioned(
                    left: -26 + dx * 1.4,
                    bottom: -48,
                    child: Container(
                      width: 110,
                      height: 110,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: hourAccent(now).withAlpha(dark ? 36 : 60),
                      ),
                    ),
                  ),
                  child!,
                ],
              ),
            );
          },
          child: _content(context, top),
        );
      },
    );
  }

  Widget _content(BuildContext context, double top) {
    final cs = context.cs;
    final tt = context.tt;
    return Padding(
            padding: EdgeInsets.only(top: top + Sp.l, bottom: Sp.l),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              title,
                              style: tt.headlineMedium?.copyWith(color: cs.onSurface),
                            ),
                            if (subtitle != null) ...[
                              const SizedBox(height: 2),
                              Text(
                                subtitle!,
                                style: tt.bodyMedium?.copyWith(
                                  color: cs.onSurface.withAlpha(180),
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                      ?trailing,
                    ],
                  ),
                ),
                if (bottom != null) ...[
                  const SizedBox(height: Sp.m),
                  bottom!,
                ],
              ],
            ),
          );
  }
}
