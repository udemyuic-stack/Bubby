import tls from "node:tls";
import crypto from "node:crypto";
import { pairProof } from "./pairing.mjs";

// A phone for tests and smoke runs: TLS with the certificate pinned by SHA-256, newline JSON.
// v1: pinned fp + token => hello. v2: pair = {code, name?, fpSeen?, nonce?} => "pair" message, trust on first use
// (fp may be omitted; fpSeen overrides the fingerprint put into the proof, to simulate a phone that saw another certificate).
// v3: caps = ["details","answers","diffs"] in hello/pair; answer(fingerprint, picks); getDiff(pillId, fileId) => all parts.
export function fakePhone({ host, port, fp, token, v = 1, onMessage, autoHello = true, pair, caps }) {
  return new Promise((resolve, reject) => {
    const msgs = [];
    const waiters = [];
    const sock = tls.connect({
      host, port, rejectUnauthorized: false, minVersion: "TLSv1.2",
    });
    const send = (o) => sock.write(JSON.stringify(o) + "\n");
    const wait = (pred, ms = 3000) => new Promise((res, rej) => {
      const hit = msgs.find(pred);
      if (hit) return res(hit);
      const w = { pred, res };
      waiters.push(w);
      setTimeout(() => { waiters.splice(waiters.indexOf(w), 1); rej(new Error("timeout waiting for message")); }, ms);
    });
    let buf = "";
    sock.on("data", (d) => {
      buf += d.toString("utf8");
      let i;
      while ((i = buf.indexOf("\n")) >= 0) {
        const m = JSON.parse(buf.slice(0, i));
        buf = buf.slice(i + 1);
        msgs.push(m);
        onMessage?.(m);
        for (const w of [...waiters]) if (w.pred(m)) { waiters.splice(waiters.indexOf(w), 1); w.res(m); }
      }
    });
    const closed = new Promise((r) => sock.on("close", r));
    sock.on("error", (e) => { reject(e); });
    sock.on("secureConnect", () => {
      const got = crypto.createHash("sha256").update(sock.getPeerCertificate().raw).digest("hex");
      if (fp && got !== fp) { sock.destroy(); return reject(new Error("certificate pin mismatch")); }
      if (pair) {
        const nonce = pair.nonce ?? crypto.randomBytes(16).toString("hex");
        const proof = pairProof(pair.code, pair.fpSeen ?? got, nonce);
        send({ type: "pair", v: 1, nonce, proof, name: pair.name ?? "fake-phone", ...(caps && { caps }) });
      } else if (autoHello) send({ type: "hello", v, token, device: "fake-phone", caps: caps ?? [] });
      const answer = (fingerprint, picks) => send({ type: "answer", fingerprint, picks });
      // resolves the "diff" parts for this file, in order
      const getDiff = async (pillId, fileId, ms = 3000) => {
        const seen = msgs.length;
        send({ type: "getDiff", pillId, fileId });
        const first = await wait((m) => m.type === "diff" && m.fileId === fileId && m.part === 0 && msgs.indexOf(m) >= seen, ms);
        const parts = [first];
        for (let i = 1; i < first.parts; i++) parts.push(await wait((m) => m.type === "diff" && m.fileId === fileId && m.part === i && msgs.indexOf(m) >= seen, ms));
        return parts;
      };
      resolve({ sock, send, wait, msgs, closed, fpSeen: got, answer, getDiff, close: () => sock.destroy() });
    });
  });
}

// Pairs by code and resolves with the reply: {type:"paired", token, name} or {type:"error", error}.
export async function pairByCode({ host, port, code, caps, ...rest }) {
  const p = await fakePhone({ host, port, caps, pair: { code, ...rest } });
  const reply = await p.wait((m) => m.type === "paired" || m.type === "error");
  return { phone: p, reply };
}

// A phone that comes in through the relay (docs/RELAY.md): WebSocket + end-to-end encryption, same interface as fakePhone.
export async function fakeRelayPhone({ url, rk, token, caps = [], autoHello = true, WebSocket: WS = globalThis.WebSocket }) {
  const { derive, createSender, createReceiver, open } = await import("./relay-crypto.mjs");
  const { room, key } = derive(rk);
  const sender = createSender(key, "p2b");
  const recv = createReceiver();
  const msgs = [], waiters = [];
  const ws = new WS(`${url.replace(/\/$/, "")}/r/${room}?role=phone`);
  const control = [];
  const wait = (pred, ms = 3000) => new Promise((res, rej) => {
    const hit = msgs.find(pred);
    if (hit) return res(hit);
    const w = { pred, res };
    waiters.push(w);
    setTimeout(() => { waiters.splice(waiters.indexOf(w), 1); rej(new Error("timeout waiting for message")); }, ms);
  });
  ws.onmessage = (ev) => {
    const text = String(ev.data);
    if (text.startsWith("{")) { control.push(JSON.parse(text)); return; }
    const m = open(key, "b2p", text);
    if (!m || !recv.accept(m)) return;
    if (m.x) { msgs.push({ type: "__closed" }); return; }
    const j = JSON.parse(m.l);
    msgs.push(j);
    for (const w of [...waiters]) if (w.pred(j)) { waiters.splice(waiters.indexOf(w), 1); w.res(j); }
  };
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = () => rej(new Error("relay connect failed")); });
  const send = (o) => ws.send(sender.line(JSON.stringify(o)));
  if (autoHello) send({ type: "hello", v: 1, token, device: "fake-phone", caps });
  return { send, wait, msgs, control, raw: (t) => ws.send(t), sender, close: () => ws.close() };
}
