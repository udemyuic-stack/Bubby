import 'package:flutter/material.dart';

/// Spacing scale.
class Sp {
  Sp._();
  static const double xs = 4;
  static const double s = 8;
  static const double m = 12;
  static const double l = 16;
  static const double xl = 24;
  static const double xxl = 32;
}

/// Corner radii.
class Rad {
  Rad._();
  static const double card = 24;
  static const double sheet = 32;
  static const double field = 18;
  static const double pill = 999;
  static const BorderRadius cardRadius = BorderRadius.all(Radius.circular(card));
  static const BorderRadius pillRadius = BorderRadius.all(Radius.circular(pill));
}

/// Animation durations.
class Dur {
  Dur._();
  static const Duration fast = Duration(milliseconds: 150);
  static const Duration med = Duration(milliseconds: 280);
  static const Duration slow = Duration(milliseconds: 500);
}

/// Brand palette derived from the mascot.
class BuddyColors {
  BuddyColors._();
  static const Color orange = Color(0xFFF0883A);
  static const Color cream = Color(0xFFFFE6C8);
  static const Color ink = Color(0xFF1B1530);
  static const Color violet = Color(0xFF7A6BC4);
  static const Color rose = Color(0xFFEE7BA3);
  static const Color teal = Color(0xFF3FB8A5);
  static const Color sky = Color(0xFF5B9BF0);
  static const Color success = Color(0xFF3DA66B);
  static const Color warning = Color(0xFFF2B01E);

  static const List<Color> accents = [orange, violet, rose, teal, sky];
}

class BuddyGradients {
  BuddyGradients._();

  static const LinearGradient brand = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFFF7A24F), BuddyColors.orange, BuddyColors.rose],
  );

  static const LinearGradient violet = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF8D7FD6), Color(0xFF5A4BA6)],
  );

  static LinearGradient header(bool dark) => LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: dark
            ? const [Color(0xFF2C2252), Color(0xFF4A2748)]
            : const [Color(0xFFFFE6C8), Color(0xFFFFD0DF)],
      );
}

extension BuddyContext on BuildContext {
  ColorScheme get cs => Theme.of(this).colorScheme;
  TextTheme get tt => Theme.of(this).textTheme;
  bool get isDark => Theme.of(this).brightness == Brightness.dark;

  /// True when the user asked the system to reduce motion.
  bool get noMotion => MediaQuery.maybeOf(this)?.disableAnimations ?? false;
}

// ---------------------------------------------------------------------------
// Hour-of-day accent: the whole app drifts through 24 hues during the day.
// ---------------------------------------------------------------------------

const List<double> _hourHues = <double>[
  262, 258, 252, 248, 270, 305, 340, 12, 26, 34, 46, 62, //
  100, 150, 178, 195, 210, 228, 275, 310, 332, 318, 296, 276,
];

/// Global clock for time-of-day colours. Updated once a minute by the app shell.
final ValueNotifier<DateTime> hourNow = ValueNotifier<DateTime>(DateTime.now());

/// Smoothly interpolated hue (degrees) for the given time.
double hourHue(DateTime t) {
  final f = t.hour + t.minute / 60 + t.second / 3600;
  final i = f.floor() % 24;
  final j = (i + 1) % 24;
  var k = f - f.floor();
  k = k * k * (3 - 2 * k);
  final a = _hourHues[i];
  final b = _hourHues[j];
  final d = ((b - a + 540) % 360) - 180;
  return (a + d * k + 360) % 360;
}

/// Main accent for the hour (HSV 0.65 / 0.95).
Color hourAccent(DateTime t) =>
    HSVColor.fromAHSV(1, hourHue(t), 0.65, 0.95).toColor();

/// Pale variant, good for light backgrounds.
Color hourAccentLight(DateTime t) =>
    HSVColor.fromAHSV(1, hourHue(t), 0.28, 1).toColor();

/// Deep variant, good for dark backgrounds and strong text.
Color hourAccentDeep(DateTime t) =>
    HSVColor.fromAHSV(1, hourHue(t), 0.75, 0.55).toColor();

/// Container tone (nav pill, chips).
Color hourContainer(DateTime t, bool dark) => dark
    ? HSVColor.fromAHSV(1, hourHue(t), 0.55, 0.40).toColor()
    : HSVColor.fromAHSV(1, hourHue(t), 0.26, 1).toColor();

/// Content colour on [hourContainer].
Color hourOnContainer(DateTime t, bool dark) => dark
    ? HSVColor.fromAHSV(1, hourHue(t), 0.15, 1).toColor()
    : HSVColor.fromAHSV(1, hourHue(t), 0.80, 0.34).toColor();

/// Rotates the hue of [c] by [deg] degrees.
Color hueShift(Color c, double deg) {
  final h = HSVColor.fromColor(c);
  return h.withHue((h.hue + deg + 360) % 360).toColor();
}

/// Gradient for primary buttons that follows the hour.
LinearGradient hourGradient(DateTime t) {
  final a = hourAccent(t);
  return LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [
      Color.lerp(a, Colors.white, 0.28)!,
      a,
      hueShift(a, 32),
    ],
  );
}

/// Route used for every push: shared-axis + fade-through, 380 ms.
/// Based on [MaterialPageRoute] so back gestures keep working.
class BubbyRoute<T> extends MaterialPageRoute<T> {
  BubbyRoute({
    required super.builder,
    super.settings,
    super.fullscreenDialog,
    super.maintainState,
  });

  @override
  Duration get transitionDuration => const Duration(milliseconds: 380);

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 380);
}

/// Fade-through + shared-axis with a slight scale and delayed content fade.
class BuddyPageTransitionsBuilder extends PageTransitionsBuilder {
  const BuddyPageTransitionsBuilder();

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    if (context.noMotion) return child;
    return _BubbyTransition(
      animation: animation,
      secondaryAnimation: secondaryAnimation,
      child: child,
    );
  }
}

class _BubbyTransition extends StatefulWidget {
  const _BubbyTransition({
    required this.animation,
    required this.secondaryAnimation,
    required this.child,
  });

  final Animation<double> animation;
  final Animation<double> secondaryAnimation;
  final Widget child;

  @override
  State<_BubbyTransition> createState() => _BubbyTransitionState();
}

class _BubbyTransitionState extends State<_BubbyTransition> {
  late CurvedAnimation _enter;
  late CurvedAnimation _exit;

  void _make() {
    _enter = CurvedAnimation(
      parent: widget.animation,
      curve: Curves.easeOutCubic,
      reverseCurve: Curves.easeInOutCubic,
    );
    _exit = CurvedAnimation(
      parent: widget.secondaryAnimation,
      curve: Curves.easeOutCubic,
      reverseCurve: Curves.easeInOutCubic,
    );
  }

  @override
  void initState() {
    super.initState();
    _make();
  }

  @override
  void didUpdateWidget(_BubbyTransition old) {
    super.didUpdateWidget(old);
    if (old.animation != widget.animation ||
        old.secondaryAnimation != widget.secondaryAnimation) {
      _enter.dispose();
      _exit.dispose();
      _make();
    }
  }

  @override
  void dispose() {
    _enter.dispose();
    _exit.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final fadeIn = widget.animation.drive(
      CurveTween(curve: const Interval(0, 0.7, curve: Curves.easeOut)),
    );
    final contentIn = widget.animation.drive(
      CurveTween(curve: const Interval(0.25, 1, curve: Curves.easeOut)),
    );
    final fadeOut = widget.secondaryAnimation.drive(
      Tween<double>(begin: 1, end: 0).chain(
        CurveTween(curve: const Interval(0, 0.6, curve: Curves.easeIn)),
      ),
    );
    return FadeTransition(
      opacity: fadeOut,
      child: SlideTransition(
        position: _exit.drive(
          Tween<Offset>(begin: Offset.zero, end: const Offset(-0.07, 0)),
        ),
        child: ScaleTransition(
          scale: _exit.drive(Tween<double>(begin: 1, end: 0.97)),
          child: FadeTransition(
            opacity: fadeIn,
            child: ColoredBox(
              color: Theme.of(context).colorScheme.surface,
              child: SlideTransition(
                position: _enter.drive(
                  Tween<Offset>(begin: const Offset(0.1, 0), end: Offset.zero),
                ),
                child: ScaleTransition(
                  scale: _enter.drive(Tween<double>(begin: 0.97, end: 1)),
                  child: FadeTransition(opacity: contentIn, child: widget.child),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

const ColorScheme _lightScheme = ColorScheme(
  brightness: Brightness.light,
  primary: BuddyColors.orange,
  onPrimary: BuddyColors.ink,
  primaryContainer: Color(0xFFFFE6C8),
  onPrimaryContainer: Color(0xFF4A2300),
  secondary: BuddyColors.violet,
  onSecondary: Colors.white,
  secondaryContainer: Color(0xFFE7E2FA),
  onSecondaryContainer: Color(0xFF2A2159),
  tertiary: BuddyColors.rose,
  onTertiary: BuddyColors.ink,
  tertiaryContainer: Color(0xFFFFDCE8),
  onTertiaryContainer: Color(0xFF5C1A38),
  error: Color(0xFFD13B3B),
  onError: Colors.white,
  errorContainer: Color(0xFFFFDAD6),
  onErrorContainer: Color(0xFF5A0F0F),
  surface: Color(0xFFFFFAF3),
  onSurface: BuddyColors.ink,
  onSurfaceVariant: Color(0xFF6A607A),
  outline: Color(0xFF8E819E),
  outlineVariant: Color(0xFFEBDFD2),
  shadow: Colors.black,
  scrim: Colors.black,
  inverseSurface: BuddyColors.ink,
  onInverseSurface: Color(0xFFFFF1E0),
  inversePrimary: Color(0xFFB45F1A),
  surfaceTint: BuddyColors.orange,
  surfaceContainerLowest: Colors.white,
  surfaceContainerLow: Colors.white,
  surfaceContainer: Color(0xFFFFF4E6),
  surfaceContainerHigh: Color(0xFFFFEEDC),
  surfaceContainerHighest: Color(0xFFFBE8D2),
);

const ColorScheme _darkScheme = ColorScheme(
  brightness: Brightness.dark,
  primary: BuddyColors.orange,
  onPrimary: Color(0xFF2B1400),
  primaryContainer: Color(0xFF5A2E0C),
  onPrimaryContainer: Color(0xFFFFE6C8),
  secondary: Color(0xFFB4A9F0),
  onSecondary: Color(0xFF211A55),
  secondaryContainer: Color(0xFF3B3377),
  onSecondaryContainer: Color(0xFFE7E2FA),
  tertiary: Color(0xFFF5A0BF),
  onTertiary: Color(0xFF4A1230),
  tertiaryContainer: Color(0xFF6B2347),
  onTertiaryContainer: Color(0xFFFFD9E6),
  error: Color(0xFFFF8A80),
  onError: Color(0xFF4A0A0A),
  errorContainer: Color(0xFF7A2020),
  onErrorContainer: Color(0xFFFFDAD6),
  surface: Color(0xFF130F1F),
  onSurface: Color(0xFFF3ECFA),
  onSurfaceVariant: Color(0xFFB9AFCB),
  outline: Color(0xFF6E6585),
  outlineVariant: Color(0xFF3A3256),
  shadow: Colors.black,
  scrim: Colors.black,
  inverseSurface: Color(0xFFF3ECFA),
  onInverseSurface: BuddyColors.ink,
  inversePrimary: Color(0xFF9A4F10),
  surfaceTint: BuddyColors.orange,
  surfaceContainerLowest: Color(0xFF0E0A18),
  surfaceContainerLow: Color(0xFF1C1631),
  surfaceContainer: Color(0xFF241D3E),
  surfaceContainerHigh: Color(0xFF2D2549),
  surfaceContainerHighest: Color(0xFF372E57),
);

class BuddyTheme {
  BuddyTheme._();

  static final ThemeData light = _build(_lightScheme);
  static final ThemeData dark = _build(_darkScheme);

  static ThemeData _build(ColorScheme cs) {
    final base = ThemeData(useMaterial3: true, colorScheme: cs);
    final t = base.textTheme.apply(bodyColor: cs.onSurface, displayColor: cs.onSurface);
    final text = t.copyWith(
      displaySmall: t.displaySmall?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -1),
      headlineLarge: t.headlineLarge?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -0.6),
      headlineMedium: t.headlineMedium?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -0.5),
      headlineSmall: t.headlineSmall?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.3),
      titleLarge: t.titleLarge?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.2),
      titleMedium: t.titleMedium?.copyWith(fontWeight: FontWeight.w700),
      titleSmall: t.titleSmall?.copyWith(fontWeight: FontWeight.w700),
      bodyLarge: t.bodyLarge?.copyWith(height: 1.4),
      bodyMedium: t.bodyMedium?.copyWith(height: 1.4),
      bodySmall: t.bodySmall?.copyWith(height: 1.35, color: cs.onSurfaceVariant),
      labelLarge: t.labelLarge?.copyWith(fontWeight: FontWeight.w700),
      labelMedium: t.labelMedium?.copyWith(fontWeight: FontWeight.w700),
    );

    const stadium = StadiumBorder();
    final fieldBorder = OutlineInputBorder(
      borderRadius: BorderRadius.circular(Rad.field),
      borderSide: BorderSide.none,
    );

    return base.copyWith(
      textTheme: text,
      splashFactory: NoSplash.splashFactory,
      splashColor: Colors.transparent,
      highlightColor: cs.onSurface.withAlpha(10),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          splashFactory: NoSplash.splashFactory,
          overlayColor: Colors.transparent,
        ),
      ),
      listTileTheme: ListTileThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      switchTheme: const SwitchThemeData(
        splashRadius: 0,
        overlayColor: WidgetStatePropertyAll<Color>(Colors.transparent),
      ),
      scaffoldBackgroundColor: cs.surface,
      appBarTheme: AppBarTheme(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        foregroundColor: cs.onSurface,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: text.titleLarge?.copyWith(fontSize: 22, fontWeight: FontWeight.w800),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        margin: EdgeInsets.zero,
        color: cs.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: Rad.cardRadius,
          side: BorderSide(color: cs.outlineVariant),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          splashFactory: NoSplash.splashFactory,
          overlayColor: Colors.transparent,
          shape: stadium,
          minimumSize: const Size(64, 52),
          padding: const EdgeInsets.symmetric(horizontal: 24),
          textStyle: text.labelLarge?.copyWith(fontSize: 16),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          splashFactory: NoSplash.splashFactory,
          overlayColor: Colors.transparent,
          shape: stadium,
          minimumSize: const Size(64, 52),
          padding: const EdgeInsets.symmetric(horizontal: 24),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          splashFactory: NoSplash.splashFactory,
          overlayColor: Colors.transparent,
          shape: stadium,
          minimumSize: const Size(64, 52),
          padding: const EdgeInsets.symmetric(horizontal: 24),
          side: BorderSide(color: cs.outline),
          textStyle: text.labelLarge?.copyWith(fontSize: 15),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          splashFactory: NoSplash.splashFactory,
          overlayColor: Colors.transparent,
          shape: stadium,
          textStyle: text.labelLarge,
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: cs.primary,
        foregroundColor: cs.onPrimary,
        elevation: 0,
        highlightElevation: 0,
        focusElevation: 0,
        hoverElevation: 0,
        shape: stadium,
        focusColor: Colors.transparent,
        hoverColor: Colors.transparent,
        splashColor: Colors.transparent,
        extendedTextStyle: text.labelLarge?.copyWith(fontSize: 16),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: cs.surfaceContainer,
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        border: fieldBorder,
        enabledBorder: fieldBorder,
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(Rad.field),
          borderSide: BorderSide(color: cs.primary, width: 2),
        ),
        labelStyle: TextStyle(color: cs.onSurfaceVariant),
      ),
      chipTheme: ChipThemeData(
        shape: stadium,
        side: BorderSide(color: cs.outlineVariant),
        backgroundColor: cs.surfaceContainer,
        selectedColor: cs.primaryContainer,
        labelStyle: text.labelLarge,
        padding: const EdgeInsets.symmetric(horizontal: 6),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: cs.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
        titleTextStyle: text.titleLarge,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: cs.inverseSurface,
        contentTextStyle: text.bodyMedium?.copyWith(color: cs.onInverseSurface),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      dividerTheme: DividerThemeData(color: cs.outlineVariant, space: 1, thickness: 1),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: cs.primary,
        linearTrackColor: cs.surfaceContainerHighest,
        circularTrackColor: cs.surfaceContainerHighest,
      ),
      segmentedButtonTheme: SegmentedButtonThemeData(
        style: SegmentedButton.styleFrom(
          selectedBackgroundColor: cs.primaryContainer,
          selectedForegroundColor: cs.onPrimaryContainer,
          side: BorderSide(color: cs.outlineVariant),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 72,
        elevation: 0,
        backgroundColor: cs.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        indicatorColor: cs.primaryContainer,
        indicatorShape: stadium,
        overlayColor: const WidgetStatePropertyAll<Color>(Colors.transparent),
        labelTextStyle: WidgetStateProperty.resolveWith<TextStyle?>((states) {
          final sel = states.contains(WidgetState.selected);
          return text.labelMedium?.copyWith(
            fontWeight: sel ? FontWeight.w800 : FontWeight.w600,
            color: sel ? cs.onSurface : cs.onSurfaceVariant,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith<IconThemeData?>((states) {
          final sel = states.contains(WidgetState.selected);
          return IconThemeData(
            size: 26,
            color: sel ? cs.onPrimaryContainer : cs.onSurfaceVariant,
          );
        }),
      ),
      pageTransitionsTheme: PageTransitionsTheme(
        builders: <TargetPlatform, PageTransitionsBuilder>{
          for (final p in TargetPlatform.values) p: const BuddyPageTransitionsBuilder(),
        },
      ),
    );
  }
}
