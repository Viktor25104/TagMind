#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../infra/compose"
docker compose down -v
