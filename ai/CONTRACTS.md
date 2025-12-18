# Contracts — TagMind

OpenAPI 3.1 contracts live in `contracts/`. They are the source of truth for HTTP surfaces and were implemented as deterministic stubs.

## tg-gateway (`contracts/tg-gateway.yaml`)
- Endpoints: `/healthz`, `/v1/tg/webhook`, `/v1/tg/dev/message`
- Notes: Accepts raw Telegram update JSON with `additionalProperties: true`. Responses include `requestId`, `ignored`, and now forward orchestrator metadata (`decision`, `replyText`). Dev endpoint returns `{requestId, contactId, answer, decision}`. Contracts document the stricter parser expectations.

## orchestrator-api (`contracts/orchestrator-api.yaml`)
- Endpoints: `/healthz`, `/v1/orchestrate`, `/v1/conversations/upsert`, `/v1/conversations/message`, `/v1/conversations/tag`
- Orchestrate modes: `chat` (default), `no_context`, `llm_only`.
- Conversations/tag mode (stored per contact): `OFF` or `SUGGEST`.
- Tag enums: `tag` ∈ {help,llm,web,recap,judge,fix,plan,safe}; `decision` ∈ {RESPOND,DO_NOT_RESPOND}. TagRequest includes optional `[count,payload,locale,text]`.
- Notes: Response schemas allow a free-form `used` object for debug metadata (history snippets, citation info, prompt type).

## web-retriever (`contracts/web-retriever.yaml`)
- Endpoints: `/healthz`, `/v1/search`
- Parameters: `query` (required), `recencyDays`, `maxResults`, `lang`, `safe`, `allowNoContext`.
- Notes: Matches stub behavior (deterministic results). Schema omits a description for `SearchResult.source` (acceptable as-is).

## llm-gateway (`contracts/llm-gateway.yaml`)
- Endpoints: `/healthz`, `/v1/complete`
- Parameters: `prompt` (required), optional `locale`, `model`, `temperature`, `maxTokens`, `citations[]`.
- Notes: Error response example in contract contains a typo (`"סה_BAD_REQUEST"`); implementation uses `"BAD_REQUEST"`. Preserve schema shape when replacing the stub.
