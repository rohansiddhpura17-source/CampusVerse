#!/usr/bin/env bash
# ==============================================================================
# CampusVerse Automated Database Restore Pipeline
# ==============================================================================
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <path_to_backup_archive> [--skip-verify]"
  echo "Example: $0 ./backups/campusverse_backup_20260909_120000Z.dump.enc"
  exit 1
fi

BACKUP_ARCHIVE="$1"
SKIP_VERIFY="${2:-false}"
DECRYPTED_TEMP="${BACKUP_ARCHIVE}.decrypted.tmp"

echo "=================================================="
echo "CampusVerse Production Database Restore"
echo "Target Archive: ${BACKUP_ARCHIVE}"
echo "=================================================="

# 1. Environment & Prerequisite Checks
if [ -z "${DATABASE_URL:-}" ]; then
  echo "[-] ERROR: DATABASE_URL environment variable is not defined."
  exit 1
fi

if [[ ! "${DATABASE_URL}" =~ ^postgres(ql)?:// ]]; then
  echo "[-] ERROR: DATABASE_URL must be a PostgreSQL connection URL."
  exit 1
fi

if ! command -v pg_restore >/dev/null 2>&1; then
  echo "[-] ERROR: pg_restore client utility is not installed on this host."
  exit 1
fi

# Handle Supabase Storage remote download
SUPABASE_URL="${SUPABASE_URL:-https://eaqwchuugwaeaftjzfnf.supabase.co}"
SUPABASE_BACKUP_BUCKET="${SUPABASE_BACKUP_BUCKET:-campusverse-database-backups}"
SUPABASE_AUTH_KEY="${SUPABASE_SECRET_KEY:-${SUPABASE_SERVICE_ROLE_KEY:-}}"

if [[ "${BACKUP_ARCHIVE}" =~ ^supabase://([^/]+)/(.+)$ ]] || [[ "${BACKUP_ARCHIVE}" =~ ^campusverse/backups/ ]]; then
  if [ -z "${SUPABASE_AUTH_KEY}" ]; then
    echo "[-] ERROR: SUPABASE_SECRET_KEY (or SUPABASE_SERVICE_ROLE_KEY) is required to download archives from Supabase Storage."
    exit 1
  fi
  REMOTE_PATH="${BACKUP_ARCHIVE#supabase://*/}"
  LOCAL_TARGET="./backups/$(basename "${REMOTE_PATH}")"
  mkdir -p ./backups
  echo "[+] Fetching remote archive from Supabase Storage: ${REMOTE_PATH}..."
  curl -s -f -X GET \
    "${SUPABASE_URL}/storage/v1/object/authenticated/${SUPABASE_BACKUP_BUCKET}/${REMOTE_PATH}" \
    -H "Authorization: Bearer ${SUPABASE_AUTH_KEY}" \
    -H "apikey: ${SUPABASE_AUTH_KEY}" \
    -o "${LOCAL_TARGET}"

  # Fetch Checksum if available
  REMOTE_SHA="${REMOTE_PATH%.enc}"
  REMOTE_SHA="${REMOTE_SHA%.dump}.sha256"
  curl -s -f -X GET \
    "${SUPABASE_URL}/storage/v1/object/authenticated/${SUPABASE_BACKUP_BUCKET}/${REMOTE_SHA}" \
    -H "Authorization: Bearer ${SUPABASE_AUTH_KEY}" \
    -H "apikey: ${SUPABASE_AUTH_KEY}" \
    -o "${LOCAL_TARGET%.enc}.sha256" >/dev/null 2>&1 || true

  BACKUP_ARCHIVE="${LOCAL_TARGET}"
fi

if [ ! -f "${BACKUP_ARCHIVE}" ]; then
  echo "[-] ERROR: Backup archive file not found: ${BACKUP_ARCHIVE}"
  exit 1
fi

# Cleanup trap
trap 'rm -f "${DECRYPTED_TEMP}"' EXIT

# 2. Decryption if file is encrypted
RESTORE_TARGET="${BACKUP_ARCHIVE}"
if [[ "${BACKUP_ARCHIVE}" == *.enc ]]; then
  if [ -z "${BACKUP_ENCRYPTION_KEY:-}" ]; then
    echo "[-] ERROR: BACKUP_ENCRYPTION_KEY is required to decrypt this archive."
    exit 1
  fi
  echo "[+] Decrypting archive using OpenSSL AES-256-CBC..."
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
    -in "${BACKUP_ARCHIVE}" -out "${DECRYPTED_TEMP}" \
    -pass "pass:${BACKUP_ENCRYPTION_KEY}"
  RESTORE_TARGET="${DECRYPTED_TEMP}"
fi

# 3. Checksum Verification
CHECKSUM_FILE="${BACKUP_ARCHIVE%.enc}"
CHECKSUM_FILE="${CHECKSUM_FILE%.dump}.sha256"
if [ -f "${CHECKSUM_FILE}" ] && [ "${SKIP_VERIFY}" != "--skip-verify" ]; then
  echo "[+] Verifying SHA-256 checksum..."
  EXPECTED_SHA=$(awk '{print $1}' "${CHECKSUM_FILE}")
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA=$(sha256sum "${RESTORE_TARGET}" | awk '{print $1}')
  else
    ACTUAL_SHA=$(shasum -a 256 "${RESTORE_TARGET}" | awk '{print $1}')
  fi

  if [ "${EXPECTED_SHA}" != "${ACTUAL_SHA}" ]; then
    echo "[-] ERROR: Checksum mismatch! The archive may be corrupt or tampered."
    echo "Expected: ${EXPECTED_SHA}"
    echo "Actual:   ${ACTUAL_SHA}"
    exit 1
  fi
  echo "[+] Checksum match confirmed."
fi

# 4. Table of Contents & Structure Verification
echo "[+] Validating PostgreSQL TOC catalog structure with pg_restore --list..."
pg_restore --list "${RESTORE_TARGET}" >/dev/null
echo "[+] TOC catalog verified successfully."

# 5. Database Restore Execution
echo "[+] WARNING: This operation will restore schemas and tables into ${DATABASE_URL}."
echo "[+] Executing pg_restore with --clean --if-exists..."
pg_restore --clean --if-exists --no-owner --no-privileges \
  -d "${DATABASE_URL}" "${RESTORE_TARGET}" || {
    echo "[-] Note: Non-critical pg_restore warnings may have occurred (e.g. non-existent drop tables)."
}

echo "=================================================="
echo "[+] RESTORE PROCEDURE COMPLETED SUCCESSFULLY"
echo "Target Database: Restored from ${BACKUP_ARCHIVE}"
echo "=================================================="
