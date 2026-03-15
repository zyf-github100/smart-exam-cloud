#!/usr/bin/env bash
set -euo pipefail

SERVER_HOST="${1:-36.137.84.162}"
SERVER_USER="${2:-root}"

echo "Opening SSH tunnels to ${SERVER_USER}@${SERVER_HOST}"
echo "  local 13306 -> remote 127.0.0.1:3306 (MySQL)"
echo "  local 6379  -> remote 127.0.0.1:6379 (Redis)"
echo "  local 5672  -> remote 127.0.0.1:5672 (RabbitMQ AMQP)"
echo "  local 15672 -> remote 127.0.0.1:15672 (RabbitMQ UI)"
echo
echo "Keep this shell open while running local backend services."

exec ssh \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=60 \
  -L 13306:127.0.0.1:3306 \
  -L 6379:127.0.0.1:6379 \
  -L 5672:127.0.0.1:5672 \
  -L 15672:127.0.0.1:15672 \
  -N \
  "${SERVER_USER}@${SERVER_HOST}"
