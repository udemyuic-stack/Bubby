import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Tiny drawn emblem for a full-costume preset.
class EmblemPainter extends CustomPainter {
  const EmblemPainter(this.kind, this.color);

  final String kind;
  final Color color;

  static Path star(Offset c, double r, {int points = 5, double inner = 0.45, double rot = -math.pi / 2}) {
    final path = Path();
    for (var i = 0; i < points * 2; i++) {
      final rr = i.isEven ? r : r * inner;
      final a = rot + i * math.pi / points;
      final o = c + Offset(math.cos(a), math.sin(a)) * rr;
      if (i == 0) {
        path.moveTo(o.dx, o.dy);
      } else {
        path.lineTo(o.dx, o.dy);
      }
    }
    return path..close();
  }

  @override
  void paint(Canvas canvas, Size size) {
    final s = math.min(size.width, size.height);
    Offset p(double x, double y) => Offset(x * s, y * s);
    final fill = Paint()..color = color;
    final white = Paint()..color = Colors.white;
    final stroke = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = s * 0.07
      ..strokeCap = StrokeCap.round;
    switch (kind) {
      case 'fire_mage':
        final f = Path()
          ..moveTo(p(0.5, 0.05).dx, p(0, 0.05).dy)
          ..cubicTo(p(0.85, 0.35).dx, p(0, 0.35).dy, p(0.95, 0.6).dx, p(0, 0.6).dy,
              p(0.75, 0.82).dx, p(0, 0.82).dy)
          ..cubicTo(p(0.65, 0.95).dx, p(0, 0.95).dy, p(0.35, 0.95).dx, p(0, 0.95).dy,
              p(0.25, 0.82).dx, p(0, 0.82).dy)
          ..cubicTo(p(0.05, 0.6).dx, p(0, 0.6).dy, p(0.25, 0.4).dx, p(0, 0.4).dy,
              p(0.38, 0.3).dx, p(0, 0.3).dy)
          ..cubicTo(p(0.4, 0.45).dx, p(0, 0.45).dy, p(0.45, 0.45).dx, p(0, 0.45).dy,
              p(0.5, 0.05).dx, p(0, 0.05).dy)
          ..close();
        canvas.drawPath(f, fill);
        canvas.drawOval(
            Rect.fromCenter(center: p(0.5, 0.74), width: s * 0.3, height: s * 0.34),
            Paint()..color = const Color(0xFFFFD84D));
      case 'ice_queen':
        for (var i = 0; i < 3; i++) {
          final a = i * math.pi / 3;
          final d = Offset(math.cos(a), math.sin(a)) * s * 0.42;
          canvas.drawLine(p(0.5, 0.5) - d, p(0.5, 0.5) + d, stroke);
        }
        for (var i = 0; i < 6; i++) {
          final a = i * math.pi / 3;
          final tip = p(0.5, 0.5) + Offset(math.cos(a), math.sin(a)) * s * 0.3;
          for (final k in [-0.6, 0.6]) {
            final b = tip + Offset(math.cos(a + k), math.sin(a + k)) * s * 0.1;
            canvas.drawLine(tip, b, stroke..strokeWidth = s * 0.05);
          }
        }
      case 'ninja':
        canvas.drawPath(star(p(0.5, 0.5), s * 0.46, points: 4, inner: 0.3), fill);
        canvas.drawCircle(p(0.5, 0.5), s * 0.09, white);
      case 'wizard':
        final hat = Path()
          ..moveTo(p(0.55, 0.04).dx, p(0, 0.04).dy)
          ..lineTo(p(0.88, 0.8).dx, p(0, 0.8).dy)
          ..lineTo(p(0.12, 0.8).dx, p(0, 0.8).dy)
          ..close();
        canvas.drawPath(hat, fill);
        canvas.drawRRect(
            RRect.fromRectAndRadius(
                Rect.fromLTRB(s * 0.04, s * 0.76, s * 0.96, s * 0.9), Radius.circular(s * 0.07)),
            fill);
        canvas.drawPath(star(p(0.5, 0.5), s * 0.13), Paint()..color = const Color(0xFFFFD84D));
      case 'samurai':
        final h = Path()
          ..moveTo(p(0.1, 0.8).dx, p(0, 0.8).dy)
          ..quadraticBezierTo(p(0.1, 0.2).dx, p(0, 0.2).dy, p(0.5, 0.2).dx, p(0, 0.2).dy)
          ..quadraticBezierTo(p(0.9, 0.2).dx, p(0, 0.2).dy, p(0.9, 0.8).dx, p(0, 0.8).dy)
          ..close();
        canvas.drawPath(h, fill);
        canvas.drawLine(p(0.5, 0.2), p(0.3, 0.02), stroke);
        canvas.drawLine(p(0.5, 0.2), p(0.7, 0.02), stroke);
        canvas.drawRect(Rect.fromLTRB(s * 0.2, s * 0.5, s * 0.8, s * 0.58), white);
      case 'superhero':
        final b = Path()
          ..moveTo(p(0.6, 0.04).dx, p(0, 0.04).dy)
          ..lineTo(p(0.2, 0.55).dx, p(0, 0.55).dy)
          ..lineTo(p(0.47, 0.55).dx, p(0, 0.55).dy)
          ..lineTo(p(0.38, 0.96).dx, p(0, 0.96).dy)
          ..lineTo(p(0.8, 0.4).dx, p(0, 0.4).dy)
          ..lineTo(p(0.53, 0.4).dx, p(0, 0.4).dy)
          ..close();
        canvas.drawPath(b, fill);
      case 'pirate':
        canvas.drawCircle(p(0.5, 0.45), s * 0.3, fill);
        canvas.drawRect(Rect.fromLTRB(s * 0.35, s * 0.68, s * 0.65, s * 0.86), fill);
        final e = Paint()..color = Colors.white;
        canvas.drawCircle(p(0.4, 0.45), s * 0.07, e);
        canvas.drawCircle(p(0.6, 0.45), s * 0.07, e);
        canvas.drawLine(p(0.1, 0.9), p(0.9, 0.95), stroke);
      case 'astronaut':
        canvas.drawCircle(p(0.5, 0.5), s * 0.44, fill);
        canvas.drawRRect(
            RRect.fromRectAndRadius(
                Rect.fromLTRB(s * 0.24, s * 0.34, s * 0.76, s * 0.66), Radius.circular(s * 0.14)),
            Paint()..color = const Color(0xFF1B1530));
        canvas.drawCircle(p(0.38, 0.45), s * 0.04, white);
      case 'angel':
        canvas.drawOval(
            Rect.fromCenter(center: p(0.5, 0.22), width: s * 0.6, height: s * 0.2),
            stroke..strokeWidth = s * 0.07);
        final w = Path()
          ..moveTo(p(0.5, 0.7).dx, p(0, 0.7).dy)
          ..quadraticBezierTo(p(0.0, 0.4).dx, p(0, 0.4).dy, p(0.05, 0.9).dx, p(0, 0.9).dy)
          ..quadraticBezierTo(p(0.3, 0.85).dx, p(0, 0.85).dy, p(0.5, 0.7).dx, p(0, 0.7).dy)
          ..moveTo(p(0.5, 0.7).dx, p(0, 0.7).dy)
          ..quadraticBezierTo(p(1.0, 0.4).dx, p(0, 0.4).dy, p(0.95, 0.9).dx, p(0, 0.9).dy)
          ..quadraticBezierTo(p(0.7, 0.85).dx, p(0, 0.85).dy, p(0.5, 0.7).dx, p(0, 0.7).dy);
        canvas.drawPath(w, fill);
      case 'devil':
        for (final sg in [-1.0, 1.0]) {
          final h = Path()
            ..moveTo(p(0.5 + sg * 0.12, 0.92).dx, p(0, 0.92).dy)
            ..quadraticBezierTo(p(0.5 + sg * 0.5, 0.8).dx, p(0, 0.8).dy,
                p(0.5 + sg * 0.32, 0.08).dx, p(0, 0.08).dy)
            ..quadraticBezierTo(p(0.5 + sg * 0.12, 0.5).dx, p(0, 0.5).dy,
                p(0.5 - sg * 0.02, 0.6).dx, p(0, 0.6).dy)
            ..close();
          canvas.drawPath(h, fill);
        }
      default:
        canvas.drawPath(star(p(0.5, 0.5), s * 0.45), fill);
    }
  }

  @override
  bool shouldRepaint(EmblemPainter old) => old.kind != kind || old.color != color;
}

/// Animated mini-effect for an aura card. Repaints with [t] (0..1, 8 s loop).
class AuraMiniPainter extends CustomPainter {
  AuraMiniPainter(this.kind, this.t) : super(repaint: t);

  final String kind;
  final Animation<double> t;

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    final v = t.value;
    final tau = math.pi * 2;
    double frac(double x) => x - x.floorToDouble();
    switch (kind) {
      case 'fire':
        for (var i = 0; i < 5; i++) {
          final x = w * (0.14 + i * 0.18);
          final fh = h * (0.45 + 0.18 * math.sin(v * tau * 8 + i * 1.7) + (i == 2 ? 0.12 : 0));
          final sway = math.sin(v * tau * 6 + i) * w * 0.03;
          final base = h * 1.02;
          final f = Path()
            ..moveTo(x - w * 0.09, base)
            ..quadraticBezierTo(x - w * 0.08, base - fh * 0.5, x + sway, base - fh)
            ..quadraticBezierTo(x + w * 0.08, base - fh * 0.5, x + w * 0.09, base)
            ..close();
          canvas.drawPath(f, Paint()..color = const Color(0xFFFF6A2B));
          canvas.drawPath(
              Path()
                ..moveTo(x - w * 0.04, base)
                ..quadraticBezierTo(x - w * 0.03, base - fh * 0.3, x + sway * 0.6, base - fh * 0.55)
                ..quadraticBezierTo(x + w * 0.03, base - fh * 0.3, x + w * 0.04, base)
                ..close(),
              Paint()..color = const Color(0xFFFFD84D));
        }
      case 'ice':
        for (var i = 0; i < 7; i++) {
          final y = frac(v * 2 + i * 0.143) * (h + 8) - 4;
          final x = w * (0.1 + 0.8 * frac(i * 0.37)) + math.sin(v * tau * 3 + i) * 4;
          final r = 2.5 + (i % 3);
          final pt = Paint()
            ..color = const Color(0xFFBFE9FF)
            ..strokeWidth = 1.2
            ..strokeCap = StrokeCap.round;
          for (var k = 0; k < 3; k++) {
            final a = k * math.pi / 3 + v * tau;
            final d = Offset(math.cos(a), math.sin(a)) * r;
            canvas.drawLine(Offset(x, y) - d, Offset(x, y) + d, pt);
          }
        }
      case 'lightning':
        final ph = frac(v * 4);
        final a = ph < 0.3 ? 1 - ph / 0.3 : 0.15;
        canvas.drawRect(Rect.fromLTWH(0, 0, w, h),
            Paint()..color = Color.fromRGBO(255, 240, 150, 0.25 * a));
        final b = Path()
          ..moveTo(w * 0.58, h * 0.05)
          ..lineTo(w * 0.34, h * 0.52)
          ..lineTo(w * 0.52, h * 0.52)
          ..lineTo(w * 0.42, h * 0.95)
          ..lineTo(w * 0.7, h * 0.4)
          ..lineTo(w * 0.52, h * 0.4)
          ..close();
        canvas.drawPath(b, Paint()..color = Color.fromRGBO(255, 224, 60, 0.35 + 0.65 * a));
      case 'hearts':
        for (var i = 0; i < 4; i++) {
          final f = frac(v * 2 + i * 0.25);
          final c = Offset(w * (0.2 + 0.2 * i) + math.sin(f * tau) * 4, h * (1.1 - f * 1.2));
          final k = 0.8 + 0.3 * (i % 2);
          final hp = Path()
            ..moveTo(c.dx, c.dy + 5 * k)
            ..cubicTo(c.dx - 9 * k, c.dy - 1 * k, c.dx - 4 * k, c.dy - 8 * k, c.dx, c.dy - 3 * k)
            ..cubicTo(c.dx + 4 * k, c.dy - 8 * k, c.dx + 9 * k, c.dy - 1 * k, c.dx, c.dy + 5 * k)
            ..close();
          canvas.drawPath(hp,
              Paint()..color = const Color(0xFFFF5C8A).withValues(alpha: math.sin(f * math.pi).clamp(0.0, 1.0)));
        }
      case 'sakura':
        for (var i = 0; i < 6; i++) {
          final f = frac(v * 2 + i * 0.167);
          final c = Offset(w * (frac(i * 0.29) * 0.9) + f * 14, f * (h + 10) - 5);
          canvas.save();
          canvas.translate(c.dx, c.dy);
          canvas.rotate(v * tau * 2 + i);
          canvas.drawOval(Rect.fromCenter(center: Offset.zero, width: 8, height: 5),
              Paint()..color = const Color(0xFFFFB7D0));
          canvas.restore();
        }
      case 'stars':
        const pos = [Offset(0.2, 0.3), Offset(0.7, 0.2), Offset(0.45, 0.55), Offset(0.82, 0.65), Offset(0.22, 0.8)];
        for (var i = 0; i < pos.length; i++) {
          final k = 0.5 + 0.5 * math.sin(v * tau * 4 + i * 1.3);
          canvas.drawPath(
              EmblemPainter.star(Offset(w * pos[i].dx, h * pos[i].dy), 3 + 5 * k, inner: 0.4),
              Paint()..color = const Color(0xFFFFE066).withValues(alpha: 0.4 + 0.6 * k));
        }
    }
  }

  @override
  bool shouldRepaint(AuraMiniPainter old) => old.kind != kind || old.t != t;
}
