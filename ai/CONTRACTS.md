# Contracts — TagMind

OpenAPI 3.1 contracts live in `contracts/`. They are the source of truth for HTTP surfaces and were implemented as deterministic stubs.

## tg-gateway (`contracts/tg-gateway.yaml`)
- Endpoints: `/healthz`, `/v1/tg/webhook`, `/v1/tg/dev/message`
- Notes: Accepts raw Telegram update JSON with `additionalProperties: true`. Responses include `requestId` and validation errors.

## orchestrator-api (`contracts/orchestrator-api.yaml`)
- Endpoints: `/healthz`, `/v1/orchestrate`
- Modes: `chat` (default), `search_only`, `llm_only`.
- Notes: Response schema allows a free-form `used` object for debug metadata.

## web-retriever (`contracts/web-retriever.yaml`)
- Endpoints: `/healthz`, `/v1/search`
- Parameters: `query` (required), `recencyDays`, `maxResults`, `lang`, `safe`, `allowNoContext`.
- Notes: Matches stub behavior (deterministic results). Schema omits a description for `SearchResult.source` (acceptable as-is).

## llm-gateway (`contracts/llm-gateway.yaml`)
- Endpoints: `/healthz`, `/v1/complete`
- Parameters: `prompt` (required), optional `locale`, `model`, `temperature`, `maxTokens`, `citations[]`.
- Notes: Error response example in contract contains a typo (`"סה_BAD_REQUEST"`); implementation uses `"BAD_REQUEST"`. Preserve schema shape when replacing the stub.
