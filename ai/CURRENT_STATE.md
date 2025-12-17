# Current Project State — TagMind

## Status
- Phase: **Conversations MVP (Postgres persistence) on top of stubs**
- All four services run locally; retriever/llm remain deterministic stubs, while orchestrator now includes real persistence for conversation sessions and message history (Postgres + Flyway).
- Docker Compose runs the full stack (including Postgres) and the smoke script exercises both the original stub chain and the new conversations flow.

## Verified (vs code/configs)
- OpenAPI contracts exist for every service in `contracts/` and match implemented endpoints and payload shapes (including conversations endpoints in orchestrator).
- `X-Request-Id` is accepted everywhere; responses include request ids in the body and response headers across all services.
- Orchestrator-api issues retriever calls (skipped when mode is `no_context`), converts results to citations, and calls llm-gateway with timeouts + a single retry for transient failures; retriever failures fall back to llm-only.
- Orchestrator-api persists:
  - `conversation_sessions` (unique `contact_id`, mode `OFF|SUGGEST`)
  - `conversation_messages` (direction `IN|OUT`, message text, request id correlation)
- Orchestrator-api endpoints added for conversations:
  - `POST /v1/conversations/upsert` (create/update session mode)
  - `POST /v1/conversations/message` (store IN message; if mode=SUGGEST, call llm-gateway and store OUT message)
- Compose ports: postgres :5432, tg-gateway :8081, orchestrator-api :8082, web-retriever :8083, llm-gateway :8084.
- Kind ingress paths: `/tg`, `/orchestrator`, `/retriever`, `/llm` on host `tagmind.local` (port 8080 on host).
- Smoke script `scripts/dev-smoke.sh` passes and now also validates the conversations flow (OFF and SUGGEST decisions).
- Orchestrator-api has Postgres-backed integration tests using Testcontainers.

## Not Implemented (intentionally deferred)
- Telegram webhook registration/handling beyond JSON validation.
- Policy/notes/grounding logic beyond the simple retriever + llm call chain (no memory/notes yet).
- Google CSE, HTTP fetching, or Gemini integration.
- Redis, authn/authz, rate limiting, audit logging.
- tg-gateway → orchestrator integration (tg-gateway still does not call orchestrator).

## Known Constraints
- Java package namespace `dev.tagmind.*`.
- Rust checks expect `clippy -D warnings` and `cargo fmt` compliance.
- Stub responses should remain stable to keep smoke tests stable (some stubs include generated data; smoke avoids asserting full content).
