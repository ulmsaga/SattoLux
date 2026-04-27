#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
SCHEMA_FILE="${ROOT_DIR}/backend/src/main/resources/db/schema.sql"
DEV_SEED_FILE="${ROOT_DIR}/backend/src/main/resources/db/dev-seed.sql"

DEV_DB_NAME="${DEV_DB_NAME:-SATTOLUX_DEV_DB}"
PROD_DB_NAME="${PROD_DB_NAME:-SATTOLUX_DB}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4}"
APPLY_DEV_SEED="false"
TEMP_DEV_SEED_FILE=""

usage() {
    cat <<'EOF'
Usage: scripts/init-db.sh [--with-dev-seed]

Options:
  --with-dev-seed   Seed SATTOLUX_DEV_DB with the shared login account from .env and sample rules
EOF
}

cleanup() {
    if [[ -n "${TEMP_DEV_SEED_FILE}" && -f "${TEMP_DEV_SEED_FILE}" ]]; then
        rm -f "${TEMP_DEV_SEED_FILE}"
    fi
}

trap cleanup EXIT

while [[ $# -gt 0 ]]; do
    case "$1" in
        --with-dev-seed)
            APPLY_DEV_SEED="true"
            shift
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

if [[ ! -f "${SCHEMA_FILE}" ]]; then
    echo "Schema file not found: ${SCHEMA_FILE}" >&2
    exit 1
fi

set -a
source "${ENV_FILE}"
set +a

if [[ -z "${DB_URL:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
    echo "DB_URL, DB_USER, DB_PASSWORD must be defined in .env" >&2
    exit 1
fi

if [[ "${APPLY_DEV_SEED}" == "true" ]]; then
    if [[ -z "${LOGIN_USER:-}" || -z "${LOGIN_PW:-}" ]]; then
        echo "LOGIN_USER, LOGIN_PW must be defined in .env when using --with-dev-seed" >&2
        exit 1
    fi
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

create_database_if_missing() {
    local database_name="$1"
    docker_mysql -e "CREATE DATABASE IF NOT EXISTS \`${database_name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
}

apply_sql_file() {
    local database_name="$1"
    local sql_file="$2"
    docker_mysql "${database_name}" < "${sql_file}"
}

sql_escape() {
    printf "%s" "$1" | sed "s/'/''/g"
}

sed_escape_replacement() {
    printf "%s" "$1" | sed -e 's/[|&]/\\&/g'
}

build_dev_seed_file() {
    if [[ ! -f "${DEV_SEED_FILE}" ]]; then
        echo "Dev seed file not found: ${DEV_SEED_FILE}" >&2
        exit 1
    fi

    local login_user password_hash login_email escaped_user escaped_hash escaped_email
    login_user="${LOGIN_USER}"
    password_hash="$(htpasswd -bnBC 10 "" "${LOGIN_PW}" | tr -d ':\n')"
    login_email="${LOGIN_EMAIL:-${login_user}@sattolux.local}"
    escaped_user="$(sed_escape_replacement "$(sql_escape "${login_user}")")"
    escaped_hash="$(sed_escape_replacement "$(sql_escape "${password_hash}")")"
    escaped_email="$(sed_escape_replacement "$(sql_escape "${login_email}")")"

    TEMP_DEV_SEED_FILE="$(mktemp)"
    sed \
        -e "s|__LOGIN_USER__|${escaped_user}|g" \
        -e "s|__LOGIN_PW_HASH__|${escaped_hash}|g" \
        -e "s|__LOGIN_EMAIL__|${escaped_email}|g" \
        "${DEV_SEED_FILE}" > "${TEMP_DEV_SEED_FILE}"
}

echo "Ensuring databases exist..."
create_database_if_missing "${DEV_DB_NAME}"
create_database_if_missing "${PROD_DB_NAME}"

echo "Applying schema to ${DEV_DB_NAME}..."
apply_sql_file "${DEV_DB_NAME}" "${SCHEMA_FILE}"

echo "Applying schema to ${PROD_DB_NAME}..."
apply_sql_file "${PROD_DB_NAME}" "${SCHEMA_FILE}"

if [[ "${APPLY_DEV_SEED}" == "true" ]]; then
    build_dev_seed_file
    echo "Applying dev seed to ${DEV_DB_NAME}..."
    apply_sql_file "${DEV_DB_NAME}" "${TEMP_DEV_SEED_FILE}"
fi

echo "Database initialization completed."
