# Current Project State — TagMind

## Status
- Phase: **Contracts + Stub implementations**
- All four services run locally as deterministic HTTP stubs.
- Compose and kind manifests build/run successfully; smoke test script exercises stub endpoints.

## Verified (vs code/configs)
- OpenAPI contracts exist for every service in `contracts/` and match implemented endpoints and payload shapes.
- `X-Request-Id` is accepted everywhere; responses include request ids in the body. Response headers are returned by tg-gateway, web-retriever, llm-gateway; orchestrator-api currently omits the header but echoes the id in the body.
- Compose ports: tg-gateway :8081, orchestrator-api :8082, web-retriever :8083, llm-gateway :8084.
- Kind ingress paths: `/tg`, `/orchestrator`, `/retriever`, `/llm` on host `tagmind.local` (port 8080 on host).
- Smoke script `scripts/dev-smoke.sh` passes against the stubs.

## Not Implemented (intentionally deferred)
- Telegram webhook registration/handling beyond JSON validation.
- Downstream calls from orchestrator to retriever/llm (or any policy/notes logic).
- Google CSE, HTTP fetching, or Gemini integration.
- Persistence (Postgres/Redis), authn/authz, rate limiting, audit logging, retries/timeouts beyond simple stub delays.

## Known Constraints
- Java package namespace `dev.tagmind.*`.
- Rust checks expect `clippy -D warnings` and `cargo fmt` compliance.
- Stub responses should remain deterministic to keep smoke tests stable.
