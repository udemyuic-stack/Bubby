import 'package:flutter/services.dart';

import 'l10n/l10n.dart';

class NativeException implements Exception {
  const NativeException(this.code, this.message);
  final String code;
  final String message;

  @override
  String toString() => message.isEmpty ? code : message;
}

class Settings {
  const Settings({
    this.key = '',
    this.keys = const [],
    this.lang = 'auto',
    this.tts = true,
    this.conv = false,
    this.study = false,
    this.voice = true,
    this.persona = '',
    this.running = false,
    this.provider = 'cloud',
    this.gemmaModel = 'e2b',
    this.outfit = '',
    this.species = 'cat',
    this.lastCrash = '',
    this.musicApp = '',
    this.hotword = true,
    this.stickerAuto = true,
    this.autostart = true,
    this.readNotifs = false,
    this.lastLang = '',
    this.musicMood = true,
    this.size = 1.0,
    this.quiet = false,
    this.autohide = true,
    this.messageDelay = 4,
    this.screenAgent = true,
    this.deviceReactions = true,
    this.stickersOnEvents = true,
  });

  final String key;
  final List<String> keys;
  final String lang;
  final bool tts;
  final bool conv;
  final bool study;
  final bool voice;
  final String persona;
  final bool running;
  final String provider;
  final String gemmaModel;
  final String outfit;
  final String species;
  final String lastCrash;
  final String musicApp;
  final bool hotword;
  final bool stickerAuto;
  final bool autostart;
  final bool readNotifs;
  final String lastLang;
  final bool musicMood;
  final double size;
  final bool quiet;
  final bool autohide;
  final int messageDelay;
  final bool screenAgent;
  final bool deviceReactions;
  final bool stickersOnEvents;

  /// True when at least one non-empty AI key is saved.
  bool get hasKeys => keys.any((k) => k.trim().isNotEmpty);

  factory Settings.fromMap(Map<dynamic, dynamic> m) => Settings(
        key: (m['key'] as String?) ?? '',
        keys: [
          for (final e in (m['keys'] as List<dynamic>?) ?? const [])
            if (e is String && e.trim().isNotEmpty) e.trim(),
        ],
        lang: (m['lang'] as String?) ?? 'auto',
        tts: (m['tts'] as bool?) ?? true,
        conv: (m['conv'] as bool?) ?? false,
        study: (m['study'] as bool?) ?? false,
        voice: (m['voice'] as bool?) ?? true,
        persona: (m['persona'] as String?) ?? '',
        running: (m['running'] as bool?) ?? false,
        provider: (m['provider'] as String?) ?? 'cloud',
        gemmaModel: (m['gemmaModel'] as String?) ?? 'e2b',
        outfit: (m['outfit'] as String?) ?? '',
        species: (m['species'] as String?) ?? 'cat',
        lastCrash: (m['lastCrash'] as String?) ?? '',
        musicApp: (m['musicApp'] as String?) ?? '',
        hotword: (m['hotword'] as bool?) ?? true,
        stickerAuto: (m['stickerAuto'] as bool?) ?? true,
        autostart: (m['autostart'] as bool?) ?? true,
        readNotifs: (m['readNotifs'] as bool?) ?? false,
        lastLang: (m['lastLang'] as String?) ?? '',
        musicMood: (m['musicMood'] as bool?) ?? true,
        size: ((m['size'] as num?)?.toDouble() ?? 1.0).clamp(0.15, 6.0),
        quiet: (m['quiet'] as bool?) ?? false,
        autohide: (m['autohide'] as bool?) ?? true,
        messageDelay: ((m['messageDelay'] as num?)?.toInt() ?? 4).clamp(0, 30),
        screenAgent: (m['screenAgent'] as bool?) ?? true,
        deviceReactions: (m['deviceReactions'] as bool?) ?? true,
        stickersOnEvents: (m['stickersOnEvents'] as bool?) ?? true,
      );

  Map<String, Object?> toMap() => {
        'key': key,
        'lang': lang,
        'tts': tts,
        'conv': conv,
        'study': study,
        'voice': voice,
        'persona': persona,
        'hotword': hotword,
      };
}

class MusicApp {
  const MusicApp({
    required this.id,
    required this.label,
    this.installed = false,
    this.kind = 'streaming',
  });

  final String id;
  final String label;
  final bool installed;

  /// 'streaming' | 'video' | 'local'
  final String kind;

  factory MusicApp.fromMap(Map<dynamic, dynamic> m) => MusicApp(
        id: (m['id'] as String?) ?? '',
        label: (m['label'] as String?) ?? '',
        installed: (m['installed'] as bool?) ?? false,
        kind: (m['kind'] as String?) ?? 'streaming',
      );
}

class ModelState {
  const ModelState({
    this.state = 'none',
    this.done = 0,
    this.total = 0,
    this.error,
  });

  final String state;
  final int done;
  final int total;
  final String? error;

  factory ModelState.fromMap(Map<dynamic, dynamic>? m) => ModelState(
        state: (m?['state'] as String?) ?? 'none',
        done: (m?['done'] as num?)?.toInt() ?? 0,
        total: (m?['total'] as num?)?.toInt() ?? 0,
        error: m?['error'] as String?,
      );
}

class ModelStatus {
  const ModelStatus({
    this.e2b = const ModelState(),
    this.e4b = const ModelState(),
    this.ramGb = 0,
    this.freeGb = 0,
  });

  final ModelState e2b;
  final ModelState e4b;
  final double ramGb;
  final double freeGb;

  ModelState operator [](String id) => id == 'e4b' ? e4b : e2b;

  factory ModelStatus.fromMap(Map<dynamic, dynamic> m) => ModelStatus(
        e2b: ModelState.fromMap(m['e2b'] as Map<dynamic, dynamic>?),
        e4b: ModelState.fromMap(m['e4b'] as Map<dynamic, dynamic>?),
        ramGb: (m['ramGb'] as num?)?.toDouble() ?? 0,
        freeGb: (m['freeGb'] as num?)?.toDouble() ?? 0,
      );
}

class Task {
  const Task({
    this.id = '',
    required this.title,
    this.notes = '',
    this.dueMillis,
    this.remind = true,
    this.done = false,
  });

  final String id;
  final String title;
  final String notes;
  final int? dueMillis;
  final bool remind;
  final bool done;

  factory Task.fromMap(Map<dynamic, dynamic> m) => Task(
        id: (m['id'] as String?) ?? '',
        title: (m['title'] as String?) ?? '',
        notes: (m['notes'] as String?) ?? '',
        dueMillis: (m['dueMillis'] as num?)?.toInt(),
        remind: (m['remind'] as bool?) ?? true,
        done: (m['done'] as bool?) ?? false,
      );

  Map<String, Object?> toMap() => {
        'id': id,
        'title': title,
        'notes': notes,
        'dueMillis': dueMillis,
        'remind': remind,
        'done': done,
      };

  DateTime? get due => dueMillis == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(dueMillis!);
}

class CalEvent {
  const CalEvent({
    required this.id,
    required this.title,
    required this.startMillis,
    required this.endMillis,
    this.location = '',
  });

  final int id;
  final String title;
  final int startMillis;
  final int endMillis;
  final String location;

  factory CalEvent.fromMap(Map<dynamic, dynamic> m) => CalEvent(
        id: (m['id'] as num?)?.toInt() ?? 0,
        title: (m['title'] as String?) ?? '',
        startMillis: (m['startMillis'] as num?)?.toInt() ?? 0,
        endMillis: (m['endMillis'] as num?)?.toInt() ?? 0,
        location: (m['location'] as String?) ?? '',
      );

  Map<String, Object?> toMap() => {
        'id': id,
        'title': title,
        'startMillis': startMillis,
        'endMillis': endMillis,
        'location': location,
      };

  DateTime get start => DateTime.fromMillisecondsSinceEpoch(startMillis);
  DateTime get end => DateTime.fromMillisecondsSinceEpoch(endMillis);
}

class AgentSession {
  const AgentSession({
    this.pillId = '',
    this.agent = '',
    this.state = 'idle',
    this.statusText = '',
    this.stepIndex = 0,
    this.stepCount = 0,
  });

  final String pillId;
  final String agent;
  final String state;
  final String statusText;
  final int stepIndex;
  final int stepCount;

  factory AgentSession.fromMap(Map<dynamic, dynamic> m) => AgentSession(
        pillId: (m['pillId'] as String?) ?? '',
        agent: (m['agent'] as String?) ?? '',
        state: (m['state'] as String?) ?? 'idle',
        statusText: (m['statusText'] as String?) ?? '',
        stepIndex: (m['stepIndex'] as num?)?.toInt() ?? 0,
        stepCount: (m['stepCount'] as num?)?.toInt() ?? 0,
      );
}

class AgentStatus {
  const AgentStatus({
    this.paired = false,
    this.desktop = '',
    this.connected = false,
    this.sessions = const [],
  });

  final bool paired;
  final String desktop;
  final bool connected;
  final List<AgentSession> sessions;

  factory AgentStatus.fromMap(Map<dynamic, dynamic> m) => AgentStatus(
        paired: (m['paired'] as bool?) ?? false,
        desktop: (m['desktop'] as String?) ?? '',
        connected: (m['connected'] as bool?) ?? false,
        sessions: [
          for (final e in (m['sessions'] as List<dynamic>?) ?? const [])
            AgentSession.fromMap(e as Map<dynamic, dynamic>),
        ],
      );
}

class ModelsResult {
  const ModelsResult(
      {this.ok = false, this.models = const [], this.message});
  final bool ok;
  final List<String> models;
  final String? message;
}

class AiTestResult {
  const AiTestResult({this.ok = false, this.message = ''});

  final bool ok;
  final String message;
}

class AgentPairResult {
  const AgentPairResult({this.ok = false, this.name, this.error});

  final bool ok;
  final String? name;
  final String? error;
}

class Native {
  Native._();

  static const MethodChannel _ch = MethodChannel('buddy/native');

  static Future<T?> _call<T>(String method, [Object? args]) async {
    try {
      return await _ch.invokeMethod<T>(method, args);
    } on PlatformException catch (e) {
      throw NativeException(e.code, e.message ?? e.code);
    } on MissingPluginException {
      throw NativeException('UNAVAILABLE', tr('Нативная часть недоступна', en: 'Native part is unavailable', uz: 'Native qism mavjud emas'));
    }
  }

  static Future<Settings> getSettings() async {
    final m = await _call<Map<dynamic, dynamic>>('getSettings');
    return Settings.fromMap(m ?? const {});
  }

  static Future<void> saveSettings(Map<String, Object?> values) =>
      _call<bool>('saveSettings', values);

  static Future<void> saveKeys(List<String> keys) =>
      _call<bool>('saveSettings', {'keys': keys});

  static Future<AiTestResult> testAi(String spec) async {
    final m = await _call<Map<dynamic, dynamic>>('testAi', {'spec': spec});
    return AiTestResult(
      ok: m?['ok'] == true,
      message: (m?['message'] as String?) ?? '',
    );
  }

  static Future<ModelsResult> listModels(String base, String key) async {
    final m = await _call<Map<dynamic, dynamic>>(
        'listModels', {'base': base, 'key': key});
    return ModelsResult(
      ok: m?['ok'] == true,
      models: [
        for (final e in (m?['models'] as List<dynamic>? ?? const []))
          e.toString()
      ],
      message: m?['message'] as String?,
    );
  }

  static Future<void> openUrl(String url) =>
      _call<bool>('openUrl', {'url': url});

  static Future<void> saveSize(double size) =>
      _call<bool>('saveSettings', {'size': size});

  static Future<Map<String, bool>> permissionsStatus() async {
    final m = await _call<Map<dynamic, dynamic>>('permissionsStatus');
    return {
      for (final e in (m ?? const {}).entries) e.key as String: e.value == true,
    };
  }

  /// [group]: 'runtime' (all), or one of mic|phone|contacts|calendar|notifications|location.
  static Future<void> requestPermissions({String group = 'runtime'}) =>
      _call<bool>('requestPermissions', {'group': group});

  /// Sends a text command to Bubby as if it was spoken.
  static Future<void> ask(String text) => _call<bool>('ask', {'text': text});

  static Future<void> openSettings(String page) =>
      _call<bool>('openSettings', {'page': page});

  static Future<void> startBuddy() => _call<bool>('startBuddy');

  static Future<void> stopBuddy() => _call<bool>('stopBuddy');

  static Future<List<Task>> listTasks() async {
    final l = await _call<List<dynamic>>('listTasks');
    return [
      for (final e in l ?? const []) Task.fromMap(e as Map<dynamic, dynamic>),
    ];
  }

  static Future<Task> saveTask(Task t) async {
    final m = await _call<Map<dynamic, dynamic>>('saveTask', t.toMap());
    return m == null ? t : Task.fromMap(m);
  }

  static Future<void> deleteTask(String id) =>
      _call<bool>('deleteTask', {'id': id});

  static Future<void> setTaskDone(String id, bool done) =>
      _call<bool>('setTaskDone', {'id': id, 'done': done});

  static Future<List<CalEvent>> listEvents(DateTime from, DateTime to) async {
    final l = await _call<List<dynamic>>('listEvents', {
      'from': from.millisecondsSinceEpoch,
      'to': to.millisecondsSinceEpoch,
    });
    return [
      for (final e in l ?? const []) CalEvent.fromMap(e as Map<dynamic, dynamic>),
    ];
  }

  static Future<int> addEvent({
    required String title,
    required DateTime start,
    required DateTime end,
    String location = '',
    String notes = '',
  }) async {
    final m = await _call<Map<dynamic, dynamic>>('addEvent', {
      'title': title,
      'startMillis': start.millisecondsSinceEpoch,
      'endMillis': end.millisecondsSinceEpoch,
      'location': location,
      'notes': notes,
    });
    return (m?['id'] as num?)?.toInt() ?? 0;
  }

  static Future<void> deleteEvent(int id) =>
      _call<bool>('deleteEvent', {'id': id});

  static Future<ModelStatus> modelStatus() async {
    final m = await _call<Map<dynamic, dynamic>>('modelStatus');
    return ModelStatus.fromMap(m ?? const {});
  }

  static Future<void> modelDownload(String id) =>
      _call<bool>('modelDownload', {'id': id});

  static Future<void> modelCancel() => _call<bool>('modelCancel');

  static Future<void> modelDelete(String id) =>
      _call<bool>('modelDelete', {'id': id});

  static Future<Uint8List> renderSticker(String mood, [String caption = '']) async {
    final b = await _call<Uint8List>(
      'renderSticker',
      {'mood': mood, 'caption': caption},
    );
    return b ?? Uint8List(0);
  }

  static Future<void> shareSticker(String mood, [String caption = '']) =>
      _call<bool>('shareSticker', {'mood': mood, 'caption': caption});

  static Future<void> startGame(String name) =>
      _call<bool>('startGame', {'name': name});

  /// where: top | bottom | left | right | center | bottom_left | bottom_right | top_left | top_right
  static Future<void> dockTo(String where) =>
      _call<bool>('dockTo', {'where': where});

  static Future<bool> batteryStatus() async {
    final m = await _call<Map<dynamic, dynamic>>('batteryStatus');
    return m?['ignoring'] == true;
  }

  static Future<AgentPairResult> agentPair(String link) async {
    final m = await _call<Map<dynamic, dynamic>>('agentPair', {'link': link});
    return AgentPairResult(
      ok: m?['ok'] == true,
      name: m?['name'] as String?,
      error: m?['error'] as String?,
    );
  }

  static Future<void> agentUnpair() => _call<bool>('agentUnpair');

  static Future<AgentStatus> agentStatus() async {
    final m = await _call<Map<dynamic, dynamic>>('agentStatus');
    return AgentStatus.fromMap(m ?? const {});
  }

  static Future<List<MusicApp>> musicApps() async {
    final l = await _call<List<dynamic>>('musicApps');
    return [
      for (final e in l ?? const []) MusicApp.fromMap(e as Map<dynamic, dynamic>),
    ];
  }

  static Future<void> testMusic(String q) => _call<bool>('testMusic', {'q': q});
}
