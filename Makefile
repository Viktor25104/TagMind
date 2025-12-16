SHELL := /bin/bash

.PHONY: help
help:
	@echo "Targets:"
	@echo "  make fmt            - форматирование (python/go/rust)"
	@echo "  make lint           - линт (python/go/rust)"
	@echo "  make compose-up     - docker compose up --build"
	@echo "  make compose-down   - docker compose down -v"
	@echo "  make kind-up        - создать kind + ingress-nginx"
	@echo "  make kind-down      - удалить kind"
	@echo "  make k8s-apply      - применить k8s манифесты"
	@echo "  make k8s-delete     - удалить k8s манифесты"
	@echo "  make check          - health-check compose + k8s"

.PHONY: fmt
fmt:
	@echo "== python fmt =="
	python3 -m black services/web-retriever/stub
	@echo "== go fmt =="
	cd services/tg-gateway/stub && gofmt -w .
	@echo "== rust fmt =="
	cd services/llm-gateway/stub && cargo fmt

.PHONY: lint
lint:
	@echo "== python lint =="
	python3 -m ruff check services/web-retriever/stub
	@echo "== go lint =="
	cd services/tg-gateway/stub && golangci-lint run ./...
	@echo "== rust lint =="
	cd services/llm-gateway/stub && cargo clippy -- -D warnings
	@echo "== java build (stub) =="
	cd services/orchestrator-api/stub && mvn -q -DskipTests package

.PHONY: compose-up
compose-up:
	./scripts/dev-compose-up.sh

.PHONY: compose-down
compose-down:
	./scripts/dev-compose-down.sh

.PHONY: kind-up
kind-up:
	./scripts/dev-kind-up.sh

.PHONY: kind-down
kind-down:
	./scripts/dev-kind-down.sh

.PHONY: k8s-apply
k8s-apply:
	./scripts/dev-k8s-apply.sh

.PHONY: k8s-delete
k8s-delete:
	./scripts/dev-k8s-delete.sh

.PHONY: check
check:
	./scripts/dev-check.sh
