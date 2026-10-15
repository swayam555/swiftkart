#!/usr/bin/env bash
# End-to-end smoke test against a running stack:  ./scripts/smoke-test.sh [base-url]
# Needs curl, jq and uuidgen. Places a real order and waits for delivery. Takes about 3 minutes.
set -uo pipefail
B=${1:-http://localhost:3002}
FAIL=0
j() { curl -s -H 'Content-Type: application/json' "$@"; }
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1 (expected $3, got $2)"; FAIL=1; fi; }

echo "Sign-in and access control"
C=$(j $B/api/auth/customer -d '{"name":"Smoke Test","phone":"9876500000"}' | jq -r .accessToken)
O=$(j $B/api/auth/ops -d '{"username":"ops","password":"swiftkart"}' | jq -r .accessToken)
CH="Authorization: Bearer $C"; OH="Authorization: Bearer $O"
check "wrong ops password is rejected" "$(j -o /dev/null -w '%{http_code}' $B/api/auth/ops -d '{"username":"ops","password":"x"}')" 401
check "customer can't open the ops API" "$(curl -s -o /dev/null -w '%{http_code}' $B/api/ops/overview -H "$CH")" 403

echo "Shopping"
# The order goes to a Mumbai store; the flash sale floods Koramangala with 50 real orders per run.
N=$(curl -s "$B/api/stores/nearest?lat=19.0544&lon=72.8340" -H "$CH"); SID=$(echo "$N" | jq -r .store.id)
FLASH=$(curl -s "$B/api/stores/nearest?lat=12.9341&lon=77.6101" -H "$CH" | jq -r .store.id)
check "Bandra West is served by a nearby dark store" "$(echo "$N" | jq .serviceable)" true
check "a city with no dark store gets 404" "$(curl -s -o /dev/null -w '%{http_code}' "$B/api/stores/nearest?lat=26.9124&lon=75.7873" -H "$CH")" 404
SKU=$(curl -s "$B/api/catalog?storeId=$SID" -H "$CH" | jq -r '[.[][] | select(.available > 5 and (.sku | startswith("FLASH") | not))][0].sku')
check "catalogue shows live stock" "$([ "$SKU" != null ] && echo yes)" yes

echo "Order saga"
K=$(uuidgen); body="{\"lat\":19.0544,\"lon\":72.8340,\"address\":\"Bandra West\",\"items\":[{\"sku\":\"$SKU\",\"qty\":2}]}"
R1=$(j $B/api/orders -H "$CH" -H "Idempotency-Key: $K" -d "$body"); R2=$(j $B/api/orders -H "$CH" -H "Idempotency-Key: $K" -d "$body")
OID=$(echo "$R1" | jq -r .id)
check "checkout reserves stock and waits for payment" "$(echo "$R1" | jq -r .status)" PAYMENT_PENDING
check "same Idempotency-Key returns the same order" "$(echo "$R2" | jq -r .id)" "$OID"
check "payment confirms the order" "$(j -X POST $B/api/orders/$OID/pay -H "$CH" -d '{"method":"UPI"}' | jq -r .status)" CONFIRMED
F=$(j $B/api/orders -H "$CH" -d "$body" | jq -r .id)
check "a declined payment releases the stock" "$(j -X POST $B/api/orders/$F/pay -H "$CH" -d '{"method":"UPI","simulateFailure":true}' | jq -r .status)" PAYMENT_FAILED
for _ in $(seq 60); do st=$(curl -s $B/api/orders/$OID -H "$CH" | jq -r .status); [ "$st" = DELIVERED ] && break; sleep 4; done
check "order is packed, picked up and delivered" "$st" DELIVERED

echo "Flash-sale lab (300 buyers, 50 units)"
A=$(j $B/api/ops/flash-sale -H "$OH" -d "{\"storeId\":\"$FLASH\",\"units\":50,\"buyers\":300,\"mode\":\"ATOMIC\"}")
check "atomic: exactly 50 confirmed" "$(echo "$A" | jq .confirmed)" 50
check "atomic: nobody refunded after paying" "$(echo "$A" | jq .refundedAfterPay)" 0
N2=$(j $B/api/ops/flash-sale -H "$OH" -d "{\"storeId\":\"$FLASH\",\"units\":50,\"buyers\":300,\"mode\":\"NAIVE\"}")
check "naive: the ledger still stops at 50" "$(echo "$N2" | jq .confirmed)" 50
echo "       naive: told $(echo "$N2" | jq .toldYes) buyers \"reserved\", refunded $(echo "$N2" | jq .refundedAfterPay) after paying"

echo "Stock integrity"
sleep 2
# The naive run leaves phantom stock in Redis on purpose; the reconciler repairs drift on its second sighting.
recon() { j -X POST $B/api/inventory/ops/reconciliation/run -H "$OH"; }
check "only the naive flash sale left drift" "$(recon | jq '[.drifts[] | select(.sku != "FLASH-PS5")] | length')" 0
for _ in 1 2 3; do D=$(recon | jq '.drifts | length'); [ "$D" = 0 ] && break; done
check "reconciliation: Redis matches the ledger after repair" "$D" 0

[ $FAIL = 0 ] && echo "All checks passed." || { echo "Some checks failed."; exit 1; }
