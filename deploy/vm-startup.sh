#!/usr/bin/env bash
# Compute Engine startup script for the yammer api VM (Ubuntu/Debian).
# Installs Docker + the compose plugin. The app files (docker-compose.yml, Caddyfile,
# render-env.sh) live in /opt/yammer — copied at setup time (see infra/SETUP.md).
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable docker
systemctl start docker
mkdir -p /opt/yammer
