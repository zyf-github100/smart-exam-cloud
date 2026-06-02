#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

run() {
  local label="$1"
  shift
  echo "==> ${label}"
  "$@"
}

run_in() {
  local label="$1"
  local dir="$2"
  shift 2
  echo "==> ${label}"
  (cd "$dir" && "$@")
}

run "Backend Maven tests" mvn -B test
run_in "Web dependency install" "$ROOT_DIR/smart-exam-web" npm ci
run_in "Web tests" "$ROOT_DIR/smart-exam-web" npm run test:ci
run_in "Web build" "$ROOT_DIR/smart-exam-web" npm run build
run "Miniapp static checks" node scripts/ci/check-miniapp.js
run_in "Flutter dependency install" "$ROOT_DIR/smart-exam-flutter" flutter pub get
run_in "Flutter tests" "$ROOT_DIR/smart-exam-flutter" flutter test
