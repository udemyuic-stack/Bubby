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
import 'setup_screen.dart';

/// Blocking set (plus a non-empty AI key). Everything else is optional.
const List<String> requiredPermissions = ['mic', 'overlay'];

/// Extra features nudged softly, never blocking.
const List<String> extraPermissions = ['accessibility'];

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  bool _running = false;
  bool _micPoked = false;
  String _species = 'cat';
  bool _busy = false;
  bool _loaded = false;
  Map<String, bool> _perms = const {};
  bool _needAi = false;
  bool _hasAi = false;
  bool _battery = false;
  bool _onboarding = false;
  bool _onboardingDecided = false;
  int _party = 0;
  bool _helloCelebrated = false;

  bool get _missing =>
      !_loaded || !_hasAi || requiredPermissions.any((k) => _perms[k] != true);

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
      final s = await Native.getSettings();
      final p = await Native.permissionsStatus();
      final bat = await Native.batteryStatus();
      if (!mounted) return;
      // the microphone was granted after the floating service started: poke it so it re-declares the microphone type
      if (s.running && p['mic'] == true && !_micPoked && p['overlay'] == true) {
        _micPoked = true;
        try { await Native.startBuddy(); } on NativeException catch (_) {}
      }
      if (p['mic'] != true) _micPoked = false;
      setState(() {
        _battery = bat;
        _hasAi = s.hasKeys || s.provider == 'gemma';
        _running = s.running;
        _species = s.species;
        _needAi = s.keys.isEmpty && s.provider != 'gemma';
        _perms = p;
        _loaded = true;
        if (!_onboardingDecided) {
          _onboardingDecided = true;
          _onboarding = _missing;
        }
      });
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _toggle() async {
    if (_busy) return;
    if (!_running && _perms['overlay'] != true) {
      showMessage(context, tr('Сначала выдайте разрешение «Поверх других окон»', en: 'First grant the “Display over other apps” permission', uz: 'Avval «Boshqa oynalar ustida ko\'rsatish» ruxsatini bering'));
      await _openSetup();
      return;
    }
    setState(() => _busy = true);
    try {
      if (_running) {
        await Native.stopBuddy();
      } else {
        await Native.startBuddy();
      }
      await _refresh();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  List<String> get _hints => [
        tr('поставь будильник на 7', en: 'set an alarm for 7', uz: 'soat 7 ga budilnik qo\'y'),
        tr('включи музыку, мне грустно', en: 'play some music, I feel sad', uz: 'musiqa qo\'y, xafaman'),
        tr('ответь на звонок', en: 'answer the call', uz: 'qo\'ng\'iroqqa javob ber'),
        tr('напомни купить молоко в 18:00', en: 'remind me to buy milk at 18:00', uz: 'soat 18:00 da sut olishni eslat'),
        tr('будь весёлым', en: 'be cheerful', uz: 'quvnoq bo\'l'),
      ];

  Future<void> _sendHint(String text) async {
    try {
      await Native.ask(text);
      if (mounted) {
        showMessage(context, tr('Отправил Bubby: $text', en: 'Sent to Bubby: $text', uz: 'Bubby ga yuborildi: $text'));
        if (!_helloCelebrated) {
          _helloCelebrated = true; // first successful voice test
          HapticFeedback.lightImpact();
          setState(() => _party++);
        }
      }
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _checkHello() => _sendHint(tr('Привет!', en: 'Hello!', uz: 'Salom!'));

  Future<void> _fixAi() async {
    await Navigator.of(context)
        .push(BubbyRoute<void>(builder: (_) => const AiSetupScreen()));
    await _refresh();
  }

  Future<void> _fixMic() async {
    try {
      if (_perms['mic'] != true) await Native.requestPermissions(group: 'mic');
      await _refresh();
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  Future<void> _fixBattery() async {
    try {
      await Native.openSettings('battery');
    } on NativeException catch (e) {
      if (mounted) showMessage(context, e);
    }
  }

  /// level: 2 green, 1 amber, 0 red.
  Widget _healthPill(String label, int level, VoidCallback onTap) {
    final color = level == 2
        ? BuddyColors.success
        : level == 1
            ? Colors.amber.shade700
            : context.cs.error;
    return InkWell(
      onTap: onTap,
      borderRadius: Rad.pillRadius,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 44),
        child: Center(
          widthFactor: 1,
          child: StatusPill(label: label, color: color, dot: true),
        ),
      ),
    );
  }

  Future<void> _openSetup() async {
    await Navigator.of(context).push(
      BubbyRoute<void>(builder: (_) => const SetupScreen()),
    );
    await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    final tt = context.tt;
    final dark = context.isDark;
    final top = MediaQuery.paddingOf(context).top;
    final granted = requiredPermissions.where((k) => _perms[k] == true).length +
        (_hasAi ? 1 : 0);
    final total = requiredPermissions.length + 1;
    if (_loaded && _onboarding) {
      return SafeArea(
        child: OnboardingStepper(
          onFinished: () {
            HapticFeedback.lightImpact();
            setState(() {
              _onboarding = false;
              _party++;
            });
            _refresh();
          },
        ),
      );
    }
    return ListView(
      padding: const EdgeInsets.only(bottom: Sp.xl),
      children: [
        _HeroBackground(
          running: _running,
          dark: dark,
          child: Stack(
            children: [
              Positioned.fill(child: SparkleField(active: _running)),
              if (_party > 0)
                Positioned.fill(
                  child: Confetti(
                    key: ValueKey(_party),
                    origin: const Alignment(0, -0.1),
                  ),
                ),
              Padding(
                padding: EdgeInsets.only(top: top + Sp.xl, bottom: Sp.xl),
                child: SizedBox(
                  width: double.infinity,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      KeyedSubtree(
                        key: kHeroMascotKey,
                        child: InteractiveMascot(
                          mood: _running ? MascotMood.happy : MascotMood.sleepy,
                          size: 190,
                          species: _species,
                        ),
                      ),
                      const SizedBox(height: Sp.m),
                      Text('Bubby', style: tt.headlineMedium),
                      const SizedBox(height: Sp.s),
                      StatusPill(
                        label: _running
                            ? tr('Помощник работает', en: 'Assistant is on', uz: 'Yordamchi ishlayapti')
                            : tr('Помощник выключен', en: 'Assistant is off', uz: 'Yordamchi o\'chiq'),
                        color: _running ? BuddyColors.success : cs.outline,
                        dot: true,
                        pulse: _running,
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: Sp.xl),
        FadeSlideIn(
          index: 1,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: _StartButton(
              running: _running,
              busy: _busy,
              enabled: _loaded && !_busy,
              onTap: _toggle,
            ),
          ),
        ),
        if (_loaded && _needAi) ...[
          const SizedBox(height: Sp.l),
          FadeSlideIn(
            index: 2,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: Sp.l),
              child: SoftCard(
                color: cs.primaryContainer,
                borderColor: cs.primary.withAlpha(110),
                onTap: () => Navigator.of(context)
                    .push(BubbyRoute<void>(builder: (_) => const AiSetupScreen()))
                    .then((_) => _refresh()),
                child: Row(
                  children: [
                    Icon(Icons.auto_awesome_rounded,
                        color: cs.onPrimaryContainer, size: 32),
                    const SizedBox(width: Sp.l),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(tr('Подключите ИИ за 2 минуты — бесплатно', en: 'Connect AI in 2 minutes — for free', uz: 'AI ni 2 daqiqada ulang — bepul'),
                              style: tt.titleMedium
                                  ?.copyWith(color: cs.onPrimaryContainer)),
                          const SizedBox(height: 2),
                          Text(tr('Нужен свой бесплатный ключ Groq. Покажем по шагам', en: 'You need your own free Groq key. We will guide you step by step', uz: 'O\'zingizning bepul Groq kalitingiz kerak. Qadamma-qadam ko\'rsatamiz'),
                              style: tt.bodyMedium
                                  ?.copyWith(color: cs.onPrimaryContainer)),
                        ],
                      ),
                    ),
                    Icon(Icons.chevron_right_rounded,
                        color: cs.onPrimaryContainer),
                  ],
                ),
              ),
            ),
          ),
        ],
        if (_loaded && _missing) ...[
          const SizedBox(height: Sp.l),
          FadeSlideIn(
            index: 2,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: Sp.l),
              child: SoftCard(
                color: cs.tertiaryContainer,
                borderColor: cs.tertiary.withAlpha(90),
                onTap: _openSetup,
                child: Row(
                  children: [
                    ProgressRing(
                      value: granted / total,
                      color: cs.tertiary,
                      child: Text(
                        '$granted/$total',
                        style: tt.labelMedium?.copyWith(
                          color: cs.onTertiaryContainer,
                        ),
                      ),
                    ),
                    const SizedBox(width: Sp.l),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            tr('Настройка', en: 'Setup', uz: 'Sozlash'),
                            style: tt.titleMedium?.copyWith(
                              color: cs.onTertiaryContainer,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            tr('Нужны разрешения, чтобы помощник работал', en: 'Permissions are needed for the assistant to work', uz: 'Yordamchi ishlashi uchun ruxsatlar kerak'),
                            style: tt.bodyMedium?.copyWith(
                              color: cs.onTertiaryContainer,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Icon(Icons.chevron_right_rounded,
                        color: cs.onTertiaryContainer),
                  ],
                ),
              ),
            ),
          ),
        ],
        if (_loaded && !_missing && extraPermissions.any((k) => _perms[k] != true)) ...[
          const SizedBox(height: Sp.l),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Sp.l),
            child: SoftCard(
              onTap: _openSetup,
              child: Row(
                children: [
                  Icon(Icons.touch_app_rounded, color: cs.primary),
                  const SizedBox(width: Sp.m),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(tr('Управление экраном голосом', en: 'Control the screen by voice', uz: 'Ekranni ovoz bilan boshqarish'),
                            style: tt.titleSmall),
                        Text(tr('Включите, чтобы Bubby нажимал кнопки за вас', en: 'Turn on so Bubby can tap buttons for you', uz: 'Bubby sizning o\'rningizga tugmalarni bosishi uchun yoqing'),
                            style: tt.bodySmall),
                      ],
                    ),
                  ),
                  Icon(Icons.chevron_right_rounded, color: cs.onSurfaceVariant),
                ],
              ),
            ),
          ),
        ],
        if (_loaded) ...[
          const SizedBox(height: Sp.xl),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Sp.l),
            child: SectionTitle(tr('Состояние', en: 'Status', uz: 'Holat'), icon: Icons.favorite_outline_rounded),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Sp.l),
            child: Wrap(
              spacing: Sp.s,
              runSpacing: Sp.s,
              children: [
                _healthPill(tr('ИИ', en: 'AI', uz: 'AI'), _hasAi ? 2 : 0, _fixAi),
                _healthPill(tr('Микрофон', en: 'Microphone', uz: 'Mikrofon'), _perms['mic'] == true ? 2 : 0, _fixMic),
                _healthPill(tr('Не засыпать', en: 'Stay awake', uz: 'Uxlamaslik'), _battery ? 2 : 1, _fixBattery),
              ],
            ),
          ),
          const SizedBox(height: Sp.m),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Sp.l),
            child: BuddyButton.tonal(
              onPressed: _checkHello,
              icon: const Icon(Icons.waving_hand_rounded),
              child: Text(tr('Проверить: скажите привет', en: 'Test: say hello', uz: 'Tekshirish: salom deng')),
            ),
          ),
        ],
        const SizedBox(height: Sp.xl),
        FadeSlideIn(
          index: 3,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: Sp.l),
            child: SectionTitle(tr('Что можно сказать', en: 'What you can say', uz: 'Nima deyish mumkin'), icon: Icons.mic_none_rounded),
          ),
        ),
        FadeSlideIn(
          index: 4,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: Sp.l),
            child: Wrap(
              spacing: Sp.s,
              runSpacing: Sp.s,
              children: [
                for (final h in _hints) _HintChip(h, () => _sendHint(h)),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _StartButton extends StatefulWidget {
  const _StartButton({
    required this.running,
    required this.busy,
    required this.enabled,
    required this.onTap,
  });

  final bool running;
  final bool busy;
  final bool enabled;
  final VoidCallback onTap;

  @override
  State<_StartButton> createState() => _StartButtonState();
}

class _StartButtonState extends State<_StartButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1300),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(_StartButton old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    if (widget.running && !context.noMotion) {
      if (!_c.isAnimating) _c.repeat(reverse: true);
    } else {
      if (_c.isAnimating) _c.stop();
      _c.value = 0;
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<DateTime>(
      valueListenable: hourNow,
      builder: (context, now, _) => _buildButton(context, now),
    );
  }

  Widget _buildButton(BuildContext context, DateTime now) {
    final running = widget.running;
    final fg = running ? Colors.white : BuddyColors.ink;
    final glow = running ? BuddyColors.violet : hourAccent(now);
    return Semantics(
      button: true,
      enabled: widget.enabled,
      label: running ? tr('Остановить', en: 'Stop', uz: 'To\'xtatish') : tr('Запустить', en: 'Start', uz: 'Ishga tushirish'),
      child: PressScale(
        enabled: widget.enabled,
        onTap: widget.onTap,
        haptic: BuddyHaptic.light,
        shimmer: false,
        ring: true,
        ringColor: glow,
        child: AnimatedOpacity(
          duration: Dur.med,
          opacity: widget.enabled || widget.busy ? 1 : 0.5,
          child: AnimatedBuilder(
            animation: _c,
            builder: (context, child) {
              final v = Curves.easeInOut.transform(_c.value);
              return Container(
                height: 68,
                decoration: BoxDecoration(
                  borderRadius: Rad.pillRadius,
                  gradient: running
                      ? BuddyGradients.violet
                      : hourGradient(now),
                  boxShadow: [
                    BoxShadow(
                      color: glow.withAlpha(running ? 90 + (40 * v).round() : 80),
                      blurRadius: running ? 18 + 8 * v : 16,
                      spreadRadius: running ? 2 + 2 * v : 0,
                      offset: Offset(0, running ? 4 : 8),
                    ),
                  ],
                ),
                child: child,
              );
            },
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (widget.busy)
                  SizedBox(
                    width: 34,
                    height: 34,
                    child: Center(
                      child: ThinkingDots(color: fg, size: 6),
                    ),
                  )
                else
                  _MorphIcon(running: running, color: fg),
                const SizedBox(width: 10),
                Flexible(
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text(
                      running ? tr('Остановить', en: 'Stop', uz: 'To\'xtatish') : tr('Запустить', en: 'Start', uz: 'Ishga tushirish'),
                      maxLines: 1,
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                        color: fg,
                      ),
                    ),
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

/// Play / stop icon that morphs with a rotate + scale transition.
class _MorphIcon extends StatelessWidget {
  const _MorphIcon({required this.running, required this.color});

  final bool running;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 34,
      height: 34,
      child: AnimatedSwitcher(
        duration: context.noMotion ? Duration.zero : Dur.med,
        switchInCurve: Curves.easeOutBack,
        switchOutCurve: Curves.easeIn,
        transitionBuilder: (child, a) => RotationTransition(
          turns: Tween<double>(begin: -0.25, end: 0).animate(a),
          child: ScaleTransition(scale: a, child: child),
        ),
        child: Icon(
          running ? Icons.stop_rounded : Icons.play_arrow_rounded,
          key: ValueKey<bool>(running),
          size: 34,
          color: color,
        ),
      ),
    );
  }
}

class _HintChip extends StatelessWidget {
  const _HintChip(this.text, this.onTap);

  final String text;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.cs;
    return Material(
      color: cs.secondaryContainer,
      borderRadius: Rad.pillRadius,
      child: InkWell(
        onTap: onTap,
        borderRadius: Rad.pillRadius,
        child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.mic_none_rounded, size: 18, color: cs.secondary),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              '«$text»',
              style: context.tt.bodyMedium?.copyWith(
                color: cs.onSecondaryContainer,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    ),
      ),
    );
  }
}

/// Hero backdrop: hour-tinted radial gradient with slowly drifting highlights
/// and twinkling glints. Static when reduced motion is requested.
class _HeroBackground extends StatefulWidget {
  const _HeroBackground({
    required this.running,
    required this.dark,
    required this.child,
  });

  final bool running;
  final bool dark;
  final Widget child;

  @override
  State<_HeroBackground> createState() => _HeroBackgroundState();
}

class _HeroBackgroundState extends State<_HeroBackground>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 18),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (context.noMotion) {
      if (_c.isAnimating) _c.stop();
      _c.value = 0.3;
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
    final dark = widget.dark;
    return ValueListenableBuilder<DateTime>(
      valueListenable: hourNow,
      builder: (context, now, _) {
        final accent = hourAccent(now);
        final inner = dark
            ? Color.lerp(const Color(0xFF4B2F6B), hourAccentDeep(now), 0.55)!
            : Color.lerp(const Color(0xFFFFD8AE), hourAccentLight(now), 0.7)!;
        final outer = dark
            ? Color.lerp(const Color(0xFF1E1736), hourAccentDeep(now), 0.15)!
            : Color.lerp(const Color(0xFFFFF1E2), hourAccentLight(now), 0.18)!;
        return Container(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            borderRadius: const BorderRadius.vertical(
              bottom: Radius.circular(40),
            ),
            gradient: RadialGradient(
              center: const Alignment(0, -0.2),
              radius: 0.95,
              colors: [inner, outer],
            ),
          ),
          child: Stack(
            children: [
              Positioned.fill(
                child: RepaintBoundary(
                  child: CustomPaint(
                    painter: _ShimmerPainter(
                      t: _c,
                      color: hueShift(accent, 25),
                      dark: dark,
                      boost: widget.running ? 1.25 : 1,
                    ),
                  ),
                ),
              ),
              widget.child,
            ],
          ),
        );
      },
    );
  }
}

class _ShimmerPainter extends CustomPainter {
  _ShimmerPainter({
    required this.t,
    required this.color,
    required this.dark,
    required this.boost,
  }) : super(repaint: t);

  final Animation<double> t;
  final Color color;
  final bool dark;
  final double boost;

  @override
  void paint(Canvas canvas, Size size) {
    final v = t.value * math.pi * 2;
    final base = (dark ? 70 : 90) * boost;
    for (var i = 0; i < 3; i++) {
      final ph = i * 2.1;
      final c = Offset(
        size.width * (0.5 + 0.38 * math.sin(v + ph)),
        size.height * (0.45 + 0.3 * math.cos(v * (i == 1 ? 2 : 1) + ph * 1.3)),
      );
      final r = size.shortestSide * (0.55 + 0.12 * math.sin(v * 2 + ph));
      final paint = Paint()
        ..shader = RadialGradient(
          colors: [
            color.withAlpha(base.round().clamp(0, 255)),
            color.withAlpha(0),
          ],
        ).createShader(Rect.fromCircle(center: c, radius: r));
      canvas.drawCircle(c, r, paint);
    }
    // Glints: small four-point stars twinkling out of phase.
    final glint = Paint()..style = PaintingStyle.fill;
    for (var i = 0; i < 7; i++) {
      final x = size.width * ((i * 0.37 + 0.11) % 1);
      final y = size.height * ((i * 0.53 + 0.17) % 0.9 + 0.05);
      final tw = math.max(0.0, math.sin(v * 2 + i * 1.7));
      if (tw < 0.05) continue;
      final r = (3 + 4 * (i % 3)) * tw;
      glint.color = Colors.white.withAlpha((tw * (dark ? 190 : 230)).round());
      final p = Path()
        ..moveTo(x, y - r)
        ..quadraticBezierTo(x, y, x + r, y)
        ..quadraticBezierTo(x, y, x, y + r)
        ..quadraticBezierTo(x, y, x - r, y)
        ..quadraticBezierTo(x, y, x, y - r);
      canvas.drawPath(p, glint);
    }
  }

  @override
  bool shouldRepaint(_ShimmerPainter old) =>
      old.color != color || old.dark != dark || old.boost != boost || old.t != t;
}
