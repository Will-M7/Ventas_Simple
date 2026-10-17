#!/usr/bin/env bash
set -Eeuo pipefail
# No BASE_URL: the suite creates its own loopback server and temporary SQLite.
cd -- "$(dirname -- "$0")"
exec npm run test:unit
