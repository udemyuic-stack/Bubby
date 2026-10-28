import { test } from "node:test";
import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import {
  encodePacket, decodePacket, serviceRecords, answerQuery, startMdns, instanceLabel, T, SERVICE, MDNS_GROUP,
} from "../lib/mdns.mjs";

const recs = () => serviceRecords({
  instance: "Мой Mac", hostLabel: "bubby-0123abcd", ip: "192.168.1.20", port: 47821,
  txt: { v: 1, name: "Мой Mac", fp: "ab".repeat(32), paired: 0 },
});

test("packet encode/decode round trip: PTR, SRV, TXT, A", () => {
  const r = recs();
  const pkt = encodePacket({ answers: [r.ptr], additionals: [r.srv, r.txt, r.a] });
  const d = decodePacket(pkt);
  assert.equal(d.flags, 0x8400);
  assert.equal(d.questions.length, 0);
  assert.deepEqual(d.answers[0], { name: SERVICE, type: T.PTR, class: 1, flush: false, ttl: 4500, data: ["Мой Mac", ...SERVICE] });
  const [srv, txt, a] = d.additionals;
  assert.deepEqual(srv.data, { priority: 0, weight: 0, port: 47821, target: ["bubby-0123abcd", "local"] });
  assert.equal(srv.flush, true);
  assert.equal(srv.ttl, 120);
  assert.deepEqual(txt.data, ["v=1", "name=Мой Mac", `fp=${"ab".repeat(32)}`, "paired=0"]);
  assert.deepEqual(a.name, ["bubby-0123abcd", "local"]);
  assert.equal(a.data, "192.168.1.20");
  // re-encoding the decoded records gives the same bytes
  assert.deepEqual(encodePacket({ answers: d.answers, additionals: d.additionals }), pkt);
});

test("query round trip, QU bit, and compressed names are decoded", () => {
  const q = encodePacket({ id: 7, flags: 0, questions: [{ name: "_bubby-agent._tcp.local", type: T.PTR, qu: true }] });
  const d = decodePacket(q);
  assert.equal(d.id, 7);
  assert.deepEqual(d.questions, [{ name: SERVICE, type: T.PTR, class: 1, qu: true }]);
  // hand-made packet: question "_bubby-agent._tcp.local", answer name is a pointer to offset 12
  const head = Buffer.from([0, 0, 0x84, 0, 0, 1, 0, 1, 0, 0, 0, 0]);
  const qname = Buffer.from([12, ...Buffer.from("_bubby-agent"), 4, ...Buffer.from("_tcp"), 5, ...Buffer.from("local"), 0, 0, 12, 0, 1]);
  const ans = Buffer.from([0xc0, 12, 0, 12, 0, 1, 0, 0, 0, 10, 0, 4, 1, 0x78, 0xc0, 12]);
  const c = decodePacket(Buffer.concat([head, qname, ans]));
  assert.deepEqual(c.answers[0].name, SERVICE);
  assert.deepEqual(c.answers[0].data, ["x", ...SERVICE]);
  assert.throws(() => decodePacket(Buffer.from([0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0xc0, 12]))); // pointer loop
  assert.throws(() => decodePacket(Buffer.alloc(5)));
});

test("answers: PTR for the service, SRV/TXT for the instance, A for the host, nothing for others", () => {
  const r = recs();
  const ask = (name, type, answers = []) => answerQuery({ questions: [{ name: name.split ? name.split(".") : name, type, class: 1 }], answers }, r);
  const ptr = ask("_bubby-agent._tcp.local", T.PTR);
  assert.deepEqual(ptr.answers, [r.ptr]);
  assert.deepEqual(new Set(ptr.additionals), new Set([r.srv, r.txt, r.a]));
  const srv = ask(["Мой Mac", ...SERVICE], T.SRV);
  assert.deepEqual(srv.answers, [r.srv]);
  assert.deepEqual(ask(["мой mac", "_BUBBY-agent", "_tcp", "local"], T.TXT).answers, [r.txt]); // case-insensitive
  assert.equal(ask(["Мой Mac", ...SERVICE], T.ANY).answers.length, 2);
  assert.deepEqual(ask("bubby-0123abcd.local", T.A).answers, [r.a]);
  assert.deepEqual(ask("_services._dns-sd._udp.local", T.PTR).answers, [r.enumPtr]);
  assert.equal(ask("_googlecast._tcp.local", T.PTR), null);
  assert.equal(ask("bubby-0123abcd.local", T.AAAA), null);
  // known answer with a fresh TTL suppresses the PTR
  assert.equal(ask("_bubby-agent._tcp.local", T.PTR, [{ ...r.ptr, ttl: 4000 }]), null);
  assert.ok(ask("_bubby-agent._tcp.local", T.PTR, [{ ...r.ptr, ttl: 100 }]));
});

test("instance label: no dots, no .local, at most 63 bytes", () => {
  assert.equal(instanceLabel("Johns-MacBook.local"), "Johns-MacBook");
  assert.equal(instanceLabel("a.b"), "a-b");
  assert.ok(Buffer.byteLength(instanceLabel("я".repeat(100))) <= 63);
  assert.equal(instanceLabel(""), "Bubby bridge");
});

class FakeSocket extends EventEmitter {
  constructor(opts) { super(); this.opts = opts; this.sent = []; this.joined = null; this.closed = false; }
  bind(o, cb) { this.bound = o; setImmediate(cb); }
  addMembership(g, ip) { if (this.failJoin) throw Object.assign(new Error("no multicast"), { code: "ENODEV" }); this.joined = [g, ip]; }
  setMulticastInterface() {} setMulticastTTL() {} setMulticastLoopback() {}
  send(buf, port, address, cb) { this.sent.push({ pkt: decodePacket(buf), port, address }); setImmediate(cb); }
  close() { this.closed = true; }
}

test("responder: joins 224.0.0.251:5353 with reuseAddr, announces, answers, says goodbye", async () => {
  let s;
  let paired = 0;
  const m = startMdns({
    name: "Test", hostLabel: "bubby-x", ip: "192.168.1.20", port: 4000, announceGapMs: 20,
    txt: () => ({ v: 1, name: "Test", fp: "f".repeat(64), paired }),
    createSocket: (o) => (s = new FakeSocket(o)),
  });
  await new Promise((r) => setTimeout(r, 60));
  assert.equal(s.opts.reuseAddr, true);
  assert.equal(s.opts.type, "udp4");
  assert.equal(s.bound.port, 5353);
  assert.deepEqual(s.joined, [MDNS_GROUP, "192.168.1.20"]);
  assert.ok(s.sent.length >= 2, "two announcements");
  assert.equal(s.sent[0].address, MDNS_GROUP);
  assert.ok(s.sent[0].pkt.answers.some((r) => r.type === T.SRV && r.data.port === 4000));

  // a query from a phone on 5353
  s.sent.length = 0;
  await new Promise((r) => setTimeout(r, 1000)); // past the 1 s per-record multicast limit
  s.emit("message", encodePacket({ flags: 0, questions: [{ name: SERVICE, type: T.PTR }] }), { address: "192.168.1.30", port: 5353 });
  await new Promise((r) => setImmediate(r));
  assert.equal(s.sent.length, 1);
  assert.equal(s.sent[0].address, MDNS_GROUP);
  assert.deepEqual(s.sent[0].pkt.answers[0].data, ["Test", ...SERVICE]);
  assert.ok(s.sent[0].pkt.additionals.some((r) => r.type === T.TXT && r.data.includes("paired=0")));

  // legacy unicast query from another port: unicast reply with the same id and the question echoed
  s.sent.length = 0;
  s.emit("message", encodePacket({ id: 99, flags: 0, questions: [{ name: ["Test", ...SERVICE], type: T.TXT }] }), { address: "192.168.1.30", port: 40000 });
  await new Promise((r) => setImmediate(r));
  assert.equal(s.sent[0].address, "192.168.1.30");
  assert.equal(s.sent[0].port, 40000);
  assert.equal(s.sent[0].pkt.id, 99);
  assert.equal(s.sent[0].pkt.questions.length, 1);
  assert.ok(s.sent[0].pkt.answers[0].ttl <= 10);

  // garbage and responses are ignored
  s.sent.length = 0;
  s.emit("message", Buffer.from("nope"), { address: "1.2.3.4", port: 5353 });
  s.emit("message", encodePacket({ answers: [recs().ptr] }), { address: "1.2.3.4", port: 5353 });
  await new Promise((r) => setImmediate(r));
  assert.equal(s.sent.length, 0);

  // TXT update re-announces
  paired = 1;
  m.update();
  await new Promise((r) => setImmediate(r));
  assert.ok(s.sent.some((x) => x.pkt.answers.some((r) => r.type === T.TXT && r.data.includes("paired=1"))));

  s.sent.length = 0;
  await m.close();
  assert.equal(s.sent.length, 1);
  assert.ok(s.sent[0].pkt.answers.length >= 4);
  assert.ok(s.sent[0].pkt.answers.every((r) => r.ttl === 0), "goodbye has TTL 0");
  assert.equal(s.closed, true);
});

test("responder: no multicast => one log line, no throw, close still works", async () => {
  const lines = [];
  const m = startMdns({
    name: "T", hostLabel: "h", ip: "10.0.0.1", port: 1, txt: () => ({ v: 1 }), log: (l) => lines.push(l),
    createSocket: (o) => Object.assign(new FakeSocket(o), { failJoin: true }),
  });
  await new Promise((r) => setTimeout(r, 20));
  assert.equal(lines.length, 1);
  assert.match(lines[0], /mDNS/);
  m.update();
  await m.close();
  assert.equal(lines.length, 1);
});

test("responder survives a socket that throws on create", async () => {
  const lines = [];
  const m = startMdns({ name: "T", hostLabel: "h", ip: "10.0.0.1", port: 1, txt: () => ({}), log: (l) => lines.push(l), createSocket: () => { throw new Error("EPERM"); } });
  await m.close();
  assert.equal(lines.length, 1);
});
