#!/usr/bin/env node
// Desktop side of the coucou phone-link protocol v1 for Claude Code. Adapted from coucou's android/tools/dev-desktop.mjs (MIT).
import tls from "node:tls";
import net from "node:net";
import fs from "node:fs";
import os from "node:os";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import {
  PROTOCOL_V, MAX_LINE, PILL_ID, AGENT, APPROVAL_TIMEOUT_MS, buddyDir, sockPath, ensureDir,
  fingerprint, truncateCommand, defaultHost, lanIps,
} from "./lib/common.mjs";
import { ensureCert } from "./lib/cert.mjs";
import { loadToken, newToken, revokeToken, tokenMatches, isPaired, markPaired, createCodePairing } from "./lib/pairing.mjs";
import { startMdns, cutUtf8 } from "./lib/mdns.mjs";

const HELLO_TIMEOUT_MS = 10_000;
const SILENCE_MS = 90_000;
const STALE_SESSION_MS = 30 * 60_000;
const FAIL_DELAY_MS = 300;
const PRIORITY = { approval: 5, question: 4, error: 3, working: 2, thinking: 2, finished: 1, idle: 0 };

// A display name from the phone: no control characters, at most 40 characters. Only used for the terminal.
const cleanName = (n) => Array.from(String(typeof n === "string" ? n : "").replace(/[\u0000-\u001f\u007f-\u009f]/g, "")).slice(0, 40).join("").trim();

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
  const { key, cert, fp: certFp } = ensureCert(dir);
  let token = loadToken(dir) ?? newToken(dir);
  // 6-digit code pairing (docs/AGENTS-V2.md section 2). The code goes only to opts.onCode (the terminal), never to log.
  const codes = createCodePairing({ fp: certFp, ttlMs: opts.codeTtlMs, onCode: (c, why) => opts.onCode?.(c, why) });
  let mdns = null;
  const pairedChanged = (phoneName) => { mdns?.update(); opts.onPaired?.(phoneName); };

  const sessions = new Map(); // sessionId -> {state, statusText, steps, updatedAt}
  const pending = new Map(); // fingerprint -> {reply, truncated, timer, sessionId}
  let phone = null; // {sock, send}

  const sessionList = () => {
    if (!sessions.size) return [];
    let best = null, steps = 0, newest = 0;
    for (const s of sessions.values()) {
      steps = Math.max(steps, s.steps);
      newest = Math.max(newest, s.updatedAt);
      if (!best || (PRIORITY[s.state] ?? 0) > (PRIORITY[best.state] ?? 0)) best = s;
    }
    return [{ pillId: PILL_ID, agent: AGENT, state: best.state, statusText: best.statusText, stepIndex: steps, stepCount: steps, updatedAt: newest }];
  };
  const pushSessions = () => phone?.send({ type: "sessions", sessions: sessionList() });

  const touch = (id, patch) => {
    const s = sessions.get(id) ?? { state: "idle", statusText: "", steps: 0, updatedAt: 0 };
    Object.assign(s, patch, { updatedAt: Date.now() });
    sessions.set(id, s);
    pushSessions();
    return s;
  };

  const resolve = (fp, result) => {
    const p = pending.get(fp);
    if (!p) return;
    pending.delete(fp);
    clearTimeout(p.timer);
    p.reply(result);
    phone?.send({ type: "approvalResolved", fingerprint: fp });
    const s = sessions.get(p.sessionId);
    if (s && s.state === "approval") touch(p.sessionId, { state: "working", statusText: "Working" });
  };

  function onEvent(ev, reply) {
    const id = ev.sessionId;
    switch (ev.event) {
      case "SessionStart": touch(id, { state: "idle", statusText: "Ready", steps: 0 }); break;
      case "UserPromptSubmit": touch(id, { state: "thinking", statusText: "Thinking", steps: 0 }); break;
      case "PreToolUse": {
        const s = sessions.get(id);
        touch(id, { state: "working", statusText: `Using ${String(ev.tool).slice(0, 30)}`, steps: (s?.steps ?? 0) + 1 });
        break;
      }
      case "PostToolUse": touch(id, { state: "working", statusText: "Working" }); break;
      case "Notification":
        if (ev.notificationType === "elicitation_dialog") touch(id, { state: "question", statusText: "Asking you a question" });
        else if (ev.notificationType === "idle_prompt") touch(id, { state: "idle", statusText: "Waiting for you" });
        break;
      case "Stop": touch(id, { state: "finished", statusText: "Done" }); break;
      case "StopFailure": touch(id, { state: "error", statusText: "Error" }); break;
      case "SessionEnd": sessions.delete(id); pushSessions(); break;
      case "PermissionRequest": return onPermission(ev, reply);
    }
    reply(null);
  }

  function onPermission(ev, reply) {
    if (!phone || ev.tool === "AskUserQuestion") return reply(null);
    const { command, truncated } = truncateCommand(ev.command);
    const tool = String(ev.tool ?? "").slice(0, 60);
    const fp = fingerprint(PILL_ID, ev.sessionId, tool, command, crypto.randomUUID());
    const timer = setTimeout(() => resolve(fp, null), approvalTimeout);
    timer.unref?.();
    pending.set(fp, { reply, truncated, timer, sessionId: ev.sessionId });
    touch(ev.sessionId, { state: "approval", statusText: "Waiting for your approval" });
    phone.send({ type: "approval", pillId: PILL_ID, fingerprint: fp, tool, command, createdAt: Date.now(), ...(truncated ? { truncated: true } : {}) });
  }

  // TLS server for the phone
  const socks = new Set();
  const server = tls.createServer({ key, cert, minVersion: "TLSv1.2" }, (sock) => {
    socks.add(sock);
    sock.on("close", () => socks.delete(sock));
    sock.setNoDelay(true);
    const send = (o) => sock.writable && sock.write(JSON.stringify(o) + "\n");
    let authed = false;
    let closing = false; // a refused pair attempt: ignore anything else on this connection
    const authenticated = () => {
      authed = true;
      clearTimeout(helloTimer);
      phone = { sock, send };
      log("phone connected");
    };
    const afterWelcome = () => { pushSessions(); };
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
      authenticated();
      log(`phone paired by code${phoneName ? ` (${phoneName})` : ""}`);
      send({ type: "paired", token: current, name });
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
        if (m.type === "pair") return onPair(m);
        if (m.type !== "hello") return fail();
        const expected = loadToken(dir);
        const ok = m.v === PROTOCOL_V && expected && tokenMatches(m.token, expected);
        if (!ok) {
          return setTimeout(() => { send({ type: "error", code: "auth", message: "bad token or version" }); sock.end(); }, FAIL_DELAY_MS);
        }
        if (phone) { send({ type: "error", code: "busy", message: "another phone is connected" }); return sock.end(); }
        if (!isPaired(dir)) { markPaired(dir); codes.stop(); pairedChanged(""); }
        authenticated();
        send({ type: "welcome", v: PROTOCOL_V, desktop: name, os: process.platform });
        afterWelcome();
        return;
      }
      switch (m.type) {
        case "ping": return send({ type: "pong" });
        case "bye": return sock.end();
        case "decision": {
          const p = typeof m.fingerprint === "string" ? pending.get(m.fingerprint) : null;
          if (!p || (m.decision !== "allow" && m.decision !== "deny")) return;
          if (m.decision === "allow" && p.truncated) return; // answered on the computer
          return resolve(m.fingerprint, m.decision);
        }
        default: return;
      }
    }, fail);
    sock.on("close", () => {
      clearTimeout(helloTimer);
      if (phone?.sock === sock) {
        phone = null;
        for (const fp of [...pending.keys()]) resolve(fp, null);
        log("phone disconnected");
      }
    });
    sock.on("error", () => {});
  });

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
    let buf = "";
    let done = false;
    const reply = (decision) => { if (!done) { done = true; c.end(decision ? JSON.stringify({ decision }) + "\n" : "\n"); } };
    c.setTimeout(10_000, () => { if (!pendingFp) c.destroy(); });
    let pendingFp = null;
    c.on("data", (d) => {
      buf += d.toString("utf8");
      if (buf.length > 256 * 1024) return c.destroy();
      const i = buf.indexOf("\n");
      if (i < 0 || pendingFp !== null) return;
      let ev;
      try { ev = JSON.parse(buf.slice(0, i)); } catch { return c.destroy(); }
      if (!ev || typeof ev.event !== "string" || typeof ev.sessionId !== "string") return reply(null);
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

  const prune = setInterval(() => {
    const now = Date.now();
    for (const [id, s] of sessions) if (now - s.updatedAt > STALE_SESSION_MS) sessions.delete(id);
  }, 60_000);
  prune.unref();

  return {
    port, host, fp: certFp, name,
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
    async close() {
      clearInterval(prune);
      codes.stop();
      await mdns?.close();
      for (const fp of [...pending.keys()]) resolve(fp, null);
      for (const s of socks) s.destroy();
      await Promise.all([new Promise((r) => server.close(r)), new Promise((r) => hookServer.close(r))]);
      fs.rmSync(sp, { force: true });
    },
  };
}

function main() {
  const args = process.argv.slice(2);
  const arg = (n) => { const i = args.indexOf(`--${n}`); return i >= 0 ? args[i + 1] : undefined; };
  const dir = buddyDir();
  if (args.includes("--help")) {
    console.log("bridge.mjs [--host IPv4] [--port N] [--name NAME] [--new-pairing] [--revoke]");
    return;
  }
  if (args.includes("--revoke")) {
    console.log(revokeToken(dir) ? "Токен сопряжения удалён. Телефон больше не сможет подключиться." : "Токена нет.");
    return;
  }
  if (args.includes("--new-pairing")) { ensureDir(dir); newToken(dir); }
  startBridge({ dir, host: arg("host"), port: arg("port") ? Number(arg("port")) : undefined, name: arg("name"), log: (m) => console.log(new Date().toISOString(), m) })
    .then((b) => {
      console.log(`Bridge слушает ${b.host}:${b.port} (только локальная сеть)`);
      console.log(`\nСсылка для сопряжения (держите в секрете):\n${b.link()}\n`);
      const stop = () => b.close().then(() => process.exit(0));
      process.on("SIGINT", stop);
      process.on("SIGTERM", stop);
    })
    .catch((e) => { console.error("Ошибка:", e.message); process.exit(1); });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) main();
