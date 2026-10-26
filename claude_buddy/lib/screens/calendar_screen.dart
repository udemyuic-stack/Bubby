import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../native.dart';
import '../mascot.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';

class CalendarScreen extends StatefulWidget {
  const CalendarScreen({super.key});

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen>
    with WidgetsBindingObserver, SingleTickerProviderStateMixin {
  List<CalEvent> _events = const [];
  bool _loaded = false;
  bool _hasPermission = true;
  DateTime? _selected;
  int _dir = 1;
  bool _sheetOpen = false;
  bool _refreshing = false;
  final ValueNotifier<double> _pull = ValueNotifier<double>(0);
  double _peak = 0;
  late final AnimationController _spin = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  );

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pull.dispose();
    _spin.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    try {
      final perms = await Native.permissionsStatus();
      final ok = perms['calendar'] == true;
      var events = const <CalEvent>[];
      if (ok) {
        final now = DateTime.now();
        final from = DateTime(now.year, now.month, now.day);
        events = await Native.listEvents(
            from, from.add(const Duration(days: 14)));
      }
      if (!mounted) return;
      setState(() {
        _hasPermission = ok;
        _events = events;
        _loaded = true;
      });
    } on NativeException catch (e) {
      if (mounted) {
        setState(() => _loaded = true);
        showMessage(context, e);
      }
    }
  }

  Future<void> _request() async {
    try {
      await Native.requestPermissions();
      await _load();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _add() async {
    setState(() => _sheetOpen = true);
    final r = await showModalBottomSheet<_NewEvent>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      useSafeArea: true,
      backgroundColor: context.cs.surface,
      sheetAnimationStyle: AnimationStyle(
        duration: const Duration(milliseconds: 460),
        reverseDuration: const Duration(milliseconds: 280),
        curve: Curves.easeOutCubic,
        reverseCurve: Curves.easeInCubic,
      ),
      builder: (_) => const _EventSheet(),
    );
    if (mounted) setState(() => _sheetOpen = false);
    if (r == null) return;
    try {
      await Native.addEvent(
        title: r.title,
        start: r.start,
        end: r.end,
        location: r.location,
      );
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
    await _load();
  }

  Future<bool> _confirmDelete(CalEvent e) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(tr('Удалить событие?', en: 'Delete event?', uz: 'Tadbir oʻchirilsinmi?')),
        content: Text(e.title),
        actions: [
          BuddyButton.text(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text(tr('Отмена', en: 'Cancel', uz: 'Bekor qilish')),
          ),
          BuddyButton.filled(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: Text(tr('Удалить', en: 'Delete', uz: 'Oʻchirish')),
          ),
        ],
      ),
    );
    return ok == true;
  }

  Future<void> _removed(CalEvent e) async {
    setState(() => _events = [
          for (final x in _events)
            if (x.id != e.id) x,
        ]);
    try {
      await Native.deleteEvent(e.id);
    } on NativeException catch (ex) {
      if (mounted) showMessage(context, ex);
    }
    await _load();
  }

  Future<void> _pullRefresh() async {
    if (_refreshing) return;
    setState(() => _refreshing = true);
    if (!context.noMotion) _spin.repeat();
    await Future.wait<void>([
      _load(),
      Future<void>.delayed(const Duration(milliseconds: 700)),
    ]);
    _spin.stop();
    if (mounted) setState(() => _refreshing = false);
  }

  bool _onScroll(ScrollNotification n) {
    if (n.depth != 0 || n.metrics.axis != Axis.vertical) return false;
    if (_refreshing) return false;
    if (n is ScrollEndNotification) {
      final fire = _peak >= 72;
      _peak = 0;
      _pull.value = 0;
      if (fire) _pullRefresh();
      return false;
    }
    final over = math.max(0.0, -n.metrics.pixels);
    _peak = math.max(_peak, over);
    if (n is ScrollUpdateNotification || n is OverscrollNotification) {
      _pull.value = over;
    }
    return false;
  }

  static bool _sameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  static Color _accent(CalEvent e) {
    var h = 0;
    for (final u in e.title.codeUnits) {
      h = (h + u) % 9973;
    }
    return BuddyColors.accents[h % BuddyColors.accents.length];
  }

  static DateTime get _today {
    final n = DateTime.now();
    return DateTime(n.year, n.month, n.day);
  }

  static int _dayIndex(DateTime? d) =>
      d == null ? -1 : DateTime.utc(d.year, d.month, d.day).difference(
          DateTime.utc(_today.year, _today.month, _today.day)).inDays;

  void _select(DateTime d) {
    final same = _selected != null && _sameDay(_selected!, d);
    final next = same ? null : d;
    setState(() {
      _dir = _dayIndex(next) >= _dayIndex(_selected) ? 1 : -1;
      _selected = next;
    });
  }

  Widget _agendaList() {
    final sel = _selected;
    final sorted = [
      for (final e in _events)
        if (sel == null || _sameDay(e.start, sel)) e,
    ]..sort((a, b) => a.startMillis.compareTo(b.startMillis));
    final rows = <Object>[];
    String? lastDay;
    for (final e in sorted) {
      final s = e.start;
      final day = '${s.year}-${s.month}-${s.day}';
      if (day != lastDay) {
        lastDay = day;
        rows.add(s);
      }
      rows.add(e);
    }
    if (rows.isNotEmpty) rows.add('hint');
    final tt = context.tt;
    final primary = context.cs.primary;
    var n = 0;
    final indexed = <int>[
      for (final r in rows) r is CalEvent ? n++ : 0,
    ];
    return ListView.builder(
      key: PageStorageKey<String>('agenda${sel?.day ?? 'all'}'),
      physics: const BouncingScrollPhysics(
        parent: AlwaysScrollableScrollPhysics(),
      ),
      padding: const EdgeInsets.only(bottom: 96),
      itemCount: rows.length,
      itemBuilder: (context, i) {
        final r = rows[i];
        if (r is DateTime) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(24, Sp.l, 24, Sp.xs),
            child: Text(
              fmtDayHeader(r),
              style: tt.titleSmall?.copyWith(color: primary),
            ),
          );
        }
        if (r is CalEvent) {
          return _StaggerIn(
            key: ValueKey('e${r.id}'),
            index: indexed[i],
            child: RepaintBoundary(
              child: Dismissible(
                key: ValueKey('dm${r.id}'),
                direction: DismissDirection.endToStart,
                resizeDuration: const Duration(milliseconds: 320),
                confirmDismiss: (_) => _confirmDelete(r),
                onDismissed: (_) => _removed(r),
                background: Padding(
                  padding:
                      const EdgeInsets.symmetric(horizontal: Sp.l, vertical: 5),
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: context.cs.error,
                      borderRadius: Rad.cardRadius,
                    ),
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: Padding(
                        padding: const EdgeInsets.only(right: 24),
                        child: Icon(Icons.delete_outline_rounded,
                            color: context.cs.onError),
                      ),
                    ),
                  ),
                ),
                child: _EventCard(event: r, accent: _accent(r)),
              ),
            ),
          );
        }
        return Padding(
          padding: const EdgeInsets.fromLTRB(24, Sp.l, 24, 0),
          child: Text(tr('Смахните событие влево, чтобы удалить', en: 'Swipe an event left to delete it', uz: 'Oʻchirish uchun tadbirni chapga suring'),
              style: tt.bodySmall),
        );
      },
    );
  }

  Widget _dayStrip() {
    final today = _today;
    final selIdx = _dayIndex(_selected);
    return SizedBox(
      height: 78,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: Sp.l),
        child: SizedBox(
          width: 14 * 54.0 + 13 * Sp.s,
          child: Stack(
            children: [
              AnimatedPositioned(
                duration: context.noMotion ? Duration.zero : Dur.slow,
                curve: Curves.easeOutBack,
                left: (selIdx < 0 ? 0 : selIdx) * (54.0 + Sp.s),
                top: 0,
                width: 54,
                height: 78,
                child: AnimatedScale(
                  duration: Dur.med,
                  curve: Curves.easeOutCubic,
                  scale: selIdx < 0 ? 0.7 : 1,
                  child: AnimatedOpacity(
                    duration: Dur.med,
                    opacity: selIdx < 0 ? 0 : 1,
                    child: ValueListenableBuilder<DateTime>(
                      valueListenable: hourNow,
                      builder: (context, now, _) => DecoratedBox(
                        decoration: BoxDecoration(
                          color: hourAccent(now),
                          borderRadius: BorderRadius.circular(20),
                          boxShadow: [
                            BoxShadow(
                              color: hourAccent(now).withAlpha(90),
                              blurRadius: 12,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              Row(
                children: [
                  for (var i = 0; i < 14; i++) ...[
                    if (i > 0) const SizedBox(width: Sp.s),
                    Builder(builder: (context) {
                      final d = DateTime(today.year, today.month, today.day + i);
                      return _DayChip(
                        date: d,
                        isToday: i == 0,
                        selected: i == selIdx,
                        hasEvents: _events.any((e) => _sameDay(e.start, d)),
                        onTap: () => _select(d),
                      );
                    }),
                  ],
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _body() {
    if (!_loaded) return const Center(child: CircularProgressIndicator());
    if (!_hasPermission) {
      return EmptyState(
        mood: MascotMood.sad,
        title: tr('Нет доступа к календарю', en: 'No calendar access', uz: 'Kalendarga ruxsat yoʻq'),
        message: tr('Разрешите доступ, чтобы видеть и создавать события.', en: 'Allow access to see and create events.', uz: 'Tadbirlarni koʻrish va yaratish uchun ruxsat bering.'),
        action: BuddyButton.filled(
          onPressed: _request,
          child: Text(tr('Дать разрешения', en: 'Grant permissions', uz: 'Ruxsat berish')),
        ),
      );
    }
    if (_events.isEmpty) {
      return EmptyState(
        mood: MascotMood.cool,
        title: tr('Событий на 14 дней нет', en: 'No events for 14 days', uz: '14 kunga tadbirlar yoʻq'),
        message: tr('Свободное время — отличный повод отдохнуть', en: 'Free time is a great reason to rest', uz: 'Boʻsh vaqt — dam olish uchun ajoyib bahona'),
      );
    }
    final sel = _selected;
    final has = sel == null || _events.any((e) => _sameDay(e.start, sel));
    final key = sel == null ? 'all' : '${sel.month}-${sel.day}';
    return Stack(
      children: [
        NotificationListener<ScrollNotification>(
          onNotification: _onScroll,
          child: _DaySwitcher(
            keyValue: key,
            dir: _dir,
            child: has
                ? _agendaList()
                : EmptyState(
                    mood: MascotMood.sleepy,
                    title: tr('В этот день событий нет', en: 'No events on this day', uz: 'Bu kunda tadbirlar yoʻq'),
                    message: tr('Bubby советует отдохнуть', en: 'Bubby suggests taking a rest', uz: 'Bubby dam olishni maslahat beradi'),
                    size: 90,
                  ),
          ),
        ),
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: IgnorePointer(
            child: _PullIndicator(
              pull: _pull,
              refreshing: _refreshing,
              spin: _spin,
            ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final sel = _selected;
    return Scaffold(
      backgroundColor: Colors.transparent,
      floatingActionButton: _hasPermission
          ? AnimatedScale(
              duration: Dur.med,
              curve: Curves.easeOutBack,
              scale: _sheetOpen ? 0.0 : 1,
              child: BuddyFab.extended(
                onPressed: _add,
                icon: AnimatedRotation(
                  duration: Dur.med,
                  turns: _sheetOpen ? 0.125 : 0,
                  child: const Icon(Icons.add_rounded),
                ),
                label: Text(tr('Событие', en: 'Event', uz: 'Tadbir')),
              ),
            )
          : null,
      body: Column(
        children: [
          GradientHeader(
            title: tr('Календарь', en: 'Calendar', uz: 'Kalendar'),
            subtitle: sel == null ? fmtDayHeader(DateTime.now()) : fmtDayHeader(sel),
            bottom: _hasPermission && _loaded ? _dayStrip() : null,
          ),
          Expanded(child: _body()),
        ],
      ),
    );
  }
}

/// Staggered slide-up + fade, 40 ms apart.
class _StaggerIn extends StatelessWidget {
  const _StaggerIn({super.key, required this.index, required this.child});

  final int index;
  final Widget child;

  @override
  Widget build(BuildContext context) => FadeSlideIn(
        index: index,
        step: const Duration(milliseconds: 40),
        duration: const Duration(milliseconds: 380),
        child: child,
      );
}

/// Cross-slides between two day lists in the direction of travel.
class _DaySwitcher extends StatefulWidget {
  const _DaySwitcher({
    required this.keyValue,
    required this.dir,
    required this.child,
  });

  final String keyValue;
  final int dir;
  final Widget child;

  @override
  State<_DaySwitcher> createState() => _DaySwitcherState();
}

class _DaySwitcherState extends State<_DaySwitcher>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 380),
    value: 1,
  )..addStatusListener((s) {
      if (s == AnimationStatus.completed && mounted) {
        setState(() => _old = null);
      }
    });
  Widget? _old;
  String _oldKey = '';
  int _oldDir = 1;

  @override
  void didUpdateWidget(_DaySwitcher old) {
    super.didUpdateWidget(old);
    if (old.keyValue != widget.keyValue) {
      if (context.noMotion) {
        _old = null;
        return;
      }
      _old = old.child;
      _oldKey = old.keyValue;
      _oldDir = widget.dir;
      _c.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cur = KeyedSubtree(key: ValueKey(widget.keyValue), child: widget.child);
    final old = _old;
    if (old == null) return cur;
    return AnimatedBuilder(
      animation: _c,
      builder: (context, _) {
        final t = Curves.easeOutCubic.transform(_c.value);
        final out = (1 - t * 1.6).clamp(0.0, 1.0);
        return Stack(
          fit: StackFit.expand,
          children: [
            IgnorePointer(
              child: Opacity(
                opacity: out,
                child: FractionalTranslation(
                  translation: Offset(-_oldDir * 0.10 * t, 0),
                  child: KeyedSubtree(key: ValueKey('o$_oldKey'), child: old),
                ),
              ),
            ),
            Opacity(
              opacity: t,
              child: FractionalTranslation(
                translation: Offset(widget.dir * 0.10 * (1 - t), 0),
                child: cur,
              ),
            ),
          ],
        );
      },
    );
  }
}

/// Bubby pull-to-refresh bubble.
class _PullIndicator extends StatelessWidget {
  const _PullIndicator({
    required this.pull,
    required this.refreshing,
    required this.spin,
  });

  final ValueListenable<double> pull;
  final bool refreshing;
  final Animation<double> spin;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<double>(
      valueListenable: pull,
      builder: (context, p, _) {
        final prog = (p / 72).clamp(0.0, 1.0);
        final shown = refreshing ? 1.0 : prog;
        final top = refreshing ? 10.0 : (math.min(p, 110) * 0.6 - 40);
        return Align(
          alignment: Alignment.topCenter,
          child: Transform.translate(
            offset: Offset(0, top),
            child: Opacity(
              opacity: shown,
              child: Transform.scale(
                scale: 0.5 + 0.5 * shown,
                child: ValueListenableBuilder<DateTime>(
                  valueListenable: hourNow,
                  builder: (context, now, _) => Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: hourGradient(now),
                      boxShadow: const [
                        BoxShadow(
                          color: Color(0x33000000),
                          blurRadius: 10,
                          offset: Offset(0, 3),
                        ),
                      ],
                    ),
                    child: RotationTransition(
                      turns: refreshing
                          ? spin
                          : AlwaysStoppedAnimation<double>(prog * 0.5),
                      child: const Icon(
                        Icons.auto_awesome_rounded,
                        color: BuddyColors.ink,
                        size: 22,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _DayChip extends StatelessWidget {
  const _DayChip({
    required this.date,
    required this.isToday,
    required this.selected,
    required this.hasEvents,
    required this.onTap,
  });

  final DateTime date;
  final bool isToday;
  final bool selected;
  final bool hasEvents;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final fg = selected ? BuddyColors.ink : cs.onSurface;
    final tt = context.tt;
    return PressScale(
      onTap: onTap,
      radius: BorderRadius.circular(20),
      child: AnimatedContainer(
        duration: Dur.med,
        curve: Curves.easeOutCubic,
        width: 54,
        height: 78,
        decoration: BoxDecoration(
          color: selected
              ? Colors.transparent
              : cs.surface.withAlpha(context.isDark ? 90 : 170),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isToday && !selected ? cs.primary : Colors.transparent,
            width: 1.5,
          ),
        ),
        child: Stack(
          children: [
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  AnimatedDefaultTextStyle(
                    duration: Dur.med,
                    style: (tt.labelSmall ?? const TextStyle())
                        .copyWith(color: fg.withAlpha(190)),
                    child: Text(weekdaysShortRu[date.weekday - 1]),
                  ),
                  const SizedBox(height: 2),
                  AnimatedDefaultTextStyle(
                    duration: Dur.med,
                    style: (tt.titleMedium ?? const TextStyle())
                        .copyWith(color: fg),
                    child: Text('${date.day}'),
                  ),
                  const SizedBox(height: 4),
                  AnimatedOpacity(
                    duration: Dur.med,
                    opacity: hasEvents ? 1 : 0,
                    child: AnimatedContainer(
                      duration: Dur.med,
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: selected ? BuddyColors.ink : cs.secondary,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            if (isToday)
              Positioned(
                top: 8,
                right: 9,
                child: _PulseDot(color: selected ? BuddyColors.ink : cs.primary),
              ),
          ],
        ),
      ),
    );
  }
}

/// Small dot with a soft breathing halo.
class _PulseDot extends StatefulWidget {
  const _PulseDot({required this.color});

  final Color color;

  @override
  State<_PulseDot> createState() => _PulseDotState();
}

class _PulseDotState extends State<_PulseDot>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1600),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (context.noMotion) {
      _c.stop();
      _c.value = 0;
    } else if (!_c.isAnimating) {
      _c.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: AnimatedBuilder(
        animation: _c,
        builder: (context, _) {
          final v = Curves.easeInOut.transform(_c.value);
          return Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: widget.color,
              boxShadow: [
                BoxShadow(
                  color: widget.color.withAlpha((120 * (1 - v)).round()),
                  blurRadius: 2 + 6 * v,
                  spreadRadius: 3 * v,
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _EventCard extends StatelessWidget {
  const _EventCard({required this.event, required this.accent});

  final CalEvent event;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: Sp.l, vertical: 5),
      child: SoftCard(
        padding: const EdgeInsets.fromLTRB(Sp.l, Sp.m, Sp.l, Sp.m),
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(fmtTime(event.start), style: tt.titleSmall),
                  Text(fmtTime(event.end), style: tt.bodySmall),
                ],
              ),
              const SizedBox(width: Sp.m),
              Container(
                width: 5,
                decoration: BoxDecoration(
                  color: accent,
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
              const SizedBox(width: Sp.m),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(event.title, style: tt.titleMedium),
                    if (event.location.isNotEmpty) ...[
                      const SizedBox(height: 3),
                      Row(
                        children: [
                          Icon(Icons.place_outlined, size: 15, color: cs.onSurfaceVariant),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(
                              event.location,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: tt.bodySmall,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NewEvent {
  const _NewEvent(this.title, this.start, this.end, this.location);
  final String title;
  final DateTime start;
  final DateTime end;
  final String location;
}

class _EventSheet extends StatefulWidget {
  const _EventSheet();

  @override
  State<_EventSheet> createState() => _EventSheetState();
}

class _EventSheetState extends State<_EventSheet> {
  final _title = TextEditingController();
  final _location = TextEditingController();
  late DateTime _start = _nextHour();
  late DateTime _end = _start.add(const Duration(hours: 1));

  static DateTime _nextHour() {
    final n = DateTime.now().add(const Duration(hours: 1));
    return DateTime(n.year, n.month, n.day, n.hour);
  }

  @override
  void dispose() {
    _title.dispose();
    _location.dispose();
    super.dispose();
  }

  Future<void> _pickStart() async {
    final d = await pickDateTime(context, _start);
    if (d == null) return;
    setState(() {
      final len = _end.difference(_start);
      _start = d;
      _end = d.add(len);
    });
  }

  Future<void> _pickEnd() async {
    final d = await pickDateTime(context, _end);
    if (d != null) setState(() => _end = d);
  }

  void _submit() {
    final title = _title.text.trim();
    if (title.isEmpty) return;
    if (!_end.isAfter(_start)) {
      showMessage(context, tr('Конец должен быть позже начала', en: 'The end must be after the start', uz: 'Tugash vaqti boshlanishdan keyin boʻlishi kerak'));
      return;
    }
    Navigator.of(context)
        .pop(_NewEvent(title, _start, _end, _location.text.trim()));
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.only(bottom: inset),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(Sp.xl, 0, Sp.xl, Sp.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            FadeSlideIn(
              child: Text(tr('Новое событие', en: 'New event', uz: 'Yangi tadbir'), style: context.tt.headlineSmall),
            ),
            const SizedBox(height: Sp.l),
            FadeSlideIn(
              index: 1,
              child: TextField(
                controller: _title,
                autofocus: true,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(labelText: tr('Название', en: 'Title', uz: 'Nomi')),
              ),
            ),
            const SizedBox(height: Sp.m),
            FadeSlideIn(
              index: 2,
              child: TextField(
                controller: _location,
                decoration: InputDecoration(labelText: tr('Место', en: 'Location', uz: 'Joy')),
              ),
            ),
            const SizedBox(height: Sp.m),
            FadeSlideIn(
              index: 3,
              child: BuddyButton.outline(
                onPressed: _pickStart,
                icon: const Icon(Icons.play_arrow),
                child: Text(tr('Начало: ${fmtDateTime(_start)}', en: 'Start: ${fmtDateTime(_start)}', uz: 'Boshlanishi: ${fmtDateTime(_start)}')),
              ),
            ),
            const SizedBox(height: Sp.s),
            FadeSlideIn(
              index: 4,
              child: BuddyButton.outline(
                onPressed: _pickEnd,
                icon: const Icon(Icons.stop),
                child: Text(tr('Конец: ${fmtDateTime(_end)}', en: 'End: ${fmtDateTime(_end)}', uz: 'Tugashi: ${fmtDateTime(_end)}')),
              ),
            ),
            const SizedBox(height: Sp.l),
            FadeSlideIn(
              index: 5,
              child: Row(
                children: [
                  Expanded(
                    child: BuddyButton.text(
                      onPressed: () => Navigator.of(context).pop(),
                      child: Text(tr('Отмена', en: 'Cancel', uz: 'Bekor qilish')),
                    ),
                  ),
                  const SizedBox(width: Sp.m),
                  Expanded(
                    child: BuddyButton.filled(
                      onPressed: _submit,
                      child: Text(tr('Добавить', en: 'Add', uz: 'Qoʻshish')),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
