// Plan limits from Claude Code's statusLine payload (docs/AGENTS-V3.md section 1).
// hook.mjs --statusline forwards only `rate_limits`; this turns it into usage.fiveHour/sevenDay and keeps the last good
// value (in <BUBBY_DIR>/limits.json, 0600) until a newer payload arrives. No token, no network.
import fs from "node:fs";
import path from "node:path";
import { ensureDir } from "./common.mjs";

const MAX_AHEAD_MS = 400 * 24 * 3600_000;

// {used_percentage: 0..100 (may exceed), resets_at: epoch seconds} => {used: 0..1, resetsAt?: ms} or undefined
export function parseWindow(w, now = Date.now()) {
  if (!w || typeof w !== "object" || Array.isArray(w)) return undefined;
  const pct = w.used_percentage;
  if (typeof pct !== "number" || !Number.isFinite(pct) || pct < 0) return undefined;
  const out = { used: Math.min(1, Math.round(pct * 100) / 10000) };
  const r = w.resets_at;
  if (typeof r === "number" && Number.isFinite(r) && r > 0) {
    const ms = Math.round(r * 1000);
    if (ms - now <= MAX_AHEAD_MS) out.resetsAt = ms; // further ahead means milliseconds were sent: dropped
  }
  return out;
}

const cleanPlan = (p) => (typeof p === "string" && /^[A-Za-z0-9 _-]{1,20}$/.test(p) ? p.trim().toLowerCase() : undefined);

// rate_limits => {fiveHour?, sevenDay?, plan?} ({} when nothing usable)
export function parseRateLimits(rl, now = Date.now()) {
  if (!rl || typeof rl !== "object" || Array.isArray(rl)) return {};
  const out = {};
  const f = parseWindow(rl.five_hour, now), s = parseWindow(rl.seven_day, now);
  if (f) out.fiveHour = f;
  if (s) out.sevenDay = s;
  const plan = cleanPlan(rl.plan);
  if (plan) out.plan = plan;
  return out;
}

// A window whose reset time has passed counts as 0 used.
const current = (w, now) => (w && w.resetsAt !== undefined && w.resetsAt <= now ? { used: 0, resetsAt: w.resetsAt } : w);

export function createLimits(dir, { now = Date.now } = {}) {
  const file = path.join(dir, "limits.json");
  let last = null; // {fiveHour?, sevenDay?, plan?, updatedAt}
  try {
    const o = JSON.parse(fs.readFileSync(file, "utf8"));
    const t = Number(o.updatedAt);
    const fiveHour = o.fiveHour && typeof o.fiveHour.used === "number" ? o.fiveHour : undefined;
    const sevenDay = o.sevenDay && typeof o.sevenDay.used === "number" ? o.sevenDay : undefined;
    if (Number.isFinite(t) && t > 0 && (fiveHour || sevenDay)) last = { ...(fiveHour && { fiveHour }), ...(sevenDay && { sevenDay }), ...(cleanPlan(o.plan) && { plan: cleanPlan(o.plan) }), updatedAt: t };
  } catch {}
  return {
    // A statusLine payload arrived. Returns true when the numbers changed.
    update(rateLimits) {
      const p = parseRateLimits(rateLimits, now());
      if (!p.fiveHour && !p.sevenDay) return false; // nothing usable: keep the last good value
      const before = last && JSON.stringify({ ...last, updatedAt: 0 });
      last = { ...p, updatedAt: now() };
      try {
        ensureDir(dir);
        const tmp = `${file}.tmp-${process.pid}`;
        fs.writeFileSync(tmp, JSON.stringify(last), { mode: 0o600 });
        fs.renameSync(tmp, file);
      } catch {}
      return before !== JSON.stringify({ ...last, updatedAt: 0 });
    },
    // {fiveHour?, sevenDay?, plan?, updatedAt?}
    snapshot() {
      if (!last) return {};
      const t = now();
      const f = current(last.fiveHour, t), s = current(last.sevenDay, t);
      return { ...(f && { fiveHour: f }), ...(s && { sevenDay: s }), ...(last.plan && { plan: last.plan }), updatedAt: last.updatedAt };
    },
  };
}
