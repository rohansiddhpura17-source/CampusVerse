# CampusVerse — Database Backup & Disaster Recovery Runbook

## 1. Backup Strategy Overview
CampusVerse production architecture utilizes managed PostgreSQL with a defense-in-depth backup and disaster recovery policy:

- **Point-In-Time Recovery (PITR)**: Continuous Write-Ahead Log (WAL) archiving provided natively by Supabase PostgreSQL enabling restoration to any second within the retention window.
- **Daily Automated Full Snapshots**: Executed daily at 02:00 UTC using `pg_dump -Fc` custom-format compressed archives.
- **Offsite Redundancy via Supabase Storage**: Encrypted snapshots mirrored directly to a private Supabase Storage bucket (`campusverse-database-backups`) in the same unified project.
- **Zero-Trust Encryption**: Snapshots encrypted at rest with OpenSSL AES-256-CBC (PBKDF2, 100,000 iterations) using a dedicated, key-rotated secret `BACKUP_ENCRYPTION_KEY`.
- **Zero AWS Dependencies**: No AWS account, IAM user, or S3 bucket required. Uses native Supabase Storage REST API authenticated via `SUPABASE_SECRET_KEY` (or legacy `SUPABASE_SERVICE_ROLE_KEY`).

---

## 2. Retention Policy
- **Daily Snapshots**: Retained for 30 days.
- **Weekly Snapshots**: Retained for 12 weeks (90 days).
- **Monthly Snapshots**: Retained for 12 months (365 days).
- **Continuous WAL Archives**: 7-day rolling window for sub-minute point-in-time recovery.

---

## 3. Automated Backup Pipeline (`backend/scripts/backup.sh`)
The automated backup script performs:
1. Prerequisite validation of `DATABASE_URL` (PostgreSQL connection string).
2. Atomic `pg_dump -Fc` extraction.
3. Pre-encryption Table of Contents (TOC) integrity verification via `pg_restore --list`.
4. SHA-256 cryptographic hash manifest generation.
5. AES-256 symmetric encryption.
6. Direct HTTPS upload to private Supabase Storage bucket (`campusverse-database-backups`) via REST API.
7. Local retention pruning of backups exceeding `BACKUP_RETENTION_DAYS`.

### Production Crontab / Systemd Timer Configuration
```cron
# Run daily database snapshot at 02:00 UTC
0 2 * * * /path/to/backend/scripts/backup.sh >> /var/log/campusverse_backup.log 2>&1
```

---

## 4. Disaster Recovery & Restore Procedure (`backend/scripts/restore.sh`)
In the event of catastrophic data corruption, hardware failure, or human error:

1. **Locate Target Snapshot**:
   Identify the target snapshot in Supabase Storage dashboard or list via REST API:
   `campusverse/backups/campusverse_backup_YYYYMMDD_HHMMSSZ.dump.enc`
2. **Execute Restore Pipeline**:
   ```bash
   export DATABASE_URL="postgresql://postgres.<ref>:<pass>@aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require"
   export BACKUP_ENCRYPTION_KEY="<stored-in-secure-vault>"
   export SUPABASE_SECRET_KEY="<from-supabase-project-settings-api>"

   # Restore directly from Supabase Storage:
   ./backend/scripts/restore.sh campusverse/backups/campusverse_backup_20260910_020000Z.dump.enc
   ```
3. **Automated Steps Executed by `restore.sh`**:
   - Downloads `.dump.enc` and `.sha256` from private Supabase Storage authenticated endpoint.
   - Decrypts the archive into a temporary stream.
   - Computes SHA-256 and compares with the published checksum manifest.
   - Validates the PostgreSQL TOC catalog via `pg_restore --list`.
   - Restores tables, constraints, foreign keys, and indexes via `pg_restore --clean --if-exists`.
   - Cleans up ephemeral decrypted artifacts on exit.
4. **Post-Restore Verification**:
   - Run `npx prisma migrate status --schema=prisma/schema.prisma` to confirm migration alignment.
   - Query row counts: `npm run db:verify`.
   - Run health probe: `curl -I https://api.campusverse.edu/api/v1/health`.

---

## 5. Production Infrastructure Config Required
To enable automated backups in production, configure the following environment variables:

| Environment Variable | Description | Example |
| :--- | :--- | :--- |
| `DATABASE_URL` | Managed PostgreSQL connection string | Configured in `backend/.env` |
| `BACKUP_ENCRYPTION_KEY` | High-entropy secret for AES-256 encryption | 64-character hex string |
| `SUPABASE_URL` | Supabase Project API URL | `https://eaqwchuugwaeaftjzfnf.supabase.co` |
| `SUPABASE_BACKUP_BUCKET`| Private Supabase Storage bucket | `campusverse-database-backups` |
| `SUPABASE_SECRET_KEY` | Recommended secret API key (`sb_secret_...`). Legacy `SUPABASE_SERVICE_ROLE_KEY` also supported | From Supabase Project Settings $\rightarrow$ API |
| `BACKUP_RETENTION_DAYS`| Local retention period | `30` |

> [!NOTE]
> Supabase Storage backup bucket is strictly **private** (`public: false`). All access requires Bearer token authentication with the secret API key (`SUPABASE_SECRET_KEY` or legacy `SUPABASE_SERVICE_ROLE_KEY`). Raw unencrypted database dumps are never stored or transmitted over the network.
