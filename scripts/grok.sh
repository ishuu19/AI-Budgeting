#!/usr/bin/env bash
# Usage: scripts/grok.sh "your prompt"   (reads XAI_* from secrets.properties; key is never printed)
set -euo pipefail
cd "$(dirname "$0")/.."
get() { grep -E "^$1=" secrets.properties | head -1 | cut -d= -f2- | tr -d '\r'; }
KEY=$(get XAI_API_KEY); BASE=$(get XAI_BASE_URL); MODEL=${GROK_MODEL:-$(get AI_MODEL_XAI)}
BASE=${BASE%/}
PROMPT="${*:?prompt required}"
BODY=$(python -c 'import json,sys;print(json.dumps({"model":sys.argv[1],"messages":[{"role":"user","content":sys.argv[2]}]}))' "$MODEL" "$PROMPT")
curl -sS "$BASE/chat/completions" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" -d "$BODY" \
 | python -c 'import json,sys;d=json.load(sys.stdin);print(d["choices"][0]["message"]["content"] if "choices" in d else d)'
