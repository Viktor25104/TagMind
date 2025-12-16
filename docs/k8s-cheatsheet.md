# Kubernetes Cheat Sheet — TagMind

Project namespace: `tagmind`

---

## Quick Status

All resources:
```bash
kubectl -n tagmind get all
````

Pods:

```bash
kubectl -n tagmind get pods -o wide
```

Ingress:

```bash
kubectl -n tagmind get ingress
kubectl -n tagmind describe ingress tagmind-ingress
```

Recent events:

```bash
kubectl -n tagmind get events --sort-by=.lastTimestamp | tail -n 50
```

---

## Logs

Deployment logs:

```bash
kubectl -n tagmind logs deploy/orchestrator-api --tail=200
kubectl -n tagmind logs deploy/tg-gateway --tail=200
kubectl -n tagmind logs deploy/web-retriever --tail=200
kubectl -n tagmind logs deploy/llm-gateway --tail=200
```

Follow logs:

```bash
kubectl -n tagmind logs -f deploy/orchestrator-api
```

Previous crash logs:

```bash
kubectl -n tagmind logs <pod-name> --previous --tail=200
```

---

## Exec into Container

```bash
kubectl -n tagmind exec -it deploy/orchestrator-api -- sh
```

Environment variables:

```bash
kubectl -n tagmind exec -it deploy/orchestrator-api -- env | sort
```

---

## Rollout Management

Restart deployment:

```bash
kubectl -n tagmind rollout restart deployment/orchestrator-api
```

Check rollout status:

```bash
kubectl -n tagmind rollout status deployment/orchestrator-api --timeout=120s
```

---

## Internal Networking Test

Test service DNS resolution:

```bash
kubectl -n tagmind exec -it deploy/orchestrator-api -- sh -lc "wget -qO- http://web-retriever/healthz && echo"
```

---

## Ingress Test (kind)

```bash
curl -H 'Host: tagmind.local' http://localhost:8080/tg/healthz
curl -H 'Host: tagmind.local' http://localhost:8080/orchestrator/healthz
curl -H 'Host: tagmind.local' http://localhost:8080/retriever/healthz
curl -H 'Host: tagmind.local' http://localhost:8080/llm/healthz
```

---

## Cleanup

Delete Kubernetes resources:

```bash
make k8s-delete
```

Delete kind cluster:

```bash
make kind-down
