#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p backups
task_backup_dir="$(mktemp -d "$(pwd)/backups/.backup.XXXXXX")"
trap 'rm -rf "$task_backup_dir"; docker compose start backend document-worker >/dev/null' EXIT
# Quiesce writers so database and immutable files form one consistent backup.
docker compose stop backend document-worker
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$task_backup_dir/database.dump"
docker compose run --rm --no-deps -T --entrypoint tar document-worker -C /data -czf - . > "$task_backup_dir/documents.tar.gz"
docker compose run --rm --no-deps -T --entrypoint tar backend -C /data -czf - . > "$task_backup_dir/backend.tar.gz"
task_archive="backups/jobpilot-$(date -u +%Y%m%dT%H%M%SZ).tar.gz"
tar -C "$task_backup_dir" -czf "$task_archive" .
printf 'Backup saved to %s. It contains private data; keep it encrypted and out of Git.\n' "$task_archive"
