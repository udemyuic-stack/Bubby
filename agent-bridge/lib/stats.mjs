// Today's counters for the phone's dashboard, kept in <BUBBY_DIR>/stats.json (0600) so they survive restarts.
// doneToday: Claude Code turns finished today (local day), counted on the Stop hook.
// tokensToday: input + output + cache-creation tokens of today's assistant messages, read from the transcript
// path the Stop hook gives (cache reads are not counted). Only reported once a transcript was actually read today.
import fs from "node:fs";
import path from "node:path";
import { ensureDir } from "./common.mjs";

const MAX_TRANSCRIPTS = 200;
const MAX_CHUNK = 64 * 1024 * 1024;

export const localDay = (d = new Date()) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

const num = (v) => (typeof v === "number" && Number.isFinite(v) && v >= 0 ? v : 0);

export function createStats(dir, { now = () => new Date(), onChange = () => {} } = {}) {
  const file = path.join(dir, "stats.json");
  let st = load();
  let chain = Promise.resolve();

  function load() {
    try {
      const o = JSON.parse(fs.readFileSync(file, "utf8"));
      return {
        day: typeof o.day === "string" ? o.day : "",
        doneToday: num(o.doneToday),
        tokensToday: num(o.tokensToday),
        tokensSeen: o.tokensSeen === true,
        transcripts: o.transcripts && typeof o.transcripts === "object" && !Array.isArray(o.transcripts) ? o.transcripts : {},
      };
    } catch { return { day: "", doneToday: 0, tokensToday: 0, tokensSeen: false, transcripts: {} }; }
  }
  function save() {
    try {
      ensureDir(dir);
      const tmp = `${file}.tmp-${process.pid}`;
      fs.writeFileSync(tmp, JSON.stringify(st), { mode: 0o600 });
      fs.renameSync(tmp, file);
    } catch {}
  }
  function roll() {
    const today = localDay(now());
    if (st.day === today) return false;
    st = { day: today, doneToday: 0, tokensToday: 0, tokensSeen: false, transcripts: st.transcripts };
    return true;
  }

  // Reads the new complete lines of a transcript since the last offset; counts only today's assistant messages.
  async function readTranscript(p) {
    let size;
    try { const s = await fs.promises.stat(p); if (!s.isFile()) return; size = s.size; } catch { return; }
    const prev = st.transcripts[p] ?? {};
    let start = num(prev.offset);
    if (start > size) start = 0; // truncated or replaced
    let skipFirst = false;
    if (size - start > MAX_CHUNK) { start = size - MAX_CHUNK; skipFirst = true; }
    if (start === size) return;
    const today = localDay(now());
    let lastId = typeof prev.lastId === "string" ? prev.lastId : null;
    let lastCount = num(prev.lastCount);
    let added = 0;
    const chunk = Buffer.alloc(size - start);
    const fh = await fs.promises.open(p, "r");
    try {
      let got = 0;
      while (got < chunk.length) {
        const { bytesRead } = await fh.read(chunk, got, chunk.length - got, start + got);
        if (!bytesRead) break;
        got += bytesRead;
      }
    } finally { await fh.close(); }
    const consumed = chunk.lastIndexOf(0x0a) + 1; // only complete lines; a line being written is read next time
    if (consumed === 0) return;
    const lines = chunk.subarray(0, consumed).toString("utf8").split("\n");
    lines.pop();
    if (skipFirst) lines.shift();
    for (const line of lines) {
      let o;
      try { o = JSON.parse(line); } catch { continue; }
      const u = o?.message?.usage;
      if (o?.type !== "assistant" || !u || typeof o.timestamp !== "string") continue;
      const t = new Date(o.timestamp);
      if (Number.isNaN(t.getTime()) || localDay(t) !== today) continue;
      const total = num(u.input_tokens) + num(u.output_tokens) + num(u.cache_creation_input_tokens);
      const id = typeof o.message.id === "string" ? o.message.id : null;
      // one message is written as several lines (one per content block) carrying the same usage
      if (id && id === lastId) { if (total > lastCount) { added += total - lastCount; lastCount = total; } continue; }
      added += total;
      lastId = id;
      lastCount = total;
    }
    delete st.transcripts[p];
    st.transcripts[p] = { offset: start + consumed, lastId, lastCount };
    const keys = Object.keys(st.transcripts);
    for (const k of keys.slice(0, Math.max(0, keys.length - MAX_TRANSCRIPTS))) delete st.transcripts[k];
    roll();
    st.tokensToday += added;
    st.tokensSeen = true;
  }

  return {
    // {doneToday, tokensToday?}
    today() {
      if (roll()) save();
      return { doneToday: st.doneToday, ...(st.tokensSeen ? { tokensToday: st.tokensToday } : {}) };
    },
    // A finished turn. Resolves once the transcript (if any) has been read.
    recordStop(transcriptPath) {
      roll();
      st.doneToday += 1;
      save();
      onChange();
      if (typeof transcriptPath !== "string" || !path.isAbsolute(transcriptPath) || !transcriptPath.endsWith(".jsonl")) return chain;
      chain = chain.then(() => readTranscript(transcriptPath)).catch(() => {}).then(() => { save(); onChange(); });
      return chain;
    },
    flush: () => chain,
  };
}
