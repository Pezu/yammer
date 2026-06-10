#!/usr/bin/env bash
# Run ON the api VM (uses the VM's service account to read Secret Manager) to (re)generate
# /opt/yammer/.env. Re-run after rotating secrets, then: cd /opt/yammer && sudo docker compose up -d
#
#   API_DOMAIN=api.example.com sudo -E ./render-env.sh
set -euo pipefail

PROJECT=yammer-order-app
: "${API_DOMAIN:?set API_DOMAIN, e.g. API_DOMAIN=api.example.com}"
sec() { gcloud secrets versions access latest --secret="$1" --project="$PROJECT"; }

cat > /opt/yammer/.env <<EOF
API_IMAGE=europe-west1-docker.pkg.dev/${PROJECT}/yammer/api:latest
INSTANCE_CONNECTION_NAME=${PROJECT}:europe-west1:yammer-db
API_DOMAIN=${API_DOMAIN}
DATABASE_USERNAME=yammer_app
DATABASE_PASSWORD=$(sec yammer-db-password)
JWT_SECRET=$(sec jwt-secret)
BRIDGE_API_KEY=$(sec bridge-api-key)
GCS_BUCKET=${PROJECT}-uploads
GCS_PROJECT_ID=${PROJECT}
EOF
chmod 600 /opt/yammer/.env
echo "Wrote /opt/yammer/.env"
