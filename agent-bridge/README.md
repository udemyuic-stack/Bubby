# agent-bridge

## Quick start (English)

```sh
cd agent-bridge
node bridge.mjs            # Node >= 18 and openssl in PATH; no npm install
```

On the first run it creates a certificate, picks your LAN address, offers to install the Claude Code hooks and the
statusLine relay (shows the diff and asks `y/N`; `--yes` installs without asking, `--no-hooks` / `--no-statusline`
skip), advertises itself over mDNS and prints:

    Open Bubby on your phone → Agents → tap this computer → type 123456

The code works once and for 5 minutes; a wrong code burns it and a new one is printed. If the computer does not show up
(some Wi-Fi networks block multicast), type the printed `host:port` in the app. `--link` also prints the old
`coucou://pair?...` link. Another phone later: `node bridge.mjs --new-pairing`. Gemini CLI or Copilot CLI too:
`node bridge.mjs --install-agent gemini` (see `docs/AGENTS-OTHER.md`). Tests: `node --test`.
Protocol: v1 link + `docs/AGENTS-V2.md` + `docs/AGENTS-V3.md`. Protocol ideas from Coucou (MIT,
https://github.com/louis-cfm/coucou); the code is Bubby's own.

---

Мост между Claude Code (и другими агентами) на компьютере и приложением Bubby на телефоне: протокол v1 (TLS с закреплённым сертификатом, JSON по строкам), дополнения v2 из `docs/AGENTS-V2.md` (mDNS, сопряжение по коду, `usage`) и v3 из `docs/AGENTS-V3.md` (лимиты плана из statusLine, ответы на вопросы Claude, подробности сессий, изменённые файлы, другие агенты). Запросы разрешений приходят на телефон, ответ Allow/Deny возвращается в Claude. Node >= 18, нужен системный `openssl`, npm-зависимостей нет. Идеи протокола взяты из Coucou (MIT); ядро v1 было адаптировано из его `android/tools/dev-desktop.mjs` (MIT).

## Запуск

```sh
node bridge.mjs                       # всё сразу: сертификат, адрес, хуки и statusLine (с вопросом), mDNS, код
node bridge.mjs --yes                 # поставить хуки и statusLine без вопроса
node bridge.mjs --no-hooks            # не ставить хуки Claude Code
node bridge.mjs --no-statusline       # не ставить statusLine (лимитов плана на телефоне не будет)
node bridge.mjs --install-agent gemini   # ещё и хуки Gemini CLI (или copilot), с diff и вопросом
node bridge.mjs --no-details --no-answers --no-diffs   # не предлагать телефону эти возможности
node bridge.mjs --link                # дополнительно напечатать старую ссылку coucou://pair?...
node bridge.mjs --host 192.168.1.20 --port 47821 --name "Мой Mac"
```

По умолчанию bridge слушает первый частный IPv4 (192.168.x, 10.x, 172.16-31.x), порт 47821. В терминале появится 6-значный код и строка `Open Bubby on your phone → Agents → tap this computer → type КОД`. В приложении откройте «Агенты», выберите компьютер в списке и введите код. Если компьютера нет в списке (сеть блокирует multicast), введите в приложении напечатанный `адрес:порт`. Ссылку `--link` с токеном никому не показывайте.

Файлы лежат в `~/.bubby/`: `cert.pem` и `key.pem` (создаются один раз, ключ 0600), `pairing.json` (токен и отметка о сопряжении), `stats.json` (счётчики за день), `limits.json` (последние лимиты плана), `statusline-previous.json` (ваша прежняя строка состояния), `hook.sock` (сокет для хуков); всё 0600. Переменная `BUBBY_DIR` меняет папку.

- `--new-pairing` выпускает новый токен и новый код (старый телефон перестаёт подходить). Второй телефон сопрягается только так.
- `--revoke` удаляет токен. Работающий bridge сразу отказывает любому `hello`.
- `--no-mdns` не объявлять себя в сети.
- `--no-transcripts` не следить за сессиями Claude Code по их файлам (только хуки). По умолчанию мост видит и сессии, начатые до установки хуков.
- Одновременно подключён один телефон; второй получает `error busy`.

## Обнаружение и сопряжение (v2)

- mDNS: сервис `_bubby-agent._tcp.local.`, записи PTR, SRV, TXT (`v=1`, `name=`, `fp=` SHA-256 сертификата, `paired=0|1`) и A. Свой маленький ответчик на `dgram` (224.0.0.251:5353, `reuseAddr`), объявление при старте и при смене `paired`, ответы на запросы, «goodbye» (TTL 0) при выходе. Если multicast недоступен, bridge пишет одну строку и работает дальше.
- Код: 6 случайных цифр, одна попытка, 5 минут. Телефон первым сообщением шлёт `{"type":"pair","v":1,"nonce","proof","name","caps"}`, где `proof = HMAC-SHA256(ключ = код, "bubby-pair-v1|" + отпечаток_увиденного_сертификата + "|" + nonce)`. Bridge считает то же со своим отпечатком и сравнивает через `timingSafeEqual`, поэтому посредник с другим сертификатом получает `bad_code`. Успех: `{"type":"paired","token","name","caps"}`, дальше соединение обычное, как после `welcome`.
- Неверный код сжигается, печатается новый. Не больше 5 неудачных попыток в минуту на все соединения, дальше `slow_down`. Код не пишется в лог. После сопряжения код больше не предлагается (`not_pairing`), пока не запущен `--new-pairing`. Старый `hello` с токеном работает как раньше.

## Возможности v3 (`caps`)

`hello` (и `pair`) может нести `"caps":["details","answers","diffs"]`; `welcome` (и `paired`) отвечает теми, что bridge предлагает этому телефону (всегда есть, `[]` если ничего). Всё включено по умолчанию, выключается флагами `--no-details`, `--no-answers`, `--no-diffs`. Телефон без `caps` получает ровно v1.

- `details`: у сессии появляются `project` (только имя папки, до 64 символов), `steps` (последние 20 шагов, до 200 символов: команда Bash, имя файла для файловых инструментов, иначе имя инструмента) и `finalLine` (первый абзац последнего ответа одной строкой, до 200 символов). Строка `sessions` держится меньше 56 КиБ: сначала выбрасываются старые шаги.
- `answers`: вопрос Claude Code (`AskUserQuestion`) уходит на телефон как `{"type":"question","pillId","fingerprint","createdAt","questions":[{question,multiSelect,options:[{label,description}]}]}`. Не больше 4 вопросов по 8 вариантов, текст до 500, метка до 120, описание до 300, без повторов; иначе вопрос остаётся в терминале. Ответ `{"type":"answer","fingerprint","picks":[[метки], ...]}` принимается, только если всё совпадает точно и вопросу меньше 115 с; Claude получает тот же ввод плюс `answers` (`{текст вопроса: метка}` или список меток для множественного выбора) через документированный ответ хука `PermissionRequest` с `updatedInput`. `hook.mjs` ещё раз проверяет ответ против настоящего ввода Claude. Что выбрано, не пишется в лог.
- `diffs`: у сессии появляются `files` (до 20, новые первыми): `{id, name, added, removed}` и `tooLarge`/`isNew`. Строки по запросу `{"type":"getDiff","pillId","fileId"}`: ответ `diff` частями по 100 строк (не больше 200 строк, строка до 400 символов, `truncated` если было больше, `gone` если файла уже нет). Не больше 20 запросов за 10 с. Строки считаются в памяти из ввода Edit/MultiEdit/Write и забываются с концом сессии.
- Другие агенты: `hook.mjs --agent <имя>` или поле `bubby_agent` (`^[a-z0-9-]{1,24}$`) дают агенту свою «пилюлю» (`agent_<имя>`). Для них bridge только показывает сессии, разрешения и вопросы не передаются. Установщики есть для Gemini CLI и GitHub Copilot CLI, остальное в `docs/AGENTS-OTHER.md`.

## Статистика (`usage`)

После `welcome`/`paired`, при изменениях (не чаще раза в 5 с) и раз в 60 с телефон получает `{"type":"usage","fiveHour":{"used","resetsAt"},"sevenDay":{...},"doneToday","tokensToday","plan","updatedAt"}`. Любого поля может не быть.

- `fiveHour`/`sevenDay`: из строки состояния Claude Code. Bridge ставит в `~/.claude/settings.json` свой `statusLine` (`node hook.mjs --statusline`): тот передаёт в bridge только объект `rate_limits`, затем запускает вашу прежнюю строку состояния (сохранена в `statusline-previous.json`) с тем же вводом и печатает её вывод без изменений; если её не было, ничего не печатает. Прежняя строка получает 5 с, потом снимается. `used` = процент/100 (0..1), `resetsAt` в мс; время сброса больше чем на 400 дней вперёд отбрасывается, прошедший сброс означает 0. Последние значения хранятся с `updatedAt` до новых. Токены и учётные данные Claude bridge не читает вовсе.
- `doneToday`: число завершённых ходов Claude Code (хук `Stop`) за местные сутки, хранится в `stats.json`.
- `tokensToday`: input + output + cache creation (без чтения из кэша) сообщений ассистента за сегодня из транскрипта, путь к которому приходит в хуке `Stop`. Читается только новая часть файла. Путь и содержимое транскрипта на телефон не уходят.

## Хуки и statusLine

`node bridge.mjs` ставит их сам (с вопросом). Отдельно:

```sh
node install-hooks.mjs                  # только показывает diff для ~/.claude/settings.json (хуки и statusLine)
node install-hooks.mjs --yes            # применяет: сначала копия settings.json.bak-ГГГГММДД-ЧЧММСС
node install-hooks.mjs --yes --no-statusline   # только хуки
node install-hooks.mjs --uninstall --yes       # убирает наше и возвращает прежний statusLine
node install-hooks.mjs --agent gemini --yes    # Gemini CLI (~/.gemini/settings.json); copilot: ~/.copilot/hooks/bubby.json
node install-hooks.mjs --agent gemini --uninstall --yes
```

Скрипт добавляет только записи, команда которых указывает на наш `hook.mjs` (события SessionStart, UserPromptSubmit, PreToolUse, PostToolUse, PermissionRequest, Notification, Stop, StopFailure, SessionEnd), чужие хуки не трогает. У `statusLine` меняется только `command` (`padding` и прочее остаются); `statusLine` не в виде объекта не трогается. Если JSON повреждён, файл не меняется. Логика общая (`lib/hooks.mjs`, `lib/agents.mjs`) для `install-hooks.mjs` и `bridge.mjs`.

## Поведение и безопасность

- TLS-сервер привязан только к выбранному LAN-адресу. Токен: 32 случайных символа, сравнение через `timingSafeEqual`. `hello` или `pair` ждём 10 с, строка больше 64 КиБ закрывает соединение, неизвестные сообщения игнорируются.
- Код сопряжения: одноразовый, 5 минут, сжигается при неудаче, не больше 5 неудач в минуту, сравнение HMAC через `timingSafeEqual`, привязан к отпечатку сертификата (защита от посредника), в лог не пишется.
- `hook.mjs` никогда не блокирует агента. Если bridge не запущен, телефона нет или решение не пришло за 110 с, он завершается с кодом 0 без вывода (Gemini и Copilot получают `{}`), и агент спрашивает как обычно. Bridge сам ничего не разрешает и ни на что не отвечает.
- Телефону уходит команда до 500 символов; длиннее обрезается, добавляется `truncated: true`, а `allow` для такого запроса игнорируется (работает только `deny`). Для файловых инструментов отправляется только имя файла. Промпты, вывод команд, полный ответ, содержимое файлов (кроме строк diff по запросу) и полные пути не отправляются; токен, команды и ответы на вопросы не пишутся в лог.
- Отпечаток запроса: SHA-256 (hex) от `pillId, sessionId, tool, command, inputKey`, склеенных через U+001F; `inputKey` случайный на каждый запрос (у вопроса вместо команды тексты вопросов).
- Решение или ответ принимается, только если отпечаток совпадает с ещё ожидающим запросом. Через 115 с запрос снимается.

## Тесты

```sh
node --test
```
Фальшивый телефон (`lib/fake-phone.mjs`) делает TLS-рукопожатие с проверкой отпечатка, hello/welcome или сопряжение по коду, шлёт `caps`, получает сессии, `usage`, approval и question, отвечает `decision` и `answer`, запрашивает `getDiff` и собирает части. Тесты покрывают сопряжение и все отказы, посредника, ограничение попыток, mDNS, статистику, statusLine (что уходит только `rate_limits`, прежняя строка работает, зависшая снимается), вопросы (точное совпадение, лимиты, таймаут), подробности и бюджет 56 КиБ, diff (части, лимиты, `gone`, частота), других агентов и установщики (statusLine, Gemini, Copilot).
