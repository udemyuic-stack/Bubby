#!/usr/bin/env node
// Desktop side of the phone link (protocol v1 + docs/AGENTS-V2.md + docs/AGENTS-V3.md) for Claude Code and other agents.
// Protocol ideas from Coucou (MIT); the v1 core was adapted from Coucou's android/tools/dev-desktop.mjs (MIT).
import tls from "node:tls";
import net from "node:net";
import fs from "node:fs";
import os from "node:os";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import { createInterface } from "node:readline/promises";
import {
  PROTOCOL_V, MAX_LINE, PILL_ID, APPROVAL_TIMEOUT_MS, buddyDir, sockPath, ensureDir,
  fingerprint, truncateCommand, defaultHost, lanIps,
} from "./lib/common.mjs";
import { ensureCert } from "./lib/cert.mjs";
import { loadToken, newToken, revokeToken, tokenMatches, isPaired, markPaired, createCodePairing } from "./lib/pairing.mjs";
import { startMdns, cutUtf8 } from "./lib/mdns.mjs";
import { createLimits } from "./lib/limits.mjs";
import { createStats } from "./lib/stats.mjs";
import { planHooks, applyHooks } from "./lib/hooks.mjs";
import { planAgent, applyAgentPlan, validAgent, INSTALLERS } from "./lib/agents.mjs";
import { offerable, answersFromPicks } from "./lib/questions.mjs";
import { createSessions, sessionsMessage, pillIdOf, stepOf, finalLineOf } from "./lib/sessions.mjs";
import { createRelayClient, loadRelay, saveRelay, forgetRelay, rotateRelay } from "./lib/relay.mjs";

const HELLO_TIMEOUT_MS = 10_000;
const SILENCE_MS = 90_000;
const STALE_SESSION_MS = 30 * 60_000;
const FAIL_DELAY_MS = 300;
const HOOK_LINE_MAX = 4 * 1024 * 1024; // a file edit can be large; hook.mjs keeps it under ~1 MiB
export const CAPS = ["details", "answers", "diffs"];
// "relay" is offered only on the LAN link, and only when a relay is configured: it hands the phone the relay address and key
const RELAY_CAP = "relay";
const DIFF_LINES = 200, DIFF_LINE_CHARS = 400, DIFF_PART = 100;
const DIFF_ASKS = 20, DIFF_WINDOW_MS = 10_000;

// A display name from the phone: no control characters, at most 40 characters. Only used for the terminal.
const cleanName = (n) => Array.from(String(typeof n === "string" ? n : "").replace(/[\u0000-\u001f\u007f-\u009f]/g, "")).slice(0, 40).join("").trim();
const clip = (s, n) => { const a = Array.from(String(s)); return a.length > n ? a.slice(0, n).join("") : String(s); };

const lineReader = (sock, onLine, onTooLong) => {
  let buf = "";
  sock.on("data", (d) => {
    buf += d.toString("utf8");
    let i;
    while ((i = buf.indexOf("\n")) >= 0) {
      const line = buf.slice(0, i);
      buf = buf.slice(i + 1);
      if (line.length > MAX_LINE) return onTooLong();
      if (line) onLine(line);
    }
    if (buf.length > MAX_LINE) onTooLong();
  });
};

export async function startBridge(opts = {}) {
  const dir = opts.dir ?? buddyDir();
  const host = opts.host ?? defaultHost();
  if (!host) throw new Error("нет частного IPv4-адреса в локальной сети; укажите --host");
  if (!lanIps().includes(host)) throw new Error(`адрес ${host} не принадлежит этому компьютеру`);
  const name = opts.name ?? os.hostname();
  const approvalTimeout = opts.approvalTimeoutMs ?? APPROVAL_TIMEOUT_MS;
  const log = opts.log ?? (() => {});
  // capabilities this bridge offers (--no-details / --no-answers / --no-diffs turn them off)
  const enabled = new Set(CAPS.filter((c) => opts.caps?.[c] !== false));
  const relayCfg = opts.relay === false ? null : (opts.relay ?? loadRelay(dir));
  const { key, cert, fp: certFp } = ensureCert(dir);
  let token = loadToken(dir) ?? newToken(dir);
  // 6-digit code pairing (docs/AGENTS-V2.md section 2). The code goes only to opts.onCode (the terminal), never to log.
  const codes = createCodePairing({ fp: certFp, ttlMs: opts.codeTtlMs, onCode: (c, why) => opts.onCode?.(c, why) });
  let mdns = null;
  const pairedChanged = (phoneName) => { mdns?.update(); opts.onPaired?.(phoneName); };

  const sessions = createSessions();
  const pending = new Map(); // fingerprint -> {kind, reply, truncated, timer, agent, sessionId, questions, createdAt}
  let phone = null; // {sock, send, caps: Set}

  const pushSessions = () => phone?.send(sessionsMessage(sessions.list(phone.caps)));

  // usage: plan limits from the statusLine relay (V3 section 1) + today's counters (V2 section 3).
  // Sent after welcome/paired, on change at most every 5 s, and every 60 s.
  const limits = createLimits(dir);
  const usageMinMs = opts.usageMinIntervalMs ?? 5_000;
  const stats = createStats(dir, { onChange: () => scheduleUsage() });
  let lastUsageAt = 0, lastUsageKey = "", usageTimer = null, usageForce = false;
  const usageMessage = () => {
    const r = limits.snapshot();
    const s = stats.today();
    return {
      type: "usage",
      ...(r.fiveHour && { fiveHour: r.fiveHour }),
      ...(r.sevenDay && { sevenDay: r.sevenDay }),
      doneToday: s.doneToday,
      ...(s.tokensToday !== undefined && { tokensToday: s.tokensToday }),
      ...(r.plan && { plan: r.plan }),
      updatedAt: r.updatedAt ?? Date.now(),
    };
  };
  const sendUsage = (force) => {
    if (!phone) return;
    const m = usageMessage();
    const k = JSON.stringify({ ...m, updatedAt: r0(m) });
    if (!force && k === lastUsageKey) return;
    lastUsageKey = k;
    lastUsageAt = Date.now();
    phone.send(m);
  };
  // updatedAt counts as a change only when it is the limits' own time
  const r0 = (m) => (m.fiveHour || m.sevenDay ? m.updatedAt : 0);
  function scheduleUsage(force = false) {
    usageForce ||= force;
    if (!phone || usageTimer) return;
    usageTimer = setTimeout(() => { usageTimer = null; const f = usageForce; usageForce = false; sendUsage(f); }, Math.max(0, lastUsageAt + usageMinMs - Date.now()));
    usageTimer.unref?.();
  }
  const usageTick = setInterval(() => scheduleUsage(true), opts.usageRefreshMs ?? 60_000);
  usageTick.unref();

  const touch = (ev, patch) => {
    sessions.touch(ev.agent, ev.sessionId, { ...patch, ...(ev.project && { project: ev.project }) });
    pushSessions();
  };

  // result: null (no decision), {decision: "allow"|"deny"} or {answers: {...}}
  const resolve = (fp, result) => {
    const p = pending.get(fp);
    if (!p) return;
    pending.delete(fp);
    clearTimeout(p.timer);
    p.reply(result);
    phone?.send({ type: "approvalResolved", fingerprint: fp });
    const s = sessions.get(p.agent, p.sessionId);
    if (s && (s.state === "approval" || s.state === "question")) touch({ agent: p.agent, sessionId: p.sessionId }, { state: "working", statusText: "Working" });
  };

  function onEvent(ev, reply) {
    if (ev.event === "StatusLine") {
      if (limits.update(ev.rateLimits)) scheduleUsage();
      return reply(null);
    }
    const s = sessions.get(ev.agent, ev.sessionId);
    switch (ev.event) {
      case "SessionStart": touch(ev, { state: "idle", statusText: "Ready", steps: 0 }); break;
      case "UserPromptSubmit": sessions.setFinal(ev.agent, ""); touch(ev, { state: "thinking", statusText: "Thinking", steps: 0 }); break;
      case "PreToolUse":
        sessions.step(ev.agent, stepOf(ev.tool, ev.command));
        touch(ev, { state: "working", statusText: `Using ${String(ev.tool).slice(0, 30)}`, steps: (s?.steps ?? 0) + 1 });
        break;
      case "PostToolUse":
        touch(ev, { state: "working", statusText: "Working" });
        if (enabled.has("diffs") && ev.edit && sessions.edit(ev.agent, ev.sessionId, ev.tool, ev.edit)) pushSessions();
        break;
      case "Notification":
        if (ev.notificationType === "elicitation_dialog") touch(ev, { state: "question", statusText: "Asking you a question" });
        else if (ev.notificationType === "idle_prompt") touch(ev, { state: "idle", statusText: "Waiting for you" });
        break;
      case "Stop":
        if (typeof ev.finalText === "string") sessions.setFinal(ev.agent, finalLineOf(ev.finalText));
        touch(ev, { state: "finished", statusText: "Done" });
        if (!ev.agent) stats.recordStop(ev.transcriptPath); // doneToday counts Claude Code turns
        break;
      case "StopFailure": touch(ev, { state: "error", statusText: "Error" }); break;
      case "SessionEnd": sessions.end(ev.agent, ev.sessionId); pushSessions(); break;
      case "PermissionRequest": return onPermission(ev, reply);
    }
    reply(null);
  }

  const wait = (fp, entry) => {
    const timer = setTimeout(() => resolve(fp, null), approvalTimeout);
    timer.unref?.();
    pending.set(fp, { ...entry, timer, createdAt: Date.now() });
  };

  function onPermission(ev, reply) {
    // other agents: display only; nothing waits on the phone
    if (!phone || ev.agent) return reply(null);
    if (ev.tool === "AskUserQuestion") {
      const questions = phone.caps.has("answers") ? offerable(ev.questions) : null;
      if (!questions) return reply(null); // stays on the computer
      const fp = fingerprint(PILL_ID, ev.sessionId, "AskUserQuestion", questions.map((q) => q.question).join("\n"), crypto.randomUUID());
      wait(fp, { kind: "question", reply, agent: null, sessionId: ev.sessionId, questions });
      touch(ev, { state: "question", statusText: "Asking you a question" });
      phone.send({ type: "question", pillId: PILL_ID, fingerprint: fp, createdAt: pending.get(fp).createdAt, questions });
      return;
    }
    const { command, truncated } = truncateCommand(ev.command);
    const tool = String(ev.tool ?? "").slice(0, 60);
    const fp = fingerprint(PILL_ID, ev.sessionId, tool, command, crypto.randomUUID());
    wait(fp, { kind: "approval", reply, truncated, agent: null, sessionId: ev.sessionId });
    touch(ev, { state: "approval", statusText: "Waiting for your approval" });
    phone.send({ type: "approval", pillId: PILL_ID, fingerprint: fp, tool, command, createdAt: Date.now(), ...(truncated ? { truncated: true } : {}) });
  }

  // a phone's getDiff => one to two "diff" lines (or gone)
  function sendDiff(send, pillId, fileId) {
    const f = typeof pillId === "string" && Number.isSafeInteger(fileId) ? sessions.file(pillId, fileId) : null;
    const base = { type: "diff", pillId: clip(String(pillId ?? ""), 64), fileId: Number.isSafeInteger(fileId) ? fileId : 0 };
    if (!f) return send({ ...base, name: "", added: 0, removed: 0, tooLarge: false, gone: true, truncated: false, part: 0, parts: 1, lines: [] });
    const rows = f.tooLarge ? [] : f.lines.slice(0, DIFF_LINES).map(([k, t]) => [["+", "-", "@"].includes(k) ? k : " ", clip(t, DIFF_LINE_CHARS)]);
    const truncated = !f.tooLarge && f.lines.length > DIFF_LINES;
    const parts = rows.length ? Math.ceil(rows.length / DIFF_PART) : 1;
    for (let i = 0; i < parts; i++) {
      send({ ...base, name: f.name, added: f.added, removed: f.removed, tooLarge: !!f.tooLarge, gone: false, truncated, part: i, parts, lines: rows.slice(i * DIFF_PART, (i + 1) * DIFF_PART) });
    }
  }

  // TLS server for the phone
  const socks = new Set();
  const onSocket = (sock, via) => {
    socks.add(sock);
    sock.on("close", () => socks.delete(sock));
    sock.setNoDelay(true);
    const send = (o) => sock.writable && sock.write(JSON.stringify(o) + "\n");
    let authed = false;
    let closing = false; // a refused pair attempt: ignore anything else on this connection
    let caps = new Set();
    const diffAsks = [];
    const offer = (m) => {
      const asked = Array.isArray(m.caps) ? m.caps.filter((c) => typeof c === "string") : [];
      caps = new Set(CAPS.filter((c) => enabled.has(c) && asked.includes(c)));
      if (relayCfg && via === "lan" && asked.includes(RELAY_CAP)) caps.add(RELAY_CAP);
      return [...caps];
    };
    const authenticated = () => {
      authed = true;
      clearTimeout(helloTimer);
      phone = { sock, send, caps, via };
      log(via === "relay" ? "phone connected (relay)" : "phone connected");
    };
    const afterWelcome = () => {
      // over the LAN (certificate-pinned) link the phone learns where the relay is and the key for it
      if (caps.has(RELAY_CAP) && relayCfg) send({ type: "relay", url: relayCfg.url, key: relayCfg.rk });
      pushSessions();
      lastUsageKey = "";
      sendUsage(true);
    };
    const refuse = (error) => {
      closing = true;
      setTimeout(() => { send({ type: "error", error }); sock.end(); }, FAIL_DELAY_MS);
    };
    const onPair = (m) => {
      const current = loadToken(dir);
      if (!current || isPaired(dir)) return refuse("not_pairing");
      if (phone) { closing = true; send({ type: "error", code: "busy", message: "another phone is connected" }); return sock.end(); }
      const r = codes.attempt(m);
      if (r !== "ok") {
        if (r === "bad_code") log("неверный код сопряжения; код сожжён, выпущен новый");
        if (r === "slow_down") log("слишком много неверных кодов, попытка отклонена");
        return refuse(r);
      }
      markPaired(dir);
      const phoneName = cleanName(m.name);
      const offered = offer(m);
      authenticated();
      log(`phone paired by code${phoneName ? ` (${phoneName})` : ""}`);
      send({ type: "paired", token: current, name, caps: offered });
      pairedChanged(phoneName);
      afterWelcome();
    };
    const helloTimer = setTimeout(() => { if (!authed) sock.destroy(); }, HELLO_TIMEOUT_MS);
    sock.setTimeout(SILENCE_MS, () => sock.destroy());
    const fail = () => sock.destroy();
    lineReader(sock, (line) => {
      let m;
      try { m = JSON.parse(line); } catch { return fail(); }
      if (!m || typeof m !== "object") return fail();
      if (closing) return;
      if (!authed) {
        if (m.type === "pair") return via === "lan" ? onPair(m) : fail(); // codes are for the home network only
        if (m.type !== "hello") return fail();
        const expected = loadToken(dir);
        const ok = m.v === PROTOCOL_V && expected && tokenMatches(m.token, expected);
        if (!ok) {
          return setTimeout(() => { send({ type: "error", code: "auth", message: "bad token or version" }); sock.end(); }, FAIL_DELAY_MS);
        }
        if (phone) { send({ type: "error", code: "busy", message: "another phone is connected" }); return sock.end(); }
        if (!isPaired(dir)) { markPaired(dir); codes.stop(); pairedChanged(""); }
        const offered = offer(m);
        authenticated();
        send({ type: "welcome", v: PROTOCOL_V, desktop: name, os: process.platform, caps: offered });
        afterWelcome();
        return;
      }
      switch (m.type) {
        case "ping": return send({ type: "pong" });
        case "bye": return sock.end();
        case "decision": {
          const p = typeof m.fingerprint === "string" ? pending.get(m.fingerprint) : null;
          if (!p || p.kind !== "approval" || (m.decision !== "allow" && m.decision !== "deny")) return;
          if (m.decision === "allow" && p.truncated) return; // answered on the computer
          return resolve(m.fingerprint, { decision: m.decision });
        }
        case "answer": {
          if (!caps.has("answers")) return;
          const p = typeof m.fingerprint === "string" ? pending.get(m.fingerprint) : null;
          if (!p || p.kind !== "question" || Date.now() - p.createdAt >= approvalTimeout) return;
          const answers = answersFromPicks(p.questions, m.picks);
          if (!answers) return; // not an exact match: nothing changes, the question stays open
          return resolve(m.fingerprint, { answers });
        }
        case "getDiff": {
          if (!caps.has("diffs")) return;
          const t = Date.now();
          while (diffAsks.length && t - diffAsks[0] >= DIFF_WINDOW_MS) diffAsks.shift();
          if (diffAsks.length >= DIFF_ASKS) return;
          diffAsks.push(t);
          return sendDiff(send, m.pillId, m.fileId);
        }
        default: return;
      }
    }, fail);
    sock.on("close", () => {
      clearTimeout(helloTimer);
      if (phone?.sock === sock) {
        phone = null;
        clearTimeout(usageTimer);
        usageTimer = null;
        usageForce = false;
        for (const fp of [...pending.keys()]) resolve(fp, null);
        log("phone disconnected");
      }
    });
    sock.on("error", () => {});
  };
  const server = tls.createServer({ key, cert, minVersion: "TLSv1.2" }, (sock) => onSocket(sock, "lan"));

  // the relay: the phone's frames from outside the home network arrive here as ordinary connections
  const relay = relayCfg ? createRelayClient({ url: relayCfg.url, rk: relayCfg.rk, log, onConnection: (c) => onSocket(c, "relay"), WebSocket: opts.WebSocket }) : null;

  // unix socket for hook.mjs
  ensureDir(dir);
  const sp = sockPath(dir);
  if (fs.existsSync(sp)) {
    const alive = await new Promise((r) => {
      const c = net.connect(sp);
      c.on("connect", () => { c.destroy(); r(true); });
      c.on("error", () => r(false));
    });
    if (alive) throw new Error("bridge уже запущен");
    fs.rmSync(sp, { force: true });
  }
  const hookServer = net.createServer((c) => {
    const chunks = [];
    let size = 0, done = false, pendingFp = null;
    const reply = (result) => { if (!done) { done = true; c.end(result ? JSON.stringify(result) + "\n" : "\n"); } };
    c.setTimeout(10_000, () => { if (!pendingFp) c.destroy(); });
    c.on("data", (d) => {
      if (pendingFp !== null) return;
      size += d.length;
      if (size > HOOK_LINE_MAX) return c.destroy();
      chunks.push(d);
      const i = d.indexOf(0x0a);
      if (i < 0) return;
      const all = Buffer.concat(chunks);
      let ev;
      try { ev = JSON.parse(all.subarray(0, all.indexOf(0x0a)).toString("utf8")); } catch { return c.destroy(); }
      if (!ev || typeof ev.event !== "string" || typeof ev.sessionId !== "string") return reply(null);
      ev.agent = validAgent(ev.agent); // absent or invalid means Claude Code
      if (typeof ev.project !== "string") delete ev.project;
      pendingFp = "";
      const before = new Set(pending.keys());
      onEvent(ev, reply);
      for (const fp of pending.keys()) if (!before.has(fp)) { pendingFp = fp; c.setTimeout(0); }
    });
    c.on("close", () => { if (pendingFp) { done = true; resolve(pendingFp, null); } });
    c.on("error", () => {});
  });
  await new Promise((res, rej) => { hookServer.once("error", rej); hookServer.listen(sp, res); });
  fs.chmodSync(sp, 0o600);

  await new Promise((res, rej) => { server.once("error", rej); server.listen(opts.port ?? 47821, host, res); });
  const port = server.address().port;

  if (!isPaired(dir)) codes.start();
  if (opts.mdns !== false) {
    mdns = startMdns({
      name, hostLabel: `bubby-${certFp.slice(0, 8)}`, ip: host, port, log,
      txt: () => ({ v: PROTOCOL_V, name: cutUtf8(name, 200), fp: certFp, paired: isPaired(dir) ? 1 : 0 }),
    });
  }

  const prune = setInterval(() => { sessions.prune(STALE_SESSION_MS); }, 60_000);
  prune.unref();

  return {
    port, host, fp: certFp, name,
    caps: [...enabled],
    pairingCode: () => codes.current(),
    paired: () => isPaired(dir),
    mdnsName: () => mdns?.instance ?? null,
    link: () => `coucou://pair?v=${PROTOCOL_V}&host=${host}&port=${port}&fp=${certFp}&token=${loadToken(dir) ?? token}&name=${encodeURIComponent(name)}`,
    newPairing() {
      token = newToken(dir);
      if (phone) phone.sock.destroy();
      codes.stop();
      codes.start("new");
      mdns?.update();
    },
    relayState: () => (relay ? relay.state() : "off"),
    relayUrl: () => relayCfg?.url ?? null,
    flushStats: () => stats.flush(),
    async close() {
      clearInterval(prune);
      clearInterval(usageTick);
      clearTimeout(usageTimer);
      relay?.close();
      codes.stop();
      await mdns?.close();
      for (const fp of [...pending.keys()]) resolve(fp, null);
      for (const s of socks) s.destroy();
      await Promise.all([new Promise((r) => server.close(r)), new Promise((r) => hookServer.close(r))]);
      fs.rmSync(sp, { force: true });
    },
  };
}

const HELP = `node bridge.mjs [options]

  First run does everything: certificate, LAN address, Claude Code hooks and the statusLine relay (asks first),
  mDNS, pairing code.

  --yes                 install the Claude Code hooks / statusLine relay / agent hooks without asking
  --no-hooks            do not install the Claude Code hooks
  --no-statusline       do not install the statusLine relay (plan limits on the phone)
  --install-agent NAME  also install hooks for another agent (${Object.keys(INSTALLERS).join(", ")}); see docs/AGENTS-OTHER.md
  --no-details          do not offer session details (project, steps, final line) to the phone
  --no-answers          do not let the phone answer Claude Code's questions
  --no-diffs            do not offer the files an agent changed to the phone
  --relay URL           also reach the phone from outside the home network through your own Cloudflare relay (relay/README.md)
  --forget-relay        stop using the relay and delete its key
  --link                also print the old coucou://pair link (contains the token, keep it secret)
  --new-pairing         new token and a new code (the old phone stops working)
  --revoke              delete the token and exit
  --host IPv4           LAN address to listen on (default: first private IPv4)
  --port N              TCP port (default 47821)
  --name NAME           computer name shown on the phone (default: host name)
  --no-mdns             do not advertise over mDNS`;

const headline = (code) => `Open Bubby on your phone → Agents → tap this computer → type ${code}`;

function printCode(code) {
  const line = headline(code);
  const bar = "─".repeat(Math.min(Array.from(line).length + 4, 100));
  console.log(`\n  ${bar}\n    ${line}\n  ${bar}`);
}

async function confirm(question) {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const answer = await rl.question(question).finally(() => rl.close());
  return /^(y|yes|д|да)$/i.test(answer.trim());
}
const tty = () => process.stdin.isTTY && process.stdout.isTTY;

// Claude Code hooks + statusLine relay, same logic as install-hooks.mjs --yes (dated .bak copy).
// Returns a short status for the banner.
async function setupClaude({ yes, hooks, statusLine }) {
  if (!hooks && !statusLine) return "не трогаю (--no-hooks --no-statusline)";
  let plan;
  try { plan = planHooks({ hooks, statusLine }); } catch (e) { console.log(`Хуки Claude Code: ${e.message}`); return "не установлены (settings.json не разобран)"; }
  if (plan.note) console.log(`Claude Code: ${plan.note}`);
  const what = [hooks && "хуки", statusLine && "statusLine"].filter(Boolean).join(" и ");
  if (!plan.diff) return `${what}: установлены`;
  if (!yes) {
    if (!tty()) {
      console.log(`Claude Code: ${what} не установлены. Запустите с --yes, чтобы установить (или --no-hooks / --no-statusline).`);
      return `${what}: не установлены (нужен --yes)`;
    }
    console.log(`Bridge добавит в ${plan.file}: ${what}.`);
    console.log("Хуки отправляют в Bubby события сессий и запросы разрешений; statusLine передаёт только лимиты плана и затем");
    console.log("запускает вашу прежнюю строку состояния без изменений. Чужие хуки не меняются; сначала делается копия .bak.\n");
    console.log(plan.diff + "\n");
    if (!(await confirm("Установить? [y/N] "))) return `${what}: не установлены (можно позже: node install-hooks.mjs --yes)`;
  }
  const backup = applyHooks(plan);
  if (backup) console.log(`Резервная копия: ${backup}`);
  return `${what}: установлены сейчас`;
}

async function setupAgent(agent, yes) {
  let plan;
  try { plan = planAgent(agent); } catch (e) { console.log(e.message); return; }
  if (!plan.diff) { console.log(`${plan.title}: хуки уже установлены.`); return; }
  if (!yes) {
    if (!tty()) { console.log(`${plan.title}: запустите с --yes, чтобы установить хуки.`); return; }
    console.log(`Bridge добавит хуки ${plan.title} в ${plan.file} (только показ сессий на телефоне; сначала копия .bak):\n`);
    console.log(plan.diff + "\n");
    if (!(await confirm("Установить? [y/N] "))) return;
  }
  const backup = applyAgentPlan(plan);
  if (backup) console.log(`Резервная копия: ${backup}`);
  console.log(`${plan.title}: хуки установлены. Удалить: node install-hooks.mjs --agent ${agent} --uninstall --yes`);
}

async function main() {
  const args = process.argv.slice(2);
  const arg = (n) => { const i = args.indexOf(`--${n}`); return i >= 0 ? args[i + 1] : undefined; };
  const has = (n) => args.includes(`--${n}`);
  const dir = buddyDir();
  if (has("help") || args.includes("-h")) { console.log(HELP); return; }
  if (has("revoke")) {
    console.log(revokeToken(dir) ? "Токен сопряжения удалён. Телефон больше не сможет подключиться." : "Токена нет.");
    return;
  }
  if (has("new-pairing")) { ensureDir(dir); newToken(dir); rotateRelay(dir); } // a new pairing also changes the relay key
  if (has("forget-relay")) console.log(forgetRelay(dir) ? "Relay отключён, ключ удалён." : "Relay не был настроен.");
  if (arg("relay")) {
    try { const c = saveRelay(dir, arg("relay")); console.log(`Relay: ${c.url}${c.url.startsWith("ws:") ? "  (без TLS: содержимое всё равно зашифровано, но адрес виден в сети)" : ""}`); }
    catch (e) { console.error("Ошибка:", e.message); process.exit(1); }
  }
  const claude = await setupClaude({ yes: has("yes"), hooks: !has("no-hooks"), statusLine: !has("no-statusline") });
  if (arg("install-agent")) await setupAgent(arg("install-agent"), has("yes"));

  let ready = false;
  const b = await startBridge({
    dir, host: arg("host"), port: arg("port") ? Number(arg("port")) : undefined, name: arg("name"),
    mdns: !has("no-mdns"),
    caps: { details: !has("no-details"), answers: !has("no-answers"), diffs: !has("no-diffs") },
    log: (m) => console.log(new Date().toISOString(), m),
    onCode: (code, why) => {
      if (!ready) return; // the first code is part of the banner below
      console.log(why === "failed" ? "\nНеверный код: он сожжён. Новый код:" : why === "expired" ? "\nКод устарел (5 минут). Новый код:" : "\nНовый код:");
      printCode(code);
    },
    onPaired: (phoneName) => console.log(`\nГотово: телефон сопряжён${phoneName ? ` (${phoneName})` : ""}. Код больше не нужен.\n`),
  });
  ready = true;

  console.log(`\n  Bubby agent bridge`);
  console.log(`  Компьютер:   ${b.name}`);
  console.log(`  Адрес:       ${b.host}:${b.port} (только локальная сеть)`);
  console.log(`  Claude Code: ${claude}`);
  console.log(`  Телефону:    ${b.caps.length ? b.caps.join(", ") : "только сессии и разрешения"}`);
  console.log(`  mDNS:        ${b.mdnsName() ? `${b.mdnsName()}._bubby-agent._tcp.local.` : "выключен"}`);
  console.log(`  Сертификат:  ${b.fp.slice(0, 16)}…`);
  console.log(`  Relay:       ${b.relayUrl() ? `${b.relayUrl()} (телефон получит ключ по домашней сети при первом подключении)` : "выключен (--relay URL, чтобы подключаться вне дома)"}`);
  if (b.pairingCode()) {
    printCode(b.pairingCode());
    console.log(`\n  Код одноразовый, действует 5 минут. Компьютера нет в списке? Введите в приложении ${b.host}:${b.port}.`);
  } else {
    console.log(`\n  Телефон уже сопряжён: просто откройте Bubby → Agents.`);
    console.log(`  Другой телефон: node bridge.mjs --new-pairing (старый перестанет подключаться).`);
  }
  if (has("link")) console.log(`\n  Ссылка для сопряжения (держите в секрете):\n  ${b.link()}`);
  console.log(`\n  Ctrl+C — остановить.\n`);

  let stopping = false;
  const stop = () => { if (stopping) return; stopping = true; b.close().finally(() => process.exit(0)); };
  process.on("SIGINT", stop);
  process.on("SIGTERM", stop);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) {
  main().catch((e) => { console.error("Ошибка:", e.message); process.exit(1); });
}
