#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# check-migration-order.sh  <base-sha>  <head-sha>
#
# Validates Flyway migration files changed between BASE and HEAD do not break
# the production database.  Blocks the deploy if any of these are true:
#
#   1. A migration file already shipped to production was EDITED
#   2. A migration file already shipped to production was RENAMED / DELETED
#   3. A new migration version is LOWER than the last shipped version
#      (out-of-order migrations that Flyway would reject)
#
# Assumes Flyway migrations live in:
#   src/main/resources/db/migration/
# ---------------------------------------------------------------------------
set -euo pipefail

BASE="${1:?Usage: $0 <base-sha> <head-sha>}"
HEAD="${2:?Usage: $0 <base-sha> <head-sha>}"

MIGRATION_DIR="src/main/resources/db/migration"

echo "=== Migration Guard ==="
echo "Base: $BASE"
echo "Head: $HEAD"
echo ""

# --- 1. Collect migration files that existed at BASE -----------------------
mapfile -t BASE_FILES < <(
  git ls-tree -r --name-only "$BASE" -- "$MIGRATION_DIR" 2>/dev/null \
    | grep -E 'V[0-9]+__.*\.sql$' | sort || true
)

# --- 2. Collect migration files that exist at HEAD -------------------------
mapfile -t HEAD_FILES < <(
  git ls-tree -r --name-only "$HEAD" -- "$MIGRATION_DIR" 2>/dev/null \
    | grep -E 'V[0-9]+__.*\.sql$' | sort || true
)

ERRORS=0

# --- 3. Detect edited or deleted files that were already shipped ------------
for f in "${BASE_FILES[@]:-}"; do
  [ -z "$f" ] && continue
  filename=$(basename "$f")

  # Check if the file still exists at HEAD
  if ! printf '%s\n' "${HEAD_FILES[@]:-}" | grep -qF "$filename"; then
    echo "::error::DELETED or RENAMED shipped migration: $filename"
    ERRORS=$((ERRORS + 1))
    continue
  fi

  # Check if the content changed
  BASE_HASH=$(git show "$BASE:$f" | sha256sum | cut -d' ' -f1)
  HEAD_HASH=$(git show "$HEAD:$f" | sha256sum | cut -d' ' -f1)
  if [ "$BASE_HASH" != "$HEAD_HASH" ]; then
    echo "::error::EDITED shipped migration: $filename"
    ERRORS=$((ERRORS + 1))
  fi
done

# --- 4. Detect out-of-order new migrations ---------------------------------
# Highest version number that existed at BASE
LAST_VERSION=""
for f in "${BASE_FILES[@]:-}"; do
  [ -z "$f" ] && continue
  VER=$(basename "$f" | grep -oP '^V\K[0-9]+(?:_[0-9]+)*')
  LAST_VERSION="$VER"
done

if [ -n "$LAST_VERSION" ]; then
  for f in "${HEAD_FILES[@]:-}"; do
    [ -z "$f" ] && continue
    filename=$(basename "$f")
    # Only check files that are NEW (not in BASE)
    if printf '%s\n' "${BASE_FILES[@]:-}" | grep -qF "$filename"; then
      continue
    fi
    NEW_VER=$(echo "$filename" | grep -oP '^V\K[0-9]+(?:_[0-9]+)*')
    # Simple numeric comparison on the first segment
    NEW_MAJOR=$(echo "$NEW_VER" | cut -d_ -f1)
    LAST_MAJOR=$(echo "$LAST_VERSION" | cut -d_ -f1)
    if [ "$NEW_MAJOR" -le "$LAST_MAJOR" ] 2>/dev/null; then
      echo "::error::Out-of-order migration: $filename (last shipped version segment: $LAST_MAJOR)"
      ERRORS=$((ERRORS + 1))
    fi
  done
fi

# --- Result ----------------------------------------------------------------
echo ""
if [ "$ERRORS" -gt 0 ]; then
  echo "::error::Migration guard failed with $ERRORS violation(s). Fix before deploying to production."
  exit 1
fi

echo "Migration guard passed — all Flyway migrations look safe to deploy."

