#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-/opt/smart-exam-cloud}"
RUNTIME_FILE="${ROOT_DIR}/.env.runtime"
LOG_DIR="${ROOT_DIR}/logs"

if [[ ! -f "${RUNTIME_FILE}" ]]; then
  echo "Missing runtime env file: ${RUNTIME_FILE}" >&2
  exit 1
fi

set -a
source "${RUNTIME_FILE}"
set +a

mkdir -p "${LOG_DIR}"

SERVICES=(
  "auth-service"
  "user-service"
  "question-service"
  "exam-service"
  "grading-service"
  "analysis-service"
  "admin-service"
  "gateway-service"
)

for service in "${SERVICES[@]}"; do
  pkill -f "/opt/smart-exam-cloud/services/${service}/target/${service}-0.1.0-SNAPSHOT.jar" || true
done

sleep 3

for service in "${SERVICES[@]}"; do
  jar_path="${ROOT_DIR}/services/${service}/target/${service}-0.1.0-SNAPSHOT.jar"
  log_path="${LOG_DIR}/${service}.log"
  if [[ ! -f "${jar_path}" ]]; then
    echo "Missing jar: ${jar_path}" >&2
    exit 1
  fi
  nohup /usr/bin/java -Xms256m -Xmx512m -jar "${jar_path}" >>"${log_path}" 2>&1 &
  echo "started ${service}"
  sleep 2
done
