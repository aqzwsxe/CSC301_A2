#!/bin/bash

BASE_URL="http://127.0.0.1:15000/product"
CLEAR_URL="$BASE_URL/internal/clear"

PAYLOAD_FILE="payloads/product_testcases.json"
EXPECTED_FILE="responses/product_responses.json"

PASS=0
FAIL=0

echo "Clearing database..."
curl -s -X POST "$CLEAR_URL"
echo "Database cleared."

for TEST in $(jq -r 'keys_unsorted[]' "$PAYLOAD_FILE"); do
  echo "Running test: $TEST"

  PAYLOAD=$(jq -c ".\"$TEST\"" "$PAYLOAD_FILE")
  EXPECTED=$(jq -c ".\"$TEST\"" "$EXPECTED_FILE")

  # -----------------------------
  # Decide HTTP method
  # -----------------------------
  if jq -e 'has("command")' <<<"$PAYLOAD" >/dev/null; then
    # create / update / delete
    RESPONSE=$(curl -s -X POST "$BASE_URL" \
      -H "Content-Type: application/json" \
      -d "$PAYLOAD")
  else
    # GET
    ID=$(jq -r '.id' <<<"$PAYLOAD")
    RESPONSE=$(curl -s -X GET "$BASE_URL/$ID")
  fi

  # Normalize JSON (or empty)
  RESPONSE_JSON=$(echo "$RESPONSE" | jq -c . 2>/dev/null || echo "{}")

  # -----------------------------
  # Compare
  # -----------------------------
  if diff <(echo "$RESPONSE_JSON") <(echo "$EXPECTED") >/dev/null; then
    echo "✅ PASS"
    PASS=$((PASS + 1))
  else
    echo "❌ FAIL"

    echo "Expected:"
    echo "$EXPECTED" | jq .

    echo "Actual:"
    echo "$RESPONSE_JSON" | jq .

    FAIL=$((FAIL + 1))
  fi

  echo "------------------------------"
done

echo
echo "========== SUMMARY =========="
echo "Passed: $PASS"
echo "Failed: $FAIL"
