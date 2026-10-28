import tls from "node:tls";
import crypto from "node:crypto";
import { pairProof } from "./pairing.mjs";

// A phone for tests and smoke runs: TLS with the certificate pinned by SHA-256, newline JSON.
// v1: pinned fp + token => hello. v2: pair = {code, name?, fpSeen?, nonce?} => "pair" message, trust on first use
// (fp may be omitted; fpSeen overrides the fingerprint put into the proof, to simulate a phone that saw another certificate).
export function fakePhone({ host, port, fp, token, v = 1, onMessage, autoHello = true, pair }) {
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
        send({ type: "pair", v: 1, nonce, proof, name: pair.name ?? "fake-phone" });
      } else if (autoHello) send({ type: "hello", v, token, device: "fake-phone", caps: [] });
      resolve({ sock, send, wait, msgs, closed, fpSeen: got, close: () => sock.destroy() });
    });
  });
}

// Pairs by code and resolves with the reply: {type:"paired", token, name} or {type:"error", error}.
export async function pairByCode({ host, port, code, ...rest }) {
  const p = await fakePhone({ host, port, pair: { code, ...rest } });
  const reply = await p.wait((m) => m.type === "paired" || m.type === "error");
  return { phone: p, reply };
}
