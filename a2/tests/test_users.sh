#!/bin/bash

BASE_URL="http://127.0.0.1:14001/user"
CLEAR_URL="$BASE_URL/internal/clear"

PAYLOAD_FILE="payloads/user_testcases.json"
EXPECTED_FILE="responses/user_responses.json"

PASS=0
FAIL=0

echo "Clearing database..."
curl -s -X POST "$CLEAR_URL"
echo "Database cleared."

for TEST in $(jq -r 'keys_unsorted[]' "$PAYLOAD_FILE"); do
  echo "Running test: $TEST"

  PAYLOAD=$(jq -c ".\"$TEST\"" "$PAYLOAD_FILE")
  EXPECTED=$(jq -c ".\"$TEST\"" "$EXPECTED_FILE")

  # 🔑 FIX: decide GET vs POST
  if jq -e 'has("command")' <<<"$PAYLOAD" >/dev/null; then
    # POST request
    RESPONSE=$(curl -s -X POST "$BASE_URL" \
      -H "Content-Type: application/json" \
      -d "$PAYLOAD")
  else
    # GET request
    ID=$(jq -r '.id' <<<"$PAYLOAD")
    RESPONSE=$(curl -s -X GET "$BASE_URL/$ID")
  fi

  RESPONSE_JSON=$(echo "$RESPONSE" | jq -c . 2>/dev/null || echo "{}")

  if diff <(echo "$RESPONSE_JSON") <(echo "$EXPECTED") > /dev/null; then
    echo "✅ PASS"
    PASS=$((PASS+1))
  else
    echo "❌ FAIL"
    echo "Expected:"
    echo "$EXPECTED" | jq .
    echo "Actual:"
    echo "$RESPONSE_JSON" | jq .
    FAIL=$((FAIL+1))
  fi

  echo "------------------------"
done

echo "SUMMARY"
echo "Passed: $PASS"
echo "Failed: $FAIL"
