# Project Overview — TagMind

TagMind is a Telegram-first AI assistant implemented as four small HTTP services:
- `tg-gateway` (Go) — Telegram edge, entrypoint for developers.
- `orchestrator-api` (Java/Spring) — assembles requests and will coordinate downstream calls.
- `web-retriever` (Python/FastAPI) — web search abstraction (stubbed, deterministic).
- `llm-gateway` (Rust/Axum) — LLM abstraction (stubbed, deterministic).

Current repository scope:
- All services are stubs that expose OpenAPI-defined HTTP endpoints.
- Local runtimes: Docker Compose (`infra/compose/docker-compose.yml`) and kind-based Kubernetes (`infra/k8s/manifests`).
- Tooling: Make targets for fmt/lint/build, smoke test script, example env files.

Guiding rules:
- OpenAPI-first: contracts live in `contracts/` and drive implementation shape.
- Deterministic stubs: no external calls yet; responses are predictable for tests.
- Request correlation: accept/generate `X-Request-Id` in every service (headers and/or body).
- Minimal infra: no databases or queues are provisioned in stubs; configs exist for future secrets.
