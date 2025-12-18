# Current Project State — TagMind

## Status
- Phase: **Telegram tag router MVP (Postgres-backed history, tag prompts, k8s-ready stubs)**
- All four services run locally; retriever/llm remain deterministic stubs, while orchestrator handles `/v1/conversations/tag` with prompt templates, Postgres history fetch, web retriever integration, and IN/OUT persistence. Tg-gateway now enforces `@tagmind` syntax and forwards valid requests to orchestrator.
- Docker Compose runs the full stack (including Postgres) and the smoke script exercises orchestrator `/v1/orchestrate` plus tg-gateway tag flows (help/llm/web/recap[5]).

## Verified (vs code/configs)
- OpenAPI contracts exist for every service in `contracts/` and match implemented endpoints (tg-gateway includes `decision/replyText`, orchestrator includes `/v1/conversations/tag` + enums).
- `X-Request-Id` is accepted everywhere; responses include request ids in the body and response headers across all services. Tg-gateway forwards ids to orchestrator tag API.
- Orchestrator-api issues retriever calls unless mode/no_context or tag != `web`, converts results to citations, and calls llm-gateway with timeouts + retry. Web tag routes `payload` to retriever, then shapes citations in prompts.
- Orchestrator-api persists:
  - `conversation_sessions` (unique `contact_id`, mode `OFF|SUGGEST`)
  - `conversation_messages` (direction `IN|OUT`, message text, request id correlation) for both conversations API and tag API.
  - History fetch variant A: last N messages ordered chronologically, used by recap/judge/fix flows.
- Orchestrator tag endpoints:
  - `POST /v1/conversations/upsert`
  - `POST /v1/conversations/message`
  - `POST /v1/conversations/tag` (tag router for help/llm/web/recap/judge/fix/plan/safe; OFF sessions immediately return `DO_NOT_RESPOND`)
- Compose ports: postgres :5432, tg-gateway :8081, orchestrator-api :8082, web-retriever :8083, llm-gateway :8084.
- Kind ingress paths: `/tg`, `/orchestrator`, `/retriever`, `/llm` on host `tagmind.local` (port 8080). `scripts/k8s-one-shot.sh` automates kind cluster creation/build/deploy/readiness.
- Smoke script `scripts/dev-smoke.sh` passes and covers @tagmind help/llm/web/recap flows plus legacy orchestrator endpoints.
- Orchestrator-api has Postgres-backed integration tests using Testcontainers (covering help, OFF, recap history order, web retriever usage, message persistence).

## Not Implemented (intentionally deferred)
- Telegram webhook registration/verification (currently stub JSON validation only; rely on dev endpoint).
- Policy/notes/grounding logic beyond the simple retriever + llm call chain (no retrieval-augmented memory besides last N history).
- Google CSE, HTTP fetching, or Gemini integration (retriever/LLM remain deterministic stubs).
- Redis, authn/authz, rate limiting, audit logging.
- Real Telegram sendMessage; tg-gateway only returns orchestrator replyText to dev clients/webhook caller.

## Known Constraints
- Java package namespace `dev.tagmind.*`.
- Rust checks expect `clippy -D warnings` and `cargo fmt` compliance.
- Stub responses should remain stable to keep smoke tests predictable (citations text is deterministic, llm-gateway prompt echoes embed configuration).
