#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
SEED_FILE="${ROOT_DIR}/backend/src/main/resources/db/prod-account-seed.sql"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4}"
TARGET_DB="${PROD_DB_NAME:-SATTOLUX_DB}"
TEMP_SEED_FILE=""

usage() {
    cat <<'EOF'
Usage: scripts/sync-prod-accounts.sh [options]

Options:
  --env-file FILE    Load environment variables from FILE (default: .env)
  --database NAME    Target database name (default: SATTOLUX_DB)
  -h, --help         Show help
EOF
}

cleanup() {
    if [[ -n "${TEMP_SEED_FILE}" && -f "${TEMP_SEED_FILE}" ]]; then
        rm -f "${TEMP_SEED_FILE}"
    fi
}

trap cleanup EXIT

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --database)
            TARGET_DB="$2"
            shift 2
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

if [[ ! -f "${ENV_FILE}" ]]; then
    echo ".env file not found: ${ENV_FILE}" >&2
    exit 1
fi

if [[ ! -f "${SEED_FILE}" ]]; then
    echo "Seed file not found: ${SEED_FILE}" >&2
    exit 1
fi

set -a
source "${ENV_FILE}"
set +a

if [[ -z "${DB_URL:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
    echo "DB_URL, DB_USER, DB_PASSWORD must be defined in .env" >&2
    exit 1
fi

if [[ -z "${LOGIN_ADMIN:-}" || -z "${LOGIN_ADMIN_PW:-}" ]]; then
    echo "LOGIN_ADMIN, LOGIN_ADMIN_PW must be defined in .env" >&2
    exit 1
fi

if [[ -z "${LOGIN_USER:-}" || -z "${LOGIN_PW:-}" ]]; then
    echo "LOGIN_USER, LOGIN_PW must be defined in .env" >&2
    exit 1
fi

DB_URL_BODY="${DB_URL#jdbc:mysql://}"
DB_HOST_PORT="${DB_URL_BODY%%/*}"
DB_HOST="${DB_HOST_PORT%%:*}"
DB_PORT="${DB_HOST_PORT##*:}"

if [[ "${DB_HOST}" == "${DB_HOST_PORT}" ]]; then
    DB_PORT="13306"
fi

docker_mysql() {
    docker run --rm -i "${MYSQL_IMAGE}" \
        mysql \
        --protocol=tcp \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${DB_USER}" \
        --password="${DB_PASSWORD}" \
        "$@"
}

sql_escape() {
    printf "%s" "$1" | sed "s/'/''/g"
}

sed_escape_replacement() {
    printf "%s" "$1" | sed -e 's/[|&]/\\&/g'
}

build_seed_file() {
    local login_admin admin_hash admin_email escaped_admin_user escaped_admin_hash escaped_admin_email
    local login_user login_hash login_email escaped_user escaped_hash escaped_email

    login_admin="${LOGIN_ADMIN}"
    admin_hash="$(htpasswd -bnBC 10 "" "${LOGIN_ADMIN_PW}" | tr -d ':\n')"
    admin_email="${LOGIN_ADMIN_EMAIL:-${login_admin}@sattolux.local}"

    login_user="${LOGIN_USER}"
    login_hash="$(htpasswd -bnBC 10 "" "${LOGIN_PW}" | tr -d ':\n')"
    login_email="${LOGIN_EMAIL:-${login_user}@sattolux.local}"

    escaped_admin_user="$(sed_escape_replacement "$(sql_escape "${login_admin}")")"
    escaped_admin_hash="$(sed_escape_replacement "$(sql_escape "${admin_hash}")")"
    escaped_admin_email="$(sed_escape_replacement "$(sql_escape "${admin_email}")")"
    escaped_user="$(sed_escape_replacement "$(sql_escape "${login_user}")")"
    escaped_hash="$(sed_escape_replacement "$(sql_escape "${login_hash}")")"
    escaped_email="$(sed_escape_replacement "$(sql_escape "${login_email}")")"

    TEMP_SEED_FILE="$(mktemp)"
    sed \
        -e "s|__LOGIN_ADMIN__|${escaped_admin_user}|g" \
        -e "s|__LOGIN_ADMIN_PW_HASH__|${escaped_admin_hash}|g" \
        -e "s|__LOGIN_ADMIN_EMAIL__|${escaped_admin_email}|g" \
        -e "s|__LOGIN_USER__|${escaped_user}|g" \
        -e "s|__LOGIN_PW_HASH__|${escaped_hash}|g" \
        -e "s|__LOGIN_EMAIL__|${escaped_email}|g" \
        "${SEED_FILE}" > "${TEMP_SEED_FILE}"
}

echo "Syncing login accounts and default generation rules to ${TARGET_DB}..."
build_seed_file
docker_mysql "${TARGET_DB}" < "${TEMP_SEED_FILE}"
echo "Sync completed for ${TARGET_DB}."
