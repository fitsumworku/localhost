#!/usr/bin/env bash
set -e

if ! psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -tAc "SELECT 1 FROM pg_database WHERE datname = 'sampleolapdata'" | grep -q 1; then
  psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    -c "CREATE DATABASE sampleolapdata"
fi

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname sampleolapdata \
  -f /docker-entrypoint-initdb.d/olap_schema.sql