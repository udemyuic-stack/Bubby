import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../mascot.dart';
import '../l10n/l10n.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/outfit_art.dart';
import '../widgets/widgets.dart';

class OutfitsScreen extends StatefulWidget {
  const OutfitsScreen({super.key});

  @override
  State<OutfitsScreen> createState() => _OutfitsScreenState();
}

class _Preset {
  const _Preset(this.id, this.label, this.items, this.color);
  final String id;
  final String label;
  final List<String> items;
  final Color color;
}

class _OutfitsScreenState extends State<OutfitsScreen>
    with SingleTickerProviderStateMixin {
  static const List<String> _slotOrder = ['head', 'eyes', 'neck', 'aura', 'hand'];
  static Map<String, String> get _slotTitles => {
    'head': tr('Голова', en: 'Head', uz: 'Bosh'),
    'eyes': tr('Глаза', en: 'Eyes', uz: 'Koʻzlar'),
    'neck': tr('Шея', en: 'Neck', uz: 'Boʻyin'),
    'aura': tr('Аура', en: 'Aura', uz: 'Aura'),
    'hand': tr('В руке', en: 'In hand', uz: 'Qoʻlda'),
  };
  static const Map<String, List<String>> _slots = {
    'head': [
      'hat', 'cap', 'crown', 'bow', 'headphones', 'flower', 'party', 'santa',
      'wizard', 'ninja', 'samurai', 'pirate', 'astronaut', 'halo', 'horns', 'catears',
    ],
    'eyes': ['glasses', 'sunglasses', 'mask', 'visor'],
    'neck': ['scarf', 'bowtie', 'cape', 'tie'],
    'aura': ['fire', 'ice', 'lightning', 'hearts', 'sakura', 'stars'],
    'hand': ['wand', 'sword', 'umbrella', 'lollipop'],
  };
  static Map<String, String> get _labels => {
    'hat': tr('Шляпа', en: 'Hat', uz: 'Shlyapa'),
    'cap': tr('Кепка', en: 'Cap', uz: 'Kepka'),
    'crown': tr('Корона', en: 'Crown', uz: 'Toj'),
    'bow': tr('Бант', en: 'Bow', uz: 'Bant'),
    'headphones': tr('Наушники', en: 'Headphones', uz: 'Quloqchin'),
    'flower': tr('Цветок', en: 'Flower', uz: 'Gul'),
    'party': tr('Колпак', en: 'Party hat', uz: 'Bayram qalpogʻi'),
    'santa': tr('Шапка Санты', en: 'Santa hat', uz: 'Santa shapkasi'),
    'wizard': tr('Колпак волшебника', en: 'Wizard hat', uz: 'Sehrgar qalpogʻi'),
    'ninja': tr('Повязка ниндзя', en: 'Ninja headband', uz: 'Ninja bogʻichi'),
    'samurai': tr('Шлем самурая', en: 'Samurai helmet', uz: 'Samuray dubulgʻasi'),
    'pirate': tr('Шляпа пирата', en: 'Pirate hat', uz: 'Qaroqchi shlyapasi'),
    'astronaut': tr('Шлем космонавта', en: 'Astronaut helmet', uz: 'Kosmonavt dubulgʻasi'),
    'halo': tr('Нимб', en: 'Halo', uz: 'Nur halqasi'),
    'horns': tr('Рожки', en: 'Horns', uz: 'Shoxlar'),
    'catears': tr('Кошачьи ушки', en: 'Cat ears', uz: 'Mushuk quloqlari'),
    'glasses': tr('Очки', en: 'Glasses', uz: 'Koʻzoynak'),
    'sunglasses': tr('Солнечные очки', en: 'Sunglasses', uz: 'Quyosh koʻzoynagi'),
    'mask': tr('Маска героя', en: 'Hero mask', uz: 'Qahramon niqobi'),
    'visor': tr('Кибер-визор', en: 'Cyber visor', uz: 'Kiber koʻzoynak'),
    'scarf': tr('Шарф', en: 'Scarf', uz: 'Sharf'),
    'bowtie': tr('Бабочка', en: 'Bow tie', uz: 'Kapalak galstuk'),
    'cape': tr('Плащ', en: 'Cape', uz: 'Plash'),
    'tie': tr('Галстук', en: 'Tie', uz: 'Galstuk'),
    'fire': tr('Огонь', en: 'Fire', uz: 'Olov'),
    'ice': tr('Лёд', en: 'Ice', uz: 'Muz'),
    'lightning': tr('Молния', en: 'Lightning', uz: 'Chaqmoq'),
    'hearts': tr('Сердечки', en: 'Hearts', uz: 'Yuraklar'),
    'sakura': tr('Сакура', en: 'Sakura', uz: 'Sakura'),
    'stars': tr('Звёзды', en: 'Stars', uz: 'Yulduzlar'),
    'wand': tr('Волшебная палочка', en: 'Wand', uz: 'Sehrli tayoqcha'),
    'sword': tr('Меч', en: 'Sword', uz: 'Qilich'),
    'umbrella': tr('Зонтик', en: 'Umbrella', uz: 'Soyabon'),
    'lollipop': tr('Леденец', en: 'Lollipop', uz: 'Lolipop'),
  };
  static const Map<String, IconData> _icons = {
    'hat': Icons.school_outlined,
    'cap': Icons.sports_baseball_outlined,
    'crown': Icons.workspace_premium_outlined,
    'bow': Icons.redeem_outlined,
    'headphones': Icons.headphones_outlined,
    'flower': Icons.local_florist_outlined,
    'party': Icons.celebration_outlined,
    'santa': Icons.ac_unit,
    'wizard': Icons.auto_fix_high_outlined,
    'ninja': Icons.sports_martial_arts_outlined,
    'samurai': Icons.shield_outlined,
    'pirate': Icons.sailing_outlined,
    'astronaut': Icons.rocket_launch_outlined,
    'halo': Icons.radio_button_unchecked,
    'horns': Icons.whatshot_outlined,
    'catears': Icons.pets_outlined,
    'glasses': Icons.visibility_outlined,
    'sunglasses': Icons.wb_sunny_outlined,
    'mask': Icons.theater_comedy_outlined,
    'visor': Icons.view_in_ar_outlined,
    'scarf': Icons.waves,
    'bowtie': Icons.filter_vintage_outlined,
    'cape': Icons.checkroom_outlined,
    'tie': Icons.arrow_downward_rounded,
    'wand': Icons.auto_fix_high_outlined,
    'sword': Icons.gavel_outlined,
    'umbrella': Icons.beach_access_outlined,
    'lollipop': Icons.icecream_outlined,
  };

  static List<_Preset> get _presets => [
    _Preset('ninja', tr('Ниндзя', en: 'Ninja', uz: 'Ninja'), ['ninja', 'scarf'], Color(0xFF3B3552)),
    _Preset('wizard', tr('Волшебник', en: 'Wizard', uz: 'Sehrgar'), ['wizard', 'wand', 'stars'], Color(0xFF6E4BD8)),
    _Preset('samurai', tr('Самурай', en: 'Samurai', uz: 'Samuray'), ['samurai', 'sword'], Color(0xFFC0392B)),
    _Preset('superhero', tr('Супергерой', en: 'Superhero', uz: 'Superqahramon'), ['mask', 'cape', 'lightning'], Color(0xFFE5365B)),
    _Preset('pirate', tr('Пират', en: 'Pirate', uz: 'Qaroqchi'), ['pirate', 'sword'], Color(0xFF8A5A34)),
    _Preset('astronaut', tr('Космонавт', en: 'Astronaut', uz: 'Kosmonavt'), ['astronaut', 'stars'], Color(0xFF3A7BD5)),
    _Preset('fire_mage', tr('Огненный маг', en: 'Fire mage', uz: 'Olovli sehrgar'), ['wizard', 'fire', 'wand'], Color(0xFFF0643A)),
    _Preset('ice_queen', tr('Снежная королева', en: 'Ice queen', uz: 'Qor malikasi'), ['crown', 'ice', 'wand'], Color(0xFF3FB5E8)),
    _Preset('angel', tr('Ангел', en: 'Angel', uz: 'Farishta'), ['halo', 'sakura'], Color(0xFFE0A93B)),
    _Preset('devil', tr('Демон', en: 'Devil', uz: 'Iblis'), ['horns', 'fire', 'cape'], Color(0xFF9B2335)),
  ];

  static List<(String, String)> get _animals => [
    ('cat', tr('Кот', en: 'Cat', uz: 'Mushuk')),
    ('dog', tr('Пёс', en: 'Dog', uz: 'It')),
    ('bunny', tr('Зайчик', en: 'Bunny', uz: 'Quyon')),
    ('bear', tr('Мишка', en: 'Bear', uz: 'Ayiq')),
    ('panda', tr('Панда', en: 'Panda', uz: 'Panda')),
    ('fox', tr('Лис', en: 'Fox', uz: 'Tulki')),
    ('frog', tr('Лягушонок', en: 'Frog', uz: 'Qurbaqa')),
    ('penguin', tr('Пингвин', en: 'Penguin', uz: 'Pingvin')),
    ('capybara', tr('Капибара', en: 'Capybara', uz: 'Kapibara')),
    ('hamster', tr('Хомяк', en: 'Hamster', uz: 'Xomyak')),
    ('owl', tr('Сова', en: 'Owl', uz: 'Boyqush')),
    ('duck', tr('Утёнок', en: 'Duck', uz: 'Oʻrdak')),
    ('pig', tr('Поросёнок', en: 'Pig', uz: 'Choʻchqa')),
    ('koala', tr('Коала', en: 'Koala', uz: 'Koala')),
    ('lion', tr('Лев', en: 'Lion', uz: 'Sher')),
    ('tiger', tr('Тигр', en: 'Tiger', uz: 'Yoʻlbars')),
  ];

  final math.Random _rng = math.Random();
  late final AnimationController _tick = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 8),
  );

  String _species = 'cat';
  Set<String> _items = {};
  Uint8List? _preview;
  bool _loaded = false;
  int _rev = 0;
  String _slot = 'head';

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (context.noMotion) {
      if (_tick.isAnimating) _tick.stop();
      _tick.value = 0.3;
    } else if (!_tick.isAnimating) {
      _tick.repeat();
    }
  }

  @override
  void dispose() {
    _tick.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final s = await Native.getSettings();
      if (!mounted) return;
      setState(() {
        _items = s.outfit.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toSet();
        _species = s.species;
        _loaded = true;
      });
      _refresh();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _refresh() async {
    try {
      final b = await Native.renderSticker('happy');
      if (mounted) setState(() => _preview = b);
    } on NativeException {
      // preview is optional
    }
  }

  String _csv(Set<String> items) {
    final out = <String>[];
    for (final slot in _slotOrder) {
      for (final i in _slots[slot]!) {
        if (items.contains(i)) out.add(i);
      }
    }
    return out.join(',');
  }

  Future<void> _set(Set<String> items) async {
    setState(() {
      _items = items;
      _rev++;
    });
    try {
      await Native.saveSettings({'outfit': _csv(items)});
      await _refresh();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _setSpecies(String id) async {
    if (id == _species) return;
    setState(() {
      _species = id;
      _rev++;
    });
    try {
      await Native.saveSettings({'species': id});
      await _refresh();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  void _toggle(String slot, String item) {
    final next = {..._items};
    final had = next.contains(item);
    next.removeAll(_slots[slot]!);
    if (!had) next.add(item);
    _set(next);
  }

  void _shuffle() {
    if (_rng.nextDouble() < 0.4) {
      _set(_presets[_rng.nextInt(_presets.length)].items.toSet());
      return;
    }
    final next = <String>{};
    for (final slot in _slotOrder) {
      if (_rng.nextDouble() < 0.55) {
        final list = _slots[slot]!;
        next.add(list[_rng.nextInt(list.length)]);
      }
    }
    if (next.isEmpty) next.add(_slots['head']![_rng.nextInt(_slots['head']!.length)]);
    _set(next);
  }

  bool _isPreset(_Preset p) =>
      _items.length == p.items.length && _items.containsAll(p.items);

  Widget _previewCard(BuildContext context) {
    final cs = context.cs;
    final preview = _preview;
    final label = _animals.firstWhere((a) => a.$1 == _species, orElse: () => _animals.first).$2;
    return SoftCard(
      padding: const EdgeInsets.all(Sp.l),
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [cs.primaryContainer, cs.surfaceContainerLow],
      ),
      child: Column(
        children: [
          SizedBox(
            height: 210,
            child: Stack(
              alignment: Alignment.center,
              children: [
                AnimatedBuilder(
                  animation: _tick,
                  builder: (context, _) {
                    final k = 0.5 + 0.5 * math.sin(_tick.value * math.pi * 2 * 4);
                    return Container(
                      width: 190 + 12 * k,
                      height: 190 + 12 * k,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        gradient: RadialGradient(
                          colors: [
                            cs.primary.withAlpha((70 + 50 * k).round()),
                            cs.primary.withAlpha(0),
                          ],
                        ),
                      ),
                    );
                  },
                ),
                Bounce(
                  trigger: _rev,
                  amount: 0.1,
                  child: SizedBox(
                    width: 170,
                    height: 170,
                    child: preview == null || preview.isEmpty
                        ? Mascot(mood: MascotMood.happy, size: 150, species: _species)
                        : Image.memory(preview, gaplessPlayback: true),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: Sp.s),
          Row(
            children: [
              Expanded(
                child: Text(
                  '$label · ${_items.isEmpty ? tr('без наряда', en: 'no outfit', uz: 'kiyimsiz') : tr('${_items.length} шт.', en: '${_items.length} pcs', uz: '${_items.length} ta')}',
                  style: context.tt.titleMedium,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              BuddyButton.tonal(
                onPressed: _shuffle,
                icon: const Icon(Icons.shuffle_rounded),
                child: Text(tr('Ещё', en: 'More', uz: 'Yana')),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _slotGrid() {
    final items = _slots[_slot]!;
    return GridView.count(
      key: ValueKey(_slot),
      crossAxisCount: 3,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: Sp.m,
      crossAxisSpacing: Sp.m,
      childAspectRatio: 1.0,
      children: [
        for (final item in items)
          _slot == 'aura'
              ? _AuraCard(
                  kind: item,
                  label: _labels[item] ?? item,
                  tick: _tick,
                  selected: _items.contains(item),
                  onTap: () => _toggle(_slot, item),
                )
              : _OutfitTile(
                  label: _labels[item] ?? item,
                  icon: _icons[item] ?? Icons.star_outline_rounded,
                  selected: _items.contains(item),
                  onTap: () => _toggle(_slot, item),
                ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(tr('Питомец и наряды', en: 'Pet & outfits', uz: 'Uy hayvoni va kiyimlar')),
        actions: [
          BuddyIconButton(
            tooltip: tr('Снять всё', en: 'Remove all', uz: 'Hammasini yechish'),
            icon: const Icon(Icons.layers_clear_outlined),
            onPressed: _items.isEmpty ? null : () => _set({}),
          ),
        ],
      ),
      body: !_loaded
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(Sp.l, Sp.s, Sp.l, Sp.xl),
              children: [
                _previewCard(context),
                Padding(
                  padding: EdgeInsets.only(top: Sp.l),
                  child: SectionTitle(tr('Питомец', en: 'Pet', uz: 'Uy hayvoni')),
                ),
                Wrap(
                  spacing: Sp.s,
                  runSpacing: Sp.s,
                  children: [
                    for (final a in _animals)
                      _AnimalCard(
                        id: a.$1,
                        label: a.$2,
                        selected: _species == a.$1,
                        onTap: () => _setSpecies(a.$1),
                      ),
                  ],
                ),
                Padding(
                  padding: EdgeInsets.only(top: Sp.l),
                  child: SectionTitle(tr('Образы', en: 'Looks', uz: 'Obrazlar')),
                ),
                SizedBox(
                  height: 152,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    clipBehavior: Clip.none,
                    padding: const EdgeInsets.symmetric(vertical: Sp.s),
                    itemCount: _presets.length,
                    separatorBuilder: (_, _) => const SizedBox(width: Sp.m),
                    itemBuilder: (context, i) {
                      final p = _presets[i];
                      return _PresetCard(
                        id: p.id,
                        label: p.label,
                        color: p.color,
                        selected: _isPreset(p),
                        onTap: () => _set(p.items.toSet()),
                      );
                    },
                  ),
                ),
                Padding(
                  padding: EdgeInsets.only(top: Sp.m),
                  child: SectionTitle(tr('Наряд по частям', en: 'Outfit piece by piece', uz: 'Kiyim qismlar boʻyicha')),
                ),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      for (final s in _slotOrder)
                        Padding(
                          padding: const EdgeInsets.only(right: Sp.s),
                          child: ChoiceChip(
                            label: Text(_slotTitles[s]!),
                            selected: _slot == s,
                            onSelected: (_) => setState(() => _slot = s),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: Sp.m),
                AnimatedSwitcher(
                  duration: context.noMotion
                      ? Duration.zero
                      : const Duration(milliseconds: 220),
                  child: _slotGrid(),
                ),
              ],
            ),
    );
  }
}

class _PresetCard extends StatelessWidget {
  const _PresetCard({
    required this.id,
    required this.label,
    required this.color,
    required this.selected,
    required this.onTap,
  });

  final String id;
  final String label;
  final Color color;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return SizedBox(
      width: 108,
      child: Bounce(
        trigger: selected,
        amount: 0.1,
        child: SoftCard(
          padding: const EdgeInsets.all(Sp.s),
          color: selected ? cs.primaryContainer : null,
          borderColor: selected ? cs.primary : null,
          borderWidth: selected ? 2 : 1,
          onTap: onTap,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 60,
                height: 60,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: color.withAlpha(36),
                ),
                child: CustomPaint(painter: EmblemPainter(id, color)),
              ),
              const SizedBox(height: Sp.s),
              Text(
                label,
                maxLines: 2,
                textAlign: TextAlign.center,
                overflow: TextOverflow.ellipsis,
                style: context.tt.labelLarge?.copyWith(
                  fontWeight: FontWeight.w800,
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

class _AuraCard extends StatelessWidget {
  const _AuraCard({
    required this.kind,
    required this.label,
    required this.tick,
    required this.selected,
    required this.onTap,
  });

  final String kind;
  final String label;
  final Animation<double> tick;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Bounce(
      trigger: selected,
      amount: 0.12,
      child: SoftCard(
        padding: const EdgeInsets.all(6),
        color: selected ? cs.primaryContainer : null,
        borderColor: selected ? cs.primary : null,
        borderWidth: selected ? 2 : 1,
        onTap: onTap,
        child: Column(
          children: [
            Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(14),
                child: DecoratedBox(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Color(0xFF2A2060), Color(0xFF14102E)],
                    ),
                  ),
                  child: SizedBox.expand(
                    child: CustomPaint(painter: AuraMiniPainter(kind, tick)),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: context.tt.labelMedium?.copyWith(
                color: selected ? cs.onPrimaryContainer : cs.onSurface,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _OutfitTile extends StatelessWidget {
  const _OutfitTile({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Bounce(
      trigger: selected,
      amount: 0.14,
      child: SoftCard(
        padding: const EdgeInsets.all(Sp.s),
        color: selected ? cs.primaryContainer : null,
        borderColor: selected ? cs.primary : null,
        borderWidth: selected ? 2 : 1,
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 30,
              color: selected ? cs.onPrimaryContainer : cs.onSurfaceVariant,
            ),
            const SizedBox(height: 6),
            Text(
              label,
              maxLines: 2,
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
              style: context.tt.labelMedium?.copyWith(
                color: selected ? cs.onPrimaryContainer : cs.onSurface,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AnimalCard extends StatelessWidget {
  const _AnimalCard({
    required this.id,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String id;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return SizedBox(
      width: 78,
      child: Bounce(
        trigger: selected,
        amount: 0.14,
        child: SoftCard(
          padding: const EdgeInsets.all(Sp.s),
          color: selected ? cs.primaryContainer : null,
          borderColor: selected ? cs.primary : null,
          borderWidth: selected ? 2 : 1,
          onTap: onTap,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              CustomPaint(
                size: const Size(48, 48),
                painter: MascotPainter(
                  selected ? MascotMood.happy : MascotMood.idle,
                  species: id,
                  shadow: false,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: context.tt.labelMedium?.copyWith(
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
