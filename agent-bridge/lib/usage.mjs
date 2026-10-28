// Claude plan limits for the phone's dashboard (docs/AGENTS-V2.md section 3), best effort.
// Reads Claude Code's OAuth token and asks the (undocumented) usage endpoint Claude Code itself uses.
// The token stays here: it is never logged, never put in an error message and never sent to the phone.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import https from "node:https";
import { execFile } from "node:child_process";

export const USAGE_URL = "https://api.anthropic.com/api/oauth/usage";
export const MIN_INTERVAL_MS = 60_000; // at most one call a minute
const MAX_BACKOFF_MS = 30 * 60_000;

const claudeDir = () => process.env.CLAUDE_CONFIG_DIR || path.join(os.homedir(), ".claude");

// {"claudeAiOauth":{"accessToken","expiresAt","subscriptionType",...}} => {accessToken, expiresAt, plan} or null
export function parseCredentials(text) {
  let o;
  try { o = JSON.parse(text); } catch { return null; }
  const c = o?.claudeAiOauth;
  if (!c || typeof c.accessToken !== "string" || !c.accessToken) return null;
  return {
    accessToken: c.accessToken,
    expiresAt: typeof c.expiresAt === "number" ? c.expiresAt : null,
    plan: typeof c.subscriptionType === "string" ? c.subscriptionType : null,
  };
}

// macOS: keychain item "Claude Code-credentials" (via `security`), then the file; elsewhere ~/.claude/.credentials.json.
export async function readCredentials({ platform = process.platform } = {}) {
  if (platform === "darwin") {
    const out = await new Promise((res) => {
      try {
        execFile("security", ["find-generic-password", "-s", "Claude Code-credentials", "-w"], { timeout: 5000 }, (err, stdout) => res(err ? null : stdout));
      } catch { res(null); }
    });
    const c = out ? parseCredentials(out.trim()) : null;
    if (c) return c;
  }
  try { return parseCredentials(fs.readFileSync(path.join(claudeDir(), ".credentials.json"), "utf8")); } catch { return null; }
}

// Default HTTP call: GET with headers, resolves {status, body}. Injectable for tests.
export function httpsGet(url, headers, timeoutMs = 10_000) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { headers, timeout: timeoutMs }, (res) => {
      let body = "";
      res.setEncoding("utf8");
      res.on("data", (d) => { body += d; if (body.length > 256 * 1024) req.destroy(new Error("response too large")); });
      res.on("end", () => resolve({ status: res.statusCode, body }));
      res.on("error", reject);
    });
    req.on("timeout", () => req.destroy(Object.assign(new Error("timeout"), { code: "ETIMEDOUT" })));
    req.on("error", reject);
  });
}

const toMs = (v) => {
  if (typeof v === "string" && v) { const t = Date.parse(v); return Number.isFinite(t) ? t : undefined; }
  if (typeof v === "number" && Number.isFinite(v) && v > 0) return v < 1e12 ? Math.round(v * 1000) : Math.round(v);
  return undefined;
};

// {utilization: percent, resets_at} => {used: 0..1, resetsAt: epoch ms}; any part may be missing.
function window(w) {
  if (!w || typeof w !== "object") return undefined;
  const out = {};
  if (typeof w.utilization === "number" && Number.isFinite(w.utilization)) out.used = Math.min(1, Math.max(0, Math.round(w.utilization * 100) / 10000));
  const r = toMs(w.resets_at);
  if (r !== undefined) out.resetsAt = r;
  return Object.keys(out).length ? out : undefined;
}

export function parseUsage(o) {
  const out = {};
  const f = window(o?.five_hour), s = window(o?.seven_day);
  if (f) out.fiveHour = f;
  if (s) out.sevenDay = s;
  return out;
}

const cleanPlan = (p) => (typeof p === "string" && /^[A-Za-z0-9_-]{1,32}$/.test(p) ? p.toLowerCase() : undefined);

// refresh() calls the endpoint if allowed (at most every minIntervalMs, longer after errors) and resolves the snapshot.
// snapshot() => {fiveHour?, sevenDay?, plan?}: whitelisted fields only. If the last call failed the limits are absent.
export function createUsageSource({ httpGet = httpsGet, readCredentials: readCreds = readCredentials, now = Date.now, log = () => {}, minIntervalMs = MIN_INTERVAL_MS } = {}) {
  let remote = {}, plan, nextAt = 0, backoff = 0, inflight = null, failing = false;
  const snapshot = () => ({ ...remote, ...(plan ? { plan } : {}) });
  const failed = (reason) => {
    remote = {};
    backoff = backoff ? Math.min(backoff * 2, MAX_BACKOFF_MS) : 2 * minIntervalMs;
    nextAt = now() + backoff;
    if (!failing) log(`лимиты Claude недоступны (${reason}), попробую позже`);
    failing = true;
  };
  const run = async () => {
    let creds = null;
    try { creds = await readCreds(); } catch {}
    plan = cleanPlan(creds?.plan);
    if (!creds?.accessToken) return failed("нет OAuth-токена Claude Code");
    if (creds.expiresAt && creds.expiresAt < now()) return failed("токен истёк, Claude Code обновит его сам");
    let r;
    try {
      r = await httpGet(USAGE_URL, {
        Authorization: `Bearer ${creds.accessToken}`,
        "anthropic-beta": "oauth-2025-04-20",
        Accept: "application/json",
        "User-Agent": "bubby-agent-bridge",
      });
    } catch (e) { return failed(typeof e?.code === "string" ? e.code : "сеть"); } // never e.message: keep anything request-related out of logs
    if (!r || r.status !== 200) return failed(`HTTP ${Number(r?.status) || "?"}`);
    let body;
    try { body = JSON.parse(r.body); } catch { return failed("не JSON"); }
    remote = parseUsage(body);
    backoff = 0;
    nextAt = now() + minIntervalMs;
    if (failing) log("лимиты Claude снова доступны");
    failing = false;
  };
  return {
    snapshot,
    refresh() {
      if (inflight) return inflight;
      if (now() < nextAt) return Promise.resolve(snapshot());
      nextAt = now() + minIntervalMs;
      inflight = run().catch(() => failed("ошибка")).then(() => { inflight = null; return snapshot(); });
      return inflight;
    },
  };
}
