#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
DEV_DB_NAME="${DEV_DB_NAME:-SATTOLUX_DEV_DB}"
PROD_DB_NAME="${PROD_DB_NAME:-SATTOLUX_DB}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4}"
RESULT_API="${SATTO_RESULT_API:-https://www.dhlottery.co.kr/lt645/selectPstLt645InfoNew.do}"
RESULT_REFERER="${SATTO_RESULT_REFERER:-https://www.dhlottery.co.kr/lt645/result}"
START_DRAW_NO=""
END_DRAW_NO=""

usage() {
    cat <<'EOF'
Usage: scripts/sync-draw-results.sh [--from DRAW_NO] [--to DRAW_NO]

Options:
  --from DRAW_NO   Start draw number. Default: next draw after latest saved DEV DB row, or 1 if empty
  --to DRAW_NO     End draw number. Default: latest available draw number
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --from)
            START_DRAW_NO="${2:-}"
            shift 2
            ;;
        --to)
            END_DRAW_NO="${2:-}"
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

set -a
source "${ENV_FILE}"
set +a

if [[ -z "${DB_URL:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
    echo "DB_URL, DB_USER, DB_PASSWORD must be defined in .env" >&2
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
        --batch \
        --skip-column-names \
        --protocol=tcp \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${DB_USER}" \
        --password="${DB_PASSWORD}" \
        "$@"
}

query_scalar() {
    local database_name="$1"
    local sql="$2"
    docker_mysql "${database_name}" -e "${sql}"
}

upsert_draw_result() {
    local database_name="$1"
    local draw_no="$2"
    local draw_date="$3"
    local no1="$4"
    local no2="$5"
    local no3="$6"
    local no4="$7"
    local no5="$8"
    local no6="$9"
    local bonus_no="${10}"

    docker_mysql "${database_name}" <<SQL
INSERT INTO satto_draw_result (
    draw_no,
    draw_date,
    no1,
    no2,
    no3,
    no4,
    no5,
    no6,
    bonus_no
) VALUES (
    ${draw_no},
    '${draw_date}',
    ${no1},
    ${no2},
    ${no3},
    ${no4},
    ${no5},
    ${no6},
    ${bonus_no}
)
ON DUPLICATE KEY UPDATE
    draw_date = VALUES(draw_date),
    no1 = VALUES(no1),
    no2 = VALUES(no2),
    no3 = VALUES(no3),
    no4 = VALUES(no4),
    no5 = VALUES(no5),
    no6 = VALUES(no6),
    bonus_no = VALUES(bonus_no);
SQL
}

extract_draw_fields() {
    python3 -c '
import json
import sys

payload = json.loads(sys.stdin.read())
for item in payload.get("data", {}).get("list", []):
    fields = [
        str(item["ltEpsd"]),
        item["ltRflYmd"],
        str(item["tm1WnNo"]),
        str(item["tm2WnNo"]),
        str(item["tm3WnNo"]),
        str(item["tm4WnNo"]),
        str(item["tm5WnNo"]),
        str(item["tm6WnNo"]),
        str(item["bnsWnNo"]),
    ]
    print("\t".join(fields))
'
}

fetch_draw_page() {
    local anchor_draw_no="$1"
    curl -fsS "${RESULT_API}?srchDir=center&srchLtEpsd=${anchor_draw_no}" \
        -H "Referer: ${RESULT_REFERER}"
}

page_first_draw_no() {
    python3 -c '
import json
import sys

payload = json.loads(sys.stdin.read())
items = payload.get("data", {}).get("list", [])
if not items:
    sys.exit(1)
print(items[0]["ltEpsd"])
'
}

find_latest_available_draw_no() {
    local low=1
    local high=1
    local page=""

    while true; do
        page="$(fetch_draw_page "${high}")"
        if printf '%s' "${page}" | page_first_draw_no >/dev/null 2>&1; then
            low="${high}"
            high="$((high * 2))"
            continue
        fi
        break
    done

    while [[ "${low}" -lt "${high}" ]]; do
        local mid="$(((low + high + 1) / 2))"
        page="$(fetch_draw_page "${mid}")"
        if printf '%s' "${page}" | page_first_draw_no >/dev/null 2>&1; then
            low="${mid}"
        else
            high="$((mid - 1))"
        fi
    done

    page="$(fetch_draw_page "${low}")"
    printf '%s' "${page}" | page_first_draw_no
}

latest_saved_draw_no="$(query_scalar "${DEV_DB_NAME}" "SELECT COALESCE(MAX(draw_no), 0) FROM satto_draw_result;")"
latest_available_draw_no="$(find_latest_available_draw_no)"

if [[ -z "${END_DRAW_NO}" ]]; then
    END_DRAW_NO="${latest_available_draw_no}"
fi

if [[ -z "${START_DRAW_NO}" ]]; then
    if [[ "${latest_saved_draw_no}" =~ ^[0-9]+$ ]] && [[ "${latest_saved_draw_no}" -gt 0 ]]; then
        START_DRAW_NO="$((latest_saved_draw_no + 1))"
    else
        START_DRAW_NO="1"
    fi
fi

if [[ "${START_DRAW_NO}" -gt "${END_DRAW_NO}" ]]; then
    echo "No draw results to sync. start_draw_no=${START_DRAW_NO}, end_draw_no=${END_DRAW_NO}"
    exit 0
fi

current_anchor="${END_DRAW_NO}"
saved_count=0

while [[ "${current_anchor}" -ge "${START_DRAW_NO}" ]]; do
    page="$(fetch_draw_page "${current_anchor}")"
    page_rows="$(printf '%s' "${page}" | extract_draw_fields)"
    if [[ -z "${page_rows}" ]]; then
        break
    fi

    page_min_draw_no=""
    while IFS=$'\t' read -r draw_no draw_date no1 no2 no3 no4 no5 no6 bonus_no; do
        if [[ -z "${draw_no}" ]]; then
            continue
        fi
        if [[ -z "${page_min_draw_no}" || "${draw_no}" -lt "${page_min_draw_no}" ]]; then
            page_min_draw_no="${draw_no}"
        fi

        if [[ "${draw_no}" -lt "${START_DRAW_NO}" || "${draw_no}" -gt "${END_DRAW_NO}" ]]; then
            continue
        fi

        normalized_draw_date="${draw_date:0:4}-${draw_date:4:2}-${draw_date:6:2}"
        upsert_draw_result "${DEV_DB_NAME}" "${draw_no}" "${normalized_draw_date}" "${no1}" "${no2}" "${no3}" "${no4}" "${no5}" "${no6}" "${bonus_no}"
        upsert_draw_result "${PROD_DB_NAME}" "${draw_no}" "${normalized_draw_date}" "${no1}" "${no2}" "${no3}" "${no4}" "${no5}" "${no6}" "${bonus_no}"
        echo "synced draw_no=${draw_no} draw_date=${normalized_draw_date}"
        saved_count="$((saved_count + 1))"
    done <<< "${page_rows}"

    if [[ -z "${page_min_draw_no}" || "${page_min_draw_no}" -le 1 ]]; then
        break
    fi

    current_anchor="$((page_min_draw_no - 1))"
done

echo "Draw result sync completed. saved_count=${saved_count}, synced_range=${START_DRAW_NO}-${END_DRAW_NO}"
