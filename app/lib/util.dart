import 'package:flutter/material.dart';

import 'l10n/l10n.dart';

void showMessage(BuildContext context, Object message) {
  final m = ScaffoldMessenger.maybeOf(context);
  if (m == null) return;
  m
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message.toString())));
}

String two(int n) => n.toString().padLeft(2, '0');

String fmtTime(DateTime d) => '${two(d.hour)}:${two(d.minute)}';

String fmtDate(DateTime d) => '${two(d.day)}.${two(d.month)}.${d.year}';

String fmtDateTime(DateTime d) => '${fmtDate(d)} ${fmtTime(d)}';

String fmtDayHeader(DateTime d) {
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(d.year, d.month, d.day);
  final diff = day.difference(today).inDays;
  final wd = weekdayName(d.weekday);
  final base = '${dayMonth(d)}, ${currentLang == 'en' ? wd : wd[0].toUpperCase() + wd.substring(1)}';
  if (diff == 0) return tr('Сегодня, $base', en: 'Today, $base', uz: 'Bugun, $base');
  if (diff == 1) return tr('Завтра, $base', en: 'Tomorrow, $base', uz: 'Ertaga, $base');
  return base;
}

Future<DateTime?> pickDateTime(BuildContext context, DateTime initial) async {
  final date = await showDatePicker(
    context: context,
    initialDate: initial,
    firstDate: DateTime.now().subtract(const Duration(days: 365)),
    lastDate: DateTime.now().add(const Duration(days: 365 * 5)),
  );
  if (date == null || !context.mounted) return null;
  final time = await showTimePicker(
    context: context,
    initialTime: TimeOfDay.fromDateTime(initial),
  );
  if (time == null) return null;
  return DateTime(date.year, date.month, date.day, time.hour, time.minute);
}

int _dayDiff(DateTime d) {
  final now = DateTime.now();
  final a = DateTime.utc(d.year, d.month, d.day);
  final b = DateTime.utc(now.year, now.month, now.day);
  return a.difference(b).inDays;
}

/// Short human date for due chips: «Сегодня, 18:00», «Завтра, 09:30», «05.11, 12:00».
String fmtDue(DateTime d) {
  final diff = _dayDiff(d);
  if (diff == 0) return tr('Сегодня, ${fmtTime(d)}', en: 'Today, ${fmtTime(d)}', uz: 'Bugun, ${fmtTime(d)}');
  if (diff == 1) return tr('Завтра, ${fmtTime(d)}', en: 'Tomorrow, ${fmtTime(d)}', uz: 'Ertaga, ${fmtTime(d)}');
  if (diff == -1) return tr('Вчера, ${fmtTime(d)}', en: 'Yesterday, ${fmtTime(d)}', uz: 'Kecha, ${fmtTime(d)}');
  return '${two(d.day)}.${two(d.month)}, ${fmtTime(d)}';
}

List<String> get weekdaysShortRu => switch (currentLang) {
      'en' => const ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'],
      'uz' => const ['Du', 'Se', 'Ch', 'Pa', 'Ju', 'Sh', 'Ya'],
      _ => const ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'],
    };
