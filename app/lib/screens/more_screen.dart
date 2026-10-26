import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';
import 'agents_screen.dart';
import 'ai_setup_screen.dart';
import 'fun_screen.dart';
import 'model_screen.dart';
import 'music_screen.dart';
import 'outfits_screen.dart';

class MoreScreen extends StatefulWidget {
  const MoreScreen({super.key});

  @override
  State<MoreScreen> createState() => _MoreScreenState();
}

class _MoreScreenState extends State<MoreScreen> with WidgetsBindingObserver {
  bool _ignoring = false;
  String _crash = '';
  bool _paired = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    try {
      final ig = await Native.batteryStatus();
      final s = await Native.getSettings();
      final ag = await Native.agentStatus();
      if (!mounted) return;
      setState(() {
        _ignoring = ig;
        _crash = s.lastCrash;
        _paired = ag.paired;
      });
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  void _open(Widget page) {
    Navigator.of(context)
        .push(BubbyRoute<void>(builder: (_) => page))
        .then((_) => _load());
  }

  Future<void> _battery() async {
    try {
      await Native.openSettings('battery');
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    final agentsItem = _MenuItem(Icons.terminal_rounded, tr('Агенты (Claude Code)', en: 'Agents (Claude Code)', uz: 'Agentlar (Claude Code)'),
        tr('Следите за Claude Code с телефона', en: 'Watch Claude Code from your phone', uz: 'Claude Code ni telefondan kuzating'), BuddyColors.violet,
        () => _open(const AgentsScreen()));
    final items = <_MenuItem>[
      _MenuItem(Icons.vpn_key_rounded, tr('Подключение ИИ', en: 'AI connection', uz: 'AI ulanishi'),
          tr('Свой бесплатный ключ и запасные подключения', en: 'Your own free key and backup connections', uz: 'O\'zingizning bepul kalitingiz va zaxira ulanishlar'), BuddyColors.violet,
          () => _open(const AiSetupScreen())),
      if (_paired) agentsItem,
      _MenuItem(Icons.memory_rounded, tr('Модель на телефоне', en: 'Model on the phone', uz: 'Telefondagi model'),
          tr('Gemma прямо на устройстве, без интернета', en: 'Gemma right on the device, no internet', uz: 'Gemma to\'g\'ridan-to\'g\'ri telefonda, internetsiz'), BuddyColors.teal,
          () => _open(const ModelScreen())),
      _MenuItem(Icons.music_note_rounded, tr('Музыка', en: 'Music', uz: 'Musiqa'),
          tr('Музыка по настроению и выбор приложения', en: 'Music for your mood and app choice', uz: 'Kayfiyatga mos musiqa va ilova tanlash'), BuddyColors.sky,
          () => _open(const MusicScreen())),
      _MenuItem(Icons.checkroom_rounded, tr('Наряды', en: 'Outfits', uz: 'Kiyimlar'),
          tr('Шляпы, очки и шарфы для Bubby', en: 'Hats, glasses and scarves for Bubby', uz: 'Bubby uchun shlyapa, ko\'zoynak va sharflar'), BuddyColors.rose,
          () => _open(const OutfitsScreen())),
      _MenuItem(Icons.sports_esports_rounded, tr('Игры и стикеры', en: 'Games and stickers', uz: 'O\'yinlar va stikerlar'),
          tr('Играйте и отправляйте стикеры друзьям', en: 'Play and send stickers to friends', uz: 'O\'ynang va do\'stlaringizga stiker yuboring'), BuddyColors.orange,
          () => _open(const FunScreen())),
    ];
    return Column(
      children: [
        GradientHeader(
          title: tr('Ещё', en: 'More', uz: 'Yana'),
          subtitle: tr('Агенты, модель, наряды и игры', en: 'Agents, model, outfits and games', uz: 'Agentlar, model, kiyimlar va o\'yinlar'),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(Sp.l, Sp.l, Sp.l, Sp.xl),
            children: [
              for (var i = 0; i < items.length; i++)
                FadeSlideIn(
                  index: i,
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: Sp.m),
                    child: SoftCard(
                      padding: const EdgeInsets.all(Sp.m),
                      onTap: items[i].onTap,
                      child: Row(
                        children: [
                          Container(
                            width: 52,
                            height: 52,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(18),
                              gradient: LinearGradient(
                                begin: Alignment.topLeft,
                                end: Alignment.bottomRight,
                                colors: [
                                  items[i].color,
                                  Color.lerp(items[i].color, Colors.black, 0.2) ??
                                      items[i].color,
                                ],
                              ),
                            ),
                            child: Icon(items[i].icon, color: Colors.white, size: 26),
                          ),
                          const SizedBox(width: Sp.l),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(items[i].title, style: tt.titleMedium),
                                const SizedBox(height: 2),
                                Text(items[i].subtitle, style: tt.bodySmall),
                              ],
                            ),
                          ),
                          Icon(Icons.chevron_right_rounded, color: cs.onSurfaceVariant),
                        ],
                      ),
                    ),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.only(bottom: Sp.m),
                child: InkWell(
                  onTap: _battery,
                  borderRadius: Rad.pillRadius,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: Sp.s),
                    child: Row(
                      children: [
                        Icon(
                          _ignoring
                              ? Icons.battery_full_rounded
                              : Icons.battery_alert_rounded,
                          size: 20,
                          color: _ignoring ? BuddyColors.success : cs.primary,
                        ),
                        const SizedBox(width: Sp.s),
                        Expanded(
                          child: Text(
                            _ignoring
                                ? tr('Не засыпать: включено', en: 'Stay awake: on', uz: 'Uxlamaslik: yoqilgan')
                                : tr('Не засыпать: выключено — нажмите, чтобы исправить', en: 'Stay awake: off — tap to fix', uz: 'Uxlamaslik: o\'chiq — tuzatish uchun bosing'),
                            style: tt.bodyMedium,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              if (!_paired)
                Theme(
                  data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                  child: ExpansionTile(
                    tilePadding: EdgeInsets.zero,
                    title: Text(tr('Для разработчиков', en: 'For developers', uz: 'Dasturchilar uchun'), style: tt.titleSmall),
                    children: [
                      SoftCard(
                        padding: const EdgeInsets.all(Sp.m),
                        onTap: agentsItem.onTap,
                        child: Row(
                          children: [
                            Icon(agentsItem.icon, color: agentsItem.color),
                            const SizedBox(width: Sp.m),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(agentsItem.title, style: tt.titleSmall),
                                  Text(agentsItem.subtitle, style: tt.bodySmall),
                                ],
                              ),
                            ),
                            Icon(Icons.chevron_right_rounded,
                                color: cs.onSurfaceVariant),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              Padding(
                padding: const EdgeInsets.only(top: Sp.m),
                child: SoftCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(children: [
                        Icon(Icons.lock_outline_rounded, color: cs.primary),
                        const SizedBox(width: Sp.s),
                        Expanded(child: Text(tr('Что Bubby отправляет', en: 'What Bubby sends', uz: 'Bubby nimalarni yuboradi'), style: tt.titleSmall)),
                      ]),
                      const SizedBox(height: Sp.s),
                      for (final line in [
                        tr('Ваши сообщения уходят только выбранному ИИ-сервису', en: 'Your messages go only to the AI service you chose', uz: 'Xabarlaringiz faqat siz tanlagan AI xizmatiga ketadi'),
                        tr('Ключи хранятся только на этом телефоне (не в копиях)', en: 'Keys stay on this phone (not in backups)', uz: 'Kalitlar faqat shu telefonda saqlanadi (zaxira nusxada emas)'),
                        tr('Текст экрана — только когда вы просите задачу на экране', en: 'Screen text only when you ask for a screen task', uz: "Ekran matni faqat ekran bo'yicha vazifa so'raganingizda"),
                        tr('Уведомления и контакты не покидают телефон, кроме нужного для ответа', en: 'Notifications and contacts never leave the phone except what is needed to answer', uz: "Bildirishnomalar va kontaktlar telefondan chiqmaydi, javob uchun kerakligidan tashqari"),
                      ])
                        Padding(
                          padding: const EdgeInsets.only(bottom: Sp.xs),
                          child: Text('• $line', style: tt.bodySmall),
                        ),
                    ],
                  ),
                ),
              ),
              if (_crash.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: Sp.m),
                  child: SoftCard(
                    color: cs.errorContainer,
                    borderColor: cs.error.withAlpha(90),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          tr('Последний сбой', en: 'Last crash', uz: 'Oxirgi nosozlik'),
                          style: tt.titleMedium?.copyWith(color: cs.onErrorContainer),
                        ),
                        const SizedBox(height: Sp.s),
                        SelectableText(
                          _crash,
                          style: TextStyle(fontSize: 12, color: cs.onErrorContainer),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _MenuItem {
  const _MenuItem(this.icon, this.title, this.subtitle, this.color, this.onTap);

  final IconData icon;
  final String title;
  final String subtitle;
  final Color color;
  final VoidCallback onTap;
}
