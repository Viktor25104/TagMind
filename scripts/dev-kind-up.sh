#!/usr/bin/env bash
set -euo pipefail

echo "Recreating kind cluster: tagmind"

kind delete cluster --name tagmind >/dev/null 2>&1 || true
kind create cluster --config "$(dirname "$0")/../infra/k8s/kind/kind-config.yaml"

echo "Installing ingress-nginx..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

echo "Waiting for ingress-nginx deployment rollout..."
kubectl rollout status deployment/ingress-nginx-controller \
  -n ingress-nginx \
  --timeout=180s

echo "Ingress-nginx is ready."
