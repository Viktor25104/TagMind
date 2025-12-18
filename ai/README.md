# TagMind — AI Knowledge Base

Authoritative, code-synced documentation for AI agents and humans. Treat this directory as the single source of truth. If facts ever diverge from code/configs, update these files.

Recommended reading order for a new AI session:
1. PROJECT_OVERVIEW.md – what TagMind is and the scope of the repo
2. CURRENT_STATE.md – what is implemented today
3. ARCHITECTURE.md – how the system is shaped
4. SERVICES.md – per-service details, including the new tag router behavior
5. CONTRACTS.md – OpenAPI locations and caveats (tg-gateway + orchestrator tag API)
6. RUNBOOK.md – how to run/verify locally (compose + kind/k8s-one-shot)
7. K8S_RUNTIME.md – end-to-end Kubernetes bootstrap via the one-shot script
8. DECISIONS.md and COMMIT_HISTORY.md – why things are the way they are and how we got here
