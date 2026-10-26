import 'dart:async';

import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';

class ModelScreen extends StatefulWidget {
  const ModelScreen({super.key});

  @override
  State<ModelScreen> createState() => _ModelScreenState();
}

class _ModelScreenState extends State<ModelScreen> {
  static const Map<String, String> _names = {
    'e2b': 'Gemma 4 E2B',
    'e4b': 'Gemma 4 E4B',
  };
  static Map<String, String> get _sizes => {
        'e2b': tr('2.6 ГБ', en: '2.6 GB', uz: '2.6 GB'),
        'e4b': tr('3.7 ГБ', en: '3.7 GB', uz: '3.7 GB'),
      };
  static Map<String, String> get _ram => {
        'e2b': tr('телефоны с 4 ГБ+ памяти', en: 'phones with 4 GB+ of RAM', uz: '4 GB+ xotirali telefonlar'),
        'e4b': tr('телефоны с 6 ГБ+ памяти', en: 'phones with 6 GB+ of RAM', uz: '6 GB+ xotirali telefonlar'),
      };
  static const Map<String, double> _needRam = {'e2b': 4, 'e4b': 6};
  static const Map<String, double> _needFree = {'e2b': 3, 'e4b': 4.2};

  ModelStatus _st = const ModelStatus();
  String _provider = 'cloud';
  String _model = 'e2b';
  bool _loaded = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _load();
    _poll();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _poll());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final s = await Native.getSettings();
      if (!mounted) return;
      setState(() {
        _provider = s.provider == 'gemma' ? 'gemma' : 'cloud';
        _model = s.gemmaModel == 'e4b' ? 'e4b' : 'e2b';
        _loaded = true;
      });
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _poll() async {
    try {
      final st = await Native.modelStatus();
      if (mounted) setState(() => _st = st);
    } on NativeException {
      // next tick retries
    }
  }

  Future<void> _run(Future<void> Function() f) async {
    try {
      await f();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
    await _poll();
  }

  Future<void> _save(Map<String, Object?> v) => _run(() => Native.saveSettings(v));

  String _mb(int b) => '${(b / 1048576).toStringAsFixed(0)} ${tr('МБ', en: 'MB', uz: 'MB')}';

  Widget _card(String id, int index) {
    final cs = context.cs;
    final tt = context.tt;
    final m = _st[id];
    final selected = _model == id;
    final busy = m.state == 'downloading';
    final frac = m.total > 0 ? (m.done / m.total).clamp(0.0, 1.0) : null;
    final label = switch (m.state) {
      'downloading' => tr('Скачивается…', en: 'Downloading…', uz: 'Yuklanmoqda…'),
      'paused' => tr('Пауза', en: 'Paused', uz: 'Pauza'),
      'ready' => tr('Готова', en: 'Ready', uz: 'Tayyor'),
      'error' => tr('Ошибка: ${m.error ?? ''}', en: 'Error: ${m.error ?? ''}', uz: 'Xato: ${m.error ?? ''}'),
      _ => tr('Не скачана', en: 'Not downloaded', uz: 'Yuklanmagan'),
    };
    final pillColor = switch (m.state) {
      'ready' => BuddyColors.success,
      'downloading' => BuddyColors.sky,
      'paused' => BuddyColors.warning,
      'error' => cs.error,
      _ => cs.outline,
    };
    final warn = <String>[
      if (_st.ramGb > 0 && _st.ramGb < _needRam[id]!)
        tr('Мало памяти: нужно ${_needRam[id]!.toStringAsFixed(0)} ГБ+, у вас ${_st.ramGb.toStringAsFixed(1)} ГБ.',
            en: 'Not enough RAM: need ${_needRam[id]!.toStringAsFixed(0)} GB+, you have ${_st.ramGb.toStringAsFixed(1)} GB.',
            uz: 'Xotira yetmaydi: ${_needRam[id]!.toStringAsFixed(0)} GB+ kerak, sizda ${_st.ramGb.toStringAsFixed(1)} GB.'),
      if (_st.freeGb > 0 && m.state != 'ready' && _st.freeGb < _needFree[id]!)
        tr('Мало места: свободно ${_st.freeGb.toStringAsFixed(1)} ГБ.',
            en: 'Not enough space: ${_st.freeGb.toStringAsFixed(1)} GB free.',
            uz: 'Joy yetmaydi: ${_st.freeGb.toStringAsFixed(1)} GB boʻsh.'),
    ];
    return FadeSlideIn(
      index: index,
      child: Padding(
        padding: const EdgeInsets.only(bottom: Sp.m),
        child: SoftCard(
          borderColor: selected ? cs.primary : null,
          borderWidth: selected ? 2 : 1,
          onTap: () {
            setState(() => _model = id);
            _save({'gemmaModel': id});
          },
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Bounce(
                    trigger: selected,
                    child: AnimatedContainer(
                      duration: Dur.med,
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: selected ? cs.primary : Colors.transparent,
                        border: Border.all(
                          color: selected ? cs.primary : cs.outline,
                          width: 2,
                        ),
                      ),
                      child: selected
                          ? Icon(Icons.check_rounded, size: 18, color: cs.onPrimary)
                          : null,
                    ),
                  ),
                  const SizedBox(width: Sp.m),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('${_names[id]} (${_sizes[id]})', style: tt.titleMedium),
                        Text(_ram[id]!, style: tt.bodySmall),
                      ],
                    ),
                  ),
                  const SizedBox(width: Sp.s),
                  Flexible(child: StatusPill(label: label, color: pillColor, dense: true)),
                ],
              ),
              if (busy || m.state == 'paused') ...[
                const SizedBox(height: Sp.m),
                ClipRRect(
                  borderRadius: Rad.pillRadius,
                  child: LinearProgressIndicator(value: frac, minHeight: 8),
                ),
                const SizedBox(height: Sp.xs),
                Text(tr('${_mb(m.done)} из ${_mb(m.total)}', en: '${_mb(m.done)} of ${_mb(m.total)}', uz: '${_mb(m.total)} dan ${_mb(m.done)}'), style: tt.bodySmall),
              ],
              for (final w in warn)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.warning_amber_rounded, size: 16, color: cs.error),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(w, style: tt.bodySmall?.copyWith(color: cs.error)),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: Sp.m),
              Wrap(
                spacing: Sp.s,
                runSpacing: Sp.s,
                children: [
                  if (m.state == 'none' || m.state == 'paused' || m.state == 'error')
                    BuddyButton.filled(
                      onPressed: () => _run(() => Native.modelDownload(id)),
                      icon: const Icon(Icons.download_rounded),
                      child: Text(m.state == 'paused' ? tr('Продолжить', en: 'Resume', uz: 'Davom ettirish') : tr('Скачать', en: 'Download', uz: 'Yuklab olish')),
                    ),
                  if (busy)
                    BuddyButton.outline(
                      onPressed: () => _run(Native.modelCancel),
                      icon: const Icon(Icons.pause_rounded),
                      child: Text(tr('Пауза', en: 'Pause', uz: 'Pauza')),
                    ),
                  if (m.state != 'none' && !busy)
                    BuddyButton.text(
                      onPressed: () async {
                        final ok = await showDialog<bool>(
                          context: context,
                          builder: (c) => AlertDialog(
                            title: Text(tr('Удалить модель?', en: 'Delete the model?', uz: 'Model oʻchirilsinmi?')),
                            content: Text(tr('Файл модели будет удалён с телефона. Скачать её можно снова.',
                                en: 'The model file will be deleted from your phone. You can download it again.',
                                uz: 'Model fayli telefondan oʻchiriladi. Uni qayta yuklab olishingiz mumkin.')),
                            actions: [
                              BuddyButton.text(
                                onPressed: () => Navigator.of(c).pop(false),
                                child: Text(tr('Отмена', en: 'Cancel', uz: 'Bekor qilish')),
                              ),
                              BuddyButton.filled(
                                onPressed: () => Navigator.of(c).pop(true),
                                child: Text(tr('Удалить', en: 'Delete', uz: 'Oʻchirish')),
                              ),
                            ],
                          ),
                        );
                        if (ok == true) _run(() => Native.modelDelete(id));
                      },
                      icon: const Icon(Icons.delete_outline_rounded),
                      child: Text(tr('Удалить', en: 'Delete', uz: 'Oʻchirish')),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tt = context.tt;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Модель на телефоне', en: 'On-device model', uz: 'Telefondagi model'))),
      body: !_loaded
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(Sp.l, Sp.s, Sp.l, Sp.xl),
              children: [
                SoftCard(
                  gradient: BuddyGradients.header(context.isDark),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        tr('Gemma 4 работает прямо на телефоне, без интернета. Это бесплатно и не требует API-ключа.',
                            en: 'Gemma 4 runs right on your phone, with no internet. It is free and needs no API key.',
                            uz: 'Gemma 4 toʻgʻridan-toʻgʻri telefonda, internetsiz ishlaydi. Bepul va API kalit kerak emas.'),
                        style: tt.bodyMedium,
                      ),
                      const SizedBox(height: Sp.s),
                      Text(
                        tr('Память: ${_st.ramGb.toStringAsFixed(1)} ГБ, свободно: ${_st.freeGb.toStringAsFixed(1)} ГБ',
                            en: 'Memory: ${_st.ramGb.toStringAsFixed(1)} GB, free: ${_st.freeGb.toStringAsFixed(1)} GB',
                            uz: 'Xotira: ${_st.ramGb.toStringAsFixed(1)} GB, boʻsh: ${_st.freeGb.toStringAsFixed(1)} GB'),
                        style: tt.labelMedium,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: Sp.l),
                _card('e2b', 0),
                _card('e4b', 1),
                const SizedBox(height: Sp.s),
                SectionTitle(tr('Использовать', en: 'Use', uz: 'Foydalanish')),
                SegmentedButton<String>(
                  segments: [
                    ButtonSegment(value: 'cloud', label: Text(tr('Облако (ключ)', en: 'Cloud (key)', uz: 'Bulut (kalit)'))),
                    ButtonSegment(value: 'gemma', label: Text(tr('Модель на телефоне', en: 'On-device model', uz: 'Telefondagi model'))),
                  ],
                  selected: {_provider},
                  onSelectionChanged: (s) {
                    setState(() => _provider = s.first);
                    _save({'provider': s.first});
                  },
                ),
                Padding(
                  padding: const EdgeInsets.only(top: Sp.m),
                  child: Text(
                    tr('Первые ответы модели медленнее. Если модель не скачана, используется облачный ключ.',
                        en: 'The first replies from the model are slower. If the model is not downloaded, the cloud key is used.',
                        uz: 'Modelning dastlabki javoblari sekinroq. Model yuklanmagan boʻlsa, bulut kalitidan foydalaniladi.'),
                    style: tt.bodySmall,
                  ),
                ),
              ],
            ),
    );
  }
}
