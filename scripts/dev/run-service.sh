#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <service-name>" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNTIME_FILE="${ROOT_DIR}/.env.runtime"
SERVICE="$1"

if [[ ! -f "${RUNTIME_FILE}" ]]; then
  echo "Missing runtime file: ${RUNTIME_FILE}" >&2
  exit 1
fi

while IFS='=' read -r key value; do
  key="${key#"${key%%[![:space:]]*}"}"
  key="${key%"${key##*[![:space:]]}"}"
  if [[ -z "${key}" || "${key}" == \#* || -z "${value:-}" ]]; then
    continue
  fi
  export "${key}=${value}"
done < "${RUNTIME_FILE}"

cd "${ROOT_DIR}"
exec mvn -f "services/${SERVICE}/pom.xml" -am spring-boot:run
