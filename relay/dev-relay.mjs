#!/usr/bin/env node
// A tiny local stand-in for the Cloudflare relay (same behaviour, no TLS, no dependencies). For tests and for trying the
// relay on a LAN:  node relay/dev-relay.mjs [port]   then   node agent-bridge/bridge.mjs --relay ws://<this computer>:<port>
import http from "node:http";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
const MAX_FRAME = 512 * 1024;
const other = (r) => (r === "bridge" ? "phone" : "bridge");

const encode = (opcode, payload) => {
  const p = Buffer.isBuffer(payload) ? payload : Buffer.from(payload);
  const n = p.length;
  const head = n < 126 ? Buffer.from([0x80 | opcode, n]) : n < 65536
    ? Buffer.from([0x80 | opcode, 126, n >> 8, n & 255])
    : Buffer.concat([Buffer.from([0x80 | opcode, 127]), (() => { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(n)); return b; })()]);
  return Buffer.concat([head, p]);
};

// returns {frames:[{opcode,payload}], rest} from a buffer of client (masked) frames; fin-fragmented messages are joined
function parse(buf, state) {
  const frames = [];
  while (buf.length >= 2) {
    const fin = (buf[0] & 0x80) !== 0, opcode = buf[0] & 15, masked = (buf[1] & 0x80) !== 0;
    let len = buf[1] & 127, off = 2;
    if (len === 126) { if (buf.length < 4) break; len = buf.readUInt16BE(2); off = 4; }
    else if (len === 127) { if (buf.length < 10) break; len = Number(buf.readBigUInt64BE(2)); off = 10; }
    if (len > MAX_FRAME * 2) return { error: true, frames, rest: buf };
    const need = off + (masked ? 4 : 0) + len;
    if (buf.length < need) break;
    let payload = buf.subarray(off + (masked ? 4 : 0), need);
    if (masked) { const k = buf.subarray(off, off + 4); payload = Buffer.from(payload.map((b, i) => b ^ k[i % 4])); }
    buf = buf.subarray(need);
    if (opcode === 0 || opcode === 1 || opcode === 2) {
      state.chunks.push(payload);
      if (opcode !== 0) state.op = opcode;
      if (fin) { frames.push({ opcode: state.op, payload: Buffer.concat(state.chunks) }); state.chunks = []; }
    } else frames.push({ opcode, payload });
  }
  return { frames, rest: buf };
}

export function startDevRelay(port = 0, host = "127.0.0.1") {
  const rooms = new Map(); // room -> {bridge, phone}
  const open = new Set(); // upgraded sockets (http.Server does not track them)
  const server = http.createServer((req, res) => { res.end("bubby dev relay ok\n"); });
  server.on("upgrade", (req, sock) => {
    const url = new URL(req.url, "http://x");
    const m = /^\/r\/([0-9a-f]{32})$/.exec(url.pathname);
    const role = url.searchParams.get("role");
    const key = req.headers["sec-websocket-key"];
    if (!m || (role !== "bridge" && role !== "phone") || !key) { sock.end("HTTP/1.1 400 Bad Request\r\n\r\n"); return; }
    const accept = crypto.createHash("sha1").update(key + GUID).digest("base64");
    sock.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
    sock.setNoDelay(true);
    open.add(sock); sock.on("close", () => open.delete(sock));
    const room = rooms.get(m[1]) ?? rooms.set(m[1], {}).get(m[1]);
    const send = (o) => sock.writable && sock.write(encode(1, typeof o === "string" ? o : JSON.stringify(o)));
    const me = { sock, send };
    if (room[role]) { room[role].send({ type: "replaced" }); room[role].sock.end(encode(8, Buffer.from([0x0f, 0xa0]))); }
    room[role] = me;
    send({ type: "peer", up: !!room[other(role)] });
    room[other(role)]?.send({ type: "peer", up: true });
    const state = { chunks: [], op: 1 };
    let buf = Buffer.alloc(0);
    sock.on("data", (d) => {
      buf = Buffer.concat([buf, d]);
      const r = parse(buf, state);
      if (r.error) return sock.destroy();
      buf = r.rest;
      for (const f of r.frames) {
        if (f.opcode === 8) { sock.end(encode(8, f.payload)); return; }
        if (f.opcode === 9) { sock.write(encode(10, f.payload)); continue; }
        if (f.opcode !== 1) continue;
        const text = f.payload.toString("utf8");
        if (text === "ping") { send("pong"); continue; }
        if (text.length > MAX_FRAME) return sock.destroy();
        room[other(role)]?.send(text);
      }
    });
    const gone = () => {
      if (room[role] === me) { delete room[role]; room[other(role)]?.send({ type: "peer", up: false }); }
    };
    sock.on("close", gone);
    sock.on("error", () => {});
  });
  return new Promise((res, rej) => {
    server.once("error", rej);
    server.listen(port, host, () => res({ port: server.address().port, close: () => new Promise((r) => { for (const s of open) s.destroy(); server.closeAllConnections?.(); server.close(r); }) }));
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const port = Number(process.argv[2] ?? 8787);
  const r = await startDevRelay(port, "0.0.0.0");
  console.log(`dev relay on ws://0.0.0.0:${r.port}  (no TLS: for a trusted network only)`);
}
