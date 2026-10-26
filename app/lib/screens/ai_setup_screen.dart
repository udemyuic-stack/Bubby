import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/l10n.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';

/// Human description of a saved key spec.
class AiSpecInfo {
  const AiSpecInfo(this.title, this.model, this.masked);
  final String title;
  final String model;
  final String masked;
}

String _mask(String k) {
  if (k.isEmpty) return '';
  if (k.length <= 8) return '••••';
  final head = k.startsWith('sk-or-')
      ? 'sk-or-'
      : k.startsWith('sk-ant')
      ? 'sk-ant-'
      : k.startsWith('AIza')
      ? 'AIza'
      : k.substring(0, 3);
  return '$head••••${k.substring(k.length - 4)}';
}

AiSpecInfo describeAiSpec(String spec) {
  final s = spec.trim();
  if (s.startsWith('AIza')) {
    return AiSpecInfo('Google Gemini', 'gemini', _mask(s));
  }
  if (s.startsWith('sk-ant')) {
    return AiSpecInfo('Anthropic', 'claude', _mask(s));
  }
  final p = s.split('|');
  if (p.length >= 2) {
    final url = p[0];
    final model = p[1];
    final token = p.length > 2 ? p[2] : '';
    final String name;
    if (url.contains('openrouter.ai')) {
      name = 'OpenRouter';
    } else if (url.contains('groq.com')) {
      name = 'Groq';
    } else {
      name = tr('Свой сервер', en: 'Custom server', uz: 'Oʻz serveringiz');
    }
    return AiSpecInfo(
      name,
      model,
      token.isEmpty
          ? url.replaceFirst(RegExp(r'^https?://'), '')
          : _mask(token),
    );
  }
  return AiSpecInfo(tr('ИИ-ключ', en: 'AI key', uz: 'AI kaliti'), '', _mask(s));
}

class _Provider {
  const _Provider({
    required this.id,
    required this.name,
    required this.icon,
    required this.tagline,
    required this.url,
    required this.siteLabel,
    required this.steps,
    required this.note,
    this.models = const [],
    this.custom = false,
  });

  final String id;
  final String name;
  final IconData icon;
  final String tagline;
  final String url;
  final String siteLabel;
  final List<String> steps;
  final String note;
  final List<String> models;
  final bool custom;
}

List<_Provider> get _providers => [
  _Provider(
    id: 'openrouter',
    name: 'OpenRouter',
    icon: Icons.hub_rounded,
    tagline: tr(
      'Бесплатные модели, в том числе Gemma 4',
      en: 'Free models, including Gemma 4',
      uz: 'Bepul modellar, jumladan Gemma 4',
    ),
    url: 'https://openrouter.ai/keys',
    siteLabel: 'openrouter.ai/keys',
    steps: [
      tr(
        'Откройте openrouter.ai/keys',
        en: 'Open openrouter.ai/keys',
        uz: 'openrouter.ai/keys sahifasini oching',
      ),
      tr(
        'Войдите и нажмите Create Key',
        en: 'Sign in and press Create Key',
        uz: 'Hisobingizga kiring va Create Key tugmasini bosing',
      ),
      tr(
        'Скопируйте ключ sk-or-…',
        en: 'Copy the key sk-or-…',
        uz: 'sk-or-… kalitini nusxalang',
      ),
      tr('Вставьте его сюда', en: 'Paste it here', uz: 'Uni shu yerga qoʻying'),
    ],
    note: tr(
      'Бесплатные модели: примерно 20 запросов в минуту и около 50 в день. Это число может меняться — проверьте на сайте.',
      en: 'Free models: about 20 requests per minute and around 50 per day. These numbers may change — check the website.',
      uz: 'Bepul modellar: daqiqasiga taxminan 20 ta va kuniga taxminan 50 ta soʻrov. Bu raqamlar oʻzgarishi mumkin — saytdan tekshiring.',
    ),
    models: [
      'google/gemma-4-31b-it:free',
      'google/gemma-4-26b-a4b-it:free',
      'nvidia/nemotron-3-super-120b-a12b:free',
      'openrouter/free',
    ],
  ),
  _Provider(
    id: 'gemini',
    name: 'Google Gemini',
    icon: Icons.auto_awesome_rounded,
    tagline: tr(
      'Ключ AIza… из Google AI Studio',
      en: 'AIza… key from Google AI Studio',
      uz: 'Google AI Studioʻdan AIza… kaliti',
    ),
    url: 'https://aistudio.google.com/apikey',
    siteLabel: 'aistudio.google.com',
    steps: [
      tr(
        'Откройте aistudio.google.com',
        en: 'Open aistudio.google.com',
        uz: 'aistudio.google.com sahifasini oching',
      ),
      tr(
        'Нажмите Get API key',
        en: 'Press Get API key',
        uz: 'Get API key tugmasini bosing',
      ),
      tr(
        'Скопируйте ключ AIza…',
        en: 'Copy the key AIza…',
        uz: 'AIza… kalitini nusxalang',
      ),
      tr('Вставьте его сюда', en: 'Paste it here', uz: 'Uni shu yerga qoʻying'),
    ],
    note: tr(
      'Бесплатных запросов в день немного — они могут закончиться.',
      en: 'There are only a few free requests per day — they can run out.',
      uz: 'Kuniga bepul soʻrovlar kam — ular tugab qolishi mumkin.',
    ),
  ),
  _Provider(
    id: 'anthropic',
    name: 'Anthropic',
    icon: Icons.psychology_rounded,
    tagline: tr(
      'Платный сервис (Claude)',
      en: 'Paid service (Claude)',
      uz: 'Pullik xizmat (Claude)',
    ),
    url: 'https://console.anthropic.com/settings/keys',
    siteLabel: 'console.anthropic.com',
    steps: [
      tr(
        'Откройте console.anthropic.com',
        en: 'Open console.anthropic.com',
        uz: 'console.anthropic.com sahifasini oching',
      ),
      tr(
        'Пополните баланс и создайте ключ в API Keys',
        en: 'Top up your balance and create a key in API Keys',
        uz: 'Balansni toʻldiring va API Keys boʻlimida kalit yarating',
      ),
      tr(
        'Скопируйте ключ sk-ant-…',
        en: 'Copy the key sk-ant-…',
        uz: 'sk-ant-… kalitini nusxalang',
      ),
      tr('Вставьте его сюда', en: 'Paste it here', uz: 'Uni shu yerga qoʻying'),
    ],
    note: tr(
      'Сервис платный: расходуется баланс вашего аккаунта.',
      en: 'The service is paid: your account balance is used up.',
      uz: 'Xizmat pullik: hisobingizdagi balans sarflanadi.',
    ),
  ),
  _Provider(
    id: 'custom',
    name: tr(
      'Свой сервер / Ollama',
      en: 'Custom server / Ollama',
      uz: 'Oʻz serveringiz / Ollama',
    ),
    icon: Icons.dns_rounded,
    tagline: tr(
      'Любой OpenAI-совместимый сервер',
      en: 'Any OpenAI-compatible server',
      uz: 'Har qanday OpenAI bilan mos server',
    ),
    url: 'https://ollama.com',
    siteLabel: 'ollama.com',
    steps: [
      tr(
        'Запустите Ollama или другой сервер на своём компьютере',
        en: 'Run Ollama or another server on your computer',
        uz: 'Kompyuteringizda Ollama yoki boshqa serverni ishga tushiring',
      ),
      tr(
        'Узнайте его адрес, например http://192.168.1.5:11434',
        en: 'Find out its address, for example http://192.168.1.5:11434',
        uz: 'Uning manzilini bilib oling, masalan http://192.168.1.5:11434',
      ),
      tr(
        'Укажите адрес и название модели ниже',
        en: 'Enter the address and the model name below',
        uz: 'Manzil va model nomini quyida kiriting',
      ),
    ],
    note: tr(
      'Работает только пока этот компьютер включён и доступен телефону (например, в одной Wi-Fi сети).',
      en: 'Works only while this computer is on and reachable from the phone (for example, on the same Wi-Fi network).',
      uz: 'Faqat shu kompyuter yoqilgan va telefon unga ulana olgandagina ishlaydi (masalan, bitta Wi-Fi tarmogʻida).',
    ),
    custom: true,
  ),
];

class AiSetupScreen extends StatefulWidget {
  const AiSetupScreen({super.key});

  @override
  State<AiSetupScreen> createState() => _AiSetupScreenState();
}

class _AiSetupScreenState extends State<AiSetupScreen>
    with WidgetsBindingObserver {
  List<String> _keys = [];
  bool _loaded = false;
  String? _open;
  int? _testing;
  String? _status;
  bool _statusOk = false;
  bool _advanced = false;
  String? _clip;
  final _groqKey = GlobalKey<_GroqCardState>();
  final Map<String, GlobalKey<_ProviderCardState>> _provKeys = {
    for (final p in _providers) p.id: GlobalKey<_ProviderCardState>(),
  };

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
    _checkClipboard();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _checkClipboard();
  }

  static bool _looksLikeKey(String t) =>
      !t.contains(RegExp(r'\s')) &&
      t.length > 20 &&
      (t.startsWith('gsk_') || t.startsWith('sk-or-') || t.startsWith('AIza'));

  Future<void> _checkClipboard() async {
    try {
      final d = await Clipboard.getData(Clipboard.kTextPlain);
      final t = d?.text?.trim() ?? '';
      if (!mounted) return;
      final known = _keys.any((k) => k.contains(t));
      setState(() => _clip = _looksLikeKey(t) && !known ? t : null);
    } catch (_) {
      // Clipboard may be unavailable; the banner is optional.
    }
  }

  void _useClip() {
    final t = _clip;
    if (t == null) return;
    final id = t.startsWith('gsk_')
        ? 'groq'
        : t.startsWith('sk-or-')
        ? 'openrouter'
        : 'gemini';
    setState(() {
      _clip = null;
      _open = id;
      if (id != 'groq') _advanced = true;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (id == 'groq') {
        _groqKey.currentState?.fill(t);
      } else {
        _provKeys[id]?.currentState?.fill(t);
      }
    });
  }

  Future<void> _load() async {
    try {
      final s = await Native.getSettings();
      if (!mounted) return;
      setState(() {
        _keys = List.of(s.keys);
        _loaded = true;
        if (_keys.isEmpty) _open = 'groq';
      });
    } on NativeException catch (e) {
      if (mounted) {
        setState(() => _loaded = true);
        showMessage(context, e);
      }
    }
  }

  Future<bool> _persist(List<String> next) async {
    try {
      await Native.saveKeys(next);
      if (!mounted) return false;
      setState(() => _keys = next);
      return true;
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
      return false;
    }
  }

  void _move(int i, int d) {
    final j = i + d;
    if (j < 0 || j >= _keys.length) return;
    final next = List.of(_keys);
    final t = next.removeAt(i);
    next.insert(j, t);
    _persist(next);
  }

  Future<void> _delete(int i) async {
    final info = describeAiSpec(_keys[i]);
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(
          tr(
            'Удалить подключение?',
            en: 'Delete this connection?',
            uz: 'Ulanish oʻchirilsinmi?',
          ),
        ),
        content: Text(
          tr(
            '${info.title} ${info.masked} будет удалено с телефона.',
            en: '${info.title} ${info.masked} will be removed from this phone.',
            uz: '${info.title} ${info.masked} telefondan oʻchiriladi.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c, false),
            child: Text(tr('Отмена', en: 'Cancel', uz: 'Bekor qilish')),
          ),
          TextButton(
            onPressed: () => Navigator.pop(c, true),
            child: Text(tr('Удалить', en: 'Delete', uz: 'Oʻchirish')),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final next = List.of(_keys)..removeAt(i);
    _persist(next);
  }

  Future<void> _test(int i) async {
    setState(() {
      _testing = i;
      _status = null;
    });
    String msg;
    bool ok = false;
    try {
      final r = await Native.testAi(_keys[i]);
      ok = r.ok;
      msg = ok
          ? tr(
              'Подключение работает',
              en: 'The connection works',
              uz: 'Ulanish ishlayapti',
            )
          : _hint(r.message);
    } on NativeException catch (e) {
      msg = _hint(e.message);
    }
    if (!mounted) return;
    setState(() {
      _testing = null;
      _status = msg;
      _statusOk = ok;
    });
    if (ok) Confetti.burst(context); // connection verified: confetti + haptic
  }

  Future<bool> _add(String spec) async {
    if (_keys.contains(spec)) {
      showMessage(
        context,
        tr(
          'Такое подключение уже добавлено',
          en: 'This connection is already added',
          uz: 'Bunday ulanish allaqachon qoʻshilgan',
        ),
      );
      return false;
    }
    final ok = await _persist([..._keys, spec]);
    if (ok && mounted) {
      setState(() {
        _open = null;
        _status = tr(
          'Подключение добавлено',
          en: 'Connection added',
          uz: 'Ulanish qoʻshildi',
        );
        _statusOk = true;
      });
    }
    return ok;
  }

  Future<bool> _addMany(List<String> specs) async {
    final next = List.of(_keys);
    for (final sp in specs) {
      if (!next.contains(sp)) next.add(sp);
    }
    return _persist(next);
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    return Scaffold(
      body: Column(
        children: [
          GradientHeader(
            title: tr('Подключение ИИ', en: 'AI connection', uz: 'AI ulanishi'),
            subtitle: tr(
              'Личный ключ для Bubby',
              en: 'Your personal key for Bubby',
              uz: 'Bubby uchun shaxsiy kalit',
            ),
          ),
          Expanded(
            child: !_loaded
                ? const SkeletonList(count: 4, cardHeight: 92)
                : ListView(
                    padding: const EdgeInsets.fromLTRB(Sp.l, Sp.l, Sp.l, Sp.xl),
                    children: [
                      SoftCard(
                        color: cs.primaryContainer,
                        borderColor: cs.primary.withAlpha(90),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              tr(
                                'Bubby думает с помощью ИИ. Чтобы им пользоваться, нужен ваш личный бесплатный пропуск — «ключ». Он как логин: ваш, не для других. Можно добавить несколько: если один закончится, Bubby сам перейдёт к следующему.',
                                en: 'Bubby thinks with the help of AI. To use it you need your own free pass — a “key”. It works like a login: it is yours, not for others. You can add several: if one runs out, Bubby switches to the next one by itself.',
                                uz: 'Bubby AI yordamida oʻylaydi. Undan foydalanish uchun sizga shaxsiy bepul ruxsatnoma — «kalit» (key) kerak. U login kabi: faqat sizniki, boshqalar uchun emas. Bir nechtasini qoʻshish mumkin: biri tugasa, Bubby oʻzi keyingisiga oʻtadi.',
                              ),
                              style: tt.bodyMedium?.copyWith(
                                color: cs.onPrimaryContainer,
                              ),
                            ),
                            const SizedBox(height: Sp.s),
                            Row(
                              children: [
                                Icon(
                                  Icons.lock_outline_rounded,
                                  size: 16,
                                  color: cs.onPrimaryContainer,
                                ),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: Text(
                                    tr(
                                      'Ключ хранится только на этом телефоне и отправляется только выбранному сервису.',
                                      en: 'The key is stored only on this phone and is sent only to the service you chose.',
                                      uz: 'Kalit faqat shu telefonda saqlanadi va faqat siz tanlagan xizmatga yuboriladi.',
                                    ),
                                    style: tt.bodySmall?.copyWith(
                                      color: cs.onPrimaryContainer,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      if (_clip != null) ...[
                        const SizedBox(height: Sp.m),
                        SoftCard(
                          color: cs.secondaryContainer,
                          borderColor: cs.secondary.withAlpha(90),
                          padding: const EdgeInsets.all(Sp.m),
                          child: Row(
                            children: [
                              Icon(
                                Icons.content_paste_rounded,
                                color: cs.onSecondaryContainer,
                              ),
                              const SizedBox(width: Sp.s),
                              Expanded(
                                child: Text(
                                  tr(
                                    'В буфере похоже на ключ — вставить?',
                                    en: 'The clipboard looks like a key — paste it?',
                                    uz: 'Buferda kalitga oʻxshash narsa bor — qoʻyaymi?',
                                  ),
                                  style: tt.bodyMedium?.copyWith(
                                    color: cs.onSecondaryContainer,
                                  ),
                                ),
                              ),
                              const SizedBox(width: Sp.s),
                              BuddyButton.tonal(
                                onPressed: _useClip,
                                child: Text(
                                  tr('Вставить', en: 'Paste', uz: 'Qoʻyish'),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                      if (_status != null) ...[
                        const SizedBox(height: Sp.m),
                        SoftCard(
                          color: _statusOk
                              ? cs.secondaryContainer
                              : cs.errorContainer,
                          borderColor: (_statusOk ? cs.secondary : cs.error)
                              .withAlpha(90),
                          padding: const EdgeInsets.all(Sp.m),
                          child: Row(
                            children: [
                              Icon(
                                _statusOk
                                    ? Icons.check_circle_rounded
                                    : Icons.error_outline_rounded,
                                color: _statusOk
                                    ? cs.onSecondaryContainer
                                    : cs.onErrorContainer,
                              ),
                              const SizedBox(width: Sp.s),
                              Expanded(
                                child: Text(
                                  _status!,
                                  style: tt.bodyMedium?.copyWith(
                                    color: _statusOk
                                        ? cs.onSecondaryContainer
                                        : cs.onErrorContainer,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                      const SizedBox(height: Sp.l),
                      SectionTitle(
                        tr(
                          'Ваши подключения',
                          en: 'Your connections',
                          uz: 'Ulanishlaringiz',
                        ),
                        icon: Icons.vpn_key_rounded,
                      ),
                      if (_keys.isEmpty)
                        SoftCard(
                          child: Text(
                            tr(
                              'Пока пусто. Начните с Groq ниже и добавьте ключ.',
                              en: 'Nothing here yet. Start with Groq below and add a key.',
                              uz: 'Hozircha boʻsh. Pastdagi Groqdan boshlang va kalit qoʻshing.',
                            ),
                            style: tt.bodyMedium,
                          ),
                        )
                      else
                        for (var i = 0; i < _keys.length; i++)
                          Padding(
                            padding: const EdgeInsets.only(bottom: Sp.m),
                            child: _connCard(i),
                          ),
                      const SizedBox(height: Sp.m),
                      SectionTitle(
                        tr(
                          'Groq — быстрый старт',
                          en: 'Groq — quick start',
                          uz: 'Groq — tez boshlash',
                        ),
                        icon: Icons.bolt_rounded,
                      ),
                      Padding(
                        padding: const EdgeInsets.only(bottom: Sp.m),
                        child: _GroqCard(
                          key: _groqKey,
                          expanded: _open == 'groq',
                          onToggle: () => setState(
                            () => _open = _open == 'groq' ? null : 'groq',
                          ),
                          onConnect: _addMany,
                        ),
                      ),
                      const SizedBox(height: Sp.s),
                      Semantics(
                        button: true,
                        expanded: _advanced,
                        label: tr(
                          'Для продвинутых',
                          en: 'For advanced users',
                          uz: 'Tajribali foydalanuvchilar uchun',
                        ),
                        child: InkWell(
                          borderRadius: BorderRadius.circular(Rad.field),
                          onTap: () => setState(() => _advanced = !_advanced),
                          child: ConstrainedBox(
                            constraints: const BoxConstraints(minHeight: 56),
                            child: Row(
                              children: [
                                Icon(
                                  Icons.add_circle_outline_rounded,
                                  color: cs.onSurfaceVariant,
                                ),
                                const SizedBox(width: Sp.s),
                                Expanded(
                                  child: Text(
                                    tr(
                                      'Для продвинутых',
                                      en: 'For advanced users',
                                      uz: 'Tajribali foydalanuvchilar uchun',
                                    ),
                                    style: tt.titleMedium,
                                  ),
                                ),
                                Icon(
                                  _advanced
                                      ? Icons.expand_less_rounded
                                      : Icons.expand_more_rounded,
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                      if (_advanced) ...[
                        Padding(
                          padding: const EdgeInsets.only(bottom: Sp.m),
                          child: Text(
                            tr(
                              'Другие сервисы: OpenRouter, Gemini, Anthropic и свой сервер.',
                              en: 'Other services: OpenRouter, Gemini, Anthropic and your own server.',
                              uz: 'Boshqa xizmatlar: OpenRouter, Gemini, Anthropic va oʻz serveringiz.',
                            ),
                            style: tt.bodySmall,
                          ),
                        ),
                        for (final p in _providers)
                          Padding(
                            padding: const EdgeInsets.only(bottom: Sp.m),
                            child: _ProviderCard(
                              key: _provKeys[p.id],
                              provider: p,
                              expanded: _open == p.id,
                              onToggle: () => setState(
                                () => _open = _open == p.id ? null : p.id,
                              ),
                              onAdd: _add,
                            ),
                          ),
                      ],
                      const SizedBox(height: Sp.m),
                      SoftCard(
                        color: cs.tertiaryContainer,
                        borderColor: cs.tertiary.withAlpha(90),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(
                                  Icons.lightbulb_outline_rounded,
                                  color: cs.onTertiaryContainer,
                                ),
                                const SizedBox(width: Sp.s),
                                Expanded(
                                  child: Text(
                                    tr(
                                      'Если ответы перестали приходить',
                                      en: 'If answers stop coming',
                                      uz: 'Agar javoblar kelmay qolsa',
                                    ),
                                    style: tt.titleMedium?.copyWith(
                                      color: cs.onTertiaryContainer,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: Sp.s),
                            Text(
                              tr(
                                'Скорее всего, закончились бесплатные запросы на сегодня. Подождите до завтра, добавьте запасное подключение другого сервиса или выберите модель на телефоне (Ещё → Модель на телефоне).',
                                en: 'Most likely the free requests for today have run out. Wait until tomorrow, add a backup connection to another service, or pick an on-phone model (More → On-phone model).',
                                uz: 'Ehtimol, bugungi bepul soʻrovlar tugagan. Ertagacha kuting, boshqa xizmatdan zaxira ulanish qoʻshing yoki telefondagi modelni tanlang (Yana → Telefondagi model).',
                              ),
                              style: tt.bodyMedium?.copyWith(
                                color: cs.onTertiaryContainer,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  Widget _connCard(int i) {
    final cs = context.cs;
    final tt = context.tt;
    final info = describeAiSpec(_keys[i]);
    final badge = i == 0
        ? tr('Основной', en: 'Primary', uz: 'Asosiy')
        : tr(
            'Запасной №${i + 1}',
            en: 'Backup #${i + 1}',
            uz: 'Zaxira №${i + 1}',
          );
    final testing = _testing == i;
    return SoftCard(
      padding: const EdgeInsets.all(Sp.m),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _Avatar(info.title),
              const SizedBox(width: Sp.m),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(info.title, style: tt.titleMedium),
                    if (info.model.isNotEmpty)
                      Text(
                        info.model,
                        style: tt.bodySmall,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    Text(info.masked, style: tt.bodySmall),
                  ],
                ),
              ),
              StatusPill(
                label: badge,
                color: i == 0 ? BuddyColors.success : cs.outline,
              ),
            ],
          ),
          const SizedBox(height: Sp.s),
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            alignment: WrapAlignment.spaceBetween,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  BuddyIconButton(
                    tooltip: tr(
                      'Сделать основным выше',
                      en: 'Move up (make primary)',
                      uz: 'Yuqoriga olish (asosiy qilish)',
                    ),
                    icon: const Icon(Icons.arrow_upward_rounded),
                    onPressed: i == 0 ? null : () => _move(i, -1),
                  ),
                  BuddyIconButton(
                    tooltip: tr(
                      'Сдвинуть ниже',
                      en: 'Move down',
                      uz: 'Pastga tushirish',
                    ),
                    icon: const Icon(Icons.arrow_downward_rounded),
                    onPressed: i == _keys.length - 1 ? null : () => _move(i, 1),
                  ),
                ],
              ),
              Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  BuddyButton.tonal(
                    onPressed: _testing != null ? null : () => _test(i),
                    loading: testing,
                    icon: const Icon(Icons.network_check_rounded),
                    child: Text(
                      testing
                          ? tr(
                              'Проверяю…',
                              en: 'Checking…',
                              uz: 'Tekshirilmoqda…',
                            )
                          : tr('Проверить', en: 'Check', uz: 'Tekshirish'),
                    ),
                  ),
                  BuddyIconButton(
                    tooltip: tr(
                      'Удалить подключение',
                      en: 'Delete connection',
                      uz: 'Ulanishni oʻchirish',
                    ),
                    icon: Icon(Icons.delete_outline_rounded, color: cs.error),
                    onPressed: () => _delete(i),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}

String _hint(String raw) {
  final m = raw.toLowerCase();
  String hint;
  if (m.contains('401') ||
      m.contains('403') ||
      m.contains('unauthor') ||
      m.contains('invalid') ||
      m.contains('api key')) {
    hint = tr(
      'Ключ неверный или не подходит к этому сервису.',
      en: 'The key is wrong or does not fit this service.',
      uz: 'Kalit notoʻgʻri yoki bu xizmatga mos kelmaydi.',
    );
  } else if (m.contains('429') || m.contains('quota') || m.contains('limit')) {
    hint = tr(
      'Бесплатные запросы на сегодня закончились. Подождите или выберите другую модель.',
      en: 'Free requests for today have run out. Wait or choose another model.',
      uz: 'Bugungi bepul soʻrovlar tugadi. Kuting yoki boshqa modelni tanlang.',
    );
  } else if (m.contains('timeout') ||
      m.contains('timed out') ||
      m.contains('unknownhost') ||
      m.contains('unable to resolve') ||
      m.contains('connect') ||
      m.contains('network')) {
    hint = tr(
      'Нет связи с сервисом. Проверьте интернет и адрес.',
      en: 'Cannot reach the service. Check your internet and the address.',
      uz: 'Xizmatga ulanib boʻlmadi. Internet va manzilni tekshiring.',
    );
  } else if (m.contains('404')) {
    hint = tr(
      'Модель или адрес не найдены. Проверьте название модели.',
      en: 'Model or address not found. Check the model name.',
      uz: 'Model yoki manzil topilmadi. Model nomini tekshiring.',
    );
  } else {
    hint = tr(
      'Проверка не удалась.',
      en: 'The check failed.',
      uz: 'Tekshirib boʻlmadi.',
    );
  }
  return hint;
}

class _Avatar extends StatelessWidget {
  const _Avatar(this.name);
  final String name;

  @override
  Widget build(BuildContext context) {
    final letters = name
        .split(RegExp(r'\s+'))
        .where((w) => w.isNotEmpty)
        .take(2)
        .map((w) => w[0].toUpperCase())
        .join();
    final base = hourAccent(DateTime.now());
    return Container(
      width: 44,
      height: 44,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [base, Color.lerp(base, Colors.black, 0.25) ?? base],
        ),
      ),
      child: Text(
        letters,
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w700,
          fontSize: 16,
        ),
      ),
    );
  }
}

class _ProviderCard extends StatefulWidget {
  const _ProviderCard({
    super.key,
    required this.provider,
    required this.expanded,
    required this.onToggle,
    required this.onAdd,
  });

  final _Provider provider;
  final bool expanded;
  final VoidCallback onToggle;
  final Future<bool> Function(String spec) onAdd;

  @override
  State<_ProviderCard> createState() => _ProviderCardState();
}

class _ProviderCardState extends State<_ProviderCard> {
  final _key = TextEditingController();
  final _url = TextEditingController();
  final _model = TextEditingController();
  bool _obscure = true;
  bool _busy = false;
  String? _error;
  late String _selected;

  @override
  void initState() {
    super.initState();
    _selected = widget.provider.models.isEmpty
        ? ''
        : widget.provider.models.first;
  }

  @override
  void dispose() {
    _key.dispose();
    _url.dispose();
    _model.dispose();
    super.dispose();
  }

  void fill(String t) => setState(() => _key.text = t);

  Future<void> _paste() async {
    final d = await Clipboard.getData(Clipboard.kTextPlain);
    final t = d?.text?.trim() ?? '';
    if (t.isEmpty) {
      if (mounted) {
        showMessage(
          context,
          tr(
            'Буфер обмена пуст',
            en: 'The clipboard is empty',
            uz: 'Bufer boʻsh',
          ),
        );
      }
      return;
    }
    setState(() => _key.text = t);
  }

  Future<void> _site() async {
    try {
      await Native.openUrl(widget.provider.url);
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  String? _compose() {
    final p = widget.provider;
    final key = _key.text.trim();
    bool bad(String s) => s.contains(RegExp(r'\s'));
    if (p.custom) {
      final url = _url.text.trim();
      final model = _model.text.trim();
      if (url.isEmpty || model.isEmpty || bad(url) || bad(model) || bad(key)) {
        setState(
          () => _error = tr(
            'Укажите адрес и модель без пробелов.',
            en: 'Enter the address and the model without spaces.',
            uz: 'Manzil va modelni boʻsh joysiz kiriting.',
          ),
        );
        return null;
      }
      if (!url.startsWith('http')) {
        setState(
          () => _error = tr(
            'Адрес должен начинаться с http:// или https://',
            en: 'The address must start with http:// or https://',
            uz: 'Manzil http:// yoki https:// bilan boshlanishi kerak',
          ),
        );
        return null;
      }
      return key.isEmpty ? '$url|$model' : '$url|$model|$key';
    }
    if (key.isEmpty || bad(key)) {
      setState(
        () => _error = tr(
          'Вставьте ключ без пробелов.',
          en: 'Paste the key without spaces.',
          uz: 'Kalitni boʻsh joysiz qoʻying.',
        ),
      );
      return null;
    }
    switch (p.id) {
      case 'openrouter':
        return 'https://openrouter.ai/api|$_selected|$key';
      case 'groq':
        return 'https://api.groq.com/openai|$_selected|$key';
      default:
        return key;
    }
  }

  Future<void> _submit() async {
    setState(() => _error = null);
    final spec = _compose();
    if (spec == null) return;
    setState(() => _busy = true);
    try {
      final r = await Native.testAi(spec);
      if (!mounted) return;
      if (!r.ok) {
        setState(() => _error = _hint(r.message));
        return;
      }
      final saved = await widget.onAdd(spec);
      if (saved && mounted) {
        _key.clear();
        _url.clear();
        _model.clear();
      }
    } on NativeException catch (e) {
      if (mounted) setState(() => _error = _hint(e.message));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    final p = widget.provider;
    return SoftCard(
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: widget.onToggle,
            child: Padding(
              padding: const EdgeInsets.all(Sp.m),
              child: Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: cs.primaryContainer,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Icon(p.icon, color: cs.onPrimaryContainer),
                  ),
                  const SizedBox(width: Sp.m),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(p.name, style: tt.titleMedium),
                        Text(p.tagline, style: tt.bodySmall),
                      ],
                    ),
                  ),
                  Icon(
                    widget.expanded
                        ? Icons.expand_less_rounded
                        : Icons.expand_more_rounded,
                  ),
                ],
              ),
            ),
          ),
          if (widget.expanded)
            Padding(
              padding: const EdgeInsets.fromLTRB(Sp.m, 0, Sp.m, Sp.m),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (var i = 0; i < p.steps.length; i++)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Text(
                        '${i + 1}. ${p.steps[i]}',
                        style: tt.bodyMedium,
                      ),
                    ),
                  const SizedBox(height: Sp.s),
                  BuddyButton.outline(
                    onPressed: _site,
                    icon: const Icon(Icons.open_in_new_rounded),
                    child: Text(
                      tr(
                        'Открыть сайт · ${p.siteLabel}',
                        en: 'Open the site · ${p.siteLabel}',
                        uz: 'Saytni ochish · ${p.siteLabel}',
                      ),
                    ),
                  ),
                  const SizedBox(height: Sp.m),
                  if (p.custom) ...[
                    TextField(
                      controller: _url,
                      keyboardType: TextInputType.url,
                      autocorrect: false,
                      enableSuggestions: false,
                      decoration: InputDecoration(
                        labelText: tr(
                          'Адрес сервера',
                          en: 'Server address',
                          uz: 'Server manzili',
                        ),
                        hintText: 'http://192.168.1.5:11434',
                      ),
                    ),
                    const SizedBox(height: Sp.m),
                    TextField(
                      controller: _model,
                      autocorrect: false,
                      enableSuggestions: false,
                      decoration: InputDecoration(
                        labelText: tr(
                          'Название модели',
                          en: 'Model name',
                          uz: 'Model nomi',
                        ),
                        hintText: 'gemma3:4b',
                      ),
                    ),
                    const SizedBox(height: Sp.m),
                  ],
                  TextField(
                    controller: _key,
                    obscureText: _obscure,
                    autocorrect: false,
                    enableSuggestions: false,
                    decoration: InputDecoration(
                      labelText: p.custom
                          ? tr(
                              'Токен (необязательно)',
                              en: 'Token (optional)',
                              uz: 'Token (majburiy emas)',
                            )
                          : tr('Ключ', en: 'Key', uz: 'Kalit'),
                      suffixIcon: BuddyIconButton(
                        icon: Icon(
                          _obscure
                              ? Icons.visibility_rounded
                              : Icons.visibility_off_rounded,
                        ),
                        tooltip: _obscure
                            ? tr(
                                'Показать ключ',
                                en: 'Show key',
                                uz: 'Kalitni koʻrsatish',
                              )
                            : tr(
                                'Скрыть ключ',
                                en: 'Hide key',
                                uz: 'Kalitni yashirish',
                              ),
                        onPressed: () => setState(() => _obscure = !_obscure),
                      ),
                    ),
                  ),
                  const SizedBox(height: Sp.s),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: BuddyButton.text(
                      onPressed: _paste,
                      icon: const Icon(Icons.content_paste_rounded),
                      child: Text(
                        tr(
                          'Вставить из буфера',
                          en: 'Paste from clipboard',
                          uz: 'Buferdan qoʻyish',
                        ),
                      ),
                    ),
                  ),
                  if (p.models.isNotEmpty) ...[
                    const SizedBox(height: Sp.s),
                    DropdownButtonFormField<String>(
                      initialValue: _selected,
                      isExpanded: true,
                      borderRadius: BorderRadius.circular(Rad.field),
                      decoration: InputDecoration(
                        labelText: tr('Модель', en: 'Model', uz: 'Model'),
                      ),
                      items: [
                        for (var i = 0; i < p.models.length; i++)
                          DropdownMenuItem(
                            value: p.models[i],
                            child: Text(
                              p.models[i],
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                      ],
                      onChanged: (v) =>
                          setState(() => _selected = v ?? _selected),
                    ),
                  ],
                  const SizedBox(height: Sp.m),
                  Text(p.note, style: tt.bodySmall),
                  if (_error != null) ...[
                    const SizedBox(height: Sp.m),
                    Container(
                      padding: const EdgeInsets.all(Sp.m),
                      decoration: BoxDecoration(
                        color: cs.errorContainer,
                        borderRadius: BorderRadius.circular(Rad.field),
                      ),
                      child: Text(
                        _error!,
                        style: tt.bodyMedium?.copyWith(
                          color: cs.onErrorContainer,
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: Sp.m),
                  SizedBox(
                    width: double.infinity,
                    child: BuddyButton.filled(
                      primary: true,
                      onPressed: _busy ? null : _submit,
                      loading: _busy,
                      child: Text(
                        _busy
                            ? tr(
                                'Проверяю…',
                                en: 'Checking…',
                                uz: 'Tekshirilmoqda…',
                              )
                            : tr(
                                'Проверить и добавить',
                                en: 'Check and add',
                                uz: 'Tekshirish va qoʻshish',
                              ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

String _groqHint(String raw) {
  final m = raw.toLowerCase();
  if (m.contains('401') ||
      m.contains('403') ||
      m.contains('unauthor') ||
      m.contains('invalid')) {
    return tr(
      'Ключ не подходит: скопируйте его заново целиком',
      en: 'The key does not fit: copy it again in full',
      uz: 'Kalit mos kelmadi: uni qaytadan toʻliq nusxalang',
    );
  }
  if (m.contains('429') || m.contains('quota') || m.contains('limit')) {
    return tr(
      'Запросы на сегодня закончились, попробуйте позже',
      en: 'Requests for today have run out, try again later',
      uz: 'Bugungi soʻrovlar tugadi, keyinroq urinib koʻring',
    );
  }
  if (m.contains('timeout') ||
      m.contains('timed out') ||
      m.contains('unknownhost') ||
      m.contains('unable to resolve') ||
      m.contains('connect') ||
      m.contains('network')) {
    return tr('Нет интернета', en: 'No internet', uz: 'Internet yoʻq');
  }
  return _hint(raw);
}

const String _groqBase = 'https://api.groq.com/openai';

String _shortModel(String id) => id.contains('/') ? id.split('/').last : id;

@visibleForTesting
List<String> pickGroqModels(List<String> all) {
  final ids = all.toSet().toList();
  final out = <String>[];
  void add(String? m) {
    if (m != null && !out.contains(m) && out.length < 3) out.add(m);
  }

  String? first(bool Function(String) f) {
    for (final m in ids) {
      if (f(m)) return m;
    }
    return null;
  }

  final qwen = ids.where((m) => m.startsWith('qwen/')).toList()
    ..sort((a, b) => b.compareTo(a));
  final newestQwen = qwen.isEmpty ? null : qwen.first;
  // Qwen is trained on many more languages than gpt-oss: with the app in Uzbek it goes first
  if (currentLang == 'uz') add(newestQwen);
  add(first((m) => m.startsWith('openai/gpt-oss-120b')));
  add(newestQwen);
  add(first((m) => m.startsWith('openai/gpt-oss-20b')));
  if (out.length < 3) {
    int size(String m) {
      final x = RegExp(r'(\d+)b').firstMatch(m.toLowerCase());
      return x == null ? 0 : int.parse(x.group(1)!);
    }

    final rest = ids.where((m) => !out.contains(m)).toList()
      ..sort((a, b) {
        int pref(String m) => RegExp('llama|gpt-oss|qwen').hasMatch(m) ? 1 : 0;
        final c = pref(b).compareTo(pref(a));
        return c != 0 ? c : size(b).compareTo(size(a));
      });
    rest.forEach(add);
  }
  return out;
}

String _connectedText(List<String> models) {
  final n = models.map(_shortModel).toList();
  if (n.isEmpty) {
    return tr(
      'Готово! Bubby подключён.',
      en: 'Done! Bubby is connected.',
      uz: 'Tayyor! Bubby ulandi.',
    );
  }
  final backups = n.length == 1
      ? ''
      : n.length == 2
      ? tr(
          ', ${n[1]} (запасная)',
          en: ', ${n[1]} (backup)',
          uz: ', ${n[1]} (zaxira)',
        )
      : tr(
          ', ${n[1]} и ${n[2]} (запасные)',
          en: ', ${n[1]} and ${n[2]} (backups)',
          uz: ', ${n[1]} va ${n[2]} (zaxira)',
        );
  return tr(
    'Подключено: ${n.first} (основная)$backups. У каждой отдельный дневной лимит — если одна закончится, Bubby сам переключится',
    en: 'Connected: ${n.first} (primary)$backups. Each has its own daily limit — if one runs out, Bubby switches by itself',
    uz: 'Ulandi: ${n.first} (asosiy)$backups. Har birining alohida kunlik limiti bor — biri tugasa, Bubby oʻzi almashadi',
  );
}

class _GroqCard extends StatefulWidget {
  const _GroqCard({
    super.key,
    required this.expanded,
    required this.onToggle,
    required this.onConnect,
  });

  final bool expanded;
  final VoidCallback onToggle;
  final Future<bool> Function(List<String> specs) onConnect;

  @override
  State<_GroqCard> createState() => _GroqCardState();
}

class _GroqCardState extends State<_GroqCard> {
  final _key = TextEditingController();
  bool _obscure = true;
  bool _busy = false;
  bool _done = false;
  List<String> _models = const [];
  String? _error;

  @override
  void dispose() {
    _key.dispose();
    super.dispose();
  }

  void fill(String t) => setState(() {
    _key.text = t;
    _error = null;
  });

  Future<void> _paste() async {
    final d = await Clipboard.getData(Clipboard.kTextPlain);
    final t = d?.text?.trim() ?? '';
    if (t.isEmpty) {
      if (mounted) {
        showMessage(
          context,
          tr(
            'Буфер обмена пуст',
            en: 'The clipboard is empty',
            uz: 'Bufer boʻsh',
          ),
        );
      }
      return;
    }
    setState(() {
      _key.text = t;
      _error = null;
    });
  }

  Future<void> _site() async {
    try {
      await Native.openUrl('https://console.groq.com/keys');
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _connect() async {
    final key = _key.text.trim();
    if (!key.startsWith('gsk_') ||
        key.contains(RegExp(r'\s')) ||
        key.length <= 20) {
      setState(
        () => _error = tr(
          'Это не похоже на ключ Groq: он начинается с gsk_',
          en: 'This does not look like a Groq key: it starts with gsk_',
          uz: 'Bu Groq kalitiga oʻxshamaydi: u gsk_ bilan boshlanadi',
        ),
      );
      return;
    }
    setState(() {
      _error = null;
      _busy = true;
    });
    try {
      final lr = await Native.listModels(_groqBase, key);
      if (!mounted) return;
      if (!lr.ok) {
        final msg = lr.message ?? '';
        final low = msg.toLowerCase();
        setState(
          () => _error = (low.contains('401') || low.contains('403'))
              ? tr(
                  'Ключ не подходит: скопируйте его заново целиком',
                  en: 'The key does not fit: copy it again in full',
                  uz: 'Kalit mos kelmadi: uni qaytadan toʻliq nusxalang',
                )
              : _groqHint(msg.isEmpty ? 'network' : msg),
        );
        return;
      }
      final chosen = pickGroqModels(lr.models);
      if (chosen.isEmpty) {
        setState(
          () => _error = tr(
            'У этого ключа нет доступных моделей',
            en: 'This key has no available models',
            uz: 'Bu kalitda mavjud modellar yoʻq',
          ),
        );
        return;
      }
      final r = await Native.testAi('$_groqBase|${chosen.first}|$key');
      if (!mounted) return;
      if (!r.ok) {
        setState(() => _error = _groqHint(r.message));
        return;
      }
      final saved = await widget.onConnect([
        for (final m in chosen) '$_groqBase|$m|$key',
      ]);
      if (saved && mounted) {
        _key.clear();
        setState(() {
          _done = true;
          _models = chosen;
        });
        Confetti.burst(context);
      }
    } on NativeException catch (e) {
      if (mounted) setState(() => _error = _groqHint(e.message));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _step(int n, IconData icon, String text) {
    final cs = context.cs;
    final tt = context.tt;
    return Padding(
      padding: const EdgeInsets.only(bottom: Sp.m),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 36,
            height: 36,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: cs.primary,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              '$n',
              style: tt.titleMedium?.copyWith(color: cs.onPrimary),
            ),
          ),
          const SizedBox(width: Sp.m),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(text, style: tt.bodyLarge),
            ),
          ),
          const SizedBox(width: Sp.s),
          Icon(icon, size: 22, color: cs.primary),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    return SoftCard(
      padding: EdgeInsets.zero,
      borderColor: cs.primary.withAlpha(140),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: widget.onToggle,
            child: Padding(
              padding: const EdgeInsets.all(Sp.m),
              child: Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: cs.primaryContainer,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Icon(
                      Icons.bolt_rounded,
                      color: cs.onPrimaryContainer,
                    ),
                  ),
                  const SizedBox(width: Sp.m),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Flexible(
                              child: Text('Groq', style: tt.titleMedium),
                            ),
                            const SizedBox(width: Sp.s),
                            StatusPill(
                              label: tr(
                                'Рекомендуем',
                                en: 'Recommended',
                                uz: 'Tavsiya etamiz',
                              ),
                              color: BuddyColors.success,
                            ),
                          ],
                        ),
                        Text(
                          tr(
                            'Бесплатно, очень быстро. Нужен свой ключ',
                            en: 'Free, very fast. You need your own key',
                            uz: 'Bepul, juda tez. Oʻz kalitingiz kerak',
                          ),
                          style: tt.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  Icon(
                    widget.expanded
                        ? Icons.expand_less_rounded
                        : Icons.expand_more_rounded,
                  ),
                ],
              ),
            ),
          ),
          if (widget.expanded)
            Padding(
              padding: const EdgeInsets.fromLTRB(Sp.m, 0, Sp.m, Sp.m),
              child: _done ? _success(cs, tt) : _guide(cs, tt),
            ),
        ],
      ),
    );
  }

  Widget _success(ColorScheme cs, TextTheme tt) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.all(Sp.m),
          decoration: BoxDecoration(
            color: cs.secondaryContainer,
            borderRadius: BorderRadius.circular(Rad.field),
          ),
          child: Row(
            children: [
              const SuccessPulse(size: 44, haptic: true),
              const SizedBox(width: Sp.m),
              Expanded(
                child: Text(
                  _connectedText(_models),
                  style: tt.bodyLarge?.copyWith(color: cs.onSecondaryContainer),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: Sp.m),
        SizedBox(
          width: double.infinity,
          child: BuddyButton.filled(
            primary: true,
            onPressed: () => Navigator.of(context).pop(),
            child: Text(tr('Готово', en: 'Done', uz: 'Tayyor')),
          ),
        ),
      ],
    );
  }

  Widget _guide(ColorScheme cs, TextTheme tt) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _step(
          1,
          Icons.open_in_new_rounded,
          tr(
            'Нажмите «Открыть Groq» — откроется console.groq.com/keys',
            en: 'Press “Open Groq” — console.groq.com/keys will open',
            uz: '«Groqni ochish» tugmasini bosing — console.groq.com/keys ochiladi',
          ),
        ),
        _step(
          2,
          Icons.login_rounded,
          tr(
            'Войдите (через Google, GitHub или e-mail) — регистрация бесплатная, карта не нужна',
            en: 'Sign in (with Google, GitHub or e-mail) — signing up is free, no card needed',
            uz: 'Kiring (Google, GitHub yoki e-mail orqali) — roʻyxatdan oʻtish bepul, karta kerak emas',
          ),
        ),
        _step(
          3,
          Icons.add_circle_outline_rounded,
          tr(
            'Нажмите «Create API Key», придумайте любое имя (например Bubby) и нажмите «Submit»',
            en: 'Press “Create API Key”, make up any name (for example Bubby) and press “Submit”',
            uz: '«Create API Key» tugmasini bosing, istalgan nom oʻylab toping (masalan, Bubby) va «Submit» tugmasini bosing',
          ),
        ),
        _step(
          4,
          Icons.copy_rounded,
          tr(
            'Скопируйте ключ — он начинается с gsk_ и показывается ОДИН раз',
            en: 'Copy the key — it starts with gsk_ and is shown only ONCE',
            uz: 'Kalitni nusxalang — u gsk_ bilan boshlanadi va FAQAT BIR marta koʻrsatiladi',
          ),
        ),
        _step(
          5,
          Icons.content_paste_rounded,
          tr(
            'Вернитесь сюда, нажмите «Вставить из буфера» и «Подключить Groq»',
            en: 'Come back here, press “Paste from clipboard” and “Connect Groq”',
            uz: 'Shu yerga qayting, «Buferdan qoʻyish» va «Groqni ulash» tugmalarini bosing',
          ),
        ),
        BuddyButton.outline(
          onPressed: _site,
          icon: const Icon(Icons.open_in_new_rounded),
          child: Text(tr('Открыть Groq', en: 'Open Groq', uz: 'Groqni ochish')),
        ),
        const SizedBox(height: Sp.s),
        Text(
          tr(
            'Сайт может немного отличаться — ищите кнопку Create API Key',
            en: 'The site may look a little different — look for the Create API Key button',
            uz: 'Sayt biroz boshqacha koʻrinishi mumkin — Create API Key tugmasini qidiring',
          ),
          style: tt.bodySmall,
        ),
        const SizedBox(height: Sp.s),
        Row(
          children: [
            Icon(Icons.warning_amber_rounded, size: 18, color: cs.error),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                tr(
                  'Никому не показывайте свой ключ',
                  en: 'Never show your key to anyone',
                  uz: 'Kalitingizni hech kimga koʻrsatmang',
                ),
                style: tt.bodyMedium?.copyWith(color: cs.error),
              ),
            ),
          ],
        ),
        const SizedBox(height: Sp.m),
        TextField(
          controller: _key,
          obscureText: _obscure,
          autocorrect: false,
          enableSuggestions: false,
          decoration: InputDecoration(
            labelText: tr(
              'Ключ Groq (gsk_…)',
              en: 'Groq key (gsk_…)',
              uz: 'Groq kaliti (gsk_…)',
            ),
            suffixIcon: BuddyIconButton(
              icon: Icon(
                _obscure
                    ? Icons.visibility_rounded
                    : Icons.visibility_off_rounded,
              ),
              tooltip: _obscure
                  ? tr(
                      'Показать ключ',
                      en: 'Show key',
                      uz: 'Kalitni koʻrsatish',
                    )
                  : tr('Скрыть ключ', en: 'Hide key', uz: 'Kalitni yashirish'),
              onPressed: () => setState(() => _obscure = !_obscure),
            ),
          ),
        ),
        const SizedBox(height: Sp.s),
        Align(
          alignment: Alignment.centerLeft,
          child: BuddyButton.text(
            onPressed: _paste,
            icon: const Icon(Icons.content_paste_rounded),
            child: Text(
              tr(
                'Вставить из буфера',
                en: 'Paste from clipboard',
                uz: 'Buferdan qoʻyish',
              ),
            ),
          ),
        ),
        const SizedBox(height: Sp.s),
        Text(
          tr(
            'Бесплатно. У каждой модели свой лимит — примерно 1000 запросов в день и 8000 токенов в минуту (точные цифры в консоли Groq, они могут меняться)',
            en: 'Free. Each model has its own limit — about 1000 requests per day and 8000 tokens per minute (exact numbers are in the Groq console and may change)',
            uz: 'Bepul. Har bir modelning oʻz limiti bor — kuniga taxminan 1000 ta soʻrov va daqiqasiga 8000 token (aniq raqamlar Groq konsolida, ular oʻzgarishi mumkin)',
          ),
          style: tt.bodySmall,
        ),
        if (_error != null) ...[
          const SizedBox(height: Sp.m),
          Container(
            padding: const EdgeInsets.all(Sp.m),
            decoration: BoxDecoration(
              color: cs.errorContainer,
              borderRadius: BorderRadius.circular(Rad.field),
            ),
            child: Text(
              _error!,
              style: tt.bodyMedium?.copyWith(color: cs.onErrorContainer),
            ),
          ),
        ],
        const SizedBox(height: Sp.m),
        SizedBox(
          width: double.infinity,
          child: BuddyButton.filled(
            primary: true,
            onPressed: _busy ? null : _connect,
            loading: _busy,
            child: Text(
              _busy
                  ? tr('Проверяю…', en: 'Checking…', uz: 'Tekshirilmoqda…')
                  : tr(
                      'Подключить Groq',
                      en: 'Connect Groq',
                      uz: 'Groqni ulash',
                    ),
            ),
          ),
        ),
      ],
    );
  }
}
