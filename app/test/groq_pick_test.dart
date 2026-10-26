import 'package:buddy/l10n/l10n.dart';
import 'package:buddy/screens/ai_setup_screen.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const ids = [
    'llama-3.3-70b-versatile',
    'openai/gpt-oss-20b',
    'qwen/qwen3.8-27b',
    'openai/gpt-oss-120b',
  ];

  tearDown(() => appLang.value = 'auto');

  test('Uzbek puts the newest Qwen first', () {
    appLang.value = 'uz';
    expect(pickGroqModels(ids), ['qwen/qwen3.8-27b', 'openai/gpt-oss-120b', 'openai/gpt-oss-20b']);
  });

  test('other languages keep gpt-oss-120b first', () {
    appLang.value = 'en';
    expect(pickGroqModels(ids), ['openai/gpt-oss-120b', 'qwen/qwen3.8-27b', 'openai/gpt-oss-20b']);
  });

  test('Uzbek without Qwen falls back to the usual order', () {
    appLang.value = 'uz';
    expect(pickGroqModels(['openai/gpt-oss-20b', 'openai/gpt-oss-120b']), ['openai/gpt-oss-120b', 'openai/gpt-oss-20b']);
  });
}
