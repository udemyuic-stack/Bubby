# agent-bridge

## Quick start (English)

```sh
cd agent-bridge
node bridge.mjs            # Node >= 18 and openssl in PATH; no npm install
```

On the first run it creates a certificate, picks your LAN address, offers to install the Claude Code hooks (shows the
diff and asks `y/N`; `--yes` installs without asking, `--no-hooks` skips), advertises itself over mDNS and prints:

    Open Bubby on your phone → Agents → tap this computer → type 123456

The code works once and for 5 minutes; a wrong code burns it and a new one is printed. If the computer does not show up
(some Wi-Fi networks block multicast), type the printed `host:port` in the app. `--link` also prints the old
`coucou://pair?...` link. Another phone later: `node bridge.mjs --new-pairing`. Tests: `node --test`.
Protocol: `docs/AGENTS-V2.md` (v2 additions) on top of the v1 link.

---

Мост между Claude Code на компьютере и приложением Bubby на телефоне (протокол v1, совместимый с coucou: TLS с закреплённым сертификатом, JSON по строкам, плюс дополнения v2 из `docs/AGENTS-V2.md`: обнаружение по mDNS, сопряжение по коду, сообщение `usage`). Запросы разрешений Claude Code приходят на телефон, ответ Allow/Deny возвращается в Claude. Node >= 18, нужен системный `openssl`, npm-зависимостей нет. Части кода адаптированы из coucou (MIT).

## Запуск

```sh
node bridge.mjs                  # всё сразу: сертификат, адрес, хуки (с вопросом), mDNS, код
node bridge.mjs --yes            # поставить хуки без вопроса
node bridge.mjs --no-hooks       # не трогать ~/.claude/settings.json
node bridge.mjs --link           # дополнительно напечатать старую ссылку coucou://pair?...
node bridge.mjs --host 192.168.1.20 --port 47821 --name "Мой Mac"
```

По умолчанию bridge слушает первый частный IPv4 (192.168.x, 10.x, 172.16-31.x), порт 47821. В терминале появится 6-значный код и строка `Open Bubby on your phone → Agents → tap this computer → type КОД`. В приложении откройте «Агенты», выберите компьютер в списке и введите код. Если компьютера нет в списке (сеть блокирует multicast), введите в приложении напечатанный `адрес:порт`. Ссылку `--link` с токеном никому не показывайте.

Файлы лежат в `~/.bubby/`: `cert.pem` и `key.pem` (создаются один раз, ключ с правами 0600), `pairing.json` (токен и отметка о сопряжении, 0600), `stats.json` (счётчики за день, 0600), `hook.sock` (сокет для хуков, 0600). Переменная `BUBBY_DIR` меняет папку.

- `--new-pairing` выпускает новый токен и новый код (старый телефон перестаёт подходить). Второй телефон сопрягается только так.
- `--revoke` удаляет токен. Работающий bridge сразу отказывает любому `hello`.
- `--no-mdns` не объявлять себя в сети.
- Одновременно подключён один телефон; второй получает `error busy`.

## Обнаружение и сопряжение (v2)

- mDNS: сервис `_bubby-agent._tcp.local.`, записи PTR, SRV, TXT (`v=1`, `name=`, `fp=` SHA-256 сертификата, `paired=0|1`) и A. Свой маленький ответчик на `dgram` (224.0.0.251:5353, `reuseAddr`), объявление при старте и при смене `paired`, ответы на запросы, «goodbye» (TTL 0) при выходе. Если multicast недоступен, bridge пишет одну строку и работает дальше.
- Код: 6 случайных цифр, одна попытка, 5 минут. Телефон первым сообщением шлёт `{"type":"pair","v":1,"nonce","proof","name"}`, где `proof = HMAC-SHA256(ключ = код, "bubby-pair-v1|" + отпечаток_увиденного_сертификата + "|" + nonce)`. Bridge считает то же со своим отпечатком и сравнивает через `timingSafeEqual`, поэтому посредник с другим сертификатом получает `bad_code`. Успех: `{"type":"paired","token","name"}`, дальше соединение обычное, как после `welcome`.
- Неверный код сжигается, печатается новый. Не больше 5 неудачных попыток в минуту на все соединения, дальше `slow_down`. Код не пишется в лог. После сопряжения код больше не предлагается (`not_pairing`), пока не запущен `--new-pairing`. Старый `hello` с токеном работает как раньше.

## Статистика (`usage`)

После `welcome`/`paired`, при изменениях (не чаще раза в 5 с) и раз в 60 с телефон получает `{"type":"usage","fiveHour":{"used","resetsAt"},"sevenDay":{...},"doneToday","tokensToday","plan","updatedAt"}`. Любого поля может не быть.

- `fiveHour`/`sevenDay`/`plan`: OAuth-токен Claude Code берётся из `~/.claude/.credentials.json` (на macOS сначала из связки ключей, элемент «Claude Code-credentials», через `security`), запрос `GET https://api.anthropic.com/api/oauth/usage` не чаще раза в минуту и только пока телефон подключён, при ошибках пауза растёт до 30 минут. Это недокументированный адрес: если не вышло, полей нет. Токен не покидает компьютер, не пишется в лог и не уходит на телефон.
- `doneToday`: число завершённых ходов Claude Code (хук `Stop`) за местные сутки, хранится в `stats.json`.
- `tokensToday`: input + output + cache creation (без чтения из кэша) сообщений ассистента за сегодня из транскрипта, путь к которому приходит в хуке `Stop`. Читается только новая часть файла. Путь и содержимое транскрипта на телефон не уходят.

## Хуки Claude Code

`node bridge.mjs` ставит их сам (с вопросом). Отдельно:

```sh
node install-hooks.mjs            # только показывает diff для ~/.claude/settings.json
node install-hooks.mjs --yes      # применяет: сначала копия settings.json.bak-ГГГГММДД-ЧЧММСС
node install-hooks.mjs --uninstall --yes
```

Скрипт добавляет только записи, команда которых указывает на наш `hook.mjs` (события SessionStart, UserPromptSubmit, PreToolUse, PostToolUse, PermissionRequest, Notification, Stop, StopFailure, SessionEnd), чужие хуки не трогает. Если JSON повреждён, файл не меняется. Diff строится от переформатированного JSON, при записи файл форматируется с отступом 2. Логика общая (`lib/hooks.mjs`) для `install-hooks.mjs` и `bridge.mjs`.

## Поведение и безопасность

- TLS-сервер привязан только к выбранному LAN-адресу. Токен: 32 случайных символа, сравнение через `timingSafeEqual`. `hello` или `pair` ждём 10 с, строка больше 64 КиБ закрывает соединение, неизвестные сообщения игнорируются.
- Код сопряжения: одноразовый, 5 минут, сжигается при неудаче, не больше 5 неудач в минуту, сравнение HMAC через `timingSafeEqual`, привязан к отпечатку сертификата (защита от посредника), в лог не пишется.
- `hook.mjs` никогда не блокирует Claude Code. Если bridge не запущен, телефона нет или решение не пришло за 110 с, он завершается с кодом 0 без вывода, и Claude показывает обычный запрос. Bridge сам ничего не разрешает.
- Телефону уходит команда до 500 символов; длиннее обрезается, добавляется `truncated: true`, а `allow` для такого запроса игнорируется (работает только `deny`). Для файловых инструментов отправляется только имя файла. Промпты, вывод и содержимое файлов не отправляются, токен и команды не пишутся в лог. OAuth-токен Claude Code никогда не отправляется.
- Отпечаток запроса: SHA-256 (hex) от `pillId, sessionId, tool, command, inputKey`, склеенных через U+001F; `inputKey` случайный на каждый запрос.
- Решение принимается, только если отпечаток совпадает с ещё ожидающим запросом. Через 115 с запрос снимается.

## Тесты

```sh
node --test
```
Фальшивый телефон (`lib/fake-phone.mjs`) делает TLS-рукопожатие с проверкой отпечатка сертификата, hello/welcome или сопряжение по коду (`pair`, в том числе с подменённым отпечатком), получает сессии, `usage` и approval и отвечает decision. Тесты покрывают сопряжение и все отказы, посредника с чужим сертификатом, сжигание кода, ограничение попыток, форму `usage` с поддельным HTTP-источником и при его отказе, сохранение `stats.json` между перезапусками, кодирование и разбор пакетов mDNS.
