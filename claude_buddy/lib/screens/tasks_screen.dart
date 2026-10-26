import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import '../native.dart';
import '../mascot.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';

class TasksScreen extends StatefulWidget {
  const TasksScreen({super.key});

  @override
  State<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends State<TasksScreen> with WidgetsBindingObserver {
  List<Task> _tasks = const [];
  bool _loaded = false;
  final Map<String, _Hold> _holds = {};
  final Map<String, int> _gen = {};
  final Set<String> _fresh = {};
  final List<Timer> _timers = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    for (final t in _timers) {
      t.cancel();
    }
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    try {
      final l = await Native.listTasks();
      if (mounted) {
        setState(() {
          _tasks = l;
          _loaded = true;
        });
      }
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _run(Future<void> Function() f) async {
    try {
      await f();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
    await _load();
  }

  Future<void> _edit([Task? task]) async {
    final result = await showDialog<Task>(
      context: context,
      builder: (_) => _TaskDialog(task: task),
    );
    if (result == null) return;
    await _run(() => Native.saveTask(result));
  }

  List<Object> _rows() {
    final open = [
      for (final t in _tasks)
        if (!t.done && !_isHeld(t.id)) t,
    ];
    final done = [
      for (final t in _tasks)
        if (t.done && !_isHeld(t.id)) t,
    ];
    final rows = <Object>[
      if (open.isNotEmpty) tr('Нужно сделать', en: 'To do', uz: 'Bajarish kerak'),
      ...open,
      if (done.isNotEmpty) tr('Готово', en: 'Done', uz: 'Tayyor'),
      ...done,
    ];
    final holds = _holds.values.toList()
      ..sort((a, b) => a.index.compareTo(b.index));
    for (final h in holds) {
      rows.insert(math.min(h.index, rows.length), h);
    }
    return rows;
  }

  bool _isHeld(String id) {
    final h = _holds[id];
    return h != null && !h.collapsing;
  }

  void _toggleDone(Task t, bool v) {
    if (_holds.containsKey(t.id)) return;
    final before = _rows();
    final idx = before.indexWhere((r) => r is Task && r.id == t.id);
    final toggled = Task(
      id: t.id,
      title: t.title,
      notes: t.notes,
      dueMillis: t.dueMillis,
      remind: t.remind,
      done: v,
    );
    setState(() {
      _tasks = [
        for (final x in _tasks)
          if (x.id == t.id) toggled else x,
      ];
      _holds[t.id] = _Hold(toggled, idx < 0 ? 0 : idx, _gen[t.id] ?? 0);
    });
    _run(() => Native.setTaskDone(t.id, v));
    // Stage 1: stay in place while the check and confetti play.
    _timers.add(Timer(const Duration(milliseconds: 650), () {
      if (!mounted) return;
      final h = _holds[t.id];
      if (h == null) return;
      setState(() {
        h.collapsing = true;
        _gen[t.id] = h.gen + 1;
        _fresh.add(t.id);
      });
      // Stage 2: collapse the ghost, then drop it.
      _timers.add(Timer(const Duration(milliseconds: 380), () {
        if (!mounted) return;
        setState(() {
          _holds.remove(t.id);
          _fresh.remove(t.id);
        });
      }));
    }));
  }

  void _delete(Task t) {
    setState(() => _tasks = _tasks.where((x) => x.id != t.id).toList());
    _run(() => Native.deleteTask(t.id));
  }

  @override
  Widget build(BuildContext context) {
    final open = _tasks.where((t) => !t.done).toList();
    final done = _tasks.where((t) => t.done).toList();
    final rows = _rows();
    return Scaffold(
      backgroundColor: Colors.transparent,
      floatingActionButton: BuddyFab.extended(
        onPressed: _edit,
        icon: const Icon(Icons.add_rounded),
        label: Text(tr('Задача', en: 'Task', uz: 'Vazifa')),
      ),
      body: Column(
        children: [
          GradientHeader(
            title: tr('Задачи', en: 'Tasks', uz: 'Vazifalar'),
            subtitle: !_loaded
                ? null
                : _tasks.isEmpty
                ? tr('Пока пусто', en: 'Nothing yet', uz: 'Hozircha boʻsh')
                : tr('Открыто: ${open.length} · выполнено: ${done.length}', en: 'Open: ${open.length} · done: ${done.length}', uz: 'Ochiq: ${open.length} · bajarilgan: ${done.length}'),
          ),
          Expanded(
            child: !_loaded
                ? const SkeletonList(count: 5)
                : _tasks.isEmpty
                ? EmptyState(
                    mood: MascotMood.happy,
                    title: tr('Задач пока нет', en: 'No tasks yet', uz: 'Hozircha vazifalar yoʻq'),
                    message: tr('Нажмите «Задача», чтобы добавить первую', en: 'Tap “Task” to add your first one', uz: 'Birinchisini qoʻshish uchun «Vazifa» ni bosing'),
                    bubble: tr('Давай что-нибудь спланируем!', en: 'Let’s plan something!', uz: 'Keling, nimadir rejalashtiramiz!'),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.only(top: Sp.m, bottom: 96),
                    itemCount: rows.length,
                    itemBuilder: (context, i) {
                      final r = rows[i];
                      if (r is _Hold) {
                        return _TaskTile(
                          key: ValueKey('${r.task.id}:${r.gen}'),
                          task: r.task,
                          index: i,
                          collapsing: r.collapsing,
                          onToggle: (v) {},
                          onEdit: () {},
                          onDelete: () {},
                        );
                      }
                      if (r is String) {
                        return Padding(
                          key: ValueKey('h$r'),
                          padding: const EdgeInsets.fromLTRB(20, Sp.m, 20, 0),
                          child: SectionTitle(r),
                        );
                      }
                      final t = r as Task;
                      return _TaskTile(
                        key: ValueKey('${t.id}:${_gen[t.id] ?? 0}'),
                        task: t,
                        index: _fresh.contains(t.id) ? -1 : i,
                        onToggle: (v) => _toggleDone(t, v),
                        onEdit: () => _edit(t),
                        onDelete: () => _delete(t),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class _Hold {
  _Hold(this.task, this.index, this.gen);
  final Task task;
  final int index;
  final int gen;
  bool collapsing = false;
}

class _TaskTile extends StatefulWidget {
  const _TaskTile({
    super.key,
    required this.task,
    required this.index,
    this.collapsing = false,
    required this.onToggle,
    required this.onEdit,
    required this.onDelete,
  });

  final Task task;

  /// List position for the stagger; -1 means "moved here": grow in place.
  final int index;
  final bool collapsing;
  final ValueChanged<bool> onToggle;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  State<_TaskTile> createState() => _TaskTileState();
}

class _TaskTileState extends State<_TaskTile>
    with SingleTickerProviderStateMixin {
  late final AnimationController _size = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 380),
    value: 1,
  );
  bool _init = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_init) return;
    _init = true;
    if (widget.index < 0 && !context.noMotion) _size.forward(from: 0);
  }

  @override
  void didUpdateWidget(_TaskTile old) {
    super.didUpdateWidget(old);
    if (widget.collapsing && !old.collapsing) {
      if (context.noMotion) {
        _size.value = 0;
      } else {
        _size.reverse();
      }
    }
  }

  @override
  void dispose() {
    _size.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final curved = _size.drive(CurveTween(curve: Curves.easeInOutCubic));
    return SizeTransition(
      sizeFactor: curved,
      alignment: Alignment.topCenter,
      child: FadeTransition(opacity: curved, child: _content(context)),
    );
  }

  Widget _content(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    final t = widget.task;
    final due = t.due;
    final onToggle = widget.onToggle;
    final onEdit = widget.onEdit;
    final onDelete = widget.onDelete;
    return FadeSlideIn(
      index: math.max(widget.index, 0),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: Sp.l, vertical: 5),
        child: Dismissible(
          key: ValueKey('d${t.id}'),
          direction: widget.collapsing
              ? DismissDirection.none
              : DismissDirection.endToStart,
          resizeDuration: const Duration(milliseconds: 320),
          background: Container(
            decoration: BoxDecoration(
              color: cs.error,
              borderRadius: Rad.cardRadius,
            ),
            alignment: Alignment.centerRight,
            padding: const EdgeInsets.only(right: 24),
            child: Icon(Icons.delete_outline_rounded, color: cs.onError),
          ),
          confirmDismiss: (_) async {
            final ok = await showDialog<bool>(
              context: context,
              builder: (c) => AlertDialog(
                title: Text(tr('Удалить задачу?', en: 'Delete task?', uz: 'Vazifa oʻchirilsinmi?')),
                content: Text(tr('«${t.title}» будет удалена.', en: '“${t.title}” will be deleted.', uz: '«${t.title}» oʻchiriladi.')),
                actions: [
                  TextButton(
                      onPressed: () => Navigator.pop(c, false),
                      child: Text(tr('Отмена', en: 'Cancel', uz: 'Bekor qilish'))),
                  TextButton(
                      onPressed: () => Navigator.pop(c, true),
                      child: Text(tr('Удалить', en: 'Delete', uz: 'Oʻchirish'))),
                ],
              ),
            );
            return ok == true;
          },
          onDismissed: (_) => onDelete(),
          child: SoftCard(
            padding: const EdgeInsets.fromLTRB(Sp.m, Sp.m, Sp.l, Sp.m),
            onTap: onEdit,
            child: Row(
              children: [
                _BouncyCheck(value: t.done, onChanged: onToggle),
                const SizedBox(width: Sp.m),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      AnimatedDefaultTextStyle(
                        duration: Dur.med,
                        style: (tt.titleMedium ?? const TextStyle()).copyWith(
                          decoration: t.done
                              ? TextDecoration.lineThrough
                              : TextDecoration.none,
                          color: t.done ? cs.onSurfaceVariant : cs.onSurface,
                        ),
                        child: Text(t.title),
                      ),
                      if (t.notes.isNotEmpty) ...[
                        const SizedBox(height: 2),
                        Text(
                          t.notes,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: tt.bodySmall,
                        ),
                      ],
                      if (due != null) ...[
                        const SizedBox(height: Sp.s),
                        _DueChip(due: due, done: t.done),
                      ],
                    ],
                  ),
                ),
                if (t.remind && due != null)
                  Padding(
                    padding: const EdgeInsets.only(left: Sp.s),
                    child: Icon(
                      Icons.notifications_active_outlined,
                      size: 20,
                      color: cs.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DueChip extends StatelessWidget {
  const _DueChip({required this.due, required this.done});

  final DateTime due;
  final bool done;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final left = due.difference(DateTime.now());
    final Color bg;
    final Color fg;
    final IconData icon;
    if (done) {
      bg = cs.surfaceContainerHighest;
      fg = cs.onSurfaceVariant;
      icon = Icons.check_rounded;
    } else if (left.isNegative) {
      bg = cs.errorContainer;
      fg = cs.onErrorContainer;
      icon = Icons.error_outline_rounded;
    } else if (left < const Duration(hours: 24)) {
      bg = cs.primaryContainer;
      fg = cs.onPrimaryContainer;
      icon = Icons.local_fire_department_outlined;
    } else if (left < const Duration(days: 4)) {
      bg = cs.secondaryContainer;
      fg = cs.onSecondaryContainer;
      icon = Icons.schedule_rounded;
    } else {
      bg = cs.surfaceContainerHighest;
      fg = cs.onSurfaceVariant;
      icon = Icons.event_outlined;
    }
    return AnimatedContainer(
      duration: Dur.med,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: bg, borderRadius: Rad.pillRadius),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: fg),
          const SizedBox(width: 5),
          Flexible(
            child: Text(
              fmtDue(due),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: context.tt.labelSmall?.copyWith(
                color: fg,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BouncyCheck extends StatelessWidget {
  const _BouncyCheck({required this.value, required this.onChanged});

  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Semantics(
      checked: value,
      label: value ? tr('Выполнено', en: 'Completed', uz: 'Bajarildi') : tr('Отметить выполненным', en: 'Mark as done', uz: 'Bajarildi deb belgilash'),
      child: Builder(builder: (ctx) => PressScale(
        onTap: () {
          if (!value) Confetti.burst(ctx); // success: confetti + light haptic
          onChanged(!value);
        },
        scale: 0.82,
        radius: Rad.pillRadius,
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Bounce(
              trigger: value,
              amount: 0.3,
              child: AnimatedContainer(
                duration: Dur.med,
                curve: Curves.easeOutBack,
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: value ? cs.primary : Colors.transparent,
                  border: Border.all(
                    color: value ? cs.primary : cs.outline,
                    width: 2,
                  ),
                ),
                child: TweenAnimationBuilder<double>(
                  tween: Tween<double>(end: value ? 1 : 0),
                  duration: context.noMotion
                      ? Duration.zero
                      : const Duration(milliseconds: 360),
                  curve: Curves.easeOutCubic,
                  builder: (context, t, _) =>
                      CustomPaint(painter: _CheckPainter(t, cs.onPrimary)),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Draws a check mark progressively (stroke draw-on).
class _CheckPainter extends CustomPainter {
  _CheckPainter(this.t, this.color);

  final double t;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    if (t <= 0) return;
    final w = size.width;
    final h = size.height;
    final path = Path()
      ..moveTo(w * 0.27, h * 0.52)
      ..lineTo(w * 0.43, h * 0.67)
      ..lineTo(w * 0.73, h * 0.35);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.6
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = color;
    for (final m in path.computeMetrics()) {
      canvas.drawPath(m.extractPath(0, m.length * t.clamp(0.0, 1.0)), paint);
    }
  }

  @override
  bool shouldRepaint(_CheckPainter old) => old.t != t || old.color != color;
}

class _TaskDialog extends StatefulWidget {
  const _TaskDialog({this.task});

  final Task? task;

  @override
  State<_TaskDialog> createState() => _TaskDialogState();
}

class _TaskDialogState extends State<_TaskDialog> {
  late final TextEditingController _title = TextEditingController(
    text: widget.task?.title ?? '',
  );
  late final TextEditingController _notes = TextEditingController(
    text: widget.task?.notes ?? '',
  );
  late DateTime? _due = widget.task?.due;
  late bool _remind = widget.task?.remind ?? true;

  @override
  void dispose() {
    _title.dispose();
    _notes.dispose();
    super.dispose();
  }

  Future<void> _pick() async {
    final d = await pickDateTime(
      context,
      _due ?? DateTime.now().add(const Duration(hours: 1)),
    );
    if (d != null) setState(() => _due = d);
  }

  void _submit() {
    final title = _title.text.trim();
    if (title.isEmpty) return;
    final old = widget.task;
    Navigator.of(context).pop(
      Task(
        id: old?.id ?? '',
        title: title,
        notes: _notes.text.trim(),
        dueMillis: _due?.millisecondsSinceEpoch,
        remind: _remind,
        done: old?.done ?? false,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final due = _due;
    return AlertDialog(
      title: Text(widget.task == null ? tr('Новая задача', en: 'New task', uz: 'Yangi vazifa') : tr('Задача', en: 'Task', uz: 'Vazifa')),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _title,
              autofocus: true,
              decoration: InputDecoration(labelText: tr('Название', en: 'Title', uz: 'Nomi')),
            ),
            TextField(
              controller: _notes,
              maxLines: 3,
              minLines: 1,
              decoration: InputDecoration(labelText: tr('Заметки', en: 'Notes', uz: 'Eslatmalar')),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: BuddyButton.outline(
                    onPressed: _pick,
                    icon: const Icon(Icons.event),
                    child: Text(due == null ? tr('Срок', en: 'Due date', uz: 'Muddat') : fmtDateTime(due)),
                  ),
                ),
                if (due != null)
                  BuddyIconButton(
                    tooltip: tr('Убрать срок', en: 'Remove due date', uz: 'Muddatni olib tashlash'),
                    icon: const Icon(Icons.clear),
                    onPressed: () => setState(() => _due = null),
                  ),
              ],
            ),
            BuddySwitchTile(
              contentPadding: EdgeInsets.zero,
              title: Text(tr('Напомнить', en: 'Remind me', uz: 'Eslatish')),
              value: _remind,
              onChanged: (v) => setState(() => _remind = v),
            ),
          ],
        ),
      ),
      actions: [
        BuddyButton.text(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(tr('Отмена', en: 'Cancel', uz: 'Bekor qilish')),
        ),
        BuddyButton.filled(onPressed: _submit, child: Text(tr('Сохранить', en: 'Save', uz: 'Saqlash'))),
      ],
    );
  }
}
