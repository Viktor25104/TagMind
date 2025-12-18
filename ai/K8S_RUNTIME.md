# Kubernetes Runtime (kind one-shot)

Цель: запустить весь стек TagMind в локальном kind-кластере одной командой и получить рабочие entrypoints через ingress.

## Быстрый старт

```bash
scripts/k8s-one-shot.sh
```

Скрипт выполняет все шаги:

1. Создаёт (или переиспользует) kind-кластер `tagmind` с портами 8080/8443.
2. Устанавливает `ingress-nginx` и ждёт его готовности.
3. Собирает локальные образы сервисов (`tg-gateway`, `orchestrator-api`, `web-retriever`, `llm-gateway`) и загружает их в kind.
4. Применяет все манифесты из `infra/k8s/manifests`.
5. Ждёт, пока деплойменты станут `Ready`.
6. Выводит подсказки по проверке/удалению.

> Требования: установленный `kind`, `kubectl`, `docker`.

## Проверка готовности

```bash
kubectl -n tagmind get pods
kubectl -n tagmind get ingress tagmind-ingress
```

Добавьте `/etc/hosts` запись:

```
127.0.0.1 tagmind.local
```

Smoke health:

```bash
curl -H 'Host: tagmind.local' http://localhost:8080/tg/healthz
curl -H 'Host: tagmind.local' http://localhost:8080/orchestrator/healthz
curl -H 'Host: tagmind.local' http://localhost:8080/retriever/healthz
curl -H 'Host: tagmind.local' http://localhost:8080/llm/healthz
```

## Взаимодействие с API

- Telegram dev endpoint: `curl -H 'Host: tagmind.local' -H 'Content-Type: application/json' http://localhost:8080/tg/v1/tg/dev/message ...`
- Orchestrator tag API: `curl -H 'Host: tagmind.local' http://localhost:8080/orchestrator/v1/conversations/tag ...`

## Логи

```bash
kubectl -n tagmind logs deploy/tg-gateway -f
kubectl -n tagmind logs deploy/orchestrator-api -f
```

## Снос окружения

```bash
kind delete cluster --name tagmind
```

Повторный запуск `scripts/k8s-one-shot.sh` пересоберёт образы и перезальёт их в кластер.
