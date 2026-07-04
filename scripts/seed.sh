#!/usr/bin/env bash
#
# Exercises a running gateway end-to-end using the seeded demo teams.
# Prereqs: `docker compose up` is healthy AND Ollama on the host has the models pulled:
#   ollama pull llama3.1 && ollama pull gemma3 && ollama pull gemma3:1b
#
set -euo pipefail
BASE="${GATEWAY_BASE:-http://localhost:8080}"

echo "==> 1. Basic completion (enterprise team, llama3.1)"
curl -sS -D - "$BASE/v1/chat/completions" \
  -H "Authorization: Bearer sk-enterprise-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3.1","messages":[{"role":"user","content":"Say hello in one short sentence."}],"stream":false,"max_tokens":64}' \
  | sed -e 's/\r$//'
echo; echo

echo "==> 2. Streaming completion (SSE)"
curl -sS -N "$BASE/v1/chat/completions" \
  -H "Authorization: Bearer sk-enterprise-key" \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3.1","messages":[{"role":"user","content":"Count from 1 to 5."}],"stream":true,"max_tokens":64}'
echo; echo

echo "==> 3. Low-priority request (uses the smaller reserved RPM bucket)"
curl -sS "$BASE/v1/chat/completions" \
  -H "Authorization: Bearer sk-internal-key" \
  -H "X-Priority: low" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma3:1b","messages":[{"role":"user","content":"One word answer: ping?"}],"stream":false,"max_tokens":16}'
echo; echo

echo "==> 4. Trigger rate limiting on the free tier (rpm=5): firing 10 requests"
for i in $(seq 1 10); do
  code=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE/v1/chat/completions" \
    -H "Authorization: Bearer sk-free-key" \
    -H "Content-Type: application/json" \
    -d '{"model":"gemma3:1b","messages":[{"role":"user","content":"hi"}],"stream":false,"max_tokens":8}')
  echo "  request $i -> HTTP $code"
done
echo

echo "==> 5. Admin: list teams (with live spend)"
curl -sS "$BASE/admin/teams" -H "X-Admin-User: demo" | sed -e 's/\r$//'
echo; echo

echo "==> 6. Admin: provider health"
curl -sS "$BASE/admin/providers/health" | sed -e 's/\r$//'
echo; echo

echo "==> 7. Admin: spending report (last 7 days)"
curl -sS "$BASE/admin/spending" | sed -e 's/\r$//'
echo; echo

echo "Done. Open Grafana at http://localhost:3000 (admin/admin) and Jaeger at http://localhost:16686"
