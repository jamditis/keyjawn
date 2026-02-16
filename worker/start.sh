#!/usr/bin/env bash
# Start keyjawn-worker
set -euo pipefail

cd "$(dirname "$0")"
source venv/bin/activate
exec python -m worker.main
