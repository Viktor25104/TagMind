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

git merge --no-ff --no-commit "$AGENT_BRANCH" || true

echo "Enforcing ignorance of 'ai/' folder..."

git rm -rf ai >/dev/null 2>&1 || true

rm -rf ai 2>/dev/null || true

if git diff --name-only --diff-filter=U | grep -q .; then
  echo "ERROR: Real conflicts detected outside of 'ai/' folder!"
  echo "Please resolve them manually:"
  git status
  exit 1
fi

echo "Ready to commit merge. 'ai/' has been excluded."
echo "Staged changes:"
git diff --cached --name-only
echo ""
echo "If OK: run -> git commit"
