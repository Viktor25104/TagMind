# TagMind — Development Setup (macOS)

TagMind is a polyglot monorepo built with Go, Java, Python, and Rust.
All services are designed to run via Docker and Kubernetes.
Local tooling is unified via Makefile.

This document describes how to set up a development environment on macOS.

---

## 1. Requirements

- macOS (Apple Silicon or Intel)
- Docker Desktop
- Homebrew
- kubectl, kind
- JDK 21 + Maven
- Go
- Rust (cargo)
- Python 3.x

---

## 2. Installation via Homebrew

```bash
brew update

brew install git make jq yq tree
brew install kubectl kind helm
brew install pre-commit
brew install go golangci-lint
brew install openjdk@21 maven
brew install rustup

rustup-init -y
source "$HOME/.cargo/env"
rustup component add rustfmt clippy

python3 -m pip install --user -U ruff black
````

Ensure Cargo is available in PATH:

```bash
echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

## 3. Verify Installation

```bash
java -version
mvn -version
go version
golangci-lint version
rustc --version
cargo --version
kubectl version --client
kind version
python3 -m ruff --version
python3 -m black --version
```

All commands must return versions without errors.

---

## 4. IDE Setup (JetBrains)

Recommended IDE per service:

* **GoLand** → `services/tg-gateway`
* **IntelliJ IDEA Ultimate** → `services/orchestrator-api`
* **PyCharm Professional** → `services/web-retriever`
* **RustRover** → `services/llm-gateway`

Recommended plugins for all IDEs:

* Kubernetes
* Docker
* YAML
* Makefile Language
* EditorConfig

---

## 5. Unified Project Commands (Makefile)

Format all code:

```bash
make fmt
```

Run linters and build stubs:

```bash
make lint
```

---

## 6. Local Development with Docker Compose

```bash
make compose-up
make check
make compose-down
```

Expected health endpoints:

* [http://localhost:8081/healthz](http://localhost:8081/healthz)
* [http://localhost:8082/healthz](http://localhost:8082/healthz)
* [http://localhost:8083/healthz](http://localhost:8083/healthz)
* [http://localhost:8084/healthz](http://localhost:8084/healthz)

---

## 7. Local Kubernetes (kind + ingress-nginx)

### 7.1 Create cluster and ingress

```bash
make kind-up
```

### 7.2 Build images and load into kind

```bash
docker build -t tg-gateway:local -f services/tg-gateway/Dockerfile .
docker build -t orchestrator-api:local -f services/orchestrator-api/Dockerfile .
docker build -t web-retriever:local -f services/web-retriever/Dockerfile .
docker build -t llm-gateway:local -f services/llm-gateway/Dockerfile .

kind load docker-image tg-gateway:local --name tagmind
kind load docker-image orchestrator-api:local --name tagmind
kind load docker-image web-retriever:local --name tagmind
kind load docker-image llm-gateway:local --name tagmind
```

### 7.3 Apply Kubernetes manifests

```bash
make k8s-apply
```

### 7.4 Verify

```bash
make check
kubectl -n tagmind get pods
```

Ingress is available locally via:

* [http://localhost:8080](http://localhost:8080) with host header `tagmind.local`

Example:

```bash
curl -H 'Host: tagmind.local' http://localhost:8080/tg/healthz
```

---

## 8. Cleanup

```bash
make k8s-delete
make kind-down
```