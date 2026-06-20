#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
npm run build:backend
npm run build:frontend
(cd electron && npm install && npx electron-builder --publish never)
