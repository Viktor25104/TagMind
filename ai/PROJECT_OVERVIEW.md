# Project Overview — TagMind

TagMind is a Telegram-first AI assistant implemented as four small HTTP services:
- `tg-gateway` (Go) — Telegram edge, entrypoint for developers.
- `orchestrator-api` (Java/Spring) — assembles requests and coordinates retriever + llm calls.
- `web-retriever` (Python/FastAPI) — web search abstraction (stubbed, deterministic).
- `llm-gateway` (Rust/Axum) — LLM abstraction (stubbed, deterministic).

Current repository scope:
- All services expose OpenAPI-defined HTTP endpoints; retriever/llm remain stubs, while orchestrator now includes a real Postgres-backed persistence layer for conversations.
- Local runtimes: Docker Compose (`infra/compose/docker-compose.yml`) and kind-based Kubernetes (`infra/k8s/manifests`).
- Tooling: Make targets for fmt/lint/build, smoke test script, example env files.

Guiding rules:
- OpenAPI-first: contracts live in `contracts/` and drive implementation shape.
- Deterministic stubs: no external calls yet; responses are predictable for tests.
- Request correlation: accept/generate `X-Request-Id` in every service (headers and/or body).
- Minimal infra: Postgres is provisioned in compose for orchestrator conversation sessions/messages; other infra (Redis, queues) is still deferred.
