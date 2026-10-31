#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[ "$#" -eq 1 ] && [ -f "$1" ] || { echo 'Usage: scripts/restore.sh backups/jobpilot-TIMESTAMP.tar.gz' >&2; exit 1; }
[ "${JOBPILOT_RESTORE_CONFIRM:-}" = 'REPLACE_LOCAL_DATA' ] || { echo 'Restore replaces local data. Back up first, then set JOBPILOT_RESTORE_CONFIRM=REPLACE_LOCAL_DATA.' >&2; exit 1; }
umask 077
task_restore_dir="$(mktemp -d)"
trap 'rm -rf "$task_restore_dir"' EXIT
# Only restore archives made by backup.sh; refuse unexpected archive paths.
python3 - "$1" "$task_restore_dir" <<'PY'
import sys,tarfile,pathlib
with tarfile.open(sys.argv[1]) as t:
 for m in t.getmembers():
  if m.name not in ('.','./database.dump','./documents.tar.gz','./backend.tar.gz','database.dump','documents.tar.gz','backend.tar.gz') or not (m.isfile() or m.isdir()):
   raise SystemExit('Unexpected backup entry')
 t.extractall(sys.argv[2],filter='data')
for name in ('documents.tar.gz','backend.tar.gz'):
 with tarfile.open(pathlib.Path(sys.argv[2])/name) as t:
  for m in t.getmembers():
   p=pathlib.PurePosixPath(m.name)
   if p.is_absolute() or '..' in p.parts or not (m.isfile() or m.isdir()): raise SystemExit('Unsafe document archive')
PY
docker compose stop backend document-worker
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --exit-on-error' < "$task_restore_dir/database.dump"
docker compose run --rm --no-deps -T --entrypoint sh document-worker -c 'find /data -mindepth 1 -delete; tar -C /data -xzf -' < "$task_restore_dir/documents.tar.gz"
docker compose run --rm --no-deps -T --entrypoint sh backend -c 'find /data -mindepth 1 -delete; tar -C /data -xzf -' < "$task_restore_dir/backend.tar.gz"
docker compose start document-worker backend
