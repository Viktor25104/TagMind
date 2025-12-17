#!/usr/bin/env bash
set -euo pipefail

echo "== TagMind smoke test (compose) =="

compose_dir="$(cd "$(dirname "$0")/../infra/compose" && pwd)"

cleanup() {
  if [[ "${SMOKE_KEEP_UP:-}" == "1" ]]; then
    echo "keep: SMOKE_KEEP_UP=1 (compose left running)"
    return
  fi
  (cd "${compose_dir}" && docker compose down -v) >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "[0/9] compose up..."
(cd "${compose_dir}" && docker compose up -d --build) >/dev/null

echo "[0/9] wait for postgres..."
for _ in $(seq 1 40); do
  if (cd "${compose_dir}" && docker compose exec -T postgres pg_isready -U tagmind -d tagmind >/dev/null 2>&1); then
    echo "ok"
    break
  fi
  sleep 1
done

req_id="req_smoke_12345678"

echo "[1/9] health checks..."
for _ in $(seq 1 30); do
  if curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8081/healthz >/dev/null 2>&1 \
    && curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8082/healthz >/dev/null 2>&1 \
    && curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8083/healthz >/dev/null 2>&1 \
    && curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8084/healthz >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8081/healthz | grep -q "ok"
curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8082/healthz | grep -q "ok"
curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8083/healthz | grep -q "ok"
curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8084/healthz | grep -q "ok"
echo "ok"

echo "[2/9] tg-gateway dev message..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"userId":"tg:1","chatId":"tg_chat:1","text":"hi"}' \
  http://localhost:8081/v1/tg/dev/message | jq -e '.requestId and .answer' >/dev/null
echo "ok"

echo "[3/9] web-retriever search..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"query":"when did ww2 start","maxResults":3,"lang":"en"}' \
  http://localhost:8083/v1/search | jq -e '.requestId and (.results|length==3)' >/dev/null
echo "ok"

echo "[4/9] llm-gateway complete..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"prompt":"hello","locale":"ru-RU","model":"stub","temperature":0.5,"maxTokens":200,"citations":[{"url":"https://example.com","title":"Example","snippet":"snippet text"}]}' \
  http://localhost:8084/v1/complete | jq -e '.requestId and .text and .usage.stub==true' >/dev/null
echo "ok"

echo "[5/9] orchestrator-api orchestrate (with context)..."
resp_chat=$(curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"userId":"tg:1","chatId":"tg_chat:1","message":"hi","mode":"chat","locale":"ru-RU"}' \
  http://localhost:8082/v1/orchestrate)
echo "${resp_chat}" | jq -e --arg rid "${req_id}" '
  .requestId==$rid
  and .used.retrieverUsed==true
  and .used.citationsCount==3
  and (.answer | contains("completion generated"))
  and (.answer | contains("citations=3"))
' >/dev/null
echo "ok"

echo "[6/9] orchestrator-api orchestrate (no_context skip retriever)..."
resp_nc=$(curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"userId":"tg:1","chatId":"tg_chat:1","message":"hi","mode":"no_context","locale":"ru-RU"}' \
  http://localhost:8082/v1/orchestrate)
echo "${resp_nc}" | jq -e --arg rid "${req_id}" '
  .requestId==$rid
  and .used.retrieverUsed==false
  and .used.citationsCount==0
  and (.answer | contains("completion generated"))
  and (.answer | contains("citations=0"))
' >/dev/null
echo "ok"

echo "[7/9] conversations upsert (OFF)..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"contactId":"tg:smoke_off","mode":"OFF"}' \
  http://localhost:8082/v1/conversations/upsert | jq -e --arg rid "${req_id}" '
    .requestId==$rid and .sessionId and .contactId=="tg:smoke_off" and .mode=="OFF"
  ' >/dev/null
echo "ok"

echo "[8/9] conversations message (OFF => DO_NOT_RESPOND)..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"contactId":"tg:smoke_off","message":"hi"}' \
  http://localhost:8082/v1/conversations/message | jq -e --arg rid "${req_id}" '
    .requestId==$rid and .sessionId and .decision=="DO_NOT_RESPOND" and (.suggestedReply==null)
  ' >/dev/null
echo "ok"

echo "[9/9] conversations message (SUGGEST => suggestedReply)..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"contactId":"tg:smoke_suggest","message":"hi"}' \
  http://localhost:8082/v1/conversations/message | jq -e --arg rid "${req_id}" '
    .requestId==$rid and .sessionId and .decision=="SUGGEST" and (.suggestedReply|type)=="string" and (.suggestedReply|length>0)
  ' >/dev/null
echo "ok"

echo "== SMOKE PASSED =="
