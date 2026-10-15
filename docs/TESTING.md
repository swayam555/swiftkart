# Testing SwiftKart

There are four levels: automated tests (no browser), a one-command API smoke test against the running
stack, Playwright browser tests of whole user journeys, and a manual click-through for demos and design reviews.

## 1. Automated tests

```bash
./mvnw verify          # needs Docker for Testcontainers
```

23 tests, including:

| Test | What it proves |
|---|---|
| `StockConcurrencyIT` (7, real Redis + Postgres) | **The atomic reservation never oversells** under 300 concurrent buyers, while naive check-then-act does; carts are all-or-nothing; reserving is idempotent per order; commit moves units to the ledger exactly once; a ledger rejection drops the reservation without phantom stock; a release blocks a later commit |
| `StoreLoadIT` (2, real Redis) | Simultaneous checkouts get distinct queue positions (so honest promises); leaving and rejoining the queue is idempotent |
| `OrderRulesTest` (6) | Status only moves forward; terminal states are final; cancellation only until pickup; the promise clock restarts at confirmation; ETA = picking + queue + ride |
| `AssignerTest` (6) | Rider choice: fairness among riders at the store, a returning rider beats nobody, batching nearby drops, never batching opposite directions, waiting when no rider is free |
| `PickDurationTest` (2) | The demo clock; picking takes a minute plus 12 s per unit |

## 2. Smoke test (stack running)

```bash
docker compose up -d --build
./scripts/smoke-test.sh                 # or: ./scripts/smoke-test.sh http://some-host
```

It prints `ok` or `FAIL` for each of 15 checks: sign-in and roles, store lookup, live catalogue, the
order saga (reserve, idempotency, pay, declined payment, delivery), the flash-sale lab in both modes,
and stock reconciliation. It takes about 3 minutes because it waits for a real delivery.

The naive flash sale leaves phantom stock in Redis on purpose. So the reconciliation step first checks
that `FLASH-PS5` is the only product with drift, then runs the reconciler until that drift is repaired.
An earlier version expected zero drift 2 s after the naive run. That only passed when thread timing
happened to leave Redis at 0, so it failed intermittently.

## 3. Browser tests (Playwright)

```bash
docker compose up -d --build
cd e2e
npm install
npx playwright install chromium          # or set CHROME_PATH=/usr/bin/google-chrome to use an installed Chrome
npm test                                  # BASE_URL defaults to http://localhost:3002
npm run report                            # HTML report, with a trace and screenshot for any failure
```

`tests/journey.spec.ts` drives the 22-step customer and ops journey: landing page, sign-in validation, location, cart, the 2-minute hold, payment, live tracking to delivery, a declined payment, a cancellation, the orders list, then the load generator, both flash-sale modes, a restock and reconciliation. Each step is a named `test.step`, so the report shows exactly where
something broke. A second test opens every page at 390 px and fails if any of them scrolls sideways.
Uncaught page errors and any 5xx response fail the run.

## 4. Manual test script

Open the **landing page** at <http://localhost:3002> and the **ops control room** at
<http://localhost:3002/ops> (`ops` / `swiftkart`).

### A. Order like a customer

| Step | Do | You should see |
|---|---|---|
| 1 | Landing page: watch the hero, then scroll to the flash-sale lab | A rider drives the route while the order card counts down; 300 dots per system show who was told "reserved" and who was refunded |
| 2 | **Start shopping** → sign in with any name and a 10-digit mobile number | "Where are you?" (Continue stays disabled until the number has 10 digits) |
| 3 | **Choose location** → **Indiranagar** | "N minute delivery", the store and distance, the catalogue with live stock ("only 3 left") |
| 4 | **Add** a few items, use **+** / **−** → open the cart → **Proceed to pay** | The cart shows the free-delivery threshold; items are reserved; a payment screen with a 2-minute ring timer |
| 5 | **Pay ₹… with PhonePe** | "Arriving in N min", then Confirmed → Packing → On the way, with the rider moving on the map. Delivered in about 1-2 real minutes (the demo clock runs 6× faster). |
| 6 | Order again, tick **Simulate a declined payment** → **Pay** | "Payment failed"; the items' stock goes back up in the catalogue |
| 7 | Order again and **Cancel order** before pickup | "Cancelled"; stock returned |
| 8 | Order again and don't pay for 2 minutes | "Expired"; the sweeper returns the stock |
| 9 | **Orders** | Every order with its outcome: "Delivered in N min", "Payment failed", "Cancelled", "Expired" |

### B. Run the city

| Step | Do | You should see |
|---|---|---|
| 10 | Control room → **Live operations** → set orders per minute (e.g. 20) → **Start** | The city map fills with rider pins (coloured by status) and saffron customer pins; KPI tiles update |
| 11 | Watch for a while | On-time rate, average delivery vs. promise, rider utilisation, and "batched" riders carrying two orders |
| 12 | Pick a store under pressure → **+ 3 riders** | "Store now has N riders"; "waiting for rider" falls |

### C. The flash-sale lab (the headline demo)

| Step | Do | You should see |
|---|---|---|
| 13 | **Flash-sale lab** → **Run with naive check-then-act** | Far more than 50 buyers told "reserved", only 50 confirmed, and every extra buyer refunded after paying |
| 14 | **Run with atomic Redis Lua (production)** | Exactly 50 told "reserved", 50 confirmed, 0 refunds, the rest told "sold out" before paying |

Each run creates 50 real, paid orders at that store, which then need riders. After several runs,
expect that store's "waiting for rider" queue to grow, and use **+ 3 riders** to clear it. That's the
overload behaviour the system is designed to make visible.

### D. Stock integrity

| Step | Do | You should see |
|---|---|---|
| 15 | **Stock & reconciliation** → choose a store | On hand (ledger), reserved, sellable, sold in the last 30 min, hours of cover; low-cover items highlighted |
| 16 | Press **+24** on an item | Sellable rises by 24 at once |
| 17 | **Run now** (reconciliation) | "All counters consistent" (after a naive flash sale, the reconciler repairs Redis drift on its second look) |

## Resetting

```bash
docker compose down -v      # wipes orders and stock; dark stores and riders are re-seeded on start
```
