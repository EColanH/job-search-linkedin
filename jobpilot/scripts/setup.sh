#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null || { echo 'Install Docker Desktop for Apple Silicon first.' >&2; exit 1; }
if [ ! -f .env ]; then
  umask 077
  cp .env.example .env
  python3 - <<'PY'
from pathlib import Path
import secrets
p=Path('.env')
p.write_text(p.read_text().replace('POSTGRES_PASSWORD=\n','POSTGRES_PASSWORD='+secrets.token_hex(24)+'\n'))
PY
fi
docker compose up --build -d --wait
printf 'JobPilot started. Open http://localhost:3000\n'
