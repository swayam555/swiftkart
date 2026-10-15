# How SwiftKart works

Part 1 is for everyone: designers, product people, recruiters. Part 2 is for engineers. The
[README](../README.md) has the architecture diagram, the order state machine and the table of
engineering decisions; this page walks through what actually happens.

---

## Part 1: in plain English

### One order

Asha in Koramangala wants milk and bread.

1. **She picks her location.** SwiftKart finds the nearest *dark store*: a small warehouse that only
   serves online orders. The app shows only what that store has on its shelves *right now*, so she
   never orders something that isn't there.
2. **She taps "Proceed to pay".** At that moment SwiftKart *reserves* her items: they are set aside for
   her and no one else can buy them. If she doesn't pay within 2 minutes, they go back on the shelf.
3. **She pays.** The order is confirmed and the delivery clock starts. The promise ("delivery in 9
   minutes") counts the time to pick her items, the orders ahead of hers, the handover and the ride.
4. **A picker packs it** while the system chooses a rider: someone who can reach the store in time,
   preferring riders who have been waiting longest so work is shared fairly. If another order is going
   the same way, one rider may carry both.
5. **She watches the rider on a map** until the doorbell rings.

### The flash sale

At noon, 50 PlayStation 5s go on sale and 300 people tap *Buy* in the same second.

- A **naive** system checks "is there stock?" for everyone at once. Most of them see "yes", pay, and
  then most are refunded because there were only 50. Each refund costs a payment fee, a support ticket
  and a disappointed customer.
- **SwiftKart** reserves stock in a single indivisible step. Exactly 50 people hear "reserved", the
  other 250 hear "sold out" *before* paying, and nobody is refunded. The ops console's **Flash-sale lab**
  runs both versions side by side so you can see the difference.

### The ops team

The control room shows a live map of every rider and waiting customer, the on-time rate, how busy
riders are, and which orders are waiting for a rider. If one store is overwhelmed, the team adds riders
with one click. The stock board shows what will run out first.

---

## Part 2: for engineers

### Services

| Service | Port | Owns |
|---|---|---|
| `gateway` | 8080 | JWT verification, customer vs ops roles, Redis rate limits (tighter on checkout) |
| `order-service` | 8091 | the order saga, payments (mock UPI), delivery promises, SSE live updates, the load generator and flash-sale lab |
| `inventory-service` | 8092 | dark stores, catalogue, Redis stock counters and reservations, the Postgres stock ledger, picking, reconciliation |
| `dispatch-service` | 8093 | riders, the Redis GEO index, assignment and batching, the fleet simulation (one leader at a time) |

### 1. Finding the store and showing stock

- `GET /api/stores/nearest` runs a Redis GEO radius query over the dark stores and checks the store's
  delivery radius. No store in range gives a 404 ("We're not in your area yet").
- `GET /api/catalog` reads each product's live *sellable* count from Redis (ledger on-hand minus active
  reservations), so "Only 3 left" is true at that moment.

### 2. Checkout: reserving a cart

`POST /api/orders` (with an optional `Idempotency-Key`):

1. order-service calls inventory-service's reserve endpoint (synchronously, behind a circuit breaker
   that **fails closed**: never take money for stock that can't be held).
2. A single **Lua script** checks every line of the cart and decrements all of them, or none. All of a
   store's keys share a hash tag (`stock:{BLR-KOR}:sku`), so the script stays atomic on Redis Cluster.
3. The reservation is recorded with an expiry in a sorted set. Every inventory replica sweeps expired
   ones, and `ZREM` decides which replica releases each, so nothing is released twice.
4. The order is saved as `PAYMENT_PENDING` together with an outbox row (one transaction).

### 3. Payment and commit

`POST /api/orders/{id}/pay`:

- **Declined:** the reservation is released and the order becomes `PAYMENT_FAILED`.
- **Captured:** inventory **commits** in three steps:
  1. *claim* the reservation, so the expiry sweeper can no longer release it;
  2. *ledger*: `UPDATE stock SET on_hand = on_hand - qty WHERE on_hand >= qty`, idempotent per order.
     Postgres is the source of truth and can never go negative;
  3. *finalize*: drop the reservation.
  If the ledger refuses (an oversell slipped through Redis), the reservation is dropped without
  creating phantom stock and the order is refunded.
- The order becomes `CONFIRMED`, and the promise clock restarts from confirmation, so time spent in
  the UPI app doesn't count against the store.

### 4. The promise

```
promise = picking time (1 min + 12 s per unit)
        + picking queue ahead of this order (atomic per-store Redis gauge)
        + handover
        + ride time (distance × road factor ÷ rider speed)
```

The queue position comes from joining a per-store sorted set, which returns each buyer's true position
even when 50 people check out in the same second. A database count would have told all 50 "nobody is
ahead of you", a bug found in testing.

### 5. Picking, dispatch and delivery

- **Picking:** inventory-service assigns a picker. Pick-and-pack timers are entries in a Redis sorted
  set (not in-memory timers), so a restart or a second replica never loses or duplicates a "packed"
  event. Status flows to order-service over Kafka.
- **Assignment:** dispatch-service finds candidate riders with a Redis GEO radius query around the store and scores
  them: time to reach the store, minus a fairness bonus for how long they've been idle.
- **Batching:** a rider still at the store takes a second order if the two drops are within 1.2 km and
  the detour is under 4 minutes.
- **One leader:** only one dispatch replica moves riders and assigns orders. It holds a Redis lease
  renewed with compare-and-extend (fenced). Fleet state is snapshotted to Redis, so a new leader picks
  up where the old one stopped.
- **Live tracking:** status changes reach the browser over Server-Sent Events. Redis pub/sub fans them
  out, because the browser may be connected to a different order-service replica than the one that
  consumed the Kafka event.

### 6. Events and consistency

```
order-service      ──order.events────▶ inventory-service (picking), dispatch-service (assignment)
inventory-service  ──store.events────▶ order-service (packing status), dispatch-service (ready for pickup)
dispatch-service   ──dispatch.events─▶ order-service (rider assigned, picked up, delivered)
```

- Every producer uses a transactional outbox; every consumer de-duplicates on the event id
  (`processed_events`).
- Kafka can deliver out of order, so order status **only moves forward** and updates use optimistic
  locking (`@Version`) with retries.

### 7. Reconciliation

Every 30 s inventory-service compares, for every product in every store, Redis's sellable count with
*ledger on-hand minus live reservations*. It repairs drift only if it sees the same drift twice in a
row (one observation may be a checkout in flight), and cleans up reservations stranded by a commit that
died half-way.

### 8. The demo clock

`TIME_SCALE=6` makes simulated time run six times faster: a 10-minute delivery takes under 2 real
minutes. Picking, rider movement and promises all use the scaled clock; payment and reservation
timeouts use real time.
