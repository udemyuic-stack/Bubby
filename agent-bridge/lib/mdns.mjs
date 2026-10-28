// Minimal mDNS / DNS-SD responder (RFC 6762/6763) on dgram, no dependencies.
// Advertises one service instance of _bubby-agent._tcp.local. with PTR, SRV, TXT and A records:
// announces on start, answers queries, sends a goodbye (TTL 0) on close.
import dgram from "node:dgram";

export const MDNS_GROUP = "224.0.0.251";
export const MDNS_PORT = 5353;
export const SERVICE = ["_bubby-agent", "_tcp", "local"];
const ENUM = ["_services", "_dns-sd", "_udp", "local"];
export const T = { A: 1, PTR: 12, TXT: 16, AAAA: 28, SRV: 33, ANY: 255 };
const CLASS_IN = 1;
const TTL_HOST = 120; // A, SRV
const TTL_OTHER = 4500; // PTR, TXT

export const toLabels = (name) => (Array.isArray(name) ? name : String(name).replace(/\.$/, "").split(".").filter(Boolean));
const lc = (s) => s.toLowerCase();
export const sameName = (a, b) => {
  const x = toLabels(a), y = toLabels(b);
  return x.length === y.length && x.every((l, i) => lc(l) === lc(y[i]));
};

// Cuts a string to at most max UTF-8 bytes without splitting a character.
export function cutUtf8(s, max) {
  let out = "";
  for (const ch of String(s)) {
    if (Buffer.byteLength(out + ch) > max) break;
    out += ch;
  }
  return out;
}

// ---------- encoding ----------

function encodeName(name) {
  const parts = [];
  for (const l of toLabels(name)) {
    const b = Buffer.from(l, "utf8");
    if (b.length === 0 || b.length > 63) throw new Error("bad DNS label");
    parts.push(Buffer.from([b.length]), b);
  }
  parts.push(Buffer.from([0]));
  return Buffer.concat(parts);
}

function encodeRdata(r) {
  switch (r.type) {
    case T.A: return Buffer.from(String(r.data).split(".").map(Number));
    case T.PTR: return encodeName(r.data);
    case T.TXT: {
      const items = (r.data ?? []).map((s) => (Buffer.isBuffer(s) ? s : Buffer.from(String(s), "utf8")));
      if (!items.length) return Buffer.from([0]);
      return Buffer.concat(items.flatMap((b) => { if (b.length > 255) throw new Error("TXT string too long"); return [Buffer.from([b.length]), b]; }));
    }
    case T.SRV: {
      const h = Buffer.alloc(6);
      h.writeUInt16BE(r.data.priority ?? 0, 0);
      h.writeUInt16BE(r.data.weight ?? 0, 2);
      h.writeUInt16BE(r.data.port, 4);
      return Buffer.concat([h, encodeName(r.data.target)]);
    }
    default: return Buffer.isBuffer(r.data) ? r.data : Buffer.alloc(0);
  }
}

function encodeRecord(r) {
  const rdata = encodeRdata(r);
  const h = Buffer.alloc(10);
  h.writeUInt16BE(r.type, 0);
  h.writeUInt16BE(((r.class ?? CLASS_IN) & 0x7fff) | (r.flush ? 0x8000 : 0), 2);
  h.writeUInt32BE(r.ttl ?? 0, 4);
  h.writeUInt16BE(rdata.length, 8);
  return Buffer.concat([encodeName(r.name), h, rdata]);
}

export function encodePacket({ id = 0, flags = 0x8400, questions = [], answers = [], authorities = [], additionals = [] }) {
  const h = Buffer.alloc(12);
  h.writeUInt16BE(id, 0);
  h.writeUInt16BE(flags, 2);
  h.writeUInt16BE(questions.length, 4);
  h.writeUInt16BE(answers.length, 6);
  h.writeUInt16BE(authorities.length, 8);
  h.writeUInt16BE(additionals.length, 10);
  const qs = questions.map((q) => {
    const t = Buffer.alloc(4);
    t.writeUInt16BE(q.type, 0);
    t.writeUInt16BE(((q.class ?? CLASS_IN) & 0x7fff) | (q.qu ? 0x8000 : 0), 2);
    return Buffer.concat([encodeName(q.name), t]);
  });
  return Buffer.concat([h, ...qs, ...answers.map(encodeRecord), ...authorities.map(encodeRecord), ...additionals.map(encodeRecord)]);
}

// ---------- decoding ----------

function decodeName(buf, off) {
  const labels = [];
  let jumped = false, end = off, hops = 0;
  for (;;) {
    if (off >= buf.length) throw new Error("name out of bounds");
    const len = buf[off];
    if (len === 0) { if (!jumped) end = off + 1; break; }
    if ((len & 0xc0) === 0xc0) {
      if (off + 1 >= buf.length || ++hops > 32) throw new Error("bad pointer");
      if (!jumped) end = off + 2;
      off = ((len & 0x3f) << 8) | buf[off + 1];
      jumped = true;
      continue;
    }
    if (len > 63 || off + 1 + len > buf.length) throw new Error("bad label");
    labels.push(buf.toString("utf8", off + 1, off + 1 + len));
    off += 1 + len;
  }
  return { labels, end };
}

function decodeRdata(buf, type, off, len) {
  const end = off + len;
  switch (type) {
    case T.A: return len === 4 ? [...buf.subarray(off, end)].join(".") : null;
    case T.PTR: return decodeName(buf, off).labels;
    case T.TXT: {
      const out = [];
      for (let p = off; p < end;) {
        const l = buf[p];
        if (p + 1 + l > end) throw new Error("bad TXT");
        if (l) out.push(buf.toString("utf8", p + 1, p + 1 + l));
        p += 1 + l;
      }
      return out;
    }
    case T.SRV: {
      if (len < 7) throw new Error("bad SRV");
      return { priority: buf.readUInt16BE(off), weight: buf.readUInt16BE(off + 2), port: buf.readUInt16BE(off + 4), target: decodeName(buf, off + 6).labels };
    }
    default: return Buffer.from(buf.subarray(off, end));
  }
}

export function decodePacket(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) throw new Error("short packet");
  const id = buf.readUInt16BE(0), flags = buf.readUInt16BE(2);
  const counts = [4, 6, 8, 10].map((o) => buf.readUInt16BE(o));
  let off = 12;
  const questions = [];
  for (let i = 0; i < counts[0]; i++) {
    const n = decodeName(buf, off);
    off = n.end;
    if (off + 4 > buf.length) throw new Error("short question");
    const cls = buf.readUInt16BE(off + 2);
    questions.push({ name: n.labels, type: buf.readUInt16BE(off), class: cls & 0x7fff, qu: !!(cls & 0x8000) });
    off += 4;
  }
  const section = (count) => {
    const out = [];
    for (let i = 0; i < count; i++) {
      const n = decodeName(buf, off);
      off = n.end;
      if (off + 10 > buf.length) throw new Error("short record");
      const type = buf.readUInt16BE(off), cls = buf.readUInt16BE(off + 2), ttl = buf.readUInt32BE(off + 4), len = buf.readUInt16BE(off + 8);
      off += 10;
      if (off + len > buf.length) throw new Error("short rdata");
      out.push({ name: n.labels, type, class: cls & 0x7fff, flush: !!(cls & 0x8000), ttl, data: decodeRdata(buf, type, off, len) });
      off += len;
    }
    return out;
  };
  const answers = section(counts[1]), authorities = section(counts[2]), additionals = section(counts[3]);
  return { id, flags, questions, answers, authorities, additionals };
}

// ---------- our records ----------

// The instance label: the computer name without ".local" and without dots, at most 63 bytes.
export function instanceLabel(name) {
  const clean = String(name ?? "").replace(/\.local\.?$/i, "").replace(/[\u0000-\u001f\u007f]/g, "").replaceAll(".", "-").trim();
  return cutUtf8(clean || "Bubby bridge", 63);
}

export function txtStrings(txt) {
  return Object.entries(txt).map(([k, v]) => cutUtf8(`${k}=${v}`, 255));
}

// records({instance, hostLabel, ip, port, txt}) => {ptr, srv, txt, a, enumPtr}
export function serviceRecords({ instance, hostLabel, ip, port, txt }, ttlScale = 1) {
  const inst = [instance, ...SERVICE];
  const host = [hostLabel, "local"];
  return {
    ptr: { name: SERVICE, type: T.PTR, ttl: TTL_OTHER * ttlScale, data: inst },
    srv: { name: inst, type: T.SRV, flush: true, ttl: TTL_HOST * ttlScale, data: { priority: 0, weight: 0, port, target: host } },
    txt: { name: inst, type: T.TXT, flush: true, ttl: TTL_OTHER * ttlScale, data: txtStrings(txt) },
    a: { name: host, type: T.A, flush: true, ttl: TTL_HOST * ttlScale, data: ip },
    enumPtr: { name: ENUM, type: T.PTR, ttl: TTL_OTHER * ttlScale, data: SERVICE },
  };
}

const wants = (q, type) => q.type === type || q.type === T.ANY;

// Which of our records answer this query. Returns {answers, additionals} or null when nothing matches.
export function answerQuery(query, recs) {
  const answers = new Set();
  const extra = new Set();
  for (const q of query.questions) {
    if (q.class !== CLASS_IN && q.class !== 255) continue;
    if (sameName(q.name, SERVICE) && wants(q, T.PTR)) { answers.add(recs.ptr); extra.add(recs.srv); extra.add(recs.txt); extra.add(recs.a); }
    if (sameName(q.name, ENUM) && wants(q, T.PTR)) answers.add(recs.enumPtr);
    if (sameName(q.name, recs.srv.name)) {
      if (wants(q, T.SRV)) { answers.add(recs.srv); extra.add(recs.a); extra.add(recs.txt); }
      if (wants(q, T.TXT)) answers.add(recs.txt);
    }
    if (sameName(q.name, recs.a.name) && wants(q, T.A)) answers.add(recs.a);
  }
  // Known-answer suppression for the shared PTR (RFC 6762 7.1): the querier already has it with at least half the TTL.
  for (const k of query.answers ?? []) {
    if (k.type === T.PTR && sameName(k.name, recs.ptr.name) && Array.isArray(k.data) && sameName(k.data, recs.ptr.data) && k.ttl >= recs.ptr.ttl / 2) answers.delete(recs.ptr);
  }
  if (!answers.size) return null;
  for (const r of answers) extra.delete(r);
  return { answers: [...answers], additionals: [...extra] };
}

// ---------- responder ----------

// startMdns({name, hostLabel, ip, port, txt: () => ({...}), log}) => {update(), close()}
// Never throws: if multicast is unavailable it logs one line and does nothing.
export function startMdns({ name, hostLabel, ip, port, txt, log = () => {}, createSocket = dgram.createSocket, announceGapMs = 1000 }) {
  const instance = instanceLabel(name);
  const recs = () => serviceRecords({ instance, hostLabel, ip, port, txt: txt() });
  let sock = null, dead = false, ready = false;
  const timers = new Set();
  const later = (fn, ms) => { const t = setTimeout(() => { timers.delete(t); fn(); }, ms); t.unref?.(); timers.add(t); };
  const lastMulticast = new Map(); // record key -> ms

  const fail = (e) => {
    if (dead) return;
    dead = true;
    log(`mDNS недоступен (${e?.code || e?.message || e}); телефон найдёт компьютер по адресу ${ip}:${port}, если ввести его вручную`);
    try { sock?.close(); } catch {}
  };
  const send = (pkt, address = MDNS_GROUP, p = MDNS_PORT) => new Promise((res) => {
    if (dead || !sock) return res();
    try { sock.send(pkt, p, address, () => res()); } catch { res(); }
  });
  const all = (r) => [r.ptr, r.srv, r.txt, r.a, r.enumPtr];
  const announce = () => {
    if (!ready || dead) return;
    const r = recs();
    send(encodePacket({ answers: all(r) }));
    const now = Date.now();
    for (const rec of all(r)) lastMulticast.set(rec.type + "|" + toLabels(rec.name).join("."), now);
  };

  const onMessage = (msg, rinfo) => {
    let q;
    try { q = decodePacket(msg); } catch { return; }
    if (q.flags & 0x8000 || !q.questions.length) return; // responses and empty packets are not for us
    const res = answerQuery(q, recs());
    if (!res) return;
    if (rinfo.port !== MDNS_PORT) {
      // legacy unicast query (RFC 6762 6.7): same id, echo the questions, short TTLs, no cache-flush bit
      const cap = (r) => ({ ...r, flush: false, ttl: Math.min(r.ttl, 10) });
      send(encodePacket({ id: q.id, questions: q.questions, answers: res.answers.map(cap), additionals: res.additionals.map(cap) }), rinfo.address, rinfo.port);
      return;
    }
    if (q.questions.every((x) => x.qu)) send(encodePacket({ answers: res.answers, additionals: res.additionals }), rinfo.address, rinfo.port);
    // multicast, but the same record at most once a second (RFC 6762 6)
    const now = Date.now();
    const fresh = res.answers.filter((r) => now - (lastMulticast.get(r.type + "|" + toLabels(r.name).join(".")) ?? 0) >= 1000);
    if (!fresh.length) return;
    for (const r of fresh) lastMulticast.set(r.type + "|" + toLabels(r.name).join("."), now);
    send(encodePacket({ answers: fresh, additionals: res.additionals }));
  };

  try {
    sock = createSocket({ type: "udp4", reuseAddr: true });
    sock.on("error", fail);
    sock.on("message", onMessage);
    sock.bind({ port: MDNS_PORT, exclusive: false }, () => {
      try {
        sock.addMembership(MDNS_GROUP, ip);
        sock.setMulticastInterface(ip);
        sock.setMulticastTTL(255);
        sock.setMulticastLoopback(true);
      } catch (e) { return fail(e); }
      ready = true;
      announce();
      later(announce, announceGapMs); // RFC 6762 8.3: at least two announcements, one second apart
    });
  } catch (e) { fail(e); }

  return {
    instance,
    // TXT changed (paired=0/1): announce again
    update() { announce(); later(announce, announceGapMs); },
    async close() {
      for (const t of timers) clearTimeout(t);
      timers.clear();
      if (ready && !dead) {
        const r = serviceRecords({ instance, hostLabel, ip, port, txt: txt() }, 0);
        await Promise.race([send(encodePacket({ answers: all(r) })), new Promise((res) => setTimeout(res, 500))]);
      }
      if (!dead) { dead = true; try { sock?.close(); } catch {} }
    },
  };
}
