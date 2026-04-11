#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

echo "==> Running backend tests"
mvn -B test

echo "==> Installing frontend dependencies"
npm --prefix smart-exam-web ci

echo "==> Running frontend tests"
npm --prefix smart-exam-web run test:ci

echo "==> Building frontend"
npm --prefix smart-exam-web run build
