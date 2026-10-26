import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/l10n.dart';
import '../mascot.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';

class AgentsScreen extends StatefulWidget {
  const AgentsScreen({super.key});

  @override
  State<AgentsScreen> createState() => _AgentsScreenState();
}

class _AgentsScreenState extends State<AgentsScreen> {
  final _link = TextEditingController();
  AgentStatus? _status;
  Timer? _timer;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => _refresh());
  }

  @override
  void dispose() {
    _timer?.cancel();
    _link.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    try {
      final s = await Native.agentStatus();
      if (mounted) setState(() => _status = s);
    } on NativeException {
      // следующий опрос попробует снова
    }
  }

  Future<void> _paste() async {
    final d = await Clipboard.getData(Clipboard.kTextPlain);
    final t = d?.text?.trim() ?? '';
    if (t.isNotEmpty && mounted) setState(() => _link.text = t);
  }

  Future<void> _pair() async {
    final link = _link.text.trim();
    if (link.isEmpty) {
      showMessage(context, tr('Вставьте ссылку из терминала', en: 'Paste the link from the terminal', uz: 'Terminaldagi havolani qoʻying'));
      return;
    }
    setState(() => _busy = true);
    try {
      final r = await Native.agentPair(link);
      if (!mounted) return;
      if (r.ok) {
        _link.clear();
        await _refresh();
      } else {
        showMessage(context, r.error ?? tr('Не удалось подключиться', en: 'Could not connect', uz: 'Ulanib boʻlmadi'));
      }
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _unpair() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(tr('Отключить компьютер?', en: 'Disconnect the computer?', uz: 'Kompyuterni uzamizmi?')),
        content: Text(tr('Телефон перестанет получать запросы от Claude Code.', en: 'The phone will stop receiving requests from Claude Code.', uz: 'Telefon Claude Code soʻrovlarini olmay qoʻyadi.')),
        actions: [
          BuddyButton.text(
            onPressed: () => Navigator.of(c).pop(false),
            child: Text(tr('Отмена', en: 'Cancel', uz: 'Bekor qilish')),
          ),
          BuddyButton.filled(
            onPressed: () => Navigator.of(c).pop(true),
            child: Text(tr('Отключить', en: 'Disconnect', uz: 'Uzish')),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await Native.agentUnpair();
      await _refresh();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = _status;
    final cs = context.cs;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Агенты', en: 'Agents', uz: 'Agentlar'))),
      body: s == null
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(Sp.l, Sp.s, Sp.l, Sp.xl),
                children: [
                  SoftCard(
                    gradient: BuddyGradients.header(context.isDark),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Mascot(mood: MascotMood.cool, size: 64),
                        const SizedBox(width: Sp.m),
                        Expanded(
                          child: Text(
                            tr(
                              'Следите за Claude Code на компьютере и разрешайте или '
                                  'отклоняйте его запросы с телефона.\n'
                                  'Телефон и компьютер должны быть в одной сети.\n'
                                  'Пока вы не подтвердите, ничего не выполняется.',
                              en: 'Keep an eye on Claude Code on your computer and allow or '
                                  'decline its requests from your phone.\n'
                                  'The phone and the computer must be on the same network.\n'
                                  'Nothing runs until you confirm.',
                              uz: 'Kompyuterdagi Claude Code ishini kuzating va uning soʻrovlariga '
                                  'telefondan ruxsat bering yoki rad eting.\n'
                                  'Telefon va kompyuter bitta tarmoqda boʻlishi kerak.\n'
                                  'Siz tasdiqlamaguncha hech narsa bajarilmaydi.',
                            ),
                            style: context.tt.bodyMedium,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: Sp.l),
                  if (s.paired) ..._paired(s) else ..._unpaired(),
                  const SizedBox(height: Sp.l),
                  Text(
                    tr('Разрешить можно только после проверки экрана блокировки', en: 'You can allow only after the lock screen check', uz: 'Ruxsat berish faqat ekran blokirovkasi tekshiruvidan keyin mumkin'),
                    style: context.tt.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
    );
  }

  List<Widget> _unpaired() => [
        TextField(
          controller: _link,
          decoration: InputDecoration(
            labelText: tr('Ссылка для подключения', en: 'Connection link', uz: 'Ulanish havolasi'),
            hintText: 'coucou://pair?...',
            suffixIcon: BuddyButton.text(
              onPressed: _paste,
              child: Text(tr('Вставить', en: 'Paste', uz: 'Qoʻyish')),
            ),
          ),
          autocorrect: false,
          enableSuggestions: false,
          keyboardType: TextInputType.url,
        ),
        const SizedBox(height: Sp.m),
        BuddyButton.filled(
          onPressed: _busy ? null : _pair,
          child: Text(tr('Подключить', en: 'Connect', uz: 'Ulash')),
        ),
        const SizedBox(height: Sp.m),
        SoftCard(
          padding: EdgeInsets.zero,
          child: ExpansionTile(
            shape: const Border(),
            collapsedShape: const Border(),
            title: Text(tr('Как настроить на компьютере', en: 'How to set up on the computer', uz: 'Kompyuterda qanday sozlanadi')),
            childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            expandedCrossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(tr('1. Запустите мост:', en: '1. Start the bridge:', uz: '1. Koʻprikni ishga tushiring:')),
              const SelectableText('node agent-bridge/bridge.mjs'),
              const SizedBox(height: 8),
              Text(tr('2. Скопируйте ссылку, которую он напечатает, и вставьте её сюда.', en: '2. Copy the link it prints and paste it here.', uz: '2. U chiqargan havolani nusxalab, shu yerga qoʻying.')),
              const SizedBox(height: 8),
              Text(tr('3. Подключите хуки Claude Code:', en: '3. Install the Claude Code hooks:', uz: '3. Claude Code hooklarini ulang:')),
              const SelectableText('node agent-bridge/install-hooks.mjs'),
            ],
          ),
        ),
      ];

  List<Widget> _paired(AgentStatus s) {
    final theme = Theme.of(context);
    return [
      Row(
        children: [
          Expanded(
            child: Text(
              s.desktop.isEmpty ? tr('Компьютер', en: 'Computer', uz: 'Kompyuter') : s.desktop,
              style: theme.textTheme.titleLarge,
            ),
          ),
          StatusPill(
            label: s.connected ? tr('на связи', en: 'online', uz: 'aloqada') : tr('нет связи', en: 'offline', uz: 'aloqa yoʻq'),
            color: s.connected ? BuddyColors.success : theme.colorScheme.outline,
            dot: true,
            pulse: s.connected,
          ),
        ],
      ),
      const SizedBox(height: Sp.m),
      if (s.sessions.isEmpty)
        Padding(
          padding: const EdgeInsets.symmetric(vertical: Sp.s),
          child: EmptyState(
            mood: MascotMood.sleepy,
            title: tr('Активных сессий нет', en: 'No active sessions', uz: 'Faol sessiyalar yoʻq'),
            size: 90,
          ),
        ),
      for (var i = 0; i < s.sessions.length; i++)
        FadeSlideIn(
          key: ValueKey('s${s.sessions[i].pillId}$i'),
          index: i,
          child: Padding(
            padding: const EdgeInsets.only(bottom: Sp.m),
            child: _sessionCard(s.sessions[i]),
          ),
        ),
      const SizedBox(height: Sp.s),
      BuddyButton.outline(onPressed: _unpair, child: Text(tr('Отключить', en: 'Disconnect', uz: 'Uzish'))),
    ];
  }

  Widget _sessionCard(AgentSession a) {
    final (label, color) = _stateInfo(a.state);
    final tt = Theme.of(context).textTheme;
    return SoftCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(a.agent, style: tt.titleMedium)),
              StatusPill(label: label, color: color, dense: true),
            ],
          ),
          if (a.statusText.isNotEmpty) ...[
            const SizedBox(height: Sp.xs),
            Text(a.statusText, style: tt.bodyMedium),
          ],
          if (a.stepCount > 0) ...[
            const SizedBox(height: Sp.m),
            ClipRRect(
              borderRadius: Rad.pillRadius,
              child: TweenAnimationBuilder<double>(
                tween: Tween<double>(
                  begin: 0,
                  end: (a.stepIndex / a.stepCount).clamp(0.0, 1.0),
                ),
                duration: Dur.slow,
                curve: Curves.easeOutCubic,
                builder: (context, v, _) =>
                    LinearProgressIndicator(value: v, minHeight: 8),
              ),
            ),
            const SizedBox(height: Sp.xs),
            Text(tr('Шаг ${a.stepIndex} из ${a.stepCount}', en: 'Step ${a.stepIndex} of ${a.stepCount}', uz: '${a.stepIndex}-qadam, jami ${a.stepCount} ta'), style: tt.bodySmall),
          ],
        ],
      ),
    );
  }

  (String, Color) _stateInfo(String state) => switch (state) {
        'idle' => (tr('Ждёт', en: 'Idle', uz: 'Kutyapti'), Colors.grey),
        'working' => (tr('Работает', en: 'Working', uz: 'Ishlayapti'), Colors.blue),
        'thinking' => (tr('Думает', en: 'Thinking', uz: 'Oylayapti'), Colors.indigo),
        'searching' => (tr('Ищет', en: 'Searching', uz: 'Qidiryapti'), Colors.teal),
        'approval' => (tr('Нужно разрешение', en: 'Needs permission', uz: 'Ruxsat kerak'), Colors.orange),
        'question' => (tr('Вопрос', en: 'Question', uz: 'Savol'), Colors.amber),
        'error' => (tr('Ошибка', en: 'Error', uz: 'Xato'), Colors.red),
        'finished' => (tr('Готово', en: 'Done', uz: 'Tayyor'), Colors.green),
        'ratelimit' => (tr('Лимит', en: 'Limit', uz: 'Limit'), Colors.deepOrange),
        'sleeping' => (tr('Спит', en: 'Sleeping', uz: 'Uxlayapti'), Colors.blueGrey),
        'dizzy' => (tr('Устал', en: 'Tired', uz: 'Charchadi'), Colors.purple),
        _ => (state, Colors.grey),
      };
}
