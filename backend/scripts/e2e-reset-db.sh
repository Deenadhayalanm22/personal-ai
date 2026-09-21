#!/usr/bin/env bash
set -euo pipefail

# This compose project owns only disposable e2e infrastructure. `-v` removes
# its PostgreSQL volume so Flyway always starts from an empty database.
compose_file="src/test/resources/infra/podman-compose.yml"
podman compose -f "$compose_file" down -v --remove-orphans
podman compose -f "$compose_file" up -d postgres wiremock
