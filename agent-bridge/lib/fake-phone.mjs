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
