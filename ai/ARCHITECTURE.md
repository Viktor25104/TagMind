# Architecture — TagMind

## High-Level Shape

```
Telegram → tg-gateway (Go)
           ↓ (later)
           orchestrator-api (Java/Spring)
             ↙           ↘
   web-retriever (Python)  llm-gateway (Rust)
```

Orchestrator now exercises the downstream stubs (retriever → llm), while external integrations remain deterministic/no-op.

## Design Principles
- OpenAPI-first: `contracts/*.yaml` define surface; stubs implement them faithfully.
- Deterministic stubs: no network calls to Telegram/Google/Gemini; responses are fixed for testability.
- Stateless services: all services are state-free; future persistence will be added later.
- Request correlation: every service accepts `X-Request-Id` and includes a request id in responses (header and/or body).
- Environments: the same services run via Docker Compose or kind + ingress-nginx; ports and paths are consistent.

## Deployment Topology
- Compose: four containers exposed on host ports 8081–8084.
- Kubernetes (kind): four Deployments + Services, fronted by a single Ingress with path prefixes `/tg`, `/orchestrator`, `/retriever`, `/llm` and host `tagmind.local`.
- Config/Secrets: `tagmind-config` ConfigMap (APP_ENV) and `tagmind-secrets` Secret (example keys only).

## Technology Choices (why polyglot)
- Go for tg-gateway: lean HTTP edge, low latency.
- Java/Spring for orchestrator: mature orchestration and lifecycle tooling.
- Python/FastAPI for retriever: ergonomics for web/data wrangling.
- Rust/Axum for llm-gateway: safety and performance for LLM I/O.
