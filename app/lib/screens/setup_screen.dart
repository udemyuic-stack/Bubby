import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/l10n.dart';
import '../mascot.dart';
import '../native.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets/widgets.dart';
import 'ai_setup_screen.dart';

/// Full-screen first-run stepper: one plain sentence, one big button.
class SetupScreen extends StatelessWidget {
  const SetupScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(tr('Настройка', en: 'Setup', uz: 'Sozlash')),
        actions: [
          IconButton(
            tooltip: tr('Все разрешения', en: 'All permissions', uz: 'Barcha ruxsatlar'),
            icon: const Icon(Icons.checklist_rounded),
            onPressed: () => Navigator.of(context).push(
              BubbyRoute<void>(builder: (_) => const PermissionsListScreen()),
            ),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: OnboardingStepper(onFinished: () => Navigator.of(context).maybePop()),
      ),
    );
  }
}

class _Step {
  const _Step(this.icon, this.title, this.why, this.action, {this.optional = false});
  final IconData icon;
  final String title;
  final String why;
  final String action;
  final bool optional;
}

class OnboardingStepper extends StatefulWidget {
  const OnboardingStepper({super.key, required this.onFinished});

  final VoidCallback onFinished;

  @override
  State<OnboardingStepper> createState() => _OnboardingStepperState();
}

class _OnboardingStepperState extends State<OnboardingStepper>
    with WidgetsBindingObserver {
  List<_Step> get _steps => [
        _Step(Icons.mic_rounded, tr('Микрофон', en: 'Microphone', uz: 'Mikrofon'), tr('Чтобы Bubby вас слышал', en: 'So Bubby can hear you', uz: 'Bubby sizni eshitishi uchun'), tr('Разрешить микрофон', en: 'Allow microphone', uz: 'Mikrofonga ruxsat bering')),
        _Step(Icons.layers_rounded, tr('Показ поверх окон', en: 'Display over other apps', uz: 'Boshqa oynalar ustida ko\'rsatish'),
            tr('Чтобы Bubby жил на экране поверх других приложений', en: 'So Bubby can live on the screen above other apps', uz: 'Bubby boshqa ilovalar ustida ekranda yashashi uchun'), tr('Открыть настройки', en: 'Open settings', uz: 'Sozlamalarni ochish')),
        _Step(Icons.auto_awesome_rounded, tr('ИИ', en: 'AI', uz: 'AI'),
            tr('Bubby думает с помощью бесплатного ИИ. Нужен ваш личный бесплатный пропуск (ключ) — получить его занимает 2 минуты', en: 'Bubby thinks with a free AI. You need your own free pass (key) — getting it takes 2 minutes', uz: 'Bubby bepul AI yordamida o\'ylaydi. Sizga shaxsiy bepul kalit kerak — uni olish 2 daqiqa oladi'),
            tr('Получить ключ', en: 'Get a key', uz: 'Kalit olish')),
        _Step(Icons.battery_charging_full_rounded, tr('Не засыпать', en: 'Stay awake', uz: 'Uxlamaslik'),
            tr('Чтобы Android не выключал Bubby в фоне', en: 'So Android does not shut Bubby down in the background', uz: 'Android Bubby ni fonda o\'chirib qo\'ymasligi uchun'), tr('Открыть настройки', en: 'Open settings', uz: 'Sozlamalarni ochish'),
            optional: true),
        _Step(Icons.celebration_rounded, tr('Попробуйте!', en: 'Try it!', uz: 'Sinab ko\'ring!'),
            tr('Нажмите на Bubby и скажите: «Привет»', en: 'Tap Bubby and say: “Hello”', uz: 'Bubby ni bosing va «Salom» deng'), tr('Готово', en: 'Done', uz: 'Tayyor')),
      ];

  int _i = 0;
  bool _positioned = false;
  bool _busy = false;
  List<bool> _ok = List<bool>.filled(5, false);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _check(initial: true);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _check();
  }

  Future<void> _check({bool initial = false}) async {
    try {
      final p = await Native.permissionsStatus();
      final s = await Native.getSettings();
      final bat = await Native.batteryStatus();
      if (!mounted) return;
      final ok = <bool>[
        p['mic'] == true,
        p['overlay'] == true,
        s.hasKeys || s.provider == 'gemma',
        bat,
        false,
      ];
      setState(() {
        _ok = ok;
        if (initial && !_positioned) {
          _positioned = true;
          final first = ok.sublist(0, 4).indexOf(false);
          _i = first < 0 ? 4 : first;
        } else if (_i < 4 && ok[_i]) {
          _i++;
          while (_i < 4 && ok[_i]) {
            _i++;
          }
        }
      });
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _act() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      switch (_i) {
        case 0:
          await Native.requestPermissions(group: 'mic');
          try {
            await Native.requestPermissions(group: 'notifications');
          } on NativeException {
            // Secondary request; ignore failures.
          }
        case 1:
          await Native.openSettings('overlay');
        case 2:
          if (!mounted) return;
          await Navigator.of(context)
              .push(BubbyRoute<void>(builder: (_) => const AiSetupScreen()));
        case 3:
          await Native.openSettings('battery');
        default:
          await Native.startBuddy();
          HapticFeedback.lightImpact();
          widget.onFinished();
          return;
      }
      await _check();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _skip() => setState(() => _i = (_i + 1).clamp(0, 4));

  @override
  Widget build(BuildContext context) {
    final tt = context.tt;
    final steps = _steps;
    final step = steps[_i];
    final passed = _ok[_i];
    return ListView(
      padding: const EdgeInsets.fromLTRB(Sp.l, Sp.l, Sp.l, Sp.xl),
      children: [
        Text(tr('Шаг ${_i + 1} из ${steps.length}', en: 'Step ${_i + 1} of ${steps.length}', uz: '${_i + 1}-qadam, jami ${steps.length} ta'), style: tt.labelLarge),
        const SizedBox(height: Sp.s),
        ClipRRect(
          borderRadius: Rad.pillRadius,
          child: TweenAnimationBuilder<double>(
            tween: Tween<double>(end: (_i + 1) / steps.length),
            duration: context.noMotion ? Duration.zero : const Duration(milliseconds: 650),
            curve: Curves.easeOutCubic,
            builder: (context, v, _) => LinearProgressIndicator(value: v, minHeight: 8),
          ),
        ),
        const SizedBox(height: Sp.xl),
        AnimatedSwitcher(
          duration: context.noMotion ? Duration.zero : Dur.med,
          child: SoftCard(
            key: ValueKey(_i),
            gradient: BuddyGradients.header(context.isDark),
            padding: const EdgeInsets.all(Sp.xl),
            child: Column(
              children: [
                _StepMascot(step: _i, passed: passed),
                const SizedBox(height: Sp.l),
                Text(step.title,
                    style: tt.headlineSmall, textAlign: TextAlign.center),
                const SizedBox(height: Sp.s),
                Text(step.why,
                    style: tt.bodyLarge, textAlign: TextAlign.center),
                if (_i == 4) ...[
                  const SizedBox(height: Sp.s),
                  Text(tr('Bubby запустится, когда вы нажмёте «Готово»', en: 'Bubby will start when you tap “Done”', uz: '«Tayyor» ni bossangiz, Bubby ishga tushadi'),
                      style: tt.bodySmall, textAlign: TextAlign.center),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: Sp.xl),
        SizedBox(
          height: 60,
          child: BuddyButton.filled(
            onPressed: _busy ? null : _act,
            loading: _busy,
            child: Text(passed && _i < 4 ? tr('Уже готово — дальше', en: 'Already done — next', uz: 'Tayyor — keyingisi') : step.action),
          ),
        ),
        if (step.optional && !passed)
          Padding(
            padding: const EdgeInsets.only(top: Sp.s),
            child: BuddyButton.text(
              onPressed: _skip,
              child: Text(tr('Пропустить', en: 'Skip', uz: 'O\'tkazib yuborish')),
            ),
          ),
        if (_i > 0)
          Padding(
            padding: const EdgeInsets.only(top: Sp.s),
            child: BuddyButton.text(
              onPressed: () => setState(() => _i--),
              child: Text(tr('Назад', en: 'Back', uz: 'Orqaga')),
            ),
          ),
      ],
    );
  }
}

/// A different mascot pose for every step, cross-fading and sliding between them.
class _StepMascot extends StatelessWidget {
  const _StepMascot({required this.step, required this.passed});

  final int step;
  final bool passed;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return SizedBox(
      height: 150,
      width: double.infinity,
      child: AnimatedSwitcher(
        duration: context.noMotion ? Duration.zero : const Duration(milliseconds: 420),
        switchInCurve: Curves.easeOutCubic,
        switchOutCurve: Curves.easeIn,
        transitionBuilder: (child, a) => FadeTransition(
          opacity: a,
          child: SlideTransition(
            position: a.drive(Tween<Offset>(begin: const Offset(0.25, 0), end: Offset.zero)),
            child: child,
          ),
        ),
        layoutBuilder: (cur, prev) => Stack(
          alignment: Alignment.center,
          children: [...prev, ?cur],
        ),
        child: KeyedSubtree(
          key: ValueKey('$step'),
          child: Stack(
            alignment: Alignment.center,
            clipBehavior: Clip.none,
            children: [
              _pose(cs),
              if (passed && step < 4)
                Positioned(
                  right: 24,
                  bottom: 8,
                  child: SuccessPulse(size: 40, haptic: true, key: ValueKey('ok$step')),
                ),
              if (step == 4) const Positioned.fill(child: Confetti()),
            ],
          ),
        ),
      ),
    );
  }

  Widget _pose(ColorScheme cs) {
    switch (step) {
      case 0:
        return Stack(
          alignment: Alignment.center,
          children: [
            _SoundRings(color: cs.primary),
            Mascot(mood: passed ? MascotMood.happy : MascotMood.listening, size: 120),
          ],
        );
      case 1:
        return _FloatingPose(
          child: Mascot(
            mood: passed ? MascotMood.happy : MascotMood.idle,
            size: 112,
            shadow: false,
          ),
        );
      case 2:
        return Stack(
          alignment: Alignment.center,
          clipBehavior: Clip.none,
          children: [
            Mascot(mood: passed ? MascotMood.excited : MascotMood.thinking, size: 120),
            if (passed)
              const Positioned(
                  right: 70, top: 4, child: Icon(Icons.auto_awesome_rounded, color: BuddyColors.warning, size: 30))
            else
              const Positioned(right: 64, top: 10, child: ThinkingDots(size: 7)),
          ],
        );
      case 3:
        return Bounce(
          trigger: passed,
          child: Mascot(mood: passed ? MascotMood.happy : MascotMood.sleepy, size: 120),
        );
      default:
        return const Mascot(mood: MascotMood.excited, size: 124);
    }
  }
}

/// Soft rings that expand from the mascot, like sound waves arriving.
class _SoundRings extends StatefulWidget {
  const _SoundRings({required this.color});

  final Color color;

  @override
  State<_SoundRings> createState() => _SoundRingsState();
}

class _SoundRingsState extends State<_SoundRings> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 2400),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (context.noMotion) {
      if (_c.isAnimating) _c.stop();
      _c.value = 0.4;
    } else if (!_c.isAnimating) {
      _c.repeat();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: RepaintBoundary(
        child: CustomPaint(
          size: const Size(220, 150),
          painter: _RingsPainter(_c, widget.color),
        ),
      ),
    );
  }
}

class _RingsPainter extends CustomPainter {
  _RingsPainter(this.t, this.color) : super(repaint: t);

  final Animation<double> t;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final c = Offset(size.width * 0.12, size.height * 0.5);
    for (var i = 0; i < 3; i++) {
      final k = (t.value + i / 3) % 1.0;
      final r = 14 + k * 70;
      canvas.drawArc(
        Rect.fromCircle(center: c, radius: r),
        -math.pi / 3,
        math.pi * 2 / 3,
        false,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3
          ..strokeCap = StrokeCap.round
          ..color = color.withAlpha((170 * (1 - k)).round()),
      );
    }
  }

  @override
  bool shouldRepaint(_RingsPainter old) => old.t != t || old.color != color;
}

/// Floats up and down above a soft shadow that breathes with it.
class _FloatingPose extends StatefulWidget {
  const _FloatingPose({required this.child});

  final Widget child;

  @override
  State<_FloatingPose> createState() => _FloatingPoseState();
}

class _FloatingPoseState extends State<_FloatingPose> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 2200),
    value: 0.5,
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (context.noMotion) {
      if (_c.isAnimating) _c.stop();
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
    return AnimatedBuilder(
      animation: _c,
      child: widget.child,
      builder: (context, child) {
        final v = Curves.easeInOut.transform(_c.value);
        return Stack(
          alignment: Alignment.bottomCenter,
          children: [
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Container(
                width: 90 - 26 * v,
                height: 12 - 3 * v,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(40),
                  color: const Color(0xFF1B1530).withAlpha((60 - 28 * v).round()),
                ),
              ),
            ),
            Padding(
              padding: EdgeInsets.only(bottom: 14 + 20 * v),
              child: child,
            ),
          ],
        );
      },
    );
  }
}

class _Perm {
  const _Perm(this.key, this.icon, this.title, this.why, this.page);
  final String key;
  final IconData icon;
  final String title;
  final String why;

  /// Settings page to open, or null to ask a runtime permission group [key].
  final String? page;
}

/// Second list: optional abilities, each asks only its own permission.
class PermissionsListScreen extends StatefulWidget {
  const PermissionsListScreen({super.key});

  @override
  State<PermissionsListScreen> createState() => _PermissionsListScreenState();
}

class _PermissionsListScreenState extends State<PermissionsListScreen>
    with WidgetsBindingObserver {
 List<_Perm> get _items => [
    _Perm('accessibility', Icons.touch_app_rounded, tr('Управление экраном голосом', en: 'Control the screen by voice', uz: 'Ekranni ovoz bilan boshqarish'),
        tr('Нажимать кнопки и листать экран по команде', en: 'Tap buttons and scroll the screen on command', uz: 'Buyruq bo\'yicha tugmalarni bosish va ekranni aylantirish'), 'accessibility'),
    _Perm('phone', Icons.call_rounded, tr('Звонки голосом', en: 'Calls by voice', uz: 'Ovoz bilan qo\'ng\'iroq'),
        tr('Отвечать на звонки и звонить по просьбе', en: 'Answer calls and make calls on request', uz: 'Qo\'ng\'iroqlarga javob berish va so\'rovingiz bo\'yicha qo\'ng\'iroq qilish'), null),
    _Perm('contacts', Icons.contacts_rounded, tr('Контакты', en: 'Contacts', uz: 'Kontaktlar'),
        tr('Называть звонящего и набирать по имени', en: 'Name the caller and dial by name', uz: 'Qo\'ng\'iroq qilayotganni aytish va ism bo\'yicha terish'), null),
    _Perm('sms', Icons.sms_rounded, tr('Отправка SMS', en: 'Send SMS', uz: 'SMS yuborish'),
        tr('Отправлять сообщения по вашей просьбе', en: 'Send text messages on request', uz: 'So\'rovingiz bo\'yicha SMS yuborish'), null),
    _Perm('calendar', Icons.event_rounded, tr('Календарь', en: 'Calendar', uz: 'Kalendar'),
        tr('Читать и создавать события', en: 'Read and create events', uz: 'Tadbirlarni o\'qish va yaratish'), null),
    _Perm('notifications', Icons.notifications_rounded, tr('Напоминания', en: 'Reminders', uz: 'Eslatmalar'),
        tr('Показывать уведомления и напоминания', en: 'Show notifications and reminders', uz: 'Bildirishnoma va eslatmalarni ko\'rsatish'), null),
    _Perm('location', Icons.wb_sunny_rounded, tr('Погода в утренней сводке', en: 'Weather in the morning briefing', uz: 'Ertalabki xulosada ob-havo'),
        tr('Примерное местоположение нужно только для погоды', en: 'Approximate location is used only for the weather', uz: "Taxminiy joylashuv faqat ob-havo uchun kerak"), null),
    _Perm('exactAlarm', Icons.alarm_rounded, tr('Точные будильники', en: 'Exact alarms', uz: 'Aniq budilniklar'),
        tr('Будильники срабатывают вовремя', en: 'Alarms go off on time', uz: 'Budilniklar o\'z vaqtida chalinadi'), 'exactAlarm'),
    _Perm('notifListener', Icons.notifications_active_rounded, tr('Уведомления', en: 'Notifications', uz: 'Bildirishnomalar'),
        tr('Читать уведомления и отвечать на них', en: 'Read and answer notifications', uz: "Bildirishnomalarni o'qish va ularga javob berish"), 'notifListener'),
    _Perm('dnd', Icons.do_not_disturb_on_rounded, tr('Не беспокоить', en: 'Do not disturb', uz: 'Bezovta qilmang'),
        tr('Режим «Не беспокоить» и беззвучный режим голосом', en: 'Do-not-disturb and silent mode by voice', uz: "Bezovta qilmang va ovozsiz rejimni ovoz bilan boshqarish"), 'dnd'),
    _Perm('writeSettings', Icons.brightness_6_rounded, tr('Яркость экрана', en: 'Screen brightness', uz: 'Ekran yorqinligi'),
        tr('Яркость экрана голосом', en: 'Screen brightness by voice', uz: "Ekran yorqinligini ovoz bilan o'zgartirish"), 'writeSettings'),
  ];

  Map<String, bool> _status = const {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    try {
      final p = await Native.permissionsStatus();
      if (mounted) setState(() => _status = p);
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _grant(_Perm p) async {
    try {
      final page = p.page;
      if (page != null) {
        await Native.openSettings(page);
      } else {
        await Native.requestPermissions(group: p.key);
      }
      await _refresh();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Все разрешения', en: 'All permissions', uz: 'Barcha ruxsatlar'))),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Sp.l, Sp.s, Sp.l, Sp.xl),
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: Sp.m),
            child: Text(tr('Дополнительные возможности', en: 'Extra features', uz: 'Qo\'shimcha imkoniyatlar'), style: tt.titleMedium),
          ),
          for (var i = 0; i < _items.length; i++)
            FadeSlideIn(
              index: i,
              child: Padding(
                padding: const EdgeInsets.only(bottom: Sp.s),
                child: _permCard(_items[i], cs, tt),
              ),
            ),
          SoftCard(
            color: cs.secondaryContainer,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.info_outline_rounded, color: cs.onSecondaryContainer),
                const SizedBox(width: Sp.m),
                Expanded(
                  child: Text(
                    tr('Если кнопка серая: Настройки → Приложения → Bubby → ⋮ → Разрешить ограниченные настройки', en: 'If the button is grey: Settings → Apps → Bubby → ⋮ → Allow restricted settings', uz: 'Agar tugma kulrang bo\'lsa: Sozlamalar → Ilovalar → Bubby → ⋮ → Cheklangan sozlamalarga ruxsat berish'),
                    style: tt.bodyMedium?.copyWith(color: cs.onSecondaryContainer),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: Sp.s),
          BuddyButton.text(
            onPressed: () async {
              try {
                await Native.openSettings('app');
              } on NativeException catch (e) {
                if (context.mounted) showMessage(context, e);
              }
            },
            icon: const Icon(Icons.settings_applications_outlined),
            child: Text(tr('Открыть настройки приложения', en: 'Open app settings', uz: 'Ilova sozlamalarini ochish')),
          ),
        ],
      ),
    );
  }

  Widget _permCard(_Perm p, ColorScheme cs, TextTheme tt) {
    final ok = _status[p.key] == true;
    return SoftCard(
      padding: const EdgeInsets.all(Sp.m),
      child: Row(
        children: [
          AnimatedContainer(
            duration: Dur.med,
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: (ok ? BuddyColors.success : cs.primary).withAlpha(36),
            ),
            child: Icon(
              ok ? Icons.check_circle_rounded : p.icon,
              color: ok ? BuddyColors.success : cs.primary,
            ),
          ),
          const SizedBox(width: Sp.m),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(p.title, style: tt.titleSmall),
                const SizedBox(height: 2),
                Text(p.why, style: tt.bodySmall),
              ],
            ),
          ),
          if (!ok) ...[
            const SizedBox(width: Sp.s),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 112),
              child: BuddyButton.tonal(
                onPressed: () => _grant(p),
                style: FilledButton.styleFrom(minimumSize: const Size(64, 40)),
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Text(p.page == null ? tr('Разрешить', en: 'Allow', uz: 'Ruxsat berish') : tr('Открыть', en: 'Open', uz: 'Ochish')),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
