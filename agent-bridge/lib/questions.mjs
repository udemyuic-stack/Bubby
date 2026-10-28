// Claude Code's AskUserQuestion, offered to the phone (docs/AGENTS-V3.md section 2).
// Nothing is ever cut: a question outside the limits stays on the computer, because the answer must carry the exact
// text and labels back. Nothing is answered without picks a person sent.

export const LIMITS = { questions: 4, options: 8, question: 500, label: 120, description: 300 };
const len = (s) => Array.from(s).length;

// tool_input.questions => [{question, multiSelect, options: [{label, description}]}], or null when it may not be offered.
export function offerable(questions) {
  if (!Array.isArray(questions) || questions.length === 0 || questions.length > LIMITS.questions) return null;
  const out = [];
  const texts = new Set();
  for (const q of questions) {
    if (!q || typeof q !== "object" || typeof q.question !== "string" || !q.question.trim()) return null;
    if (len(q.question) > LIMITS.question || texts.has(q.question)) return null; // answers are keyed by the text
    texts.add(q.question);
    if (q.multiSelect !== undefined && typeof q.multiSelect !== "boolean") return null;
    if (!Array.isArray(q.options) || q.options.length === 0 || q.options.length > LIMITS.options) return null;
    const labels = new Set();
    const options = [];
    for (const o of q.options) {
      if (!o || typeof o !== "object" || typeof o.label !== "string" || !o.label || len(o.label) > LIMITS.label) return null;
      if (o.description !== undefined && (typeof o.description !== "string" || len(o.description) > LIMITS.description)) return null;
      if (labels.has(o.label)) return null;
      labels.add(o.label);
      options.push({ label: o.label, description: o.description ?? "" });
    }
    out.push({ question: q.question, multiSelect: q.multiSelect === true, options });
  }
  return out;
}

// The phone's picks (one list of labels per question, in order) => the answers map Claude Code takes
// ({question text: label} or {question text: [labels]} for a multiple choice), or null if they do not match exactly.
export function answersFromPicks(questions, picks) {
  if (!Array.isArray(picks) || picks.length !== questions.length) return null;
  const answers = {};
  for (let i = 0; i < questions.length; i++) {
    const q = questions[i], p = picks[i];
    if (!Array.isArray(p) || p.length === 0 || !p.every((x) => typeof x === "string")) return null;
    const labels = new Set(q.options.map((o) => o.label));
    if (!p.every((x) => labels.has(x)) || new Set(p).size !== p.length) return null;
    if (q.multiSelect) answers[q.question] = [...p];
    else if (p.length === 1) answers[q.question] = p[0];
    else return null;
  }
  return answers;
}

// hook.mjs checks again, against the input Claude Code really sent, before printing anything.
export function answersFit(input, answers) {
  const qs = input?.questions;
  if (!Array.isArray(qs) || qs.length === 0 || !answers || typeof answers !== "object" || Array.isArray(answers)) return false;
  if (Object.keys(answers).length !== qs.length) return false;
  return qs.every((q) => {
    if (typeof q?.question !== "string" || !Object.hasOwn(answers, q.question)) return false;
    const labels = (Array.isArray(q.options) ? q.options : []).map((o) => o?.label).filter((l) => typeof l === "string");
    const a = answers[q.question];
    if (q.multiSelect === true) return Array.isArray(a) && a.length > 0 && new Set(a).size === a.length && a.every((x) => typeof x === "string" && labels.includes(x));
    return typeof a === "string" && labels.includes(a);
  });
}

// The documented PermissionRequest output that answers AskUserQuestion: the same input plus `answers`.
export function answerOutput(input, answers) {
  if (!answersFit(input, answers)) return null;
  return { hookSpecificOutput: { hookEventName: "PermissionRequest", decision: { behavior: "allow", updatedInput: { ...input, answers } } } };
}
