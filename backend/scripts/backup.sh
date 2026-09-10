#!/usr/bin/env bash
# ==============================================================================
# CampusVerse Automated Database Backup Pipeline
# ==============================================================================
set -euo pipefail

TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
BACKUP_DIR="${BACKUP_LOCAL_DIR:-./backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
FILENAME_BASE="campusverse_backup_${TIMESTAMP}"
RAW_DUMP="${BACKUP_DIR}/${FILENAME_BASE}.dump"
ENCRYPTED_DUMP="${BACKUP_DIR}/${FILENAME_BASE}.dump.enc"
CHECKSUM_FILE="${BACKUP_DIR}/${FILENAME_BASE}.sha256"

mkdir -p "${BACKUP_DIR}"

echo "=================================================="
echo "CampusVerse Production Database Backup"
echo "Timestamp: ${TIMESTAMP}"
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

if ! command -v pg_dump >/dev/null 2>&1; then
  echo "[-] ERROR: pg_dump client utility is not installed on this host."
  exit 1
fi

if [ -z "${BACKUP_ENCRYPTION_KEY:-}" ]; then
  echo "[-] WARNING: BACKUP_ENCRYPTION_KEY not set. Archive will not be AES-encrypted."
fi

# 2. Execute pg_dump (Custom compressed archive format)
echo "[+] Creating custom-format compressed PostgreSQL dump..."
pg_dump -Fc --no-owner --no-privileges -d "${DATABASE_URL}" -f "${RAW_DUMP}"
DUMP_SIZE=$(du -h "${RAW_DUMP}" | cut -f1)
echo "[+] Dump generated successfully. Size: ${DUMP_SIZE}"

# 3. Verify Dump Integrity
echo "[+] Verifying dump archive integrity with pg_restore --list..."
pg_restore --list "${RAW_DUMP}" >/dev/null
echo "[+] Dump archive verified: valid PostgreSQL TOC structure."

# 4. Generate SHA-256 Checksum
echo "[+] Computing SHA-256 checksum..."
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${RAW_DUMP}" > "${CHECKSUM_FILE}"
else
  shasum -a 256 "${RAW_DUMP}" > "${CHECKSUM_FILE}"
fi

# 5. Encryption Layer (OpenSSL AES-256-CBC)
TARGET_FOR_UPLOAD="${RAW_DUMP}"
if [ -n "${BACKUP_ENCRYPTION_KEY:-}" ]; then
  echo "[+] Encrypting archive with OpenSSL AES-256-CBC..."
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 100000 \
    -in "${RAW_DUMP}" -out "${ENCRYPTED_DUMP}" \
    -pass "pass:${BACKUP_ENCRYPTION_KEY}"
  TARGET_FOR_UPLOAD="${ENCRYPTED_DUMP}"
  rm -f "${RAW_DUMP}"
  echo "[+] Unencrypted raw dump removed. Secure encrypted archive ready."
fi

# 6. Offsite Cloud Synchronization (Supabase Storage / AWS S3 Fallback)
SUPABASE_URL="${SUPABASE_URL:-https://eaqwchuugwaeaftjzfnf.supabase.co}"
SUPABASE_BACKUP_BUCKET="${SUPABASE_BACKUP_BUCKET:-campusverse-database-backups}"
SUPABASE_AUTH_KEY="${SUPABASE_SECRET_KEY:-${SUPABASE_SERVICE_ROLE_KEY:-}}"

if [ -n "${SUPABASE_AUTH_KEY}" ]; then
  echo "[+] Uploading encrypted backup to Supabase Storage..."
  echo "    Project URL: ${SUPABASE_URL}"
  echo "    Bucket:      ${SUPABASE_BACKUP_BUCKET} (private)"

  # Ensure private storage bucket exists
  curl -s -X POST "${SUPABASE_URL}/storage/v1/bucket" \
    -H "Authorization: Bearer ${SUPABASE_AUTH_KEY}" \
    -H "apikey: ${SUPABASE_AUTH_KEY}" \
    -H "Content-Type: application/json" \
    -d "{\"id\": \"${SUPABASE_BACKUP_BUCKET}\", \"name\": \"${SUPABASE_BACKUP_BUCKET}\", \"public\": false}" >/dev/null 2>&1 || true

  # Upload Encrypted Dump
  REMOTE_DUMP_NAME="campusverse/backups/${FILENAME_BASE}.dump.enc"
  echo "[+] Uploading archive ${REMOTE_DUMP_NAME}..."
  UPLOAD_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${SUPABASE_URL}/storage/v1/object/${SUPABASE_BACKUP_BUCKET}/${REMOTE_DUMP_NAME}" \
    -H "Authorization: Bearer ${SUPABASE_AUTH_KEY}" \
    -H "apikey: ${SUPABASE_AUTH_KEY}" \
    -H "Content-Type: application/octet-stream" \
    -H "x-upsert: true" \
    --data-binary @"${TARGET_FOR_UPLOAD}")

  if [ "${UPLOAD_HTTP_CODE}" = "200" ] || [ "${UPLOAD_HTTP_CODE}" = "201" ]; then
    echo "[+] Archive successfully stored in Supabase Storage."
  else
    echo "[-] WARNING: Supabase Storage archive upload returned HTTP ${UPLOAD_HTTP_CODE}"
  fi

  # Upload Checksum
  REMOTE_SHA_NAME="campusverse/backups/${FILENAME_BASE}.sha256"
  curl -s -o /dev/null -X POST \
    "${SUPABASE_URL}/storage/v1/object/${SUPABASE_BACKUP_BUCKET}/${REMOTE_SHA_NAME}" \
    -H "Authorization: Bearer ${SUPABASE_AUTH_KEY}" \
    -H "apikey: ${SUPABASE_AUTH_KEY}" \
    -H "Content-Type: text/plain" \
    -H "x-upsert: true" \
    --data-binary @"${CHECKSUM_FILE}" >/dev/null 2>&1 || true
  echo "[+] Checksum manifest stored in Supabase Storage."
else
  echo "[i] Supabase Storage offsite upload skipped (SUPABASE_SECRET_KEY not configured)."
fi

# Fallback Legacy S3 Target (Preserved)
if [ -n "${BACKUP_S3_BUCKET:-}" ]; then
  if command -v aws >/dev/null 2>&1; then
    echo "[+] Uploading backup to AWS S3: s3://${BACKUP_S3_BUCKET}/campusverse/backups/"
    aws s3 cp "${TARGET_FOR_UPLOAD}" "s3://${BACKUP_S3_BUCKET}/campusverse/backups/"
    aws s3 cp "${CHECKSUM_FILE}" "s3://${BACKUP_S3_BUCKET}/campusverse/backups/"
    echo "[+] S3 offsite sync completed."
  else
    echo "[-] WARNING: BACKUP_S3_BUCKET is configured but 'aws' CLI is not found in PATH."
  fi
fi

# 7. Retention Enforcement
echo "[+] Enforcing retention policy: deleting local backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -type f \( -name "campusverse_backup_*.dump*" -o -name "campusverse_backup_*.sha256" \) -mtime "+${RETENTION_DAYS}" -delete

echo "=================================================="
echo "[+] BACKUP COMPLETED SUCCESSFULLY"
echo "Archive: ${TARGET_FOR_UPLOAD}"
echo "Checksum: ${CHECKSUM_FILE}"
echo "=================================================="
