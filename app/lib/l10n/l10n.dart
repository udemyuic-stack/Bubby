import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';

/// UI language: 'auto' (follow the phone), 'ru', 'uz' or 'en'. Changing it rebuilds the whole app (see BubbyApp).
final ValueNotifier<String> appLang = ValueNotifier<String>('auto');

/// The language actually in use: always 'ru', 'uz' or 'en'.
String get currentLang {
  final v = appLang.value;
  if (v == 'ru' || v == 'uz' || v == 'en') return v;
  final code = ui.PlatformDispatcher.instance.locale.languageCode;
  return (code == 'ru' || code == 'uz') ? code : 'en';
}

/// Picks the text for the current language. Write every user-visible string through this:
/// `tr('Привет', en: 'Hello', uz: 'Salom')`. Russian is the base text.
String tr(String ru, {required String en, required String uz}) {
  switch (currentLang) {
    case 'en':
      return en;
    case 'uz':
      return uz;
    default:
      return ru;
  }
}

/// Plural helper for counts: [one] for 1, 21…; [few] for 2–4, 22–24… (Russian), [many] otherwise.
/// English and Uzbek use [one] for 1 and [many] for everything else.
String plural(int n, {required String one, required String few, required String many}) {
  if (currentLang != 'ru') return n == 1 ? one : many;
  final m10 = n % 10, m100 = n % 100;
  if (m10 == 1 && m100 != 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}

const List<String> _monthsRu = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
const List<String> _monthsEn = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const List<String> _monthsUz = ['yanvar', 'fevral', 'mart', 'aprel', 'may', 'iyun', 'iyul', 'avgust', 'sentabr', 'oktabr', 'noyabr', 'dekabr'];
const List<String> _daysRu = ['понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота', 'воскресенье'];
const List<String> _daysEn = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const List<String> _daysUz = ['dushanba', 'seshanba', 'chorshanba', 'payshanba', 'juma', 'shanba', 'yakshanba'];

/// Month name for 1..12 (Russian in the genitive: «5 января»).
String monthName(int month) => switch (currentLang) { 'en' => _monthsEn, 'uz' => _monthsUz, _ => _monthsRu }[month - 1];

/// Weekday name for DateTime.weekday (1 = Monday).
String weekdayName(int weekday) => switch (currentLang) { 'en' => _daysEn, 'uz' => _daysUz, _ => _daysRu }[weekday - 1];

/// Short weekday (first 3 letters, capitalised).
String weekdayShort(int weekday) {
  final s = weekdayName(weekday);
  final t = s.length > 3 ? s.substring(0, 3) : s;
  return t[0].toUpperCase() + t.substring(1);
}

/// «5 January» / «5 января» / «5-yanvar».
String dayMonth(DateTime d) => currentLang == 'uz' ? '${d.day}-${monthName(d.month)}' : '${d.day} ${monthName(d.month)}';
