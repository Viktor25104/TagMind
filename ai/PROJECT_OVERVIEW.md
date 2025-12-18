# Project Overview — TagMind

TagMind is a Telegram-first AI assistant implemented as four small HTTP services:
- `tg-gateway` (Go) – Telegram edge, entrypoint for developers; now enforces strict `@tagmind <tag>` syntax, parses optional `[n]`, derives `contactId= "tg:"+chatId`, and calls the orchestrator tag API.
- `orchestrator-api` (Java/Spring) – assembles requests and coordinates retriever + llm calls; persists sessions/messages in Postgres and exposes `/v1/conversations/tag` to route tags help/llm/web/recap/judge/fix/plan/safe.
- `web-retriever` (Python/FastAPI) – web search abstraction (stubbed, deterministic).
- `llm-gateway` (Rust/Axum) – LLM abstraction (stubbed, deterministic).

Current repository scope:
- All services expose OpenAPI-defined HTTP endpoints. Retriever/llm remain deterministic stubs, while orchestrator is backed by Postgres (sessions + message history) and shapes prompts/history per tag. Tg-gateway is wired to orchestrator tag routing and ignores everything else by default.
- Local runtimes: Docker Compose (`infra/compose/docker-compose.yml`) and kind-based Kubernetes (`infra/k8s/manifests`). A one-shot script (`scripts/k8s-one-shot.sh`) bootstraps kind + ingress + manifests end to end.
- Tooling: Make targets for fmt/lint/build, smoke test script (now covering @tagmind help/llm/web/recap flows), example env files, Testcontainers integration tests.

Guiding rules:
- OpenAPI-first: contracts live in `contracts/` and drive implementation shape (including the new `/v1/conversations/tag` schema).
- Deterministic stubs: no external calls yet; responses are predictable for tests while still exercising retriever→LLM pipelines for web tags.
- Request correlation: accept/generate `X-Request-Id` in every service (headers and/or body).
- Minimal infra: Postgres stores conversations + tag history (variant A: last N IN/OUT); other infra (Redis, queues) is still deferred.
