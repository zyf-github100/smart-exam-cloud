#!/usr/bin/env bash
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-http://192.168.242.10:8848}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
TENANT="${NACOS_NAMESPACE:-}"
USERNAME="${NACOS_USERNAME:-}"
PASSWORD="${NACOS_PASSWORD:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_IDS=(
  "common.yaml"
  "gateway-service.yaml"
  "auth-service.yaml"
  "user-service.yaml"
  "question-service.yaml"
  "exam-service.yaml"
  "grading-service.yaml"
  "analysis-service.yaml"
  "admin-service.yaml"
)

TOKEN=""
if [[ -n "$USERNAME" && -n "$PASSWORD" ]]; then
  LOGIN_RESP="$(curl -fsS -X POST "${NACOS_ADDR}/nacos/v1/auth/login" \
    --data-urlencode "username=${USERNAME}" \
    --data-urlencode "password=${PASSWORD}" || true)"
  TOKEN="$(printf '%s' "$LOGIN_RESP" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  if [[ -n "$TOKEN" ]]; then
    echo "Nacos login success."
  else
    echo "Nacos login failed or token empty, continue without token."
  fi
fi

for f in "${DATA_IDS[@]}"; do
  file="${SCRIPT_DIR}/${f}"
  if [[ ! -f "$file" ]]; then
    echo "Config file not found: $file" >&2
    exit 1
  fi

  args=(
    --data-urlencode "dataId=${f}"
    --data-urlencode "group=${GROUP}"
    --data-urlencode "type=yaml"
    --data-urlencode "content@${file}"
  )
  if [[ -n "$TENANT" ]]; then
    args+=(--data-urlencode "tenant=${TENANT}")
  fi
  if [[ -n "$TOKEN" ]]; then
    args+=(--data-urlencode "accessToken=${TOKEN}")
  fi

  resp="$(curl -fsS -X POST "${NACOS_ADDR}/nacos/v1/cs/configs" "${args[@]}")"
  if [[ "$resp" != "true" ]]; then
    echo "Import failed: ${f}, response=${resp}" >&2
    exit 1
  fi
  echo "Imported: ${f}"
done

echo "All nacos configs imported."
