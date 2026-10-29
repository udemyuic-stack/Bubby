// Bubby relay: a small Cloudflare Worker + Durable Object that connects a phone and a computer that are not on the same
// network. It only passes opaque, end-to-end encrypted frames between the two sides of one "room"; it holds no keys,
// stores nothing and cannot read or change what goes through (docs/RELAY.md).
import { DurableObject } from "cloudflare:workers";

const ROOM_RE = /^[0-9a-f]{32}$/;
const ROLES = ["bridge", "phone"];
const MAX_FRAME = 512 * 1024;
const RATE_WINDOW_MS = 10_000;
const RATE_MAX = 400; // frames per socket per window

export const other = (role) => (role === "bridge" ? "phone" : "bridge");

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/" || url.pathname === "/health") return new Response("bubby relay ok\n", { headers: { "content-type": "text/plain" } });
    const m = /^\/r\/([0-9a-f]{32})$/.exec(url.pathname);
    if (!m || !ROOM_RE.test(m[1])) return new Response("not found", { status: 404 });
    const role = url.searchParams.get("role");
    if (!ROLES.includes(role)) return new Response("bad role", { status: 400 });
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return new Response("expected a websocket", { status: 426 });
    const stub = env.ROOM.get(env.ROOM.idFromName(m[1]));
    return stub.fetch(request);
  },
};

export class Room extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    // keep-alive text frames are answered by the runtime without waking the object
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(request) {
    const role = new URL(request.url).searchParams.get("role");
    // one socket per role: a new one replaces the old one
    for (const old of this.ctx.getWebSockets(role)) {
      try { old.send(JSON.stringify({ type: "replaced" })); old.close(4000, "replaced"); } catch {}
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server, [role]);
    server.serializeAttachment({ role, n: 0, t: Date.now() });
    // tell the new socket whether the other side is here, and the other side that this one arrived
    server.send(JSON.stringify({ type: "peer", up: this.ctx.getWebSockets(other(role)).length > 0 }));
    this.#tell(other(role), { type: "peer", up: true });
    return new Response(null, { status: 101, webSocket: client });
  }

  webSocketMessage(ws, message) {
    const a = ws.deserializeAttachment() ?? {};
    const role = a.role;
    if (!role) return ws.close(1008, "no role");
    const now = Date.now();
    if (now - (a.t ?? 0) > RATE_WINDOW_MS) { a.t = now; a.n = 0; }
    a.n = (a.n ?? 0) + 1;
    ws.serializeAttachment(a);
    if (a.n > RATE_MAX) return ws.close(1008, "too many frames");
    const size = typeof message === "string" ? message.length : message.byteLength;
    if (size > MAX_FRAME) return ws.close(1009, "frame too large");
    if (typeof message !== "string") return; // text only
    for (const peer of this.ctx.getWebSockets(other(role))) {
      try { peer.send(message); } catch {}
    }
  }

  webSocketClose(ws) { this.#gone(ws); }
  webSocketError(ws) { this.#gone(ws); }

  #gone(ws) {
    const role = ws.deserializeAttachment()?.role;
    try { ws.close(1000, "bye"); } catch {}
    if (role && this.ctx.getWebSockets(role).filter((s) => s !== ws).length === 0) this.#tell(other(role), { type: "peer", up: false });
  }

  #tell(role, obj) {
    for (const s of this.ctx.getWebSockets(role)) { try { s.send(JSON.stringify(obj)); } catch {} }
  }
}
