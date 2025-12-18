# Architecture – TagMind

## High-Level Shape

```
Telegram -> tg-gateway (Go)
           -> orchestrator-api (Java/Spring)
              -> Postgres (sessions + message history)
              -> web-retriever (Python, tag=web)
              -> llm-gateway (Rust)
```

Tg-gateway filters Telegram updates to `@tagmind <tag>` commands, derives `contactId = "tg:" + chatId`, and forwards valid requests to orchestrator `/v1/conversations/tag`. Orchestrator fetches the last N IN/OUT records from Postgres, shapes tag-specific prompts (help, llm, web, recap, judge, fix, plan, safe), calls web-retriever only for `web` tags, then calls llm-gateway and persists both IN and OUT messages. External integrations (Telegram sendMessage, Google CSE, Gemini) remain stubbed/no-op.

## Design Principles
- **OpenAPI-first**: `contracts/*.yaml` define each surface; code matches these schemas (tg-gateway adds decision/reply fields, orchestrator exposes `/v1/conversations/tag` with tag/decision enums).
- **Deterministic stubs**: no real Telegram/Google/Gemini calls yet; stub responses stay predictable for smoke tests and integration tests.
- **Mostly stateless services**: orchestrator is the only component with Postgres state; tg-gateway and downstream stubs remain stateless.
- **Request correlation**: every service accepts `X-Request-Id` and echoes it in headers/body; tg-gateway forwards the header to orchestrator.
- **Shared environments**: Compose and kind share the same service layout; ingress paths mirror compose ports.

## Deployment Topology
- **Compose**: Postgres + four services exposed on host ports 5432 and 8081–8084. `scripts/dev-smoke.sh` brings this stack up and validates tag flows (help/llm/web/recap) plus orchestrator `/v1/orchestrate`.
- **Kubernetes (kind)**: Deployments + Services for each component, fronted by a single Ingress with host `tagmind.local` and prefixes `/tg`, `/orchestrator`, `/retriever`, `/llm`. `scripts/k8s-one-shot.sh` creates/reuses the `tagmind` cluster, installs ingress-nginx, builds & loads local images, applies manifests, waits for readiness, and prints smoke instructions.
- **Config/Secrets**: `tagmind-config` ConfigMap (APP_ENV, orchestrator URLs) and `tagmind-secrets` Secret (example keys only).

## Technology Choices (why polyglot)
- Go for tg-gateway: lean HTTP edge and Telegram normalization.
- Java/Spring for orchestrator: resilience patterns, Postgres + Flyway, Testcontainers support.
- Python/FastAPI for retriever: ergonomic stub for search/data wrangling.
- Rust/Axum for llm-gateway: safety/performance for prompt handling and deterministic completion generation.
