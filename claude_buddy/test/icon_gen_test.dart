// Generates Bubby launcher icons. Run: flutter test test/icon_gen_test.dart
import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';

const _orange = ui.Color(0xFFF0883A);
const _cream = ui.Color(0xFFFFE6C8);
const _ink = ui.Color(0xFF1B1530);
const _red = ui.Color(0xFFE5365B);
const _white = ui.Color(0xFFFFFFFF);

void _background(ui.Canvas c, double s) {
  final rect = ui.Rect.fromLTWH(0, 0, s, s);
  c.drawRect(
    rect,
    ui.Paint()
      ..shader = ui.Gradient.linear(
        ui.Offset.zero,
        ui.Offset(s, s),
        const [
          ui.Color(0xFF7A6BC4),
          ui.Color(0xFFEE7BA3),
          ui.Color(0xFFF0883A),
        ],
        const [0, 0.55, 1],
      ),
  );
  c.drawRect(
    rect,
    ui.Paint()
      ..shader = ui.Gradient.radial(ui.Offset(s * .5, s * .52), s * .55, [
        const ui.Color(0x66FFFFFF),
        const ui.Color(0x00FFFFFF),
      ]),
  );
}

void _sparkle(ui.Canvas c, ui.Offset o, double r, ui.Paint p) {
  final path = ui.Path()
    ..moveTo(o.dx, o.dy - r)
    ..quadraticBezierTo(o.dx, o.dy, o.dx + r, o.dy)
    ..quadraticBezierTo(o.dx, o.dy, o.dx, o.dy + r)
    ..quadraticBezierTo(o.dx, o.dy, o.dx - r, o.dy)
    ..quadraticBezierTo(o.dx, o.dy, o.dx, o.dy - r)
    ..close();
  c.drawPath(path, p);
}

/// Foreground artwork in a unit-sized box of side [s]. [mono] draws a single
/// colour silhouette (features knocked out).
void _art(ui.Canvas c, double s, {required bool mono, bool sparkles = true}) {
  ui.Offset p(double x, double y) => ui.Offset(x * s, y * s);
  ui.Paint col(ui.Color k) => ui.Paint()..color = mono ? _white : k;
  final clear = ui.Paint()..blendMode = ui.BlendMode.clear;
  final fill = col(_orange), light = col(_cream);

  if (mono) c.saveLayer(ui.Rect.fromLTWH(0, 0, s, s), ui.Paint());

  // Sparkles
  if (sparkles) {
    final sp = ui.Paint()..color = _white;
    _sparkle(c, p(0.14, 0.17), s * 0.07, sp);
    _sparkle(c, p(0.86, 0.14), s * 0.09, sp);
    _sparkle(c, p(0.9, 0.48), s * 0.045, sp);
    _sparkle(c, p(0.08, 0.5), s * 0.04, sp);
  }

  // Face (cat), mapped into inner box so ears/cheeks fit
  c.save();
  const k = 0.80;
  c.translate(s * (1 - k) / 2, s * (1 - k) / 2 - s * 0.03);
  c.scale(k);
  for (final sign in [-1.0, 1.0]) {
    final ear = ui.Path()
      ..moveTo(p(0.5 + sign * 0.36, 0.5).dx, p(0, 0.5).dy)
      ..lineTo(p(0.5 + sign * 0.30, 0.10).dx, p(0, 0.10).dy)
      ..lineTo(p(0.5 + sign * 0.08, 0.28).dx, p(0, 0.28).dy)
      ..close();
    c.drawPath(ear, fill);
    final inner = ui.Path()
      ..moveTo(p(0.5 + sign * 0.31, 0.40).dx, p(0, 0.40).dy)
      ..lineTo(p(0.5 + sign * 0.28, 0.20).dx, p(0, 0.20).dy)
      ..lineTo(p(0.5 + sign * 0.16, 0.31).dx, p(0, 0.31).dy)
      ..close();
    c.drawPath(inner, mono ? clear : light);
  }
  c.drawOval(
    ui.Rect.fromCenter(center: p(0.5, 0.58), width: s * 0.86, height: s * 0.74),
    fill,
  );
  if (!mono) {
    c.drawOval(
      ui.Rect.fromCenter(
        center: p(0.5, 0.64),
        width: s * 0.62,
        height: s * 0.50,
      ),
      light,
    );
    // cheeks
    for (final sign in [-1.0, 1.0]) {
      c.drawOval(
        ui.Rect.fromCenter(
          center: p(0.5 + sign * 0.27, 0.68),
          width: s * .09,
          height: s * .05,
        ),
        ui.Paint()..color = const ui.Color(0x66EE7BA3),
      );
    }
  }
  final line = (mono ? clear : (ui.Paint()..color = _ink))
    ..style = ui.PaintingStyle.stroke
    ..strokeWidth = s * 0.04
    ..strokeCap = ui.StrokeCap.round;
  for (final sign in [-1.0, 1.0]) {
    final ctr = p(0.5 + sign * 0.14, 0.60);
    c.drawArc(
      ui.Rect.fromCenter(center: ctr, width: s * 0.13, height: s * 0.11),
      math.pi,
      math.pi,
      false,
      line,
    );
  }
  final dark = mono ? clear : (ui.Paint()..color = _ink);
  c.drawPath(
    ui.Path()
      ..moveTo(p(0.47, 0.68).dx, p(0, 0.68).dy)
      ..lineTo(p(0.53, 0.68).dx, p(0, 0.68).dy)
      ..lineTo(p(0.5, 0.71).dx, p(0, 0.71).dy)
      ..close(),
    mono ? clear : (ui.Paint()..color = _red),
  );
  c.drawPath(
    ui.Path()
      ..moveTo(p(0.43, 0.73).dx, p(0, 0.73).dy)
      ..quadraticBezierTo(
        p(0.5, 0.86).dx,
        p(0, 0.86).dy,
        p(0.57, 0.73).dx,
        p(0, 0.73).dy,
      )
      ..close(),
    dark,
  );
  c.restore();

  // Flame (left) and snowflake (right)
  _flame(c, p(0.14, 0.80), s * 0.15, mono);
  _snow(c, p(0.86, 0.80), s * 0.075, mono, clear);

  if (mono) c.restore();
}

void _flame(ui.Canvas c, ui.Offset o, double h, bool mono) {
  ui.Path f(double k) => ui.Path()
    ..moveTo(o.dx, o.dy - h * k)
    ..cubicTo(
      o.dx + h * .15 * k,
      o.dy - h * .55 * k,
      o.dx + h * .55 * k,
      o.dy - h * .25 * k,
      o.dx + h * .42 * k,
      o.dy + h * .25 * k,
    )
    ..cubicTo(
      o.dx + h * .3 * k,
      o.dy + h * .6 * k,
      o.dx - h * .3 * k,
      o.dy + h * .6 * k,
      o.dx - h * .42 * k,
      o.dy + h * .25 * k,
    )
    ..cubicTo(
      o.dx - h * .5 * k,
      o.dy - h * .1 * k,
      o.dx - h * .1 * k,
      o.dy - h * .35 * k,
      o.dx,
      o.dy - h * k,
    )
    ..close();
  c.drawPath(
    f(1),
    ui.Paint()..color = mono ? _white : const ui.Color(0xFFFF5A1F),
  );
  if (!mono) {
    c.drawPath(
      f(.6).shift(ui.Offset(0, h * .25)),
      ui.Paint()..color = const ui.Color(0xFFFFD23F),
    );
  }
}

void _snow(ui.Canvas c, ui.Offset o, double r, bool mono, ui.Paint clear) {
  final p = ui.Paint()
    ..color = mono ? _white : const ui.Color(0xFFBFEFFF)
    ..style = ui.PaintingStyle.stroke
    ..strokeWidth = r * 0.32
    ..strokeCap = ui.StrokeCap.round;
  if (!mono) {
    c.drawCircle(o, r * 1.35, ui.Paint()..color = const ui.Color(0xFF4F86E0));
  }
  for (var i = 0; i < 3; i++) {
    final a = i * math.pi / 3;
    final d = ui.Offset(math.cos(a), math.sin(a)) * r;
    c.drawLine(o - d, o + d, p);
  }
}

Future<List<int>> _png(int px, void Function(ui.Canvas, double) draw) async {
  final rec = ui.PictureRecorder();
  final c = ui.Canvas(rec);
  draw(c, px.toDouble());
  final img = await rec.endRecording().toImage(px, px);
  final bd = await img.toByteData(format: ui.ImageByteFormat.png);
  return bd!.buffer.asUint8List();
}

void _full(ui.Canvas c, double s) {
  _background(c, s);
  c.save();
  c.translate(s * .03, s * .03);
  c.scale(.94);
  _art(c, s, mono: false);
  c.restore();
}

void _layer(ui.Canvas c, double s, bool mono) {
  const k = 0.66;
  c.translate(s * (1 - k) / 2, s * (1 - k) / 2);
  c.scale(k);
  _art(c, s, mono: mono, sparkles: !mono);
}

void main() {
  testWidgets('generate Bubby icons', (tester) async {
    await tester.runAsync(() async {
      final res = 'android/app/src/main/res';
      const legacy = {
        'mdpi': 48,
        'hdpi': 72,
        'xhdpi': 96,
        'xxhdpi': 144,
        'xxxhdpi': 192,
      };
      const adaptive = {
        'mdpi': 108,
        'hdpi': 162,
        'xhdpi': 216,
        'xxhdpi': 324,
        'xxxhdpi': 432,
      };
      Future<void> w(String path, List<int> b) async {
        final f = File(path)..createSync(recursive: true);
        f.writeAsBytesSync(b);
      }

      for (final e in legacy.entries) {
        final d = '$res/mipmap-${e.key}';
        await w('$d/ic_launcher.png', await _png(e.value, _full));
        await w(
          '$d/ic_launcher_round.png',
          await _png(e.value, (c, s) {
            c.clipPath(ui.Path()..addOval(ui.Rect.fromLTWH(0, 0, s, s)));
            _full(c, s);
          }),
        );
      }
      for (final e in adaptive.entries) {
        final d = '$res/mipmap-${e.key}';
        await w(
          '$d/ic_launcher_foreground.png',
          await _png(e.value, (c, s) => _layer(c, s, false)),
        );
        await w(
          '$d/ic_launcher_monochrome.png',
          await _png(e.value, (c, s) => _layer(c, s, true)),
        );
      }
      await w(
        '$res/values/ic_launcher_background.xml',
        '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#7A6BC4</color>
</resources>
'''
            .codeUnits,
      );
      const xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
</adaptive-icon>
''';
      await w('$res/mipmap-anydpi-v26/ic_launcher.xml', xml.codeUnits);
      await w('$res/mipmap-anydpi-v26/ic_launcher_round.xml', xml.codeUnits);
      await w('../buby-icon-1024.png', await _png(1024, _full));
    });
  });
}
