// End-to-end encryption for the relay (docs/RELAY.md). The relay only ever sees `1.` + base64url(iv || ciphertext || tag).
import crypto from "node:crypto";

export const ROOM_BYTES = 16;
const MAX_SKEW_MS = 5 * 60_000;

export const newRelayKey = () => crypto.randomBytes(32).toString("base64url");

const expand = (rk, label) => crypto.createHmac("sha256", rk).update(Buffer.concat([Buffer.from(label), Buffer.from([1])])).digest();

// rk (32 random bytes, base64url) -> { room: 32 hex chars used in the relay URL, key: AES-256 key }
export function derive(rkText) {
  const rk = Buffer.from(String(rkText), "base64url");
  if (rk.length !== 32) throw new Error("relay key must be 32 bytes");
  return { room: expand(rk, "bubby-relay-room").subarray(0, ROOM_BYTES).toString("hex"), key: expand(rk, "bubby-relay-enc") };
}

// dir: "b2p" (bridge to phone) or "p2b"; it is the AAD, so a frame cannot be turned around and sent back
export function seal(key, dir, obj, rnd = crypto.randomBytes(12)) {
  const c = crypto.createCipheriv("aes-256-gcm", key, rnd);
  c.setAAD(Buffer.from(dir));
  const ct = Buffer.concat([c.update(JSON.stringify(obj), "utf8"), c.final()]);
  return "1." + Buffer.concat([rnd, ct, c.getAuthTag()]).toString("base64url");
}

export function open(key, dir, text) {
  if (typeof text !== "string" || !text.startsWith("1.") || text.length > 700_000) return null;
  try {
    const b = Buffer.from(text.slice(2), "base64url");
    if (b.length < 12 + 16) return null;
    const d = crypto.createDecipheriv("aes-256-gcm", key, b.subarray(0, 12));
    d.setAAD(Buffer.from(dir));
    d.setAuthTag(b.subarray(b.length - 16));
    const obj = JSON.parse(Buffer.concat([d.update(b.subarray(12, b.length - 16)), d.final()]).toString("utf8"));
    return obj && typeof obj === "object" ? obj : null;
  } catch { return null; }
}

// Replay protection: a message must be fresh (clock within 5 minutes) and its counter must grow within its sender's boot id.
export function createReceiver(now = () => Date.now()) {
  const boots = new Map(); // boot id -> highest seq seen
  return {
    accept(m) {
      if (!m || typeof m.b !== "string" || m.b.length > 32 || !Number.isSafeInteger(m.s) || !Number.isFinite(m.t)) return false;
      if (Math.abs(now() - m.t) > MAX_SKEW_MS) return false;
      const last = boots.get(m.b);
      if (last !== undefined && m.s <= last) return false;
      boots.set(m.b, m.s);
      if (boots.size > 16) boots.delete(boots.keys().next().value);
      return true;
    },
  };
}

// Builds the plaintext of one frame; `line` is one protocol line without its newline, `x: 1` marks "this connection ended".
export function createSender(key, dir, now = () => Date.now()) {
  const b = crypto.randomBytes(6).toString("hex");
  let s = 0;
  return {
    boot: b,
    line: (line) => seal(key, dir, { t: now(), s: ++s, b, l: line }),
    end: () => seal(key, dir, { t: now(), s: ++s, b, l: "", x: 1 }),
  };
}
