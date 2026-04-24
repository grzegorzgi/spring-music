#!/usr/bin/env bash
# PreToolUse hook: blocks cross-boundary imports between legacy/ and services/
# Fires on Edit and Write tool calls.
# Exit 0 = allow. Exit 1 = block with message.

INPUT=$(cat)

FILE=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('file_path', '') or d.get('path', ''))
" 2>/dev/null)

CONTENT=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('new_string', '') or d.get('content', '') or '')
" 2>/dev/null)

# Normalize path separators
FILE=$(echo "$FILE" | tr '\\\\' '/')

# Block: services/ code importing from legacy/
if echo "$FILE" | grep -q "services/"; then
  if echo "$CONTENT" | grep -qE "from ['\"].*legacy/|require\(['\"].*legacy/"; then
    echo "ACL BOUNDARY VIOLATION: Code in services/ may not import from legacy/ directly."
    echo "Use acl/src/translator.ts as the bridge instead."
    echo "See decisions/ADR-003-acl-design.md for rationale."
    exit 1
  fi
fi

# Block: legacy/ code importing from services/
if echo "$FILE" | grep -q "legacy/"; then
  if echo "$CONTENT" | grep -qE "from ['\"].*services/|require\(['\"].*services/"; then
    echo "ACL BOUNDARY VIOLATION: Code in legacy/ may not import from services/ directly."
    echo "The monolith must remain unaware of its successors."
    echo "See decisions/ADR-003-acl-design.md for rationale."
    exit 1
  fi
fi

exit 0
