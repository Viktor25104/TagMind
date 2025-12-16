# Services Overview

## tg-gateway (Go, `services/tg-gateway/stub`)
Purpose: Telegram edge. Accepts Telegram webhooks (JSON validated only) and provides a dev endpoint to simulate a message.
- Port: 8081 (compose); Ingress: `/tg/...`
- Endpoints: `GET /healthz`, `POST /v1/tg/webhook`, `POST /v1/tg/dev/message`
- Behavior: validates HTTP method and JSON; returns `{requestId, ok}` or stub answer message. Always sets `X-Request-Id` response header.
- Env usage: none in stub (secrets placeholders exist for future).

## orchestrator-api (Java/Spring, `services/orchestrator-api/stub`)
Purpose: Orchestration surface; currently echoes inputs without downstream calls.
- Port: 8082; Ingress: `/orchestrator/...`
- Endpoints: `GET /healthz`, `POST /v1/orchestrate`
- Behavior: validates required fields; returns stub answer and echoes mode/locale defaults. Request id is in response body; response header is not set in the stub.
- Build: Maven (JDK 21); packaged jar already present in `target/` for the stub.

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
