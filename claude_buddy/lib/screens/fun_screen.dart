import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../mascot.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';

class FunScreen extends StatefulWidget {
  const FunScreen({super.key});

  @override
  State<FunScreen> createState() => _FunScreenState();
}

class _FunScreenState extends State<FunScreen> {
  static List<String> get _games => [
        tr('Угадай число', en: 'Guess the number', uz: 'Sonni top'),
        tr('Камень-ножницы-бумага', en: 'Rock-paper-scissors', uz: 'Tosh-qaychi-qogʻoz'),
        tr('Загадки', en: 'Riddles', uz: 'Topishmoqlar'),
        tr('Викторина', en: 'Quiz', uz: 'Viktorina'),
        tr('Слова-цепочка', en: 'Word chain', uz: 'Soʻz zanjiri'),
      ];
  static const List<String> _moods = [
    'happy', 'love', 'laugh', 'cool', 'sleepy', 'crying', 'excited', 'wink',
  ];

  final Map<String, Uint8List> _stickers = {};

  @override
  void initState() {
    super.initState();
    for (final m in _moods) {
      _render(m);
    }
  }

  Future<void> _render(String mood) async {
    try {
      final b = await Native.renderSticker(mood);
      if (mounted) setState(() => _stickers[mood] = b);
    } on NativeException {
      // tile stays empty
    }
  }

  Future<void> _play(String name) async {
    try {
      await Native.startGame(name);
      if (mounted) showMessage(context, tr('Нажмите на Bubby и говорите', en: 'Tap Bubby and speak', uz: 'Bubby ni bosing va gapiring'));
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _share(String mood) async {
    try {
      await Native.shareSticker(mood);
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Игры и стикеры', en: 'Games & stickers', uz: 'Oʻyinlar va stikerlar'))),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Sp.l, Sp.s, Sp.l, Sp.xl),
        children: [
          SectionTitle(tr('Игры', en: 'Games', uz: 'Oʻyinlar'), icon: Icons.sports_esports_rounded),
          Wrap(
            spacing: Sp.s,
            runSpacing: Sp.s,
            children: [
              for (var i = 0; i < _games.length; i++)
                FadeSlideIn(
                  index: i,
                  child: PressScale(
                    onTap: () => _play(_games[i]),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
                      decoration: BoxDecoration(
                        color: BuddyColors.accents[i % BuddyColors.accents.length]
                            .withAlpha(context.isDark ? 70 : 48),
                        borderRadius: Rad.pillRadius,
                      ),
                      child: Text(_games[i], style: context.tt.labelLarge),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: Sp.xl),
          SectionTitle(tr('Стикеры (нажмите, чтобы отправить)', en: 'Stickers (tap to send)', uz: 'Stikerlar (yuborish uchun bosing)'),
              icon: Icons.emoji_emotions_outlined),
          GridView.count(
            crossAxisCount: 4,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: Sp.s,
            crossAxisSpacing: Sp.s,
            children: [
              for (final m in _moods)
                PressScale(
                  onTap: () => _share(m),
                  radius: BorderRadius.circular(18),
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: cs.surfaceContainer,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: cs.outlineVariant),
                    ),
                    child: _stickers[m] == null || _stickers[m]!.isEmpty
                        ? const Mascot(mood: MascotMood.wink, size: 40, animate: false)
                        : Image.memory(_stickers[m]!),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
