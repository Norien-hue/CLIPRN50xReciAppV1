#!/bin/bash
set -e

echo "=== Generating CLIP embeddings ==="

TOKEN=$(curl -s -X POST http://localhost:3000/api/usuarios/login \
  -H "Content-Type: application/json" \
  -d '{"nombre":"adminb","password":"clase1234"}' | python3 -c "import sys,json;print(json.load(sys.stdin).get('token',''))")

if [ -z "$TOKEN" ]; then
  echo "ERROR: Login failed"
  exit 1
fi
echo "Token obtained OK"

echo "Running precompute_embeddings.py inside container..."
sudo docker exec \
  -e API_TOKEN="$TOKEN" \
  -e API_BASE="http://localhost:3000" \
  clip-service python precompute_embeddings.py

echo "Restarting clip-service container..."
sudo docker restart clip-service

sleep 5

echo ""
echo "=== Health check ==="
curl -s http://localhost:8080/health
echo ""
echo "=== Done ==="
