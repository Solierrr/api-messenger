#!/bin/sh
set -e

INFISICAL_TOKEN=$(infisical login --method=universal-auth \
  --client-id="$INFISICAL_CLIENT_ID" \
  --client-secret="$INFISICAL_CLIENT_SECRET" \
  --silent --plain)

exec infisical run \
  --token="$INFISICAL_TOKEN" \
  --projectId=2296d19c-5f3b-41e1-afa3-fcde39966a71 \
  --env="${INFISICAL_ENV:-qa}" \
  --path=/ \
  -- sh -c 'echo "DEBUG_MONGO_URI_LEN=${#DB_MONGO_URI} DEBUG_MONGO_URI_PREFIX=$(echo "$DB_MONGO_URI" | cut -c1-14) DEBUG_MONGO_MSG=$DB_MONGO_MESSENGER"; exec java -jar app.jar'
