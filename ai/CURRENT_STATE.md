# Current Project State — TagMind

## Status
- Phase: **Contracts + Stub implementations**
- All four services run locally as deterministic HTTP stubs; orchestrator now calls retriever and llm-gateway internally.
- Compose and kind manifests build/run successfully; smoke test script exercises stub endpoints and the cross-service chain.

## Verified (vs code/configs)
- OpenAPI contracts exist for every service in `contracts/` and match implemented endpoints and payload shapes.
- `X-Request-Id` is accepted everywhere; responses include request ids in the body and response headers across all services.
- Orchestrator-api issues retriever calls (skipped when mode is `no_context`), converts results to citations, and calls llm-gateway with timeouts + a single retry for transient failures; retriever failures fall back to llm-only.
- Compose ports: tg-gateway :8081, orchestrator-api :8082, web-retriever :8083, llm-gateway :8084.
- Kind ingress paths: `/tg`, `/orchestrator`, `/retriever`, `/llm` on host `tagmind.local` (port 8080 on host).
- Smoke script `scripts/dev-smoke.sh` passes against the stubs.

## Not Implemented (intentionally deferred)
- Telegram webhook registration/handling beyond JSON validation.
- Policy/notes/grounding logic beyond the simple retriever + llm call chain.
- Google CSE, HTTP fetching, or Gemini integration.
- Persistence (Postgres/Redis), authn/authz, rate limiting, audit logging.

## Known Constraints
- Java package namespace `dev.tagmind.*`.
- Rust checks expect `clippy -D warnings` and `cargo fmt` compliance.
- Stub responses should remain deterministic to keep smoke tests stable.
