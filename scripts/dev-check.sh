#!/usr/bin/env bash
set -euo pipefail

check() {
  local url="$1"
  curl -fsS "$url" >/dev/null
  echo "ok: $url"
}

echo "=== Compose health ==="
check http://localhost:8081/healthz
check http://localhost:8082/healthz
curl -fsS http://localhost:8083/healthz >/dev/null && echo "ok: http://localhost:8083/healthz"
check http://localhost:8084/healthz

echo
echo "=== K8s health (via ingress on localhost:8080) ==="
if ! curl -fsS -m 1 http://localhost:8080 >/dev/null 2>&1; then
  echo "skip: localhost:8080 is not listening (kind/ingress not started)"
  exit 0
fi

curl -fsS -H 'Host: tagmind.local' http://localhost:8080/tg/healthz >/dev/null && echo "ok: /tg/healthz"
curl -fsS -H 'Host: tagmind.local' http://localhost:8080/orchestrator/healthz >/dev/null && echo "ok: /orchestrator/healthz"
curl -fsS -H 'Host: tagmind.local' http://localhost:8080/retriever/healthz >/dev/null && echo "ok: /retriever/healthz"
curl -fsS -H 'Host: tagmind.local' http://localhost:8080/llm/healthz >/dev/null && echo "ok: /llm/healthz"

echo "OK"
