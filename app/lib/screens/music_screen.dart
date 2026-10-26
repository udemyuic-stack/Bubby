import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';

class MusicScreen extends StatefulWidget {
  const MusicScreen({super.key});

  @override
  State<MusicScreen> createState() => _MusicScreenState();
}

class _Mood {
  const _Mood(this.ru, this.en, this.uz, this.qRu, this.qEn, this.qUz, this.icon, this.color);

  final String ru, en, uz;
  String get label => tr(ru, en: en, uz: uz);
  final String qRu, qEn, qUz;
  String get query => tr(qRu, en: qEn, uz: qUz);
  final IconData icon;
  final Color color;
}

const List<_Mood> _moods = [
  _Mood('Весёлое', 'Happy', 'Quvnoq', 'весёлая музыка', 'happy upbeat music', 'quvnoq musiqa', Icons.sentiment_very_satisfied_rounded,
      BuddyColors.warning),
  _Mood('Грустное', 'Sad', 'Gʻamgin', 'грустная музыка', 'sad music', "g'amgin musiqa", Icons.water_drop_rounded, BuddyColors.sky),
  _Mood('Спокойное', 'Calm', 'Sokin', 'спокойная музыка', 'calm relaxing music', 'tinch musiqa', Icons.spa_rounded, BuddyColors.teal),
  _Mood('Энергичное', 'Energetic', 'Shijoatli', 'энергичная музыка', 'energetic workout music', 'shijoatli musiqa', Icons.bolt_rounded, BuddyColors.orange),
  _Mood('Романтика', 'Romantic', 'Romantik', 'романтическая музыка', 'romantic music', 'romantik musiqa', Icons.favorite_rounded,
      BuddyColors.rose),
  _Mood('Фокус', 'Focus', 'Diqqat', 'музыка для концентрации', 'focus concentration music', 'diqqat uchun musiqa', Icons.center_focus_strong_rounded,
      BuddyColors.violet),
  _Mood('Ночь', 'Night', 'Tun', 'ночная музыка', 'night chill music', 'tungi musiqa', Icons.nightlight_round, Color(0xFF5A4BA6)),
  _Mood('Дорога', 'On the road', 'Yoʻlda', 'музыка в дорогу', 'road trip music', "yo'lda tinglash uchun musiqa", Icons.directions_car_rounded,
      BuddyColors.success),
];

class _MusicScreenState extends State<MusicScreen> {
  bool _loaded = false;
  List<MusicApp> _apps = const [];
  String _app = '';
  bool _byMood = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final s = await Native.getSettings();
      List<MusicApp> apps = const [];
      try {
        apps = await Native.musicApps();
      } on NativeException catch (e) {
        if (mounted) showMessage(context, e);
      }
      if (!mounted) return;
      setState(() {
        _app = s.musicApp;
        _byMood = s.musicMood;
        _apps = apps;
        _loaded = true;
      });
    } on NativeException catch (e) {
      if (!mounted) return;
      setState(() => _loaded = true);
      showMessage(context, e);
    }
  }

  Future<void> _pickApp(String id) async {
    final prev = _app;
    setState(() => _app = id);
    try {
      await Native.saveSettings({'musicApp': id});
    } on NativeException catch (e) {
      if (!mounted) return;
      setState(() => _app = prev);
      showMessage(context, e);
    }
  }

  Future<void> _setByMood(bool v) async {
    final prev = _byMood;
    setState(() => _byMood = v);
    try {
      await Native.saveSettings({'musicMood': v});
    } on NativeException catch (e) {
      if (!mounted) return;
      setState(() => _byMood = prev);
      showMessage(context, e);
    }
  }

  Future<void> _play(_Mood m) async {
    showMessage(context, tr('Включаю: ${m.label.toLowerCase()}…', en: 'Playing: ${m.label.toLowerCase()}…', uz: 'Qoʻyyapman: ${m.label.toLowerCase()}…'));
    try {
      await Native.testMusic(m.query);
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Музыка', en: 'Music', uz: 'Musiqa'))),
      body: !_loaded
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(Sp.l, Sp.s, Sp.l, Sp.xl),
              children: [
                FadeSlideIn(
                  index: 0,
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: Sp.l),
                    child: SoftCard(
                      gradient: BuddyGradients.violet,
                      borderColor: Colors.transparent,
                      child: Row(
                        children: [
                          Container(
                            width: 56,
                            height: 56,
                            decoration: BoxDecoration(
                              color: Colors.white.withAlpha(46),
                              borderRadius: BorderRadius.circular(18),
                            ),
                            child: const Icon(Icons.music_note_rounded,
                                color: Colors.white, size: 30),
                          ),
                          const SizedBox(width: Sp.l),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  tr('Музыка по настроению', en: 'Music for your mood', uz: 'Kayfiyatga mos musiqa'),
                                  style: tt.titleMedium
                                      ?.copyWith(color: Colors.white),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  tr('Скажите: «Bubby, включи музыку» — я спрошу про настроение и подберу плейлист',
                                      en: 'Say: “Bubby, play some music” — I will ask about your mood and pick a playlist',
                                      uz: 'Ayting: «Bubby, musiqa qoʻy» — kayfiyatingizni soʻrab, pleylist tanlayman'),
                                  style: tt.bodyMedium?.copyWith(
                                      color: Colors.white.withAlpha(230)),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                FadeSlideIn(
                  index: 1,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SectionTitle(tr('Выберите настроение', en: 'Pick a mood', uz: 'Kayfiyatni tanlang'),
                          icon: Icons.mood_rounded),
                      Padding(
                        padding: const EdgeInsets.only(bottom: Sp.m),
                        child: LayoutBuilder(
                          builder: (context, c) {
                            final w = (c.maxWidth - Sp.m) / 2;
                            return Wrap(
                              spacing: Sp.m,
                              runSpacing: Sp.m,
                              children: [
                                for (final m in _moods)
                                  SizedBox(
                                    width: w,
                                    child: _MoodCard(
                                      mood: m,
                                      onTap: () => _play(m),
                                    ),
                                  ),
                              ],
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),
                FadeSlideIn(
                  index: 2,
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: Sp.l),
                    child: SoftCard(
                      padding: const EdgeInsets.symmetric(
                          horizontal: Sp.l, vertical: Sp.s),
                      child: BuddySwitchTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(tr('Подбирать музыку по настроению', en: 'Pick music by mood', uz: 'Musiqani kayfiyatga qarab tanlash')),
                        subtitle: Text(tr('Bubby сам выберет плейлист под ваше настроение', en: 'Bubby will choose a playlist to match your mood', uz: 'Bubby kayfiyatingizga mos pleylistni oʻzi tanlaydi')),
                        value: _byMood,
                        onChanged: _setByMood,
                      ),
                    ),
                  ),
                ),
                FadeSlideIn(
                  index: 3,
                  child: SectionTitle(tr('Какие приложения можно использовать', en: 'Which apps can be used', uz: 'Qaysi ilovalardan foydalanish mumkin'),
                      icon: Icons.apps_rounded),
                ),
                FadeSlideIn(
                  index: 4,
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: Sp.s),
                    child: _AppCard(
                      label: tr('Автоматически', en: 'Automatic', uz: 'Avtomatik'),
                      subtitle: tr('Телефон сам выберет подходящий проигрыватель', en: 'The phone will pick a suitable player', uz: 'Telefon mos pleyerni oʻzi tanlaydi'),
                      selected: _app.isEmpty,
                      onTap: () => _pickApp(''),
                    ),
                  ),
                ),
                for (var i = 0; i < _apps.length; i++)
                  Padding(
                    padding: const EdgeInsets.only(bottom: Sp.s),
                    child: _AppCard(
                      label: _apps[i].label,
                      kind: _apps[i].kind,
                      installed: _apps[i].installed,
                      selected: _app == _apps[i].id,
                      onTap: () => _pickApp(_apps[i].id),
                    ),
                  ),
                const SizedBox(height: Sp.s),
                FadeSlideIn(
                  index: 5,
                  child: SoftCard(
                    color: cs.secondaryContainer,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(Icons.info_outline_rounded,
                                color: cs.onSecondaryContainer),
                            const SizedBox(width: Sp.s),
                            Text(tr('Как это работает', en: 'How it works', uz: 'Bu qanday ishlaydi'),
                                style: tt.titleMedium
                                    ?.copyWith(color: cs.onSecondaryContainer)),
                          ],
                        ),
                        const SizedBox(height: Sp.s),
                        Text(
                          tr('Работает через проигрыватели, которые принимают поиск по голосу: YouTube Music и Spotify лучше всего; для остальных откроется поиск в приложении.',
                              en: 'Works through players that accept voice search: YouTube Music and Spotify work best; for the others, the in-app search will open.',
                              uz: 'Ovozli qidiruvni qabul qiladigan pleyerlar orqali ishlaydi: YouTube Music va Spotify eng yaxshi; qolganlarida ilova ichidagi qidiruv ochiladi.'),
                          style: tt.bodyMedium
                              ?.copyWith(color: cs.onSecondaryContainer),
                        ),
                        const SizedBox(height: Sp.s),
                        Text(
                          tr('Приложение должно быть установлено, и вы должны быть в нём авторизованы.',
                              en: 'The app must be installed and you must be signed in to it.',
                              uz: 'Ilova oʻrnatilgan boʻlishi va unga kirgan boʻlishingiz kerak.'),
                          style: tt.bodyMedium
                              ?.copyWith(color: cs.onSecondaryContainer),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}

class _MoodCard extends StatelessWidget {
  const _MoodCard({required this.mood, required this.onTap});

  final _Mood mood;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SoftCard(
      padding: const EdgeInsets.all(Sp.m),
      onTap: onTap,
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: mood.color.withAlpha(40),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(mood.icon, color: mood.color, size: 24),
          ),
          const SizedBox(width: Sp.m),
          Expanded(
            child: Text(
              mood.label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: context.tt.titleSmall,
            ),
          ),
        ],
      ),
    );
  }
}

class _AppCard extends StatelessWidget {
  const _AppCard({
    required this.label,
    required this.selected,
    required this.onTap,
    this.subtitle,
    this.kind,
    this.installed,
  });

  final String label;
  final String? subtitle;
  final String? kind;
  final bool? installed;
  final bool selected;
  final VoidCallback onTap;

  static String kindLabel(String k) => switch (k) {
        'video' => tr('Видео', en: 'Video', uz: 'Video'),
        'local' => tr('На устройстве', en: 'On device', uz: 'Qurilmada'),
        _ => tr('Стриминг', en: 'Streaming', uz: 'Strimming'),
      };

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    final inst = installed;
    return SoftCard(
      padding: const EdgeInsets.symmetric(horizontal: Sp.l, vertical: Sp.m),
      borderColor: selected ? cs.primary : null,
      borderWidth: selected ? 1.5 : 1,
      onTap: onTap,
      child: Row(
        children: [
          Icon(
            selected
                ? Icons.radio_button_checked_rounded
                : Icons.radio_button_unchecked_rounded,
            color: selected ? cs.primary : cs.onSurfaceVariant,
          ),
          const SizedBox(width: Sp.m),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: tt.titleSmall),
                if (subtitle != null) ...[
                  const SizedBox(height: 2),
                  Text(subtitle!, style: tt.bodySmall),
                ],
                if (kind != null) ...[
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: Sp.s,
                    runSpacing: 4,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 3),
                        decoration: BoxDecoration(
                          color: cs.surfaceContainerHighest,
                          borderRadius: Rad.pillRadius,
                        ),
                        child: Text(kindLabel(kind!), style: tt.labelSmall),
                      ),
                      if (inst != null)
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 8,
                              height: 8,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: inst
                                    ? BuddyColors.success
                                    : cs.outline,
                              ),
                            ),
                            const SizedBox(width: 5),
                            Text(inst ? tr('Установлено', en: 'Installed', uz: 'Oʻrnatilgan') : tr('Не установлено', en: 'Not installed', uz: 'Oʻrnatilmagan'),
                                style: tt.labelSmall),
                          ],
                        ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
