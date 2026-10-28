import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { ensureDir } from "./common.mjs";

const file = (dir) => path.join(dir, "pairing.json");
const read = (dir) => { try { return JSON.parse(fs.readFileSync(file(dir), "utf8")); } catch { return null; } };
const write = (dir, o) => {
  ensureDir(dir);
  const tmp = `${file(dir)}.tmp-${process.pid}`;
  fs.writeFileSync(tmp, JSON.stringify(o), { mode: 0o600 });
  fs.chmodSync(tmp, 0o600);
  fs.renameSync(tmp, file(dir));
};

export function loadToken(dir) {
  const t = read(dir)?.token;
  return typeof t === "string" && /^[A-Za-z0-9_-]{24,128}$/.test(t) ? t : null;
}

// A fresh token is not paired yet: the bridge offers a 6-digit code until a phone uses it (or connects with hello).
export function newToken(dir) {
  const token = crypto.randomBytes(24).toString("base64url"); // 32 chars
  write(dir, { token, createdAt: Date.now() });
  return token;
}

export function revokeToken(dir) {
  const had = fs.existsSync(file(dir));
  fs.rmSync(file(dir), { force: true });
  return had;
}

// paired = a phone has received this token (by code or by the link). Then no code is offered until --new-pairing.
export const isPaired = (dir) => loadToken(dir) !== null && typeof read(dir)?.pairedAt === "number";

export function markPaired(dir) {
  const o = read(dir);
  if (!o || loadToken(dir) === null || typeof o.pairedAt === "number") return;
  write(dir, { ...o, pairedAt: Date.now() });
}

export function tokenMatches(given, expected) {
  if (typeof given !== "string" || typeof expected !== "string") return false;
  const a = Buffer.from(given), b = Buffer.from(expected);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

// docs/AGENTS-V2.md section 2: HMAC-SHA256(key = UTF-8 code, data = "bubby-pair-v1|" + fpSeen + "|" + nonce), lowercase hex.
export const pairProof = (code, fpSeen, nonce) =>
  crypto.createHmac("sha256", Buffer.from(String(code), "utf8")).update(`bubby-pair-v1|${fpSeen}|${nonce}`, "utf8").digest("hex");

export const CODE_TTL_MS = 5 * 60_000;
export const MAX_FAILS = 5;
export const FAIL_WINDOW_MS = 60_000;

// The 6-digit one-use code. onCode(code, reason) fires for every new code: "start", "failed" (burnt), "expired", "new".
// The code itself is only handed to onCode (the terminal), never to a log.
export function createCodePairing({ fp, ttlMs = CODE_TTL_MS, maxFails = MAX_FAILS, windowMs = FAIL_WINDOW_MS, onCode = () => {}, now = Date.now }) {
  let code = null, timer = null;
  const fails = [];
  const issue = (reason) => {
    let c;
    do c = String(crypto.randomInt(0, 1_000_000)).padStart(6, "0"); while (c === code);
    code = c;
    clearTimeout(timer);
    timer = setTimeout(() => issue("expired"), ttlMs);
    timer.unref?.();
    onCode(code, reason);
  };
  const stop = () => { clearTimeout(timer); timer = null; code = null; };
  return {
    start(reason = "start") { if (!code) issue(reason); },
    stop,
    current: () => code,
    // m is the phone's {"type":"pair",...}. Returns "ok" | "bad_code" | "slow_down" | "not_pairing".
    attempt(m) {
      if (!code) return "not_pairing";
      const t = now();
      while (fails.length && t - fails[0] >= windowMs) fails.shift();
      if (fails.length >= maxFails) return "slow_down";
      const nonce = typeof m?.nonce === "string" && /^[0-9a-f]{32}$/.test(m.nonce) ? m.nonce : null;
      const proof = typeof m?.proof === "string" && /^[0-9a-f]{64}$/.test(m.proof) ? m.proof : "0".repeat(64);
      const expected = pairProof(code, fp, nonce ?? "");
      const match = crypto.timingSafeEqual(Buffer.from(proof, "utf8"), Buffer.from(expected, "utf8"));
      if (match && nonce && m.v === 1) { stop(); return "ok"; }
      fails.push(t);
      issue("failed"); // a failed attempt burns the code
      return "bad_code";
    },
  };
}
