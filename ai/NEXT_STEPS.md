# Next Steps — TagMind

## Near-term (after conversations persistence landed)
- Wire `tg-gateway` into orchestrator conversations (next sprint): normalize Telegram updates into `{contactId, message}` and call `POST /v1/conversations/message`.
- Improve suggestion quality by shaping the LLM prompt (system + user) and by using stored message history (currently only persistence is implemented; history is not fed into prompts yet).
- Add API to fetch conversation history for debugging (read-only) and/or to prune old messages.
- Add structured logging (incl. request id) and DB-level observability (slow queries, connection pool).

## Integrations (after orchestration wiring)
- Replace web-retriever stub with Google CSE search + HTML fetch/extract pipeline (respect existing request/response schema).
- Replace llm-gateway stub with Gemini calls; preserve response shape and add usage metrics as available.
- Implement Telegram webhook registration/verification in tg-gateway and route normalized messages to orchestrator-api.

## Operational hardening
- Extend persistence beyond conversations: policies/allowlists, notes, audit log, rate limiting (likely Redis).
- Add authn/authz, rate limits, and structured logging; extend smoke tests accordingly.
