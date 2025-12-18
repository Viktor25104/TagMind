# Next Steps — TagMind

## Near-term
- Deliver real Telegram integration: register webhook, verify `X-Telegram-Bot-Api-Secret-Token`, and send responses via sendMessage once orchestrator returns `decision=RESPOND`.
- Add retriever + LLM real backends (Google CSE + fetcher, Gemini or other model) while keeping contract and `used` metadata stable.
- Extend observability: structured logging (incl. request id), metrics, DB visibility (slow queries, connection pool health), audit events for tag flows.
- Provide history/diagnostic APIs (read-only) to inspect last N messages per contact and to prune/expire data.

## Integrations
- Replace web-retriever stub with Google CSE search + HTML fetch/extract pipeline (respect existing request/response schema).
- Replace llm-gateway stub with Gemini (or similar) and propagate usage metrics.
- Add optional tools per tag (e.g., plan uses structured output, safe uses policy checks) once real LLM is wired.

## Operational hardening
- Extend persistence beyond conversations: notes/policies, per-contact settings, rate limits (likely Redis).
- Add authn/authz, rate limits, and structured logging; extend smoke/integration tests accordingly.
- Harden Kubernetes workflow: CI to run `scripts/k8s-one-shot.sh`, automated image build/push, and ingress TLS for non-local clusters.
