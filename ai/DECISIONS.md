# Architectural Decisions — TagMind

## Stub-first with OpenAPI-first contracts
Reasons:
- Align all services on contracts before wiring real integrations.
- Keep responses deterministic for smoke tests and reproducibility.
- Allow infra (compose/k8s/ingress) to stabilize early.

## Polyglot per responsibility
Reasons:
- Go for the Telegram edge to keep footprint small.
- Java/Spring for orchestration ergonomics and resilience patterns.
- Python/FastAPI for search/fetch ergonomics.
- Rust/Axum for LLM proxying safety/performance.

## Request correlation via `X-Request-Id`
Reasons:
- Uniform debugging across services and environments.
- Enables tracing once services begin calling each other.
- Every service generates an id when absent; bodies always include it.

## Postgres-only persistence for conversations (no in-memory session state)
Reasons:
- Conversations MVP requires durable storage for sessions and message history.
- Postgres is provisioned in local compose and is easy to inspect for debugging.
- Avoids hidden state in orchestrator instances; keeps behavior reproducible.

## Flyway migrations on startup (single source of DB truth)
Reasons:
- No “manual SQL” steps; DB schema is created/updated automatically on boot.
- Schema changes are versioned and reviewable in git.
- Works consistently in compose and in Testcontainers-backed integration tests.

## Deterministic responses until orchestration is wired
Reasons:
- Avoids external rate limits and secrets during early development.
- Keeps smoke tests and CI green without network access.
- Makes regression detection easy when replacing stubs with real calls.

## Single ingress with path prefixes (kind)
Reasons:
- Simplifies local cluster routing without per-service LoadBalancers.
- Mirrors compose port layout while keeping DNS/host stable (`tagmind.local`).
