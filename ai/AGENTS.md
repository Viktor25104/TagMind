# Repository Guidelines

## Project Structure & Module Organization
- Services: `services/tg-gateway` (Go), `services/orchestrator-api` (Java/Spring), `services/web-retriever` (Python/FastAPI), `services/llm-gateway` (Rust/Axum); each has a `stub/` that implements the OpenAPI contract.
- Contracts: `contracts/*.yaml` (authoritative HTTP surfaces).
- Infra: `infra/compose/docker-compose.yml`, `infra/k8s/manifests` (kind ingress paths `/tg`, `/orchestrator`, `/retriever`, `/llm`).
- Scripts: `scripts/dev-*.sh` for compose/kind workflows; `scripts/dev-smoke.sh` for end-to-end checks (now covering @tagmind help/llm/web/recap flows); `scripts/k8s-one-shot.sh` to bootstrap kind + ingress + manifests in one step.
- AI knowledge base: `ai/` (read first; keep in sync with code/configs).

## Build, Test, and Development Commands
- Format: `make fmt` (black, gofmt, cargo fmt).
- Lint/build: `make lint` (ruff, golangci-lint, clippy -D warnings, mvn package -DskipTests).
- Compose run: `make compose-up` / `make compose-down`; health: `make check`; smoke: `make smoke` or `./scripts/dev-smoke.sh`.
- Kubernetes: preferred path is `scripts/k8s-one-shot.sh` (creates or reuses the `tagmind` kind cluster, installs ingress-nginx, builds/loads local images, applies manifests, waits for readiness). Manual fallback: `make kind-up`, build/load images, `make k8s-apply`, teardown via `make k8s-delete` + `make kind-down`.

## Coding Style & Naming Conventions
- Follow language defaults: gofmt, rustfmt, black; keep stubs deterministic.
- Lint gates: `golangci-lint`, `ruff`, `clippy -D warnings`; fix warnings instead of suppressing them.
- Request correlation: accept/generate `X-Request-Id`, echo in responses, and propagate headers when chaining calls.
- Ports/paths: keep compose ports 8081–8084 and ingress prefixes stable unless architecture changes explicitly.
- Derived conventions: `contactId = "tg:" + chatId` for tg-gateway/orchestrator; tag commands must start with `@tagmind`, optional `[n]`, optional payload; tg-gateway should default to `{ignored:true}` for everything else.

## Testing Guidelines
- Primary check: `scripts/dev-smoke.sh` (covers tg-gateway tag flows plus orchestrator endpoints); keep stub responses deterministic to avoid flaky assertions.
- Orchestrator integration tests use Postgres + Testcontainers (cover help/OFF/history/web/persistence). Mirror contract payloads and propagate `X-Request-Id` when adding tests.
- When modifying contracts or behaviors (tag prompts, retriever usage, new tags), extend both the smoke script and integration tests accordingly.

## Commit & Pull Request Guidelines
- Commit style mirrors repo history (short imperative line with optional scope, e.g., `feat(tg-gateway): ...`).
- Ensure AI docs stay in sync with code/configs in the same PR.
- PR checklist: summary, commands/tests run (`make lint`, `./scripts/dev-smoke.sh`, etc.), mention contract updates, and follow-up tasks if needed.

## Security & Configuration Tips
- Secrets are never committed; use `env/local.compose.env` and `infra/k8s/manifests/02-secrets.example.yaml` as templates.
- Keep stubs network-isolated; do not add real API keys into default env files.
- Default hostnames/ports are referenced in `ai/RUNBOOK.md`; update manifests/docs if you change them.
