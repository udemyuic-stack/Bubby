// Minimal unified-style line diff (LCS), enough for a settings file.
export function unifiedDiff(a, b, nameA, nameB, ctx = 3) {
  const x = a.split("\n"), y = b.split("\n");
  const n = x.length, m = y.length;
  const L = Array.from({ length: n + 1 }, () => new Uint32Array(m + 1));
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--)
    L[i][j] = x[i] === y[j] ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
  const ops = [];
  let i = 0, j = 0;
  while (i < n && j < m) {
    if (x[i] === y[j]) { ops.push([" ", x[i]]); i++; j++; }
    else if (L[i + 1][j] >= L[i][j + 1]) ops.push(["-", x[i++]]);
    else ops.push(["+", y[j++]]);
  }
  while (i < n) ops.push(["-", x[i++]]);
  while (j < m) ops.push(["+", y[j++]]);
  if (!ops.some((o) => o[0] !== " ")) return "";
  const keep = new Array(ops.length).fill(false);
  ops.forEach((o, k) => { if (o[0] !== " ") for (let d = -ctx; d <= ctx; d++) if (ops[k + d]) keep[k + d] = true; });
  const lines = [`--- ${nameA}`, `+++ ${nameB}`];
  let k = 0;
  while (k < ops.length) {
    if (!keep[k]) { k++; continue; }
    let e = k, la = 0, lb = 0;
    while (e < ops.length && keep[e]) { if (ops[e][0] !== "+") la++; if (ops[e][0] !== "-") lb++; e++; }
    let sa = 1, sb = 1;
    for (let q = 0; q < k; q++) { if (ops[q][0] !== "+") sa++; if (ops[q][0] !== "-") sb++; }
    lines.push(`@@ -${sa},${la} +${sb},${lb} @@`);
    for (let q = k; q < e; q++) lines.push(ops[q][0] + ops[q][1]);
    k = e;
  }
  return lines.join("\n");
}
