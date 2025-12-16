# Next Steps — TagMind

## Near-term (keep stubs deterministic where possible)
- Add HTTP clients in orchestrator-api to call web-retriever and llm-gateway using the existing OpenAPI contracts; propagate `X-Request-Id` to downstream and back in response headers.
- Introduce timeouts/retries and basic error mapping in orchestrator-api before enabling real integrations.
- Align response headers: ensure orchestrator-api sets `X-Request-Id` like other services.

## Integrations (after orchestration wiring)
- Replace web-retriever stub with Google CSE search + HTML fetch/extract pipeline (respect existing request/response schema).
- Replace llm-gateway stub with Gemini calls; preserve response shape and add usage metrics as available.
- Implement Telegram webhook registration/verification in tg-gateway and route normalized messages to orchestrator-api.

## Operational hardening
- Add persistence (Postgres/Redis) for policies, notes, rate limiting, audit logs.
- Add authn/authz, rate limits, and structured logging; extend smoke tests accordingly.
