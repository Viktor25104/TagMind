#!/usr/bin/env bash
set -euo pipefail

echo "== TagMind smoke test (compose) =="

req_id="req_smoke_12345678"

echo "[1/6] health checks..."
curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8081/healthz | grep -q "ok"
curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8082/healthz | grep -q "ok"
curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8083/healthz | grep -q "ok"
curl -sS -H "X-Request-Id: ${req_id}" http://localhost:8084/healthz | grep -q "ok"
echo "ok"

echo "[2/6] tg-gateway dev message..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"userId":"tg:1","chatId":"tg_chat:1","text":"hi"}' \
  http://localhost:8081/v1/tg/dev/message | jq -e '.requestId and .answer' >/dev/null
echo "ok"

echo "[3/6] web-retriever search..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"query":"when did ww2 start","maxResults":3,"lang":"en"}' \
  http://localhost:8083/v1/search | jq -e '.requestId and (.results|length==3)' >/dev/null
echo "ok"

echo "[4/6] llm-gateway complete..."
curl -sS -H "Content-Type: application/json" -H "X-Request-Id: ${req_id}" \
  -d '{"prompt":"hello","locale":"ru-RU","model":"stub","temperature":0.5,"maxTokens":200,"citations":[{"url":"https://example.com","title":"Example","snippet":"snippet text"}]}' \
  http://localhost:8084/v1/complete | jq -e '.requestId and .text and .usage.stub==true' >/dev/null
echo "ok"

echo "[5/6] orchestrator-api orchestrate (with context)..."
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

echo "[6/6] orchestrator-api orchestrate (no_context skip retriever)..."
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

echo "== SMOKE PASSED =="
