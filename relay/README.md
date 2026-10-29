# Bubby relay (Cloudflare Worker)

See [docs/RELAY.md](../docs/RELAY.md) for how it works and how to deploy it. Short version:

```
cd relay && npx wrangler deploy
node agent-bridge/bridge.mjs --relay <the address it prints>
```

`node relay/dev-relay.mjs` runs a local stand-in for testing.
