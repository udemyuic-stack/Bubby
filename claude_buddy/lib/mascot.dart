import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import 'theme.dart';

enum MascotMood {
  idle,
  happy,
  sleepy,
  love,
  wink,
  excited,
  sad,
  cool,

  /// Being picked up: wide eyes, small "o" mouth, a sweat drop.
  grabbed,

  /// Being stroked: blissful closed eyes, big blush, floating hearts.
  pet,

  /// Cupped "ear", wide eyes glancing sideways, tiny "o" mouth.
  listening,

  /// Eyes up and to the side, a trail of three thought dots.
  thinking,

  /// Eyes squeezed shut, wide open grin, happy tears.
  laugh,

  /// Spiral eyes and little stars after a spin.
  dizzy,

  /// Eyes shut, huge open mouth.
  yawn,
}

class MascotPainter extends CustomPainter {
  const MascotPainter(
    this.mood, {
    this.blink = false,
    this.species = 'cat',
    this.speaking = false,
    this.talkPhase = 0,
    this.shadow = true,
    this.tilt = Offset.zero,
    this.look = Offset.zero,
    this.time = 0,
    this.lift = 0.5,
    this.eyesShut = false,
  });

  final MascotMood mood;

  /// Forces closed (line) eyes regardless of mood, e.g. while asleep.
  final bool eyesShut;
  final bool blink;
  final String species;

  /// When true the mouth flaps; [talkPhase] is the flap angle in radians.
  final bool speaking;
  final double talkPhase;

  /// Draws a soft ground shadow under the body.
  final bool shadow;

  /// Parallax tilt, each axis -1..1: face plate and eyes shift most, ears
  /// a bit, body least, the ground shadow opposite.
  final Offset tilt;

  /// Where the pupils look, each axis -1..1.
  final Offset look;

  /// Seconds; drives the ~3.2 s breathing pulse.
  final double time;

  /// Bounce height 0 (ground contact, squashed) .. 1 (apex, stretched).
  final double lift;

  static const Map<String, List<Color>> _palette = {
    'cat': [Color(0xFFF0883A), Color(0xFFFFE6C8), Color(0xFFFFE6C8)],
    'dog': [Color(0xFFC98B5A), Color(0xFFF6E1C3), Color(0xFF8A5A34)],
    'bunny': [Color(0xFFEFE6EE), Color(0xFFFFFFFF), Color(0xFFFFB6C8)],
    'bear': [Color(0xFF9A6B45), Color(0xFFE8CBA5), Color(0xFFE8CBA5)],
    'panda': [Color(0xFFE6E6EC), Color(0xFFFFFFFF), Color(0xFF1B1530)],
    'fox': [Color(0xFFE8692B), Color(0xFFFFF1E0), Color(0xFF1B1530)],
    'frog': [Color(0xFF6CC070), Color(0xFFD8F2B8), Color(0xFFD8F2B8)],
    'penguin': [Color(0xFF2F3A5C), Color(0xFFF4F4F8), Color(0xFF2F3A5C)],
    'capybara': [Color(0xFFB07A4B), Color(0xFFD9B48A), Color(0xFF8A5A34)],
    'hamster': [Color(0xFFF2C48D), Color(0xFFFFF0DC), Color(0xFFFFB6C8)],
    'owl': [Color(0xFF8B6B4A), Color(0xFFF2E6D0), Color(0xFF6E5438)],
    'duck': [Color(0xFFFFD84D), Color(0xFFFFF0A8), Color(0xFFFFD84D)],
    'pig': [Color(0xFFF5A3B8), Color(0xFFFFD3DE), Color(0xFFE88AA2)],
    'koala': [Color(0xFF9AA0A6), Color(0xFFDDE1E4), Color(0xFF7C8288)],
    'lion': [Color(0xFFF0B349), Color(0xFFFFE3A8), Color(0xFFB5652B)],
    'tiger': [Color(0xFFF08A24), Color(0xFFFFEBD2), Color(0xFFF08A24)],
  };

  static const Color body = Color(0xFFF0883A);
  static const Color face = Color(0xFFFFE6C8);
  static const Color ink = Color(0xFF1B1530);
  static const Color _red = Color(0xFFE5365B);
  static const Color _orange = Color(0xFFFF9A2E);

  static Color _shift(Color c, double d) {
    final h = HSLColor.fromColor(c);
    return h.withLightness((h.lightness + d).clamp(0.0, 1.0)).toColor();
  }

  static const Color _cool = Color(0xFF3A2352);
  static Color _hi(Color c) => Color.lerp(c, Colors.white, 0.42)!;
  static Color _sh(Color c, double k) => Color.lerp(c, _cool, k)!;
  static Color _warm(Color c) => Color.lerp(c, const Color(0xFFFF8A5C), 0.55)!;

  /// Smooth soft step used for idle blinking helpers.
  static double _clamp01(double v) => v < 0 ? 0 : (v > 1 ? 1 : v);

  /// Draws a plump, lit sphere: light from the upper left, soft mid tone,
  /// darker lower-right rim, warm subsurface glow and a thin rim light.
  static void sphere(Canvas canvas, Rect r, Color c, {double rim = 1}) {
    canvas.drawOval(
      r,
      Paint()
        ..shader = RadialGradient(
          center: const Alignment(-0.38, -0.5),
          radius: 1.0,
          colors: [_hi(c), c, _sh(c, 0.2), _sh(c, 0.4)],
          stops: const [0, 0.42, 0.82, 1],
        ).createShader(r),
    );
    if (rim <= 0) return;
    canvas.save();
    canvas.clipPath(Path()..addOval(r));
    final w = _warm(c);
    canvas.drawRect(
      r,
      Paint()
        ..shader = RadialGradient(
          center: const Alignment(0.45, 0.62),
          radius: 0.6,
          colors: [w.withAlpha((80 * rim).round()), w.withAlpha(0)],
        ).createShader(r),
    );
    final m = r.shortestSide;
    canvas.drawOval(
      r.deflate(m * 0.02),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = m * 0.03
        ..shader = LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            const Color(0x00FFFFFF),
            const Color(0x00FFFFFF),
            Colors.white.withAlpha((120 * rim).round()),
          ],
          stops: const [0, 0.6, 1],
        ).createShader(r)
        ..maskFilter = MaskFilter.blur(BlurStyle.normal, m * 0.012),
    );
    canvas.restore();
  }

  /// Cheap gradient oval for small parts (ears, noses, patches).
  static void blob(Canvas canvas, Rect r, Color c) => sphere(canvas, r, c, rim: 0);

  /// Gradient path (pointed ears etc.) with a faint darker outline for depth.
  static void shape(Canvas canvas, Path pa, Color c, double s,
      {bool inner = false}) {
    final b = pa.getBounds();
    canvas.drawPath(
      pa,
      Paint()
        ..shader = LinearGradient(
          begin: inner ? Alignment.bottomCenter : Alignment.topLeft,
          end: inner ? Alignment.topCenter : Alignment.bottomRight,
          colors: inner
              ? [_sh(c, 0.3), c, _hi(c)]
              : [_hi(c), c, _sh(c, 0.35)],
          stops: const [0, 0.5, 1],
        ).createShader(b),
    );
    if (!inner) {
      canvas.drawPath(
        pa,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = s * 0.008
          ..strokeJoin = StrokeJoin.round
          ..color = _sh(c, 0.6).withAlpha(70),
      );
    }
  }

  /// Soft specular glint: radial-faded white ellipse + crisp core + dot.
  static void glint(Canvas canvas, Offset c, double w, double h, double ang,
      {double a = 1}) {
    canvas.save();
    canvas.translate(c.dx, c.dy);
    canvas.rotate(ang);
    final soft = Rect.fromCenter(center: Offset.zero, width: w, height: h);
    canvas.drawOval(
      soft,
      Paint()
        ..shader = RadialGradient(colors: [
          Colors.white.withAlpha((235 * a).round()),
          Colors.white.withAlpha((90 * a).round()),
          Colors.white.withAlpha(0),
        ], stops: const [0, 0.5, 1])
            .createShader(soft),
    );
    canvas.drawOval(
      Rect.fromCenter(center: Offset(-w * 0.04, -h * 0.04), width: w * 0.42, height: h * 0.34),
      Paint()..color = Colors.white.withAlpha((235 * a).round()),
    );
    canvas.restore();
    canvas.drawCircle(c.translate(w * 0.5, h * 0.55), h * 0.2,
        Paint()..color = Colors.white.withAlpha((190 * a).round()));
  }

  @override
  void paint(Canvas canvas, Size size) {
    final s = math.min(size.width, size.height);
    canvas.translate((size.width - s) / 2, (size.height - s) / 2);
    Offset p(double x, double y) => Offset(x * s, y * s);
    final pal = _palette[species] ?? _palette['cat']!;
    final dark = Paint()..color = ink;
    final white = Paint()..color = Colors.white;
    final earShift = tilt * (s * 0.014);
    final bodyShift = tilt * (s * 0.006);
    final faceShift = tilt * (s * 0.03);
    final eyeShift = tilt * (s * 0.012);
    var lk = Offset(look.dx * s * 0.014, look.dy * s * 0.011);
    if (mood == MascotMood.thinking) {
      lk = Offset(s * 0.012, -s * 0.012);
    } else if (mood == MascotMood.listening) {
      lk = Offset(-s * 0.014, 0);
    }
    final br = math.sin(time * 2 * math.pi / 3.2);
    final lf = _clamp01(lift);

    if (shadow) {
      // Contact shadow: shrinks and fades as the figure rises, shifts opposite to tilt.
      final k = 1.0 - 0.22 * lf;
      final so = Offset(-tilt.dx * s * 0.025, 0);
      final big = Rect.fromCenter(
          center: p(0.5, 0.965) + so, width: s * 0.74 * k, height: s * 0.1 * k);
      canvas.drawOval(
        big,
        Paint()
          ..shader = RadialGradient(colors: [
            const Color(0x401B1530).withAlpha((70 - 28 * lf).round()),
            const Color(0x001B1530),
          ]).createShader(big),
      );
      final core = Rect.fromCenter(
          center: p(0.5, 0.965) + so, width: s * 0.42 * k, height: s * 0.045 * k);
      canvas.drawOval(
        core,
        Paint()
          ..shader = RadialGradient(colors: [
            const Color(0x1B1B1530).withAlpha((95 - 40 * lf).round()),
            const Color(0x001B1530),
          ]).createShader(core),
      );
    }

    // Whole-figure motion: bounce lift, squash & stretch, breathing, tilt yaw.
    canvas.save();
    canvas.translate(0, -s * 0.035 - (lf - 0.5) * s * 0.05);
    final pv = p(0.5, 0.93);
    canvas.translate(pv.dx, pv.dy);
    canvas.rotate(tilt.dx * 0.05);
    final stretch = (lf - 0.5) * 0.04 + br * 0.014;
    canvas.scale((1 - stretch * 0.6) * (1 - 0.02 * tilt.dx.abs()), 1 + stretch);
    final skew = Matrix4.identity()..setEntry(0, 1, -tilt.dy * 0.03);
    canvas.transform(skew.storage);
    canvas.translate(-pv.dx, -pv.dy);

    final headRect =
        Rect.fromCenter(center: p(0.5, 0.58), width: s * 0.86, height: s * 0.74);

    // ---- ears / mane layer (parallax: a bit)
    canvas.save();
    canvas.translate(earShift.dx, earShift.dy);
    if (species == 'lion') {
      final mane = Rect.fromCircle(center: p(0.5, 0.56), radius: s * 0.47);
      sphere(canvas, mane, pal[2]);
      for (var i = 0; i < 14; i++) {
        final a = i * math.pi * 2 / 14;
        final c = p(0.5, 0.56) + Offset(math.cos(a), math.sin(a)) * s * 0.44;
        sphere(canvas, Rect.fromCircle(center: c, radius: s * 0.05),
            _shift(pal[2], i.isEven ? 0.04 : -0.05), rim: 0.5);
      }
    }
    for (final sign in [-1.0, 1.0]) {
      switch (species) {
        case 'dog':
          canvas.save();
          canvas.translate(p(0.5 + sign * 0.38, 0.30).dx, p(0, 0.30).dy);
          canvas.rotate(sign * 0.25);
          sphere(canvas,
              Rect.fromCenter(center: Offset.zero, width: s * 0.2, height: s * 0.38),
              pal[2]);
          blob(
              canvas,
              Rect.fromCenter(
                  center: Offset(-sign * s * 0.015, -s * 0.03),
                  width: s * 0.08,
                  height: s * 0.24),
              _shift(pal[2], 0.07));
          canvas.restore();
        case 'bunny':
          canvas.save();
          canvas.translate(p(0.5 + sign * 0.2, 0.2).dx, p(0, 0.2).dy);
          canvas.rotate(sign * 0.12);
          sphere(canvas,
              Rect.fromCenter(center: Offset.zero, width: s * 0.2, height: s * 0.46),
              _shift(pal[0], -0.03));
          blob(canvas,
              Rect.fromCenter(center: Offset(0, s * 0.02), width: s * 0.1, height: s * 0.34),
              pal[2]);
          canvas.restore();
        case 'bear':
        case 'panda':
        case 'capybara':
        case 'hamster':
        case 'koala':
        case 'lion':
          final big = species == 'koala';
          final rad = s * (big ? 0.14 : 0.1);
          final c = p(0.5 + sign * (big ? 0.34 : 0.3), big ? 0.3 : 0.28);
          sphere(canvas, Rect.fromCircle(center: c, radius: rad),
              species == 'lion' ? pal[0] : pal[2]);
          if (species == 'bear' || species == 'hamster' || big) {
            blob(
                canvas,
                Rect.fromCircle(
                    center: c.translate(0, rad * 0.06), radius: rad * 0.55),
                big
                    ? pal[1]
                    : (species == 'bear' ? pal[1] : _shift(pal[2], 0.06)));
          }
        case 'duck':
          break;
        case 'frog':
          final fc = p(0.5 + sign * 0.22, 0.27);
          sphere(canvas, Rect.fromCircle(center: fc, radius: s * 0.11), pal[0]);
          sphere(canvas, Rect.fromCircle(center: fc, radius: s * 0.07), Colors.white,
              rim: 0.4);
          final fp = fc + lk * 0.9;
          canvas.drawCircle(fp, s * 0.035, dark);
          canvas.drawCircle(fp.translate(-s * 0.012, -s * 0.014), s * 0.012, white);
        case 'penguin':
          break;
        default:
          final ear = Path()
            ..moveTo(p(0.5 + sign * 0.36, 0.5).dx, p(0, 0.5).dy)
            ..quadraticBezierTo(p(0.5 + sign * 0.40, 0.2).dx, p(0, 0.2).dy,
                p(0.5 + sign * 0.30, 0.10).dx, p(0, 0.10).dy)
            ..quadraticBezierTo(p(0.5 + sign * 0.16, 0.15).dx, p(0, 0.15).dy,
                p(0.5 + sign * 0.08, 0.28).dx, p(0, 0.28).dy)
            ..close();
          shape(canvas, ear, _shift(pal[0], -0.07), s);
          final inner = Path()
            ..moveTo(p(0.5 + sign * 0.31, 0.40).dx, p(0, 0.40).dy)
            ..quadraticBezierTo(p(0.5 + sign * 0.33, 0.24).dx, p(0, 0.24).dy,
                p(0.5 + sign * 0.28, 0.19).dx, p(0, 0.19).dy)
            ..quadraticBezierTo(p(0.5 + sign * 0.20, 0.23).dx, p(0, 0.23).dy,
                p(0.5 + sign * 0.16, 0.31).dx, p(0, 0.31).dy)
            ..close();
          shape(canvas, inner, species == 'fox' ? pal[2] : pal[1], s, inner: true);
      }
    }
    canvas.restore();

    // The head casts a soft shadow onto the ears for depth.
    canvas.drawOval(
      headRect.shift(Offset(s * 0.012 + earShift.dx * 0.3, s * 0.02)),
      Paint()
        ..color = const Color(0x2A1B1530)
        ..maskFilter = MaskFilter.blur(BlurStyle.normal, s * 0.022),
    );

    // ---- head (body layer, moves least)
    canvas.save();
    canvas.translate(bodyShift.dx, bodyShift.dy);
    sphere(canvas, headRect, pal[0]);
    if (species == 'penguin') {
      for (final sign in [-1.0, 1.0]) {
        sphere(canvas, Rect.fromCircle(center: p(0.5 + sign * 0.14, 0.60), radius: s * 0.125),
            pal[1], rim: 0.5);
      }
    }
    canvas.restore();

    // ---- face plate (moves most)
    canvas.save();
    canvas.translate(faceShift.dx, faceShift.dy);
    final muzzle =
        Rect.fromCenter(center: p(0.5, 0.64), width: s * 0.62, height: s * 0.50);
    canvas.drawOval(
      muzzle.shift(Offset(s * 0.008, s * 0.014)),
      Paint()
        ..color = const Color(0x241B1530)
        ..maskFilter = MaskFilter.blur(BlurStyle.normal, s * 0.014),
    );
    sphere(canvas, muzzle, pal[1], rim: 0.7);

    if (species == 'panda') {
      for (final sign in [-1.0, 1.0]) {
        canvas.save();
        canvas.translate(p(0.5 + sign * 0.14, 0.58).dx, p(0, 0.58).dy);
        canvas.rotate(sign * -0.5);
        sphere(canvas,
            Rect.fromCenter(center: Offset.zero, width: s * 0.15, height: s * 0.2),
            const Color(0xFF2B2B33), rim: 0.6);
        canvas.restore();
      }
    }
    if (species == 'owl') {
      for (final sign in [-1.0, 1.0]) {
        final oc = p(0.5 + sign * 0.14, 0.58);
        sphere(canvas, Rect.fromCircle(center: oc, radius: s * 0.115),
            _shift(pal[1], 0.04), rim: 0.5);
        canvas.drawCircle(
            oc,
            s * 0.115,
            Paint()
              ..color = _shift(pal[0], -0.05)
              ..style = PaintingStyle.stroke
              ..strokeWidth = s * 0.012);
      }
    }
    if (species == 'fox') {
      for (final sign in [-1.0, 1.0]) {
        final cheekP = Path()
          ..moveTo(p(0.5 + sign * 0.42, 0.62).dx, p(0, 0.62).dy)
          ..quadraticBezierTo(p(0.5 + sign * 0.28, 0.80).dx, p(0, 0.80).dy,
              p(0.5, 0.80).dx, p(0, 0.80).dy)
          ..quadraticBezierTo(p(0.5 + sign * 0.22, 0.66).dx, p(0, 0.66).dy,
              p(0.5 + sign * 0.42, 0.62).dx, p(0, 0.62).dy);
        shape(canvas, cheekP, pal[1], s);
      }
    }

    canvas.save();
    canvas.translate(eyeShift.dx, eyeShift.dy);
    final line = Paint()
      ..color = ink
      ..style = PaintingStyle.stroke
      ..strokeWidth = s * 0.028
      ..strokeCap = StrokeCap.round;

    void lineEye(Offset c) =>
        canvas.drawLine(c.translate(-s * 0.04, 0), c.translate(s * 0.04, 0), line);

    void openEye(Offset c, {double k = 1}) {
      if (blink || eyesShut) {
        lineEye(c);
        return;
      }
      final ow = s * 0.078 * k, oh = s * 0.105 * k;
      final outer = Rect.fromCenter(center: c, width: ow, height: oh);
      canvas.drawOval(outer, dark);
      // Layered iris that follows the look direction.
      final ic = c + lk * k;
      final iris = Rect.fromCenter(center: ic, width: ow * 0.86, height: oh * 0.88);
      canvas.drawOval(
        iris,
        Paint()
          ..shader = const RadialGradient(
            center: Alignment(0.2, 0.6),
            radius: 0.9,
            colors: [Color(0xFF6A5AA8), Color(0xFF2C2350), Color(0xFF120D24)],
            stops: [0, 0.55, 1],
          ).createShader(iris),
      );
      canvas.drawOval(
          Rect.fromCenter(center: ic, width: ow * 0.4, height: oh * 0.42), dark);
      // Faint reflection of a window (lower right), main + secondary glints.
      canvas.drawRRect(
          RRect.fromRectAndRadius(
              Rect.fromCenter(
                  center: ic.translate(ow * 0.16, oh * 0.2),
                  width: ow * 0.3,
                  height: oh * 0.2),
              Radius.circular(s * 0.004)),
          Paint()..color = const Color(0x40FFFFFF));
      canvas.drawOval(
          Rect.fromCenter(
              center: c.translate(-s * 0.013 * k, -s * 0.026 * k),
              width: s * 0.036 * k,
              height: s * 0.04 * k),
          white);
      canvas.drawCircle(c.translate(s * 0.016 * k, s * 0.022 * k), s * 0.007 * k,
          Paint()..color = const Color(0xCCFFFFFF));
    }

    void arcEye(Offset c, {required bool up}) {
      final rect = Rect.fromCenter(center: c, width: s * 0.10, height: s * 0.08);
      canvas.drawArc(rect, up ? math.pi : 0, math.pi, false, line);
    }

    void heart(Offset c, double k, Color col) {
      final h = Path()
        ..moveTo(c.dx, c.dy + s * 0.05 * k)
        ..cubicTo(c.dx - s * 0.09 * k, c.dy - s * 0.01 * k, c.dx - s * 0.04 * k,
            c.dy - s * 0.08 * k, c.dx, c.dy - s * 0.03 * k)
        ..cubicTo(c.dx + s * 0.04 * k, c.dy - s * 0.08 * k, c.dx + s * 0.09 * k,
            c.dy - s * 0.01 * k, c.dx, c.dy + s * 0.05 * k)
        ..close();
      canvas.drawPath(h, Paint()..color = col);
    }

    final l = p(0.36, 0.58);
    final r = p(0.64, 0.58);
    switch (mood) {
      case MascotMood.idle:
        openEye(l);
        openEye(r);
      case MascotMood.happy:
        arcEye(l.translate(0, s * 0.02), up: true);
        arcEye(r.translate(0, s * 0.02), up: true);
      case MascotMood.sleepy:
        lineEye(l);
        lineEye(r);
      case MascotMood.love:
        heart(l, 1, _red);
        heart(r, 1, _red);
        canvas.drawCircle(l.translate(-s * 0.012, -s * 0.02), s * 0.01,
            Paint()..color = const Color(0xCCFFFFFF));
        canvas.drawCircle(r.translate(-s * 0.012, -s * 0.02), s * 0.01,
            Paint()..color = const Color(0xCCFFFFFF));
      case MascotMood.wink:
        openEye(l);
        arcEye(r.translate(0, s * 0.02), up: true);
      case MascotMood.excited:
        for (final c in [l, r]) {
          openEye(c, k: 1.3);
        }
      case MascotMood.sad:
        openEye(l);
        openEye(r);
        canvas.drawLine(l.translate(-s * 0.05, -s * 0.06),
            l.translate(s * 0.045, -s * 0.095), line);
        canvas.drawLine(r.translate(s * 0.05, -s * 0.06),
            r.translate(-s * 0.045, -s * 0.095), line);
        canvas.drawCircle(l.translate(-s * 0.012, s * 0.09), s * 0.018,
            Paint()..color = const Color(0xFF6EC1F5));
      case MascotMood.cool:
        final lens = Paint()..color = ink;
        for (final c in [l, r]) {
          canvas.drawRRect(
              RRect.fromRectAndRadius(
                  Rect.fromCenter(center: c, width: s * 0.17, height: s * 0.10),
                  Radius.circular(s * 0.035)),
              lens);
          canvas.drawLine(c.translate(-s * 0.05, -s * 0.02),
              c.translate(-s * 0.02, -s * 0.02),
              Paint()
                ..color = const Color(0x99FFFFFF)
                ..strokeWidth = s * 0.014
                ..strokeCap = StrokeCap.round);
        }
        canvas.drawLine(l.translate(s * 0.085, 0), r.translate(-s * 0.085, 0), line);
      case MascotMood.grabbed:
        openEye(l, k: 1.45);
        openEye(r, k: 1.45);
        canvas.drawLine(l.translate(-s * 0.05, -s * 0.09),
            l.translate(s * 0.045, -s * 0.11), line);
        canvas.drawLine(r.translate(s * 0.05, -s * 0.09),
            r.translate(-s * 0.045, -s * 0.11), line);
        final drop = Path()
          ..moveTo(p(0.80, 0.30).dx, p(0, 0.30).dy)
          ..quadraticBezierTo(p(0.84, 0.37).dx, p(0, 0.37).dy, p(0.80, 0.40).dx,
              p(0, 0.40).dy)
          ..quadraticBezierTo(p(0.76, 0.37).dx, p(0, 0.37).dy, p(0.80, 0.30).dx,
              p(0, 0.30).dy);
        canvas.drawPath(drop, Paint()..color = const Color(0xFF6EC1F5));
      case MascotMood.pet:
        arcEye(l.translate(0, s * 0.02), up: true);
        arcEye(r.translate(0, s * 0.02), up: true);
        heart(p(0.80, 0.22), 0.8, _red.withAlpha(200));
        heart(p(0.70, 0.12), 0.5, _red.withAlpha(150));
      case MascotMood.listening:
        openEye(l, k: 1.15);
        openEye(r, k: 1.15);
        canvas.drawLine(l.translate(-s * 0.05, -s * 0.085),
            l.translate(s * 0.045, -s * 0.105), line);
        canvas.drawLine(r.translate(s * 0.05, -s * 0.085),
            r.translate(-s * 0.045, -s * 0.105), line);
      case MascotMood.thinking:
        openEye(l);
        openEye(r);
        canvas.drawLine(r.translate(-s * 0.045, -s * 0.085),
            r.translate(s * 0.05, -s * 0.105), line);
        for (var i = 0; i < 3; i++) {
          final ph = (math.sin((time * 1.4 - i * 0.18) * 2 * math.pi) + 1) / 2;
          canvas.drawCircle(
              p(0.74 + i * 0.07, 0.30 - i * 0.07),
              s * (0.014 + 0.01 * i) * (0.8 + 0.4 * ph),
              Paint()..color = ink.withAlpha((120 + 120 * ph).round()));
        }
      case MascotMood.laugh:
        arcEye(l.translate(0, s * 0.02), up: true);
        arcEye(r.translate(0, s * 0.02), up: true);
        canvas.drawCircle(l.translate(-s * 0.05, s * 0.06), s * 0.012,
            Paint()..color = const Color(0xFF6EC1F5));
        canvas.drawCircle(r.translate(s * 0.05, s * 0.06), s * 0.012,
            Paint()..color = const Color(0xFF6EC1F5));
      case MascotMood.dizzy:
        for (final c in [l, r]) {
          final sp = Path();
          for (var i = 0; i <= 40; i++) {
            final a = i / 40 * math.pi * 5 + time * 9;
            final rad = s * 0.045 * (i / 40);
            final pt = c + Offset(math.cos(a), math.sin(a)) * rad;
            i == 0 ? sp.moveTo(pt.dx, pt.dy) : sp.lineTo(pt.dx, pt.dy);
          }
          canvas.drawPath(
              sp,
              Paint()
                ..color = ink
                ..style = PaintingStyle.stroke
                ..strokeWidth = s * 0.014
                ..strokeCap = StrokeCap.round);
        }
        for (var i = 0; i < 3; i++) {
          final a = time * 4 + i * 2.1;
          final c = p(0.5, 0.14) + Offset(math.cos(a) * s * 0.2, math.sin(a) * s * 0.035);
          final st = Path()
            ..moveTo(c.dx, c.dy - s * 0.03)
            ..quadraticBezierTo(c.dx, c.dy, c.dx + s * 0.03, c.dy)
            ..quadraticBezierTo(c.dx, c.dy, c.dx, c.dy + s * 0.03)
            ..quadraticBezierTo(c.dx, c.dy, c.dx - s * 0.03, c.dy)
            ..quadraticBezierTo(c.dx, c.dy, c.dx, c.dy - s * 0.03);
          canvas.drawPath(st, Paint()..color = const Color(0xFFFFC83D));
        }
      case MascotMood.yawn:
        lineEye(l);
        lineEye(r);
    }

    canvas.restore();

    // Nose / snout / beak.
    switch (species) {
      case 'duck':
        sphere(canvas,
            Rect.fromCenter(center: p(0.5, 0.70), width: s * 0.22, height: s * 0.11),
            _orange, rim: 0.5);
        canvas.drawOval(
            Rect.fromCenter(center: p(0.5, 0.675), width: s * 0.14, height: s * 0.035),
            Paint()..color = const Color(0x66FFFFFF));
      case 'pig':
        sphere(canvas,
            Rect.fromCenter(center: p(0.5, 0.69), width: s * 0.2, height: s * 0.13),
            pal[2], rim: 0.6);
        glint(canvas, p(0.46, 0.665), s * 0.05, s * 0.02, -0.3, a: 0.8);
        canvas.drawOval(Rect.fromCenter(center: p(0.47, 0.69), width: s * 0.025, height: s * 0.04), dark);
        canvas.drawOval(Rect.fromCenter(center: p(0.53, 0.69), width: s * 0.025, height: s * 0.04), dark);
      case 'penguin':
      case 'owl':
        canvas.drawPath(
            Path()
              ..moveTo(p(0.46, 0.67).dx, p(0, 0.67).dy)
              ..lineTo(p(0.54, 0.67).dx, p(0, 0.67).dy)
              ..lineTo(p(0.5, 0.73).dx, p(0, 0.73).dy)
              ..close(),
            Paint()..color = _orange);
      case 'frog':
        canvas.drawCircle(p(0.47, 0.67), s * 0.01, Paint()..color = _shift(pal[0], -0.2));
        canvas.drawCircle(p(0.53, 0.67), s * 0.01, Paint()..color = _shift(pal[0], -0.2));
      case 'koala':
        blob(canvas,
            Rect.fromCenter(center: p(0.5, 0.66), width: s * 0.15, height: s * 0.1),
            const Color(0xFF3A3340));
        canvas.drawOval(
            Rect.fromCenter(center: p(0.48, 0.64), width: s * 0.05, height: s * 0.02),
            Paint()..color = const Color(0x55FFFFFF));
      case 'dog':
      case 'bear':
      case 'panda':
      case 'capybara':
      case 'lion':
      case 'fox':
        blob(canvas,
            Rect.fromCenter(center: p(0.5, 0.675), width: s * 0.1, height: s * 0.065),
            const Color(0xFF2A2240));
        glint(canvas, p(0.48, 0.66), s * 0.04, s * 0.02, -0.2, a: 0.9);
      default:
        final nose = Path()
          ..moveTo(p(0.47, 0.68).dx, p(0, 0.68).dy)
          ..quadraticBezierTo(p(0.5, 0.675).dx, p(0, 0.675).dy, p(0.53, 0.68).dx, p(0, 0.68).dy)
          ..lineTo(p(0.5, 0.71).dx, p(0, 0.71).dy)
          ..close();
        shape(canvas, nose, species == 'bunny' ? const Color(0xFFFF8FA8) : _red, s,
            inner: true);
        canvas.drawCircle(p(0.485, 0.683), s * 0.005,
            Paint()..color = const Color(0xCCFFFFFF));
    }

    // Mouth.
    final open = speaking ? 0.5 + 0.5 * math.sin(talkPhase) : 0.0;
    if (speaking && mood != MascotMood.sleepy) {
      final h = s * (0.03 + 0.08 * open);
      final mouthRect = Rect.fromCenter(center: p(0.5, 0.775), width: s * (0.10 + 0.03 * open), height: h);
      canvas.drawOval(mouthRect, dark);
      canvas.save();
      canvas.clipRect(mouthRect);
      canvas.drawOval(
          Rect.fromCenter(center: p(0.5, 0.775 + 0.02 * open), width: s * 0.07, height: h * 0.7),
          Paint()..color = _red);
      canvas.restore();
    } else if (mood == MascotMood.happy || mood == MascotMood.love || mood == MascotMood.pet) {
      final mouth = Path()
        ..moveTo(p(0.43, 0.73).dx, p(0, 0.73).dy)
        ..quadraticBezierTo(
            p(0.5, 0.84).dx, p(0, 0.84).dy, p(0.57, 0.73).dx, p(0, 0.73).dy)
        ..close();
      canvas.drawPath(mouth, dark);
    } else if (mood == MascotMood.grabbed) {
      canvas.drawOval(
          Rect.fromCenter(center: p(0.5, 0.78), width: s * 0.07, height: s * 0.09), dark);
    } else if (mood == MascotMood.excited) {
      final mouth = Path()
        ..moveTo(p(0.41, 0.725).dx, p(0, 0.725).dy)
        ..quadraticBezierTo(
            p(0.5, 0.89).dx, p(0, 0.89).dy, p(0.59, 0.725).dx, p(0, 0.725).dy)
        ..close();
      canvas.drawPath(mouth, dark);
      canvas.drawOval(
          Rect.fromCenter(center: p(0.5, 0.815), width: s * 0.08, height: s * 0.04),
          Paint()..color = _red);
    } else if (mood == MascotMood.laugh || mood == MascotMood.yawn) {
      final big = mood == MascotMood.yawn;
      final mr = Rect.fromCenter(
          center: p(0.5, big ? 0.79 : 0.775),
          width: s * (big ? 0.15 : 0.18),
          height: s * (big ? 0.16 : 0.12));
      canvas.drawOval(mr, dark);
      canvas.save();
      canvas.clipPath(Path()..addOval(mr));
      canvas.drawOval(
          Rect.fromCenter(
              center: mr.center.translate(0, mr.height * 0.32),
              width: mr.width * 0.7,
              height: mr.height * 0.55),
          Paint()..color = _red);
      canvas.restore();
    } else if (mood == MascotMood.listening) {
      canvas.drawOval(
          Rect.fromCenter(center: p(0.5, 0.77), width: s * 0.045, height: s * 0.05),
          dark);
    } else if (mood == MascotMood.thinking) {
      canvas.drawLine(p(0.46, 0.76), p(0.55, 0.75), line);
    } else if (mood == MascotMood.dizzy) {
      final mouth = Path()
        ..moveTo(p(0.43, 0.77).dx, p(0, 0.77).dy)
        ..quadraticBezierTo(p(0.47, 0.73).dx, p(0, 0.73).dy, p(0.5, 0.77).dx, p(0, 0.77).dy)
        ..quadraticBezierTo(p(0.53, 0.81).dx, p(0, 0.81).dy, p(0.57, 0.77).dx, p(0, 0.77).dy);
      canvas.drawPath(mouth, line);
    } else if (mood == MascotMood.sleepy) {
      canvas.drawOval(
          Rect.fromCenter(center: p(0.5, 0.77), width: s * 0.05, height: s * 0.06),
          line);
    } else if (mood == MascotMood.sad) {
      final mouth = Path()
        ..moveTo(p(0.44, 0.79).dx, p(0, 0.79).dy)
        ..quadraticBezierTo(
            p(0.5, 0.73).dx, p(0, 0.73).dy, p(0.56, 0.79).dx, p(0, 0.79).dy);
      canvas.drawPath(mouth, line);
    } else if (mood == MascotMood.cool) {
      final mouth = Path()
        ..moveTo(p(0.43, 0.74).dx, p(0, 0.74).dy)
        ..quadraticBezierTo(
            p(0.52, 0.80).dx, p(0, 0.80).dy, p(0.59, 0.72).dx, p(0, 0.72).dy);
      canvas.drawPath(mouth, line);
    } else {
      final mouth = Path()
        ..moveTo(p(0.5, 0.71).dx, p(0, 0.71).dy)
        ..quadraticBezierTo(
            p(0.46, 0.76).dx, p(0, 0.76).dy, p(0.42, 0.73).dx, p(0, 0.73).dy)
        ..moveTo(p(0.5, 0.71).dx, p(0, 0.71).dy)
        ..quadraticBezierTo(
            p(0.54, 0.76).dx, p(0, 0.76).dy, p(0.58, 0.73).dx, p(0, 0.73).dy);
      canvas.drawPath(mouth, line);
    }

    if (species == 'tiger') {
      final st = Paint()
        ..color = ink
        ..style = PaintingStyle.stroke
        ..strokeWidth = s * 0.03
        ..strokeCap = StrokeCap.round;
      for (final sign in [-1.0, 1.0]) {
        canvas.drawLine(p(0.5 + sign * 0.03, 0.30), p(0.5 + sign * 0.03, 0.40), st);
        canvas.drawLine(p(0.5 + sign * 0.14, 0.32), p(0.5 + sign * 0.16, 0.41), st);
        canvas.drawLine(p(0.5 + sign * 0.40, 0.58), p(0.5 + sign * 0.33, 0.60), st);
        canvas.drawLine(p(0.5 + sign * 0.40, 0.66), p(0.5 + sign * 0.33, 0.66), st);
      }
    } else if (species == 'cat') {
      final wh = Paint()
        ..color = const Color(0x661B1530)
        ..style = PaintingStyle.stroke
        ..strokeWidth = s * 0.012
        ..strokeCap = StrokeCap.round;
      for (final sign in [-1.0, 1.0]) {
        canvas.drawLine(p(0.5 + sign * 0.20, 0.70), p(0.5 + sign * 0.38, 0.67), wh);
        canvas.drawLine(p(0.5 + sign * 0.20, 0.73), p(0.5 + sign * 0.38, 0.75), wh);
      }
    }

    final strong = mood == MascotMood.excited ||
        mood == MascotMood.love ||
        mood == MascotMood.pet;
    final cr = s * (strong ? 0.058 : 0.046);
    for (final cx in [0.27, 0.73]) {
      final cb = Rect.fromCenter(center: p(cx, 0.68), width: cr * 2.6, height: cr * 1.8);
      canvas.drawOval(
        cb,
        Paint()
          ..shader = RadialGradient(colors: [
            Color(strong ? 0xB0E5365B : 0x80E5365B),
            const Color(0x00E5365B),
          ]).createShader(cb),
      );
    }
    canvas.restore(); // face plate

    if (mood == MascotMood.listening) {
      // Cupped paw behind the ear on the left.
      canvas.save();
      canvas.translate(p(0.07, 0.5).dx, p(0, 0.5).dy);
      canvas.rotate(-0.25);
      sphere(canvas,
          Rect.fromCenter(center: Offset.zero, width: s * 0.17, height: s * 0.26), pal[0]);
      canvas.drawArc(
          Rect.fromCenter(center: Offset(s * 0.03, 0), width: s * 0.1, height: s * 0.16),
          -math.pi / 2,
          math.pi,
          false,
          Paint()
            ..color = _sh(pal[0], 0.45)
            ..style = PaintingStyle.stroke
            ..strokeWidth = s * 0.012
            ..strokeCap = StrokeCap.round);
      canvas.restore();
    }

    // ---- glossy coat: specular glint, shifts opposite to tilt
    final go = tilt * (-s * 0.012);
    glint(canvas, p(0.33, 0.33) + go, s * 0.2, s * 0.085, -0.5);
    canvas.restore(); // figure motion
  }

  @override
  bool shouldRepaint(MascotPainter old) =>
      old.mood != mood ||
      old.blink != blink ||
      old.species != species ||
      old.speaking != speaking ||
      old.talkPhase != talkPhase ||
      old.shadow != shadow ||
      old.tilt != tilt ||
      old.look != look ||
      old.time != time ||
      old.lift != lift ||
      old.eyesShut != eyesShut;
}

/// The mascot, bouncing, breathing, blinking and glancing around. Loops are
/// skipped when the system asks for reduced motion or [animate] is false, and
/// stop with the surrounding `TickerMode` (off-screen tabs/routes).
/// Idle motion is a single throttled (~20 fps) ticker, not a 60 fps loop.
/// Pass [tilt] (each axis -1..1, e.g. from the accelerometer) for parallax;
/// without it the head turns gently on its own. [lookAt] (-1..1 per axis)
/// makes the pupils follow a point such as a finger.
class Mascot extends StatefulWidget {
  const Mascot({
    super.key,
    this.mood = MascotMood.idle,
    this.size = 160,
    this.animate = true,
    this.species = 'cat',
    this.speaking = false,
    this.tilt,
    this.lookAt,
    this.shadow = true,
    this.eyesShut = false,
    this.freezeTime,
  });

  final MascotMood mood;
  final double size;
  final bool animate;
  final String species;
  final bool speaking;
  final Offset? tilt;
  final Offset? lookAt;
  final bool shadow;
  final bool eyesShut;

  /// Pins the internal clock (seconds), mainly for stills.
  final double? freezeTime;

  @override
  State<Mascot> createState() => _MascotState();
}

class _MascotState extends State<Mascot> with SingleTickerProviderStateMixin {
  static const double _period = 16;
  static const int _frameMs = 48;
  late final Ticker _ticker = createTicker(_onTick);
  final ValueNotifier<double> _time = ValueNotifier<double>(0);
  Duration _last = Duration.zero;
  Offset _smooth = Offset.zero;

  void _onTick(Duration e) {
    if ((e - _last).inMilliseconds < _frameMs) return;
    _last = e;
    _time.value = (e.inMicroseconds / 1e6) % _period;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(Mascot old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    final still = !widget.animate || context.noMotion;
    if (still) {
      if (_ticker.isActive) _ticker.stop();
    } else if (!_ticker.isActive) {
      _last = Duration.zero;
      _ticker.start();
    }
  }

  @override
  void dispose() {
    _ticker.dispose();
    _time.dispose();
    super.dispose();
  }

  static Offset _auto(double ts) {
    const a = 2 * math.pi / _period;
    return Offset(
      math.sin(a * 3 * ts + 0.9) * 0.7 + math.sin(a * 7 * ts + 2.0) * 0.3,
      math.sin(a * 2 * ts + 0.3) * 0.5 + math.sin(a * 5 * ts + 1.1) * 0.2,
    );
  }

  @override
  Widget build(BuildContext context) {
    final size = widget.size;
    return SizedBox(
      width: size,
      height: size,
      child: RepaintBoundary(child: AnimatedBuilder(
        animation: _time,
        builder: (context, _) {
          final animated = widget.animate && !context.noMotion;
          final ts = widget.freezeTime ?? (animated ? _time.value : 0.0);
          final auto = _auto(ts);
          final given = widget.tilt;
          final target = given ?? auto * 0.55;
          // Low-pass filter so tilt input never jitters.
          _smooth = Offset.lerp(_smooth, target, animated ? 0.25 : 1)!;
          final at = widget.lookAt;
          final look = at ?? (given != null ? given * 0.9 + auto * 0.2 : auto);
          final lift = animated ? (math.sin(ts * math.pi) + 1) / 2 : 0.5;
          final blink = animated && ts % 4 > 3.85;
          return CustomPaint(
            size: Size(size, size),
            painter: MascotPainter(
              widget.mood,
              blink: blink,
              species: widget.species,
              speaking: widget.speaking,
              talkPhase: ts * 2 * math.pi * 3,
              tilt: at != null ? at * 0.6 : _smooth,
              look: look,
              time: ts,
              lift: lift,
              shadow: widget.shadow,
              eyesShut: widget.eyesShut,
            ),
          );
        },
      )),
    );
  }
}
