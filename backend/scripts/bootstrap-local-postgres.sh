#!/usr/bin/env bash
# Convenience entry point for developers working from the backend directory.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$script_dir/../../scripts/bootstrap-local-postgres.sh"
