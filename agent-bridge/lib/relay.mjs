// Computer side of the relay (docs/RELAY.md): keeps one outgoing WebSocket to the relay and turns the phone's encrypted
// frames into an ordinary duplex "socket", so bridge.mjs handles it exactly like a LAN connection.
import { Duplex } from "node:stream";
import fs from "node:fs";
import path from "node:path";
import { derive, createReceiver, createSender, open, newRelayKey } from "./relay-crypto.mjs";
import { ensureDir } from "./common.mjs";

const KEEPALIVE_MS = 30_000;

export const relayFile = (dir) => path.join(dir, "relay.json");

export function loadRelay(dir) {
  try {
    const j = JSON.parse(fs.readFileSync(relayFile(dir), "utf8"));
    if (typeof j.url === "string" && typeof j.rk === "string") { derive(j.rk); return { url: j.url, rk: j.rk }; }
  } catch {}
  return null;
}

export function saveRelay(dir, url, rk) {
  ensureDir(dir);
  const cfg = { url: normalizeUrl(url), rk: rk ?? loadRelay(dir)?.rk ?? newRelayKey() };
  fs.writeFileSync(relayFile(dir), JSON.stringify(cfg), { mode: 0o600 });
  try { fs.chmodSync(relayFile(dir), 0o600); } catch {}
  return cfg;
}

export const forgetRelay = (dir) => { try { fs.rmSync(relayFile(dir)); return true; } catch { return false; } };

// a new key for a new pairing: the old phone loses the relay together with the token
export function rotateRelay(dir) {
  const c = loadRelay(dir);
  return c ? saveRelay(dir, c.url, newRelayKey()) : null;
}

export function normalizeUrl(u) {
  let x;
  try { x = new URL(String(u).trim()); } catch { throw new Error("адрес relay не разобран"); }
  if (x.protocol === "https:") x.protocol = "wss:";
  if (x.protocol === "http:") x.protocol = "ws:";
  if (x.protocol !== "wss:" && x.protocol !== "ws:") throw new Error("адрес relay должен начинаться с wss://");
  if (x.username || x.password || x.search || x.hash) throw new Error("в адресе relay не должно быть логина, параметров и якоря");
  return x.origin;
}

class RelayConn extends Duplex {
  constructor(send, endMark) {
    super({ allowHalfOpen: false });
    this._send = send;
    this._endMark = endMark;
    this._timer = null;
  }
  _read() {}
  _write(chunk, _enc, cb) {
    for (const line of chunk.toString("utf8").split("\n")) if (line) this._send(line);
    cb();
  }
  _final(cb) { this.push(null); cb(); }
  _destroy(err, cb) { clearTimeout(this._timer); this._endMark(); cb(err); }
  setNoDelay() { return this; }
  setTimeout(ms, cb) {
    clearTimeout(this._timer);
    if (ms > 0) {
      this._ms = ms; this._cb = cb;
      this._timer = setTimeout(() => cb?.(), ms);
      this._timer.unref?.();
    }
    return this;
  }
  rearm() { if (this._ms) this.setTimeout(this._ms, this._cb); }
}

// opts: { url, rk, onConnection(duplex), log, WebSocket }
export function createRelayClient(opts) {
  const { room, key } = derive(opts.rk);
  const base = normalizeUrl(opts.url);
  const target = `${base}/r/${room}?role=bridge`;
  const WS = opts.WebSocket ?? globalThis.WebSocket;
  if (!WS) throw new Error("нужен Node 22 или новее (WebSocket)");
  const log = opts.log ?? (() => {});
  const send = createSender(key, "b2p");
  const recv = createReceiver();
  let ws = null, closed = false, backoff = 1000, conn = null, up = false, peer = false, retryTimer = null, keep = null;

  const state = () => (up ? (peer ? "phone-online" : "waiting") : "offline");
  const wsSend = (t) => { try { if (ws?.readyState === 1) ws.send(t); } catch {} };

  const dropConn = () => { const c = conn; conn = null; if (c && !c.destroyed) c.destroy(); };

  function connect() {
    if (closed) return;
    let sock;
    try { sock = new WS(target); } catch (e) { log(`relay: ${e.message}`); return schedule(); }
    ws = sock;
    sock.onopen = () => {
      up = true; backoff = 1000;
      log("relay: connected");
      keep = setInterval(() => wsSend("ping"), KEEPALIVE_MS);
      keep.unref?.();
    };
    sock.onmessage = (ev) => {
      const text = typeof ev.data === "string" ? ev.data : "";
      if (text.startsWith("{")) {
        let c; try { c = JSON.parse(text); } catch { return; }
        if (c.type === "peer") { peer = !!c.up; if (!peer) dropConn(); }
        else if (c.type === "replaced") log("relay: another computer took this room");
        return;
      }
      if (text === "pong" || !text) return;
      const m = open(key, "p2b", text);
      if (!m || !recv.accept(m) || typeof m.l !== "string") return;
      if (m.x) return dropConn();
      if (!conn) {
        conn = new RelayConn((line) => wsSend(send.line(line)), () => wsSend(send.end()));
        const mine = conn;
        mine.on("close", () => { if (conn === mine) conn = null; });
        mine.on("error", () => {});
        opts.onConnection(mine);
      }
      conn.rearm();
      if (m.l) conn.push(Buffer.from(m.l + "\n", "utf8"));
    };
    const down = () => {
      if (ws !== sock) return;
      up = false; peer = false; clearInterval(keep); dropConn();
      ws = null;
      if (!closed) { log("relay: disconnected"); schedule(); }
    };
    sock.onclose = down;
    sock.onerror = () => {};
  }

  function schedule() {
    if (closed || retryTimer) return;
    retryTimer = setTimeout(() => { retryTimer = null; connect(); }, backoff);
    retryTimer.unref?.();
    backoff = Math.min(backoff * 2, 30_000);
  }

  connect();
  return {
    url: base, room, key,
    state,
    close() { closed = true; clearTimeout(retryTimer); clearInterval(keep); dropConn(); try { ws?.close(); } catch {} },
  };
}
