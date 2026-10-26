import 'dart:math' as math;
import 'dart:async';

import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../mascot.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';
import 'ai_setup_screen.dart';
import 'music_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  // Language-neutral English instructions are saved as the persona text; labels are translated.
  static const List<String> _moods = [
    'funny and cheerful',
    'serious and precise',
    'warm and affectionate',
    'sarcastic',
    'sleepy',
    'energetic',
    '',
  ];

  // Old Russian values saved by earlier versions map to the same chips.
  static const Map<String, String> _legacy = {
    'Весёлый': 'funny and cheerful',
    'Серьёзный': 'serious and precise',
    'Ласковый': 'warm and affectionate',
    'Саркастичный': 'sarcastic',
    'Сонный': 'sleepy',
    'Энергичный': 'energetic',
  };

  String _moodLabel(String m) => switch (m) {
        'funny and cheerful' => tr('Весёлый', en: 'Cheerful', uz: 'Quvnoq'),
        'serious and precise' => tr('Серьёзный', en: 'Serious', uz: 'Jiddiy'),
        'warm and affectionate' => tr('Ласковый', en: 'Affectionate', uz: 'Mehribon'),
        'sarcastic' => tr('Саркастичный', en: 'Sarcastic', uz: 'Kinoyali'),
        'sleepy' => tr('Сонный', en: 'Sleepy', uz: 'Uyqusiragan'),
        'energetic' => tr('Энергичный', en: 'Energetic', uz: 'Shijoatli'),
        _ => tr('По умолчанию', en: 'Default', uz: 'Odatiy'),
      };

  final _persona = TextEditingController();
  List<String> _keys = const [];
  bool _loaded = false;
  bool _saved = false;
  Timer? _savedTimer;
  Timer? _textTimer;
  String _lang = 'auto';
  bool _tts = true;
  bool _voice = true;
  bool _conv = false;
  bool _study = false;
  bool _hotword = true;
  bool _stickerAuto = true;
  bool _autostart = true;
  bool _screenAgent = true;
  bool _deviceReactions = true;
  bool _stickersOnEvents = true;
  bool _statusBadge = true;
  bool _stateGlow = true;
  bool _readNotifs = false;
  bool _quiet = false;
  bool _autohide = true;
  int _messageDelay = 4;
  String _musicApp = '';
  List<MusicApp> _apps = const [];
  double _size = 1.0;
  String _species = 'cat';
  Timer? _sizeTimer;

  @override
  void initState() {
    super.initState();
    _load();
    _loadMusic();
  }

  @override
  void dispose() {
    _textTimer?.cancel();
    _savedTimer?.cancel();
    _persona.dispose();
    _sizeTimer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final s = await Native.getSettings();
      if (!mounted) return;
      setState(() {
        _keys = s.keys;
        _persona.text = _legacy[s.persona] ?? s.persona;
        _lang = const ['auto', 'uz', 'ru', 'en'].contains(s.lang) ? s.lang : 'auto';
        _tts = s.tts;
        _voice = s.voice;
        _conv = s.conv;
        _study = s.study;
        _hotword = s.hotword;
        _messageDelay = const [0, 4, 8].contains(s.messageDelay) ? s.messageDelay : 4;
        _stickerAuto = s.stickerAuto;
        _autostart = s.autostart;
        _screenAgent = s.screenAgent;
        _deviceReactions = s.deviceReactions;
        _stickersOnEvents = s.stickersOnEvents;
        _statusBadge = s.statusBadge;
        _stateGlow = s.stateGlow;
        _readNotifs = s.readNotifs;
        _quiet = s.quiet;
        _autohide = s.autohide;
        _musicApp = s.musicApp;
        _size = s.size;
        _species = s.species;
        _loaded = true;
      });
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _loadMusic() async {
    try {
      final s = await Native.getSettings();
      final apps = await Native.musicApps();
      if (!mounted) return;
      setState(() {
        _musicApp = s.musicApp;
        _apps = apps;
      });
    } on NativeException {
      // Music summary is optional; ignore failures.
    }
  }

  Future<void> _openMusic() async {
    await Navigator.of(context)
        .push(BubbyRoute<void>(builder: (_) => const MusicScreen()));
    if (mounted) _loadMusic();
  }

  String get _musicTitle {
    if (_musicApp.isEmpty) return tr('Автоматически', en: 'Automatic', uz: 'Avtomatik');
    for (final a in _apps) {
      if (a.id == _musicApp) return a.label;
    }
    return _musicApp;
  }

  String get _musicSummary {
    final names = [
      for (final a in _apps)
        if (a.installed) a.label,
    ];
    return names.isEmpty
        ? tr('Поддерживаемые приложения не найдены', en: 'No supported apps found', uz: 'Qoʻllab-quvvatlanadigan ilovalar topilmadi')
        : tr('Установлено: ${names.join(', ')}', en: 'Installed: ${names.join(', ')}', uz: 'Oʻrnatilgan: ${names.join(', ')}');
  }

  // the slider is logarithmic so both tiny (x0.15) and huge (x6) sizes are easy to reach
  static const double _sizeMin = 0.15;
  static const double _sizeMax = 6.0;
  double _sizeToSlider(double s) => math.log(s.clamp(_sizeMin, _sizeMax) / _sizeMin) / math.log(_sizeMax / _sizeMin);
  double _sliderToSize(double t) => _sizeMin * math.pow(_sizeMax / _sizeMin, t).toDouble();

  void _setSize(double v, {bool now = false}) {
    v = (v.clamp(_sizeMin, _sizeMax) * 100).round() / 100;
    setState(() => _size = v);
    _sizeTimer?.cancel();
    if (now) {
      _persistSize();
    } else {
      _sizeTimer = Timer(const Duration(milliseconds: 250), _persistSize);
    }
  }

  Future<void> _persistSize() async {
    try {
      await Native.saveSize(_size);
      _flashSaved();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Widget _sizeCard() {
    final cs = context.cs;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          height: 160,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: cs.surfaceContainer,
            borderRadius: BorderRadius.circular(Rad.field),
          ),
          child: Mascot(size: 72 * _size.clamp(_sizeMin, 2.2), species: _species),
        ),
        const SizedBox(height: Sp.m),
        Row(
          children: [
            Expanded(
              child: Slider(
                value: _sizeToSlider(_size),
                min: 0,
                max: 1,
                label: '×${_size.toStringAsFixed(2)}',
                onChanged: (t) => _setSize(_sliderToSize(t)),
              ),
            ),
            SizedBox(
              width: 56,
              child: Text('×${_size.toStringAsFixed(2)}',
                  textAlign: TextAlign.end, style: context.tt.titleMedium),
            ),
          ],
        ),
        Row(
          children: [
            Expanded(
              child: BuddyButton.tonal(
                onPressed: () => _setSize(_size * 0.8, now: true),
                child: Text(tr('Меньше', en: 'Smaller', uz: 'Kichikroq')),
              ),
            ),
            const SizedBox(width: Sp.s),
            Expanded(
              child: BuddyButton.tonal(
                onPressed: () => _setSize(1.0, now: true),
                child: Text(tr('Обычный', en: 'Normal', uz: 'Odatiy')),
              ),
            ),
            const SizedBox(width: Sp.s),
            Expanded(
              child: BuddyButton.tonal(
                onPressed: () => _setSize(_size * 1.25, now: true),
                child: Text(tr('Больше', en: 'Larger', uz: 'Kattaroq')),
              ),
            ),
          ],
        ),
        const SizedBox(height: Sp.m),
        Text(tr('Положение', en: 'Position', uz: 'Joylashuv'), style: context.tt.titleSmall),
        const SizedBox(height: Sp.s),
        Wrap(
          spacing: Sp.s,
          runSpacing: Sp.s,
          children: [
            for (final d in _docks)
              _MoodChip(
                label: d.$1,
                selected: _dock == d.$2,
                onTap: () => _dockTo(d.$2),
              ),
          ],
        ),
        const SizedBox(height: Sp.s),
        Text(
          tr('Чтобы Bubby не мешал, уменьшите его и поставьте у зарядки — он будет внизу по центру',
              en: 'To keep Bubby out of the way, make him smaller and put him by the charging port — he will sit at the bottom center',
              uz: 'Bubby halaqit bermasligi uchun uni kichraytiring va zaryadlash joyiga qoʻying — u pastda, oʻrtada turadi'),
          style: context.tt.bodySmall,
        ),
        const SizedBox(height: Sp.s),
        Text(
          tr('Уменьшите Bubby и поставьте рядом с часами — он не будет закрывать видео и интерфейс',
              en: 'Make Bubby smaller and put him next to the clock — he will not cover videos or the interface',
              uz: 'Bubby’ni kichraytirib, soat yoniga qoʻying — u video va interfeysni yopmaydi'),
          style: context.tt.bodySmall,
        ),
        const SizedBox(height: Sp.m),
        Text(
          tr('Также можно менять размер двумя пальцами прямо на экране или голосом: «Bubby, стань больше»',
              en: 'You can also resize with two fingers right on the screen or by voice: “Bubby, get bigger”',
              uz: 'Oʻlchamini ekranda ikki barmoq bilan yoki ovoz bilan ham oʻzgartirish mumkin: «Bubby, kattaroq boʻl»'),
          style: context.tt.bodySmall,
        ),
      ],
    );
  }

  List<(String, String)> get _docks => [
        (tr('Сверху', en: 'Top', uz: 'Tepada'), 'top'),
        (tr('У зарядки (снизу)', en: 'By the charger (bottom)', uz: 'Zaryadlash yonida (pastda)'), 'bottom'),
        (tr('Слева', en: 'Left', uz: 'Chapda'), 'left'),
        (tr('Справа', en: 'Right', uz: 'Oʻngda'), 'right'),
        (tr('По центру', en: 'Center', uz: 'Oʻrtada'), 'center'),
        (tr('В строке состояния (у часов)', en: 'In the status bar (by the clock)', uz: 'Holat qatorida (soat yonida)'), 'status_left'),
        (tr('В строке состояния (справа)', en: 'In the status bar (right)', uz: 'Holat qatorida (oʻngda)'), 'status_right'),
      ];
  String _dock = '';

  Future<void> _dockTo(String where) async {
    setState(() => _dock = where);
    try {
      await Native.dockTo(where);
      _flashSaved();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  void _flashSaved() {
    if (!mounted) return;
    setState(() => _saved = true);
    _savedTimer?.cancel();
    _savedTimer = Timer(const Duration(milliseconds: 1800), () {
      if (mounted) setState(() => _saved = false);
    });
  }

  /// Saves only the changed key.
  Future<void> _save(String key, Object? value) async {
    try {
      await Native.saveSettings({key: value});
      _flashSaved();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  void _onPersona(String _) {
    _textTimer?.cancel();
    _textTimer = Timer(const Duration(milliseconds: 400),
        () => _save('persona', _persona.text.trim()));
  }

  Future<void> _openAi() async {
    await Navigator.of(context)
        .push(BubbyRoute<void>(builder: (_) => const AiSetupScreen()));
    if (mounted) _load();
  }

  Widget _aiCard() {
    final cs = context.cs;
    final tt = context.tt;
    final n = _keys.length;
    final summary = n == 0
        ? tr('Пока не подключено', en: 'Not connected yet', uz: 'Hali ulanmagan')
        : tr('Основное: ${describeAiSpec(_keys.first).title}${n > 1 ? ' · запасных: ${n - 1}' : ''}',
            en: 'Main: ${describeAiSpec(_keys.first).title}${n > 1 ? ' · backups: ${n - 1}' : ''}',
            uz: 'Asosiy: ${describeAiSpec(_keys.first).title}${n > 1 ? ' · zaxira: ${n - 1}' : ''}');
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.all(Sp.m),
          decoration: BoxDecoration(
            color: cs.primaryContainer,
            borderRadius: BorderRadius.circular(Rad.field),
          ),
          child: Row(
            children: [
              Icon(Icons.vpn_key_rounded, color: cs.onPrimaryContainer),
              const SizedBox(width: Sp.m),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(tr('ИИ-подключения: $n', en: 'AI connections: $n', uz: 'AI ulanishlari: $n'),
                        style: tt.titleMedium
                            ?.copyWith(color: cs.onPrimaryContainer)),
                    Text(summary,
                        style: tt.bodySmall
                            ?.copyWith(color: cs.onPrimaryContainer)),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: Sp.m),
        BuddyButton.filled(
          onPressed: _openAi,
          icon: const Icon(Icons.tune_rounded),
          child: Text(n == 0 ? tr('Подключить ИИ', en: 'Connect AI', uz: 'AI ulash') : tr('Управлять подключениями', en: 'Manage connections', uz: 'Ulanishlarni boshqarish')),
        ),
      ],
    );
  }

  void _pickMood(String m) {
    _persona.text = m;
    _textTimer?.cancel();
    setState(() {});
    _save('persona', _persona.text.trim());
  }

  Widget _group(int index, String title, IconData icon, List<Widget> children) {
    return FadeSlideIn(
      index: index,
      child: Padding(
        padding: const EdgeInsets.only(bottom: Sp.l),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SectionTitle(title, icon: icon),
            SoftCard(
              padding: const EdgeInsets.all(Sp.l),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: children,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        GradientHeader(
          title: tr('Настройки', en: 'Settings', uz: 'Sozlamalar'),
          subtitle: tr('Подстройте Bubby под себя', en: 'Tune Bubby to your liking', uz: 'Bubby’ni oʻzingizga moslang'),
        ),
        AnimatedSwitcher(
          duration: Dur.fast,
          child: _saved
              ? Padding(
                  key: const ValueKey('saved'),
                  padding: const EdgeInsets.only(top: Sp.s, right: Sp.l),
                  child: Align(
                    alignment: Alignment.centerRight,
                    child: Text(tr('Сохранено ✓', en: 'Saved ✓', uz: 'Saqlandi ✓'),
                        style: context.tt.labelMedium
                            ?.copyWith(color: context.cs.onSurfaceVariant)),
                  ),
                )
              : const SizedBox(key: ValueKey('nosaved'), height: Sp.s),
        ),
        Expanded(
          child: !_loaded
              ? const Center(child: CircularProgressIndicator())
              : ListView(
                  padding: const EdgeInsets.fromLTRB(Sp.l, Sp.l, Sp.l, Sp.xl),
                  children: [
                    _group(0, tr('Искусственный интеллект', en: 'Artificial intelligence', uz: 'Sunʼiy intellekt'), Icons.auto_awesome_rounded, [
                      _aiCard(),
                      const SizedBox(height: Sp.m),
                      DropdownButtonFormField<String>(
                        initialValue: _lang,
                        isExpanded: true,
                        borderRadius: BorderRadius.circular(Rad.field),
                        decoration: const InputDecoration(labelText: 'Язык / Language / Til'),
                        items: [
                          DropdownMenuItem(value: 'auto', child: Text(tr('Авто', en: 'Auto', uz: 'Avto'))),
                          const DropdownMenuItem(value: 'uz', child: Text('Oʻzbekcha')),
                          const DropdownMenuItem(value: 'ru', child: Text('Русский')),
                          const DropdownMenuItem(value: 'en', child: Text('English')),
                        ],
                        onChanged: (v) {
                          setState(() => _lang = v ?? 'auto');
                          appLang.value = _lang;
                          _save('lang', _lang);
                        },
                      ),
                      const SizedBox(height: Sp.s),
                      Text(
                        tr('Это также язык, на котором Bubby говорит и отвечает',
                            en: 'This is also the language Bubby speaks and answers in',
                            uz: 'Bu Bubby gapiradigan va javob beradigan til hamdir'),
                        style: context.tt.bodySmall,
                      ),
                    ]),
                    FadeSlideIn(
                      index: 1,
                      child: Padding(
                        padding: const EdgeInsets.only(bottom: Sp.l),
                        child: SoftCard(
                          padding: const EdgeInsets.all(Sp.m),
                          onTap: _openMusic,
                          child: Row(
                            children: [
                              Container(
                                width: 52,
                                height: 52,
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(18),
                                  gradient: BuddyGradients.violet,
                                ),
                                child: const Icon(Icons.music_note_rounded,
                                    color: Colors.white, size: 26),
                              ),
                              const SizedBox(width: Sp.l),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(tr('Музыка и приложения', en: 'Music and apps', uz: 'Musiqa va ilovalar'),
                                        style: context.tt.titleMedium),
                                    const SizedBox(height: 2),
                                    Text(tr('Приложение: $_musicTitle', en: 'App: $_musicTitle', uz: 'Ilova: $_musicTitle'),
                                        style: context.tt.bodyMedium),
                                    Text(_musicSummary,
                                        style: context.tt.bodySmall),
                                  ],
                                ),
                              ),
                              Icon(Icons.chevron_right_rounded,
                                  color: context.cs.onSurfaceVariant),
                            ],
                          ),
                        ),
                      ),
                    ),
                    _group(1, tr('Голос и поведение', en: 'Voice and behavior', uz: 'Ovoz va xulq'), Icons.record_voice_over_rounded, [
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Озвучивать ответы', en: 'Speak replies aloud', uz: 'Javoblarni ovoz chiqarib aytish')),
                        value: _tts,
                        onChanged: (v) {
                          setState(() => _tts = v);
                          _save('tts', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Голосовой ввод по касанию', en: 'Voice input on tap', uz: 'Bosganda ovozli kiritish')),
                        value: _voice,
                        onChanged: (v) {
                          setState(() => _voice = v);
                          _save('voice', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Режим диалога', en: 'Conversation mode', uz: 'Suhbat rejimi')),
                        subtitle: Text(tr('Слушать снова после ответа', en: 'Listen again after a reply', uz: 'Javobdan keyin yana tinglash')),
                        value: _conv,
                        onChanged: (v) {
                          setState(() => _conv = v);
                          _save('conv', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Звать по имени: «Bubby»', en: 'Call by name: “Bubby”', uz: 'Ism bilan chaqirish: «Bubby»')),
                        subtitle: Text(tr('Индикатор микрофона остаётся включённым, батарея расходуется больше; скажите «Bubby» — и он послушает',
                            en: 'The microphone indicator stays on and it uses more battery; say “Bubby” and he will listen',
                            uz: "Mikrofon indikatori yoniq turadi va batareya ko'proq sarflanadi; «Bubby» deng — u tinglaydi")),
                        value: _hotword,
                        onChanged: (v) {
                          setState(() => _hotword = v);
                          _save('hotword', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Зачитывать уведомления', en: 'Read notifications aloud', uz: "Bildirishnomalarni ovoz chiqarib o'qish")),
                        subtitle: Text(tr('Нужен доступ к уведомлениям; читает вслух только при включённом экране', en: 'Needs notification access; reads aloud only while the screen is on', uz: "Bildirishnomalarga ruxsat kerak; faqat ekran yoniq paytida o'qiydi")),
                        value: _readNotifs,
                        onChanged: (v) {
                          setState(() => _readNotifs = v);
                          _save('readNotifs', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Режим учёбы', en: 'Study mode', uz: 'Oʻqish rejimi')),
                        subtitle: Text(tr('Bubby объясняет шаг за шагом и проверяет вас вопросами',
                            en: 'Bubby explains step by step and quizzes you',
                            uz: 'Bubby bosqichma-bosqich tushuntiradi va savollar bilan tekshiradi')),
                        value: _study,
                        onChanged: (v) {
                          setState(() => _study = v);
                          _save('study', v);
                        },
                      ),
                    ]),
                    _group(2, tr('Поведение Bubby', en: "Bubby's behaviour", uz: 'Bubby xulq-atvori'), Icons.tune_rounded, [
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Разрешить Bubby читать экран', en: 'Let Bubby read the screen', uz: "Bubby ekranni o'qishiga ruxsat")),
                        subtitle: Text(tr('Так он может нажимать и печатать за вас; тексты банковских приложений и паролей не читаются; текст экрана уходит выбранному ИИ-сервису', en: 'It is how he can tap and type for you; texts of banking and password apps are never read; screen text is sent to your chosen AI service', uz: "Shu bilan u siz uchun bosadi va yozadi; bank va parol ilovalari matni o'qilmaydi; ekran matni tanlangan AI xizmatiga yuboriladi")),
                        value: _screenAgent,
                        onChanged: (v) {
                          setState(() => _screenAgent = v);
                          _save('screenAgent', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Реагировать на зарядку и наушники', en: 'React to charging and headphones', uz: 'Zaryadka va quloqchinga munosabat bildirish')),
                        value: _deviceReactions,
                        onChanged: (v) {
                          setState(() => _deviceReactions = v);
                          _save('deviceReactions', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Стикеры при реакциях', en: 'Stickers on reactions', uz: 'Reaksiyalarda stiklerlar')),
                        value: _stickersOnEvents,
                        onChanged: (v) {
                          setState(() => _stickersOnEvents = v);
                          _save('stickersOnEvents', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Отвечать стикерами', en: 'Reply with stickers', uz: 'Stikerlar bilan javob berish')),
                        value: _stickerAuto,
                        onChanged: (v) {
                          setState(() => _stickerAuto = v);
                          _save('stickerAuto', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Запускать после перезагрузки', en: 'Start after reboot', uz: 'Telefon qayta yonganda ishga tushirish')),
                        value: _autostart,
                        onChanged: (v) {
                          setState(() => _autostart = v);
                          _save('autostart', v);
                        },
                      ),
                    ]),
                    _group(2, tr('Сообщения', en: 'Messages', uz: 'Xabarlar'), Icons.sms_rounded, [
                      DropdownButtonFormField<int>(
                        initialValue: _messageDelay,
                        borderRadius: BorderRadius.circular(Rad.field),
                        decoration: InputDecoration(
                            labelText: tr('Пауза перед отправкой', en: 'Pause before sending', uz: 'Yuborishdan oldin pauza')),
                        items: [
                          DropdownMenuItem(value: 0, child: Text(tr('Сразу', en: 'Immediately', uz: 'Darhol'))),
                          DropdownMenuItem(value: 4, child: Text(tr('4 с', en: '4 s', uz: '4 s'))),
                          DropdownMenuItem(value: 8, child: Text(tr('8 с', en: '8 s', uz: '8 s'))),
                        ],
                        onChanged: (v) {
                          final d = v ?? 4;
                          setState(() => _messageDelay = d);
                          _save('messageDelay', d);
                        },
                      ),
                    ]),
                    _group(2, tr('Вид Bubby', en: 'Bubby look', uz: 'Bubby koʻrinishi'), Icons.palette_rounded, [
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Значок состояния', en: 'Status badge', uz: 'Holat belgisi')),
                        subtitle: Text(tr('Маленький значок на голове: думает, слушает, ждёт подтверждения, лимит, ошибка, готово', en: 'A small badge on the head: thinking, listening, waiting for approval, limit reached, error, done', uz: 'Boshdagi kichik belgi: oʻylayapti, tinglayapti, tasdiq kutyapti, limit tugadi, xato, tayyor')),
                        value: _statusBadge,
                        onChanged: (v) {
                          setState(() => _statusBadge = v);
                          _save('statusBadge', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Цветное свечение', en: 'Coloured glow', uz: 'Rangli nur')),
                        subtitle: Text(tr('Мягкое свечение цвета текущего состояния вокруг Bubby', en: 'A soft glow around Bubby in the colour of the current state', uz: 'Bubby atrofida hozirgi holat rangidagi yumshoq nur')),
                        value: _stateGlow,
                        onChanged: (v) {
                          setState(() => _stateGlow = v);
                          _save('stateGlow', v);
                        },
                      ),
                    ]),
                    _group(2, tr('Размер Bubby', en: 'Bubby size', uz: 'Bubby oʻlchami'), Icons.zoom_out_map_rounded, [_sizeCard()]),
                    _group(2, tr('Не мешать при просмотре', en: 'Stay out of the way during videos', uz: 'Video koʻrganda halaqit bermasin'), Icons.movie_rounded, [
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Скрыть Bubby сейчас', en: 'Hide Bubby now', uz: 'Bubby’ni hozir yashirish')),
                        subtitle: Text(tr('Вернуть можно здесь, из шторки уведомлений или голосом: «Bubby»',
                            en: 'You can bring him back here, from the notification shade, or by voice: “Bubby”',
                            uz: 'Uni shu yerdan, bildirishnomalar pardasidan yoki ovoz bilan qaytarish mumkin: «Bubby»')),
                        value: _quiet,
                        onChanged: (v) {
                          setState(() => _quiet = v);
                          _save('quiet', v);
                        },
                      ),
                      BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Прятать в полноэкранном видео', en: 'Hide during fullscreen video', uz: 'Toʻliq ekran videoda yashirish')),
                        subtitle: Text(tr('Bubby сам скрывается, когда видео на весь экран, и возвращается после',
                            en: 'Bubby hides on his own when a video is fullscreen and comes back afterwards',
                            uz: 'Video butun ekranda boʻlganda Bubby oʻzi yashirinadi va keyin qaytadi')),
                        value: _autohide,
                        onChanged: (v) {
                          setState(() => _autohide = v);
                          _save('autohide', v);
                        },
                      ),
                    ]),
                    _group(2, tr('Характер', en: 'Personality', uz: 'Xarakter'), Icons.mood_rounded, [
                      TextField(
                        controller: _persona,
                        onChanged: _onPersona,
                        decoration: InputDecoration(
                          labelText: tr('Характер / настроение', en: 'Personality / mood', uz: 'Xarakter / kayfiyat'),
                        ),
                      ),
                      const SizedBox(height: Sp.m),
                      ListenableBuilder(
                        listenable: _persona,
                        builder: (context, _) {
                          final cur = _persona.text.trim();
                          return Wrap(
                            spacing: Sp.s,
                            runSpacing: Sp.s,
                            children: [
                              for (final m in _moods)
                                _MoodChip(
                                  label: _moodLabel(m),
                                  selected: cur == m,
                                  onTap: () => _pickMood(m),
                                ),
                            ],
                          );
                        },
                      ),
                    ]),
                  ],
                ),
        ),
      ],
    );
  }
}

class _MoodChip extends StatelessWidget {
  const _MoodChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Bounce(
      trigger: selected,
      amount: 0.1,
      child: PressScale(
        onTap: onTap,
        child: AnimatedContainer(
          duration: Dur.med,
          curve: Curves.easeOutCubic,
          constraints: const BoxConstraints(minHeight: 48),
          alignment: Alignment.center,
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
          decoration: BoxDecoration(
            color: selected ? cs.primaryContainer : cs.surfaceContainer,
            borderRadius: Rad.pillRadius,
            border: Border.all(
              color: selected ? cs.primary : cs.outlineVariant,
              width: selected ? 1.5 : 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              AnimatedSize(
                duration: Dur.med,
                curve: Curves.easeOutCubic,
                child: selected
                    ? Padding(
                        padding: const EdgeInsets.only(right: 6),
                        child: Icon(Icons.check_rounded, size: 16, color: cs.onPrimaryContainer),
                      )
                    : const SizedBox.shrink(),
              ),
              Text(
                label,
                style: context.tt.labelLarge?.copyWith(
                  color: selected ? cs.onPrimaryContainer : cs.onSurface,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
