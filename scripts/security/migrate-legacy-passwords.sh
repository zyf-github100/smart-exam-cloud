#!/usr/bin/env bash
set -euo pipefail

MODE="dry-run"
JDBC_URL=""
DB_USER=""
DB_PASSWORD=""
BATCH_SIZE=200
SAMPLE_SIZE=20
LIMIT=""
REPORT_FILE="runtime-logs/password-migration-report.json"
ROLLBACK_SQL=""
SKIP_ENV_RUNTIME="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode=*) MODE="${1#*=}" ;;
    --jdbc-url=*) JDBC_URL="${1#*=}" ;;
    --db-user=*) DB_USER="${1#*=}" ;;
    --db-password=*) DB_PASSWORD="${1#*=}" ;;
    --batch-size=*) BATCH_SIZE="${1#*=}" ;;
    --sample-size=*) SAMPLE_SIZE="${1#*=}" ;;
    --limit=*) LIMIT="${1#*=}" ;;
    --report-file=*) REPORT_FILE="${1#*=}" ;;
    --rollback-sql=*) ROLLBACK_SQL="${1#*=}" ;;
    --skip-env-runtime) SKIP_ENV_RUNTIME="true" ;;
    *)
      echo "Unsupported argument: $1" >&2
      exit 1
      ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNTIME_FILE="${ROOT_DIR}/.env.runtime"

if [[ "${SKIP_ENV_RUNTIME}" != "true" && -f "${RUNTIME_FILE}" ]]; then
  while IFS='=' read -r key value; do
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    if [[ -z "${key}" || "${key}" == \#* ]]; then
      continue
    fi
    export "${key}=${value}"
  done < "${RUNTIME_FILE}"
fi

if [[ -z "${JDBC_URL}" ]]; then
  if [[ -n "${MYSQL_URL:-}" ]]; then
    JDBC_URL="${MYSQL_URL}"
  else
    JDBC_URL="jdbc:mysql://${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3306}/user_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
  fi
fi

if [[ -z "${DB_USER}" ]]; then
  DB_USER="${MYSQL_USERNAME:-smart_exam_app}"
fi

if [[ -z "${DB_PASSWORD}" ]]; then
  DB_PASSWORD="${MYSQL_PASSWORD:-}"
fi

EXEC_ARGS=(
  "--mode=${MODE}"
  "--jdbc-url=${JDBC_URL}"
  "--db-user=${DB_USER}"
  "--db-password=${DB_PASSWORD}"
  "--batch-size=${BATCH_SIZE}"
  "--sample-size=${SAMPLE_SIZE}"
  "--report-file=${REPORT_FILE}"
)

if [[ -n "${LIMIT}" ]]; then
  EXEC_ARGS+=("--limit=${LIMIT}")
fi

if [[ "${MODE}" == "migrate" && -n "${ROLLBACK_SQL}" ]]; then
  EXEC_ARGS+=("--rollback-sql=${ROLLBACK_SQL}")
fi

cd "${ROOT_DIR}"
exec mvn -f "services/auth-service/pom.xml" -am -DskipTests compile exec:java \
  "-Dexec.mainClass=com.smart.exam.auth.tool.LegacyPasswordMigrationTool" \
  "-Dexec.args=${EXEC_ARGS[*]}"
