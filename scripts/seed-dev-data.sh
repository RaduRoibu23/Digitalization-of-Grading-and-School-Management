#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker compose -f "${ROOT_DIR}/infra/docker-compose.local.yml" up -d postgres-app postgres-keycloak keycloak backend

echo "Datele de referinta, seed-urile demo pentru catalog/orar si importul realm-ului Keycloak sunt aplicate automat la pornirea serviciilor."
