# Repository Guidelines

## Project Structure & Module Organization
- Services: `services/tg-gateway` (Go), `services/orchestrator-api` (Java/Spring), `services/web-retriever` (Python/FastAPI), `services/llm-gateway` (Rust/Axum); each has a `stub/` implementing the OpenAPI contract.
- Contracts: `contracts/*.yaml` (authoritative HTTP surfaces).
- Infra: `infra/compose/docker-compose.yml`, `infra/k8s/manifests` (kind ingress paths `/tg`, `/orchestrator`, `/retriever`, `/llm`).
- Scripts: `scripts/dev-*.sh` for compose/kind workflows; `scripts/dev-smoke.sh` for end-to-end stub check.
- AI knowledge base: `ai/` (read first; keep in sync with code/configs).

## Build, Test, and Development Commands
- Format: `make fmt` (black, gofmt, cargo fmt).
- Lint/build: `make lint` (ruff, golangci-lint, clippy -D warnings, mvn package -DskipTests).
- Compose run: `make compose-up` / `make compose-down`; health: `make check`; smoke: `make smoke` or `./scripts/dev-smoke.sh`.
- Kind: `make kind-up`, build/load images manually (see `docs/dev-setup.md`), then `make k8s-apply`; cleanup with `make k8s-delete` and `make kind-down`.

## Coding Style & Naming Conventions
- Follow language defaults: gofmt, rustfmt, black; keep code deterministic in stubs.
- Lint gates: `golangci-lint`, `ruff`, `clippy -D warnings`; fix warnings, do not ignore.
- Request correlation: accept/generate `X-Request-Id`, echo in responses; preserve headers when wiring calls.
- Paths/ports: keep compose ports 8081–8084 and ingress prefixes stable unless architecture is explicitly changed.

## Testing Guidelines
- Primary check is the smoke script (`scripts/dev-smoke.sh`), which hits all stub endpoints; keep responses deterministic to avoid flakiness.
- No formal unit test suites yet; if adding, mirror existing request/response contracts and use generated/propagated `X-Request-Id`.
- When modifying contracts or behaviors, extend the smoke script accordingly.

## Commit & Pull Request Guidelines
- Commit style mirrors repo history: short imperative line with type-like prefix when relevant (e.g., `feat: add ...`, `docs: ...`, `infra: ...`).
- Ensure AI docs in `ai/` reflect code/config changes in the same PR.
- PR checklist: summary of change and scope, commands/tests run (e.g., `make lint`, `./scripts/dev-smoke.sh`), mention contract updates, and any follow-up actions. Screenshots are unnecessary for backend-only work.

## Security & Configuration Tips
- Secrets are never committed; use `env/local.compose.env` and `infra/k8s/manifests/02-secrets.example.yaml` as templates.
- Keep stubs network-isolated; do not add real API keys into default env files.
- Default hostnames/ports are referenced in `ai/RUNBOOK.md`; avoid divergence without updating docs and manifests.
