# Services Overview

## tg-gateway (Go, `services/tg-gateway/stub`)
Purpose: Telegram edge. Accepts Telegram webhooks (JSON validated only) and provides a dev endpoint to simulate a message.
- Port: 8081 (compose); Ingress: `/tg/...`
- Endpoints: `GET /healthz`, `POST /v1/tg/webhook`, `POST /v1/tg/dev/message`
- Behavior: validates HTTP method and JSON; only processes messages that begin with `@tagmind <tag>[: ...]`. Parser supports optional `[n]` counts and payload. Derives `contactId = "tg:" + chatId`, calls orchestrator `/v1/conversations/tag`, and returns `decision` + `replyText`. Non-commands return `{ok:true, ignored:true}`. Always sets `X-Request-Id`.
- Env usage: `ORCHESTRATOR_URL` / `ORCHESTRATOR_TAG_URL` for downstream calls (defaults to service DNS in compose/kind).

## orchestrator-api (Java/Spring, `services/orchestrator-api/stub`)
Purpose: Orchestration surface; persists sessions/messages in Postgres and calls retriever + llm-gateway stubs.
- Port: 8082; Ingress: `/orchestrator/...`
- Endpoints:
  - `GET /healthz`
  - `POST /v1/orchestrate`
  - `POST /v1/conversations/upsert` (create/update session mode for a contact)
  - `POST /v1/conversations/message` (store IN message; if mode=SUGGEST, call llm-gateway and store OUT message)
  - `POST /v1/conversations/tag` (tag router for help/llm/web/recap/judge/fix/plan/safe)
- Behavior:
  - `/v1/orchestrate`: validates required fields; for modes other than `no_context` it calls web-retriever (max 3 results), converts results to llm citations, and always calls llm-gateway. Retries transient network errors once, applies connect/read timeouts, and falls back to llm-only on retriever failure.
  - Conversations/tag: persists sessions + IN/OUT messages in Postgres (Flyway migrations on startup). Session mode is `OFF|SUGGEST`; `OFF` for tags returns `DO_NOT_RESPOND` without calling LLM. `SUGGEST` continues to call llm-gateway and stores suggested replies. Recap/judge/fix fetch the last N messages (variant A) ordered chronologically; `web` tags call web-retriever to produce citations before prompting LLM. Tag prompts are deterministic stub templates wired per tag.
  - Sets `X-Request-Id` header in responses. Downstream URLs are configurable via `RETRIEVER_URL` / `LLM_URL` (compose defaults include service DNS + ports). `RETRIEVER_URL` is reused for tag routing as well.
- Build: Maven (JDK 21); stub uses Spring Boot + Flyway + Testcontainers for integration tests.

## web-retriever (Python/FastAPI, `services/web-retriever/stub`)
Purpose: Web search abstraction; stubbed deterministic search results.
- Port: 8083; Ingress: `/retriever/...`
- Endpoints: `GET /healthz`, `POST /v1/search`
- Behavior: validates query; sleeps ~50ms; returns up to 5 fabricated results with `publishedAt` timestamps. Sets `X-Request-Id` header. Honors optional `RETRIEVER_STUB_BASE_URL` env (default `https://example.com`) for generated URLs.
- Dependencies: `fastapi`, `uvicorn[standard]`.

## llm-gateway (Rust/Axum, `services/llm-gateway/stub`)
Purpose: LLM abstraction; stubbed completion generation.
- Port: 8084; Ingress: `/llm/...`
- Endpoints: `GET /healthz`, `POST /v1/complete`
- Behavior: validates `prompt`; echoes locale/model/temperature/maxTokens; counts citations and returns a stub text/usage payload. Sets `X-Request-Id` header.
- Build: Rust 1.82; enforces `cargo fmt` + `clippy -D warnings`.
