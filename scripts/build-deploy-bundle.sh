#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="${ROOT_DIR}/frontend"
BACKEND_DIR="${ROOT_DIR}/backend"
DEPLOY_DIR="${ROOT_DIR}/scripts/deploy"
OUTPUT_BASE_DIR="${ROOT_DIR}/dist/deploy"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BUNDLE_NAME="sattolux-deploy-${TIMESTAMP}"
BUNDLE_DIR="${OUTPUT_BASE_DIR}/${BUNDLE_NAME}"
ARCHIVE_PATH="${OUTPUT_BASE_DIR}/${BUNDLE_NAME}.tar.gz"

mkdir -p "${OUTPUT_BASE_DIR}"
rm -rf "${BUNDLE_DIR}"
mkdir -p "${BUNDLE_DIR}/frontend" "${BUNDLE_DIR}/backend/target" "${BUNDLE_DIR}/scripts/deploy" "${BUNDLE_DIR}/scripts"

echo "[1/4] Building frontend dist"
(
    cd "${FRONTEND_DIR}"
    npm run build
)

echo "[2/4] Building backend jar"
(
    cd "${BACKEND_DIR}"
    ./mvnw -q -DskipTests package
)

echo "[3/4] Copying deployment assets"
cp -R "${FRONTEND_DIR}/dist" "${BUNDLE_DIR}/frontend/"
cp "${BACKEND_DIR}/target/sattolux.jar" "${BUNDLE_DIR}/backend/target/"
cp "${ROOT_DIR}/scripts/run-backend.sh" "${BUNDLE_DIR}/scripts/"
cp "${DEPLOY_DIR}/README.md" "${DEPLOY_DIR}/sattolux.service" "${DEPLOY_DIR}/nginx-sattolux.conf" "${BUNDLE_DIR}/scripts/deploy/"

cat > "${BUNDLE_DIR}/DEPLOY_ARTIFACTS.md" <<'EOF'
# Deploy Artifacts

이 번들은 서버 업로드용 최소 파일만 포함한다.

포함 항목:
- `frontend/dist`
- `backend/target/sattolux.jar`
- `scripts/run-backend.sh`
- `scripts/deploy/README.md`
- `scripts/deploy/sattolux.service`
- `scripts/deploy/nginx-sattolux.conf`

주의:
- `.env` 파일은 포함하지 않는다.
- 서버에서 `/opt/sattolux/.env`를 별도로 작성해야 한다.
EOF

echo "[4/4] Creating archive"
tar -czf "${ARCHIVE_PATH}" -C "${OUTPUT_BASE_DIR}" "${BUNDLE_NAME}"

echo
echo "Deploy bundle created"
echo "  directory : ${BUNDLE_DIR}"
echo "  archive   : ${ARCHIVE_PATH}"
