// The lines a file edit changed, from the tool input the hooks see (docs/AGENTS-V3.md section 4).
// In memory only. Lines are [kind, text] with kind "+", "-", " " or "@" ("@@ <line in the edited text>").
import path from "node:path";

const MAX_BYTES = 200 * 1024;
const MAX_LINES = 4000;
const MAX_CELLS = 1_000_000;
const CONTEXT = 3;
export const KEEP_LINES = 201; // the phone gets 200 at most; one more says "there were more"

export const fileName = (p) => (typeof p === "string" ? path.basename(p.replaceAll("\\", "/")) : "");

const split = (t) => {
  const parts = t.replace(/\r\n/g, "\n").split("\n");
  if (parts[parts.length - 1] === "") parts.pop();
  return parts;
};
const countLines = (t) => (t ? split(t).length : 0);

// LCS line diff with CONTEXT lines around each change => {added, removed, lines} or {added, removed, tooLarge}
export function diffText(oldText, newText) {
  if (Buffer.byteLength(oldText) + Buffer.byteLength(newText) > MAX_BYTES) return { added: countLines(newText), removed: countLines(oldText), tooLarge: true };
  const a = split(oldText), b = split(newText);
  if (a.length + b.length > MAX_LINES || a.length * b.length > MAX_CELLS) return { added: b.length, removed: a.length, tooLarge: true };
  const n = a.length, m = b.length, w = m + 1;
  const L = new Uint16Array((n + 1) * w);
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--)
    L[i * w + j] = a[i] === b[j] ? L[(i + 1) * w + j + 1] + 1 : Math.max(L[(i + 1) * w + j], L[i * w + j + 1]);
  const ops = []; // [kind, text, newLineNo]
  let i = 0, j = 0;
  while (i < n && j < m) {
    if (a[i] === b[j]) { ops.push([" ", a[i], j + 1]); i++; j++; }
    else if (L[(i + 1) * w + j] >= L[i * w + j + 1]) ops.push(["-", a[i++], j + 1]);
    else { ops.push(["+", b[j], j + 1]); j++; }
  }
  while (i < n) ops.push(["-", a[i++], j + 1]);
  while (j < m) { ops.push(["+", b[j], j + 1]); j++; }
  let added = 0, removed = 0;
  const keep = new Array(ops.length).fill(false);
  ops.forEach((o, k) => {
    if (o[0] === " ") return;
    if (o[0] === "+") added++; else removed++;
    for (let d = -CONTEXT; d <= CONTEXT; d++) if (ops[k + d]) keep[k + d] = true;
  });
  const lines = [];
  for (let k = 0; k < ops.length; k++) {
    if (!keep[k]) continue;
    if (k === 0 || !keep[k - 1]) lines.push(["@", `@@ ${ops[k][2]}`]);
    lines.push([ops[k][0], ops[k][1]]);
    if (lines.length >= KEEP_LINES) break;
  }
  return { added, removed, lines };
}

// Edit / MultiEdit / Write tool input => {name, added, removed, tooLarge?, isNew?, lines} or null (not a change).
// hook.mjs may have replaced texts that were too big with {tooLarge, added, removed}.
export function buildFileDiff(tool, input) {
  const i = input && typeof input === "object" ? input : {};
  const name = fileName(i.file_path).slice(0, 80);
  if (!name) return null;
  if (i.tooLarge === true) {
    const added = Math.max(0, Number(i.added) | 0), removed = Math.max(0, Number(i.removed) | 0);
    return added || removed ? { name, added, removed, tooLarge: true, ...(tool === "Write" && { isNew: true }), lines: [] } : null;
  }
  const str = (v) => (typeof v === "string" ? v : null);
  let edits;
  if (tool === "Edit") edits = [[str(i.old_string), str(i.new_string)]];
  else if (tool === "MultiEdit") edits = Array.isArray(i.edits) ? i.edits.map((e) => [str(e?.old_string), str(e?.new_string)]) : [];
  else if (tool === "Write") {
    const c = str(i.content);
    if (!c) return null;
    const d = diffText("", c);
    return { name, added: d.added, removed: 0, ...(d.tooLarge && { tooLarge: true }), isNew: true, lines: d.lines ?? [] };
  } else return null;
  let added = 0, removed = 0, tooLarge = false;
  const lines = [];
  for (const [o, nw] of edits) {
    if (o === null || nw === null) continue;
    const d = diffText(o, nw);
    added += d.added;
    removed += d.removed;
    if (d.tooLarge) tooLarge = true;
    else for (const l of d.lines) if (lines.length < KEEP_LINES) lines.push(l);
  }
  if (!added && !removed) return null;
  return { name, added, removed, ...(tooLarge && { tooLarge: true }), lines: tooLarge ? [] : lines };
}
