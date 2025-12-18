#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST_DIR="${ROOT_DIR}/infra/k8s/manifests"
KIND_CONFIG="${ROOT_DIR}/infra/k8s/kind/kind-config.yaml"
CLUSTER_NAME="tagmind"

log() {
  printf '[k8s] %s\n' "$*"
}

ensure_cluster() {
  if ! kind get clusters >/dev/null 2>&1; then
    log "kind not available (kind get clusters failed). Please install kind."
    exit 1
  fi

  if ! kind get clusters | grep -Fxq "${CLUSTER_NAME}"; then
    log "Creating kind cluster ${CLUSTER_NAME}..."
    kind create cluster --name "${CLUSTER_NAME}" --config "${KIND_CONFIG}"
    log "Cluster ${CLUSTER_NAME} created."
  else
    log "kind cluster ${CLUSTER_NAME} already exists (reuse)."
  fi
}

install_ingress() {
  log "Applying ingress-nginx controller..."
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
  log "Waiting for ingress-nginx rollout..."
  kubectl rollout status deployment/ingress-nginx-controller -n ingress-nginx --timeout=180s
}

build_and_load_images() {
  local services=(
    "tg-gateway services/tg-gateway/Dockerfile"
    "orchestrator-api services/orchestrator-api/Dockerfile"
    "web-retriever services/web-retriever/Dockerfile"
    "llm-gateway services/llm-gateway/Dockerfile"
  )

  for item in "${services[@]}"; do
    local name dockerfile
    name=$(awk '{print $1}' <<<"${item}")
    dockerfile=$(awk '{print $2}' <<<"${item}")
    log "Building image ${name}:local ..."
    docker build -t "${name}:local" -f "${ROOT_DIR}/${dockerfile}" "${ROOT_DIR}"
    log "Loading ${name}:local into kind..."
    kind load docker-image --name "${CLUSTER_NAME}" "${name}:local"
  done
}

apply_manifests() {
  log "Applying TagMind Kubernetes manifests..."
  for manifest in 00-namespace.yaml 01-configmap.yaml 02-secrets.example.yaml 03-deployments.yaml 04-services.yaml 05-ingress.yaml 06-networkpolicy.yaml; do
    kubectl apply -f "${MANIFEST_DIR}/${manifest}"
  done
}

wait_for_deployments() {
  log "Waiting for workloads to become ready..."
  for deploy in tg-gateway orchestrator-api web-retriever llm-gateway; do
    kubectl -n tagmind rollout status "deployment/${deploy}" --timeout=180s
  done
}

print_summary() {
  cat <<'EOF'
=== TagMind kind deployment is ready ===

Check component status:
  kubectl -n tagmind get pods

Ingress endpoints (requires /etc/hosts entry "127.0.0.1 tagmind.local"):
  curl -H 'Host: tagmind.local' http://localhost:8080/tg/healthz
  curl -H 'Host: tagmind.local' http://localhost:8080/orchestrator/healthz
  curl -H 'Host: tagmind.local' http://localhost:8080/retriever/healthz
  curl -H 'Host: tagmind.local' http://localhost:8080/llm/healthz

To remove everything:
  kind delete cluster --name tagmind
EOF
}

ensure_cluster
install_ingress
build_and_load_images
apply_manifests
wait_for_deployments
print_summary
