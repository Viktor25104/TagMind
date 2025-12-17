# Runbook — TagMind (Local Dev)

## Quickstart (Docker Compose)
1) `cp env/local.compose.env.example env/local.compose.env` (stubs do not require external secrets; Postgres uses local defaults from the example file).
2) `make compose-up`
3) Health: `make check` or curl `http://localhost:8081/healthz` .. `/8084/healthz`.
4) Smoke: `./scripts/dev-smoke.sh` (brings compose up if needed, validates stub chain + conversations flow; tears down unless `SMOKE_KEEP_UP=1`).
5) Teardown: `make compose-down`.

## Kubernetes (kind)
1) `make kind-up` (creates `tagmind` cluster, installs ingress-nginx).
2) Build/load images (see `docs/dev-setup.md` section 7.2).
3) `make k8s-apply` (applies manifests, including example secrets).
4) Health via ingress: `make check` or curl with host `tagmind.local` on port 8080 (paths `/tg/healthz`, `/orchestrator/healthz`, `/retriever/healthz`, `/llm/healthz`).
5) Remove: `make k8s-delete` then `make kind-down`.

## Observability/Debugging
- Request correlation: send `X-Request-Id` header; each service echoes a request id in the body and response headers.
- Orchestrator makes intra-cluster calls to web-retriever (skipped for `mode=no_context`) and llm-gateway using service DNS; smoke tests assert this path.
- Orchestrator persists conversation sessions/messages in Postgres (compose includes a local Postgres container; schema is managed via Flyway migrations at startup).
- Logs: `kubectl -n tagmind logs deploy/<service>` for k8s, `docker compose logs` for compose.
- Network policy: `infra/k8s/manifests/06-networkpolicy.yaml` allows intra-namespace traffic and all egress.

## Tooling
- Formatting: `make fmt` (black/gofmt/cargo fmt).
- Lint/build: `make lint` (ruff, golangci-lint, clippy, mvn package).
- Smoke: `make smoke` (wrapper for `scripts/dev-smoke.sh`).
