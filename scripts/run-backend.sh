#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
DEFAULT_JAR="${BACKEND_DIR}/target/sattolux-0.0.1-SNAPSHOT.jar"
DEFAULT_ENV_FILE="${ROOT_DIR}/.env"
DEFAULT_LOG_FILE="${BACKEND_DIR}/logs/sattolux.log"
DEFAULT_PID_FILE="${BACKEND_DIR}/run/sattolux.pid"

ENV_FILE="${DEFAULT_ENV_FILE}"
JAR_FILE="${DEFAULT_JAR}"
PROFILE=""
PORT=""
JAVA_OPTS_INPUT=""
DAEMON_MODE="false"
BUILD_FIRST="false"
LOG_FILE="${DEFAULT_LOG_FILE}"
PID_FILE="${DEFAULT_PID_FILE}"
APP_ARGS=()

usage() {
    cat <<'EOF'
Usage: scripts/run-backend.sh [options] [-- <spring-args...>]

Options:
  --env-file FILE     Load environment variables from FILE (default: .env)
  --jar FILE          Spring Boot jar path (default: backend/target/sattolux-0.0.1-SNAPSHOT.jar)
  --profile PROFILE   spring profile override (default: SPRING_PROFILES_ACTIVE or prod)
  --port PORT         server.port override (default: SERVER_PORT or 8081)
  --java-opts OPTS    Extra JVM options string
  --build             Run backend package build before start
  --daemon            Run in background and write pid/log files
  --log-file FILE     Log file path for --daemon
  --pid-file FILE     PID file path for --daemon
  -h, --help          Show help

Examples:
  scripts/run-backend.sh --profile prod --port 8081
  scripts/run-backend.sh --env-file /opt/sattolux/.env --daemon --build
  scripts/run-backend.sh -- --logging.level.com.saga.sattolux=INFO
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --jar)
            JAR_FILE="$2"
            shift 2
            ;;
        --profile)
            PROFILE="$2"
            shift 2
            ;;
        --port)
            PORT="$2"
            shift 2
            ;;
        --java-opts)
            JAVA_OPTS_INPUT="$2"
            shift 2
            ;;
        --build)
            BUILD_FIRST="true"
            shift
            ;;
        --daemon)
            DAEMON_MODE="true"
            shift
            ;;
        --log-file)
            LOG_FILE="$2"
            shift 2
            ;;
        --pid-file)
            PID_FILE="$2"
            shift 2
            ;;
        --)
            shift
            APP_ARGS=("$@")
            break
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [[ -f "${ENV_FILE}" ]]; then
    set -a
    source "${ENV_FILE}"
    set +a
fi

PROFILE="${PROFILE:-${SPRING_PROFILES_ACTIVE:-prod}}"
PORT="${PORT:-${SERVER_PORT:-8081}}"

if [[ -z "${DB_URL:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" || -z "${JWT_SECRET:-}" ]]; then
    echo "DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET must be set before starting backend." >&2
    exit 1
fi

if [[ "${PROFILE}" == *prod* ]] && [[ "${SPRING_SQL_INIT_MODE:-}" == "always" ]]; then
    echo "SPRING_SQL_INIT_MODE=always is not recommended for production. Use 'never' unless schema init is intentional." >&2
fi

if [[ "${BUILD_FIRST}" == "true" ]]; then
    (
        cd "${BACKEND_DIR}"
        ./mvnw -q -DskipTests package
    )
fi

if [[ ! -f "${JAR_FILE}" ]]; then
    echo "Jar file not found: ${JAR_FILE}" >&2
    echo "Run with --build or package backend first." >&2
    exit 1
fi

mkdir -p "$(dirname "${LOG_FILE}")" "$(dirname "${PID_FILE}")"

JAVA_CMD=(java)
if [[ -n "${JAVA_OPTS:-}" ]]; then
    # shellcheck disable=SC2206
    JAVA_ENV_OPTS=( ${JAVA_OPTS} )
    JAVA_CMD+=("${JAVA_ENV_OPTS[@]}")
fi
if [[ -n "${JAVA_OPTS_INPUT}" ]]; then
    # shellcheck disable=SC2206
    JAVA_INPUT_OPTS=( ${JAVA_OPTS_INPUT} )
    JAVA_CMD+=("${JAVA_INPUT_OPTS[@]}")
fi

SPRING_ARGS=(
    "--spring.profiles.active=${PROFILE}"
    "--server.port=${PORT}"
)

if [[ ${#APP_ARGS[@]} -gt 0 ]]; then
    SPRING_ARGS+=("${APP_ARGS[@]}")
fi

echo "Starting SattoLux backend"
echo "  profile : ${PROFILE}"
echo "  port    : ${PORT}"
echo "  jar     : ${JAR_FILE}"
if [[ -f "${ENV_FILE}" ]]; then
    echo "  env     : ${ENV_FILE}"
else
    echo "  env     : <not loaded>"
fi

if [[ "${DAEMON_MODE}" == "true" ]]; then
    nohup "${JAVA_CMD[@]}" -jar "${JAR_FILE}" "${SPRING_ARGS[@]}" >> "${LOG_FILE}" 2>&1 &
    echo $! > "${PID_FILE}"
    echo "  mode    : daemon"
    echo "  pid     : $(cat "${PID_FILE}")"
    echo "  log     : ${LOG_FILE}"
else
    exec "${JAVA_CMD[@]}" -jar "${JAR_FILE}" "${SPRING_ARGS[@]}"
fi
