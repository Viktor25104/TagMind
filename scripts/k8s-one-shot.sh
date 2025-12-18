#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST_DIR="${ROOT_DIR}/infra/k8s/manifests"
KIND_CONFIG="${ROOT_DIR}/infra/k8s/kind/kind-config.yaml"
CLUSTER_NAME="tagmind"

# log prints the given message(s) prefixed with '[k8s]'.
log() {
  printf '[k8s] %s\n' "$*"
}

# ensure_cluster ensures a Kind cluster named "tagmind" exists, creating it using KIND_CONFIG if it is missing.
# Exits with status 1 if the `kind` command is not available.
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

# install_ingress applies the ingress-nginx controller manifest for Kind and waits for the ingress-nginx-controller deployment to finish rolling out.
install_ingress() {
  log "Applying ingress-nginx controller..."
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
  log "Waiting for ingress-nginx rollout..."
  kubectl rollout status deployment/ingress-nginx-controller -n ingress-nginx --timeout=180s
}

# build_and_load_images builds local Docker images for tg-gateway, orchestrator-api, web-retriever, and llm-gateway, tags them with `:local`, and loads them into the Kind cluster identified by `CLUSTER_NAME`.
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

# apply_manifests applies TagMind Kubernetes manifest files from the manifests directory in the required deployment order.
apply_manifests() {
  log "Applying TagMind Kubernetes manifests..."
  local manifests=(
    00-namespace.yaml
    01-configmap.yaml
    02-secrets.yaml
    03-deployments.yaml
    04-services.yaml
    05-ingress.yaml
    06-networkpolicy.yaml
    07-postgres.yaml
  )
  for manifest in "${manifests[@]}"; do
    kubectl apply -f "${MANIFEST_DIR}/${manifest}"
  done
}

# wait_for_deployments waits for core TagMind workloads to become ready by waiting for the postgres statefulset and the tg-gateway, orchestrator-api, web-retriever, and llm-gateway deployments to finish rolling out (180s timeout each).
wait_for_deployments() {
  log "Waiting for workloads to become ready..."
  log "Waiting for postgres statefulset..."
  kubectl -n tagmind rollout status statefulset/postgres --timeout=180s
  for deploy in tg-gateway orchestrator-api web-retriever llm-gateway; do
    kubectl -n tagmind rollout status "deployment/${deploy}" --timeout=180s
  done
}

# verify_ingress verifies that each configured ingress host responds successfully to /healthz when requested via localhost:8080 with the appropriate Host header.
verify_ingress() {
  log "Verifying ingress endpoints..."
  local hosts=(
    tg.tagmind.local
    orchestrator.tagmind.local
    retriever.tagmind.local
    llm.tagmind.local
  )
  for host in "${hosts[@]}"; do
    local url="http://localhost:8080/healthz"
    if ! curl -fsS -H "Host: ${host}" "${url}" >/dev/null; then
      log "Ingress health check failed for host ${host} (${url})"
      exit 1
    fi
    log "ok: ${host} -> ${url}"
  done
}

# print_summary prints a brief readiness summary with commands to check pods, perform ingress health checks, and delete the TagMind Kind cluster.
print_summary() {
  cat <<'EOF'
=== TagMind kind deployment is ready ===

Check component status:
  kubectl -n tagmind get pods

Ingress endpoints (requires /etc/hosts entries for *.tagmind.local -> 127.0.0.1):
  curl -H 'Host: tg.tagmind.local' http://localhost:8080/healthz
  curl -H 'Host: orchestrator.tagmind.local' http://localhost:8080/healthz
  curl -H 'Host: retriever.tagmind.local' http://localhost:8080/healthz
  curl -H 'Host: llm.tagmind.local' http://localhost:8080/healthz

To remove everything:
  kind delete cluster --name tagmind
EOF
}

ensure_cluster
install_ingress
build_and_load_images
apply_manifests
wait_for_deployments
verify_ingress
print_summary