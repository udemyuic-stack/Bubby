import tls from "node:tls";
import crypto from "node:crypto";

// A phone for tests and smoke runs: TLS with the certificate pinned by SHA-256, newline JSON.
export function fakePhone({ host, port, fp, token, v = 1, onMessage, autoHello = true }) {
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
      if (got !== fp) { sock.destroy(); return reject(new Error("certificate pin mismatch")); }
      if (autoHello) send({ type: "hello", v, token, device: "fake-phone", caps: [] });
      resolve({ sock, send, wait, msgs, closed, close: () => sock.destroy() });
    });
  });
}
