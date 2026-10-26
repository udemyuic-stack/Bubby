import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { ensureDir } from "./common.mjs";

const file = (dir) => path.join(dir, "pairing.json");

export function loadToken(dir) {
  try {
    const t = JSON.parse(fs.readFileSync(file(dir), "utf8")).token;
    return typeof t === "string" && /^[A-Za-z0-9_-]{24,128}$/.test(t) ? t : null;
  } catch { return null; }
}

export function newToken(dir) {
  ensureDir(dir);
  const token = crypto.randomBytes(24).toString("base64url"); // 32 chars
  fs.writeFileSync(file(dir), JSON.stringify({ token, createdAt: Date.now() }), { mode: 0o600 });
  fs.chmodSync(file(dir), 0o600);
  return token;
}

export function revokeToken(dir) {
  const had = fs.existsSync(file(dir));
  fs.rmSync(file(dir), { force: true });
  return had;
}

export function tokenMatches(given, expected) {
  if (typeof given !== "string" || typeof expected !== "string") return false;
  const a = Buffer.from(given), b = Buffer.from(expected);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
