#!/usr/bin/env bash
set -euo pipefail
kubectl delete -f "$(dirname "$0")/../infra/k8s/manifests" --ignore-not-found=true
