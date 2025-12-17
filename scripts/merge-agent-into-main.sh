#!/usr/bin/env bash
set -euo pipefail

AGENT_BRANCH="${1:-agent}"

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "Not a git repo"; exit 1; }

current_branch="$(git branch --show-current)"
if [[ "$current_branch" != "main" ]]; then
  echo "ERROR: checkout main first (current: $current_branch)"
  exit 1
fi

git status --porcelain | grep -q . && { echo "ERROR: working tree not clean"; git status --porcelain; exit 1; }

echo "Fetching..."
git fetch origin "$AGENT_BRANCH":"$AGENT_BRANCH" >/dev/null 2>&1 || git fetch origin

echo "Merging $AGENT_BRANCH into main (no commit yet)..."
git merge --no-ff --no-commit "$AGENT_BRANCH"

# Remove ai/ changes from index and workspace (ai must never land in main)
if git ls-files --error-unmatch ai >/dev/null 2>&1; then
  echo "Removing tracked ai/ from main..."
  git rm -r --cached ai >/dev/null 2>&1 || true
fi

echo "Dropping ai/ content from staging/worktree..."
git restore --staged ai 2>/dev/null || true
git checkout -- ai 2>/dev/null || true
rm -rf ai 2>/dev/null || true

echo "Ready to commit merge. Review staged changes:"
git diff --cached --name-only
echo ""
echo "If OK: run -> git commit"
