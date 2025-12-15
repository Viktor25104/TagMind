#!/usr/bin/env bash
set -euo pipefail

kubectl apply -f "$(dirname "$0")/../infra/k8s/manifests/00-namespace.yaml"
kubectl apply -f "$(dirname "$0")/../infra/k8s/manifests/01-configmap.yaml"
kubectl apply -f "$(dirname "$0")/../infra/k8s/manifests/02-secrets.example.yaml"
kubectl apply -f "$(dirname "$0")/../infra/k8s/manifests/03-deployments.yaml"
kubectl apply -f "$(dirname "$0")/../infra/k8s/manifests/04-services.yaml"
kubectl apply -f "$(dirname "$0")/../infra/k8s/manifests/05-ingress.yaml"
kubectl apply -f "$(dirname "$0")/../infra/k8s/manifests/06-networkpolicy.yaml"

kubectl -n tagmind get all
