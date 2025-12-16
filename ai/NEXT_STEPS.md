# Next Steps — TagMind

## Near-term (keep stubs deterministic where possible)
- Layer basic policy/prompt shaping in orchestrator-api (respecting `chat` / `search_only` / `llm_only` intent) and surface richer `used` metadata.
- Make retriever/llm client settings configurable (timeouts, retries, max results, locale mapping) and add lightweight contract tests around the orchestration chain.
- Add structured logging with request id propagation for downstream calls and errors.

## Integrations (after orchestration wiring)
- Replace web-retriever stub with Google CSE search + HTML fetch/extract pipeline (respect existing request/response schema).
- Replace llm-gateway stub with Gemini calls; preserve response shape and add usage metrics as available.
- Implement Telegram webhook registration/verification in tg-gateway and route normalized messages to orchestrator-api.

## Operational hardening
- Add persistence (Postgres/Redis) for policies, notes, rate limiting, audit logs.
- Add authn/authz, rate limits, and structured logging; extend smoke tests accordingly.
