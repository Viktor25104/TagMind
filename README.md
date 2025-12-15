# TagMind

Summon-by-mention Telegram AI agent with **Google CSE** web retrieval and **Gemini** reasoning.
Designed for personal use with strict access control, audit logging, and a clear “bot speaks only when called” model.

## Core idea

TagMind does **not** “read all chats”.
It reacts only when you explicitly summon it:

- Mention: `@TagMind ...`
- Reply to bot message
- Direct message to the bot

This keeps behavior predictable, reduces risk, and makes the agent usable in groups without spam.

## Features (MVP scope)

- **Mention-only** response mode (safe by default)
- `web:` mode: answers with **web retrieval** using **Google Custom Search Engine**
- `fact:` mode: answers without browsing (pure model reasoning)
- Personal **notes/memory** commands (`remember`, `recall`, `forget`)
- **Allowlist** for chats + admin-only control
- **Audit log** of requests (who invoked, where, which mode)
- Rate limiting and timeouts

## Architecture (multi-language microservices)

Recommended MVP: 4 services

1) `tg-gateway` (Go)  
   Telegram bot webhook receiver + interaction UI (inline buttons), mention filter, routing to orchestrator.

2) `orchestrator-api` (Java, Spring Boot)  
   Policy enforcement (allowlist/modes), request assembly, calling web-retriever and llm-gateway, formatting response, writing audit logs.

3) `web-retriever` (Python, FastAPI)  
   Google CSE search → fetch pages → extract readable text → return evidence chunks.

4) `llm-gateway` (Rust)  
   Gemini wrapper: retries, timeouts, safety filters, prompt contract, response normalization.

Data plane:
- Postgres: policies, chats, users, notes, audit
- Redis: cache for search/fetch + rate limits + short-lived state

## Repo layout (suggested)

This repository can host all services as folders:

- `services/tg-gateway`
- `services/orchestrator-api`
- `services/web-retriever`
- `services/llm-gateway`
- `infra/docker-compose.yml`

## Commands (user-facing)

### Web mode (Google CSE + fetch)
- `@TagMind web: кто такой X?`
- `@TagMind web: найди официальную документацию по Y`
- `@TagMind web: дай сводку по Z и приложи источники`

### Fact mode (no browsing)
- `@TagMind fact: объясни разницу между OAuth и JWT`
- `@TagMind fact: почему COOP/COEP нужен для SharedArrayBuffer`

### Memory / Notes
- `@TagMind remember: пароль от тестового стенда хранится в Vault`
- `@TagMind recall: пароль от тестового стенда`
- `@TagMind forget: пароль от тестового стенда`

### Admin controls
- `/mode off`
- `/mode mention`
- `/mode suggest`
- `/allow_chat`
- `/deny_chat`

## Security model

- **Default: mention-only** (bot responds only on explicit summon)
- Chat allowlist: bot ignores non-approved chats
- Admin-only mode changes: only `ADMIN_TELEGRAM_USER_ID` can change configuration
- Rate limit: prevent spam/flooding
- Audit events: visibility for debugging and accountability
- Secrets: keep keys in Vault/Secrets Manager (never commit them)

## Configuration (environment)

These environment variables are required across services:

- `TELEGRAM_BOT_TOKEN`
- `ADMIN_TELEGRAM_USER_ID`
- `POSTGRES_DSN`
- `REDIS_URL`
- `GOOGLE_CSE_API_KEY`
- `GOOGLE_CSE_CX`
- `GEMINI_API_KEY`

Recommended:
- `DEFAULT_MODE=mention`
- `MAX_MSGS_PER_MINUTE=20`
- `FETCH_TIMEOUT_SECONDS=12`
- `LLM_TIMEOUT_SECONDS=25`

## Google CSE setup (quick)

1) Create a Custom Search Engine and enable “Search the entire web”
2) Get:
   - `GOOGLE_CSE_API_KEY` (Google Cloud API key with Custom Search API enabled)
   - `GOOGLE_CSE_CX` (CSE identifier)

TagMind uses CSE results as candidate sources and then fetches pages to extract readable text.

## Strict license

This project is published under **GNU AGPLv3 (AGPL-3.0-only)**:
- Strong copyleft
- **Network use** triggers source distribution obligations
- Suitable for public repos when you want strict control and prevent proprietary SaaS forks

See `LICENSE`.

## Contributing

By contributing, you agree that your contribution is licensed under AGPL-3.0-only.

## Disclaimer

This project is intended for lawful personal productivity and research.
Do not use it for stalking, doxxing, illegal data collection, or privacy-invasive automation.
