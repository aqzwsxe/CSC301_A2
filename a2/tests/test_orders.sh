#!/bin/bash

BASE_URL="http://127.0.0.1:14000/order"   # <-- change port if needed

PAYLOAD_FILE="payloads/order_testcases.json"
EXPECTED_FILE="responses/order_responses.json"

PASS=0
FAIL=0

for TEST in $(jq -r 'keys_unsorted[]' "$PAYLOAD_FILE"); do
  echo "Running test: $TEST"

  PAYLOAD=$(jq -c ".\"$TEST\"" "$PAYLOAD_FILE")
  EXPECTED=$(jq -c ".\"$TEST\"" "$EXPECTED_FILE")

  # -----------------------------
  # Decide HTTP method
  # -----------------------------
  if jq -e 'has("command")' <<<"$PAYLOAD" >/dev/null; then
    # POST: place order / cancel order
    RESPONSE=$(curl -s -X POST "$BASE_URL" \
      -H "Content-Type: application/json" \
      -d "$PAYLOAD")
  else
    # GET: /order/{id}
    ID=$(jq -r '.id' <<<"$PAYLOAD")
    RESPONSE=$(curl -s -X GET "$BASE_URL/$ID")
  fi

  # Normalize JSON (or empty)
  RESPONSE_JSON=$(echo "$RESPONSE" | jq -c . 2>/dev/null || echo "{}")

  # -----------------------------
  # Compare JSON
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