package com.swiftkart.order.order;

import com.swiftkart.contracts.DispatchEvent;
import com.swiftkart.contracts.OrderEvent;
import com.swiftkart.contracts.OrderLine;
import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.contracts.ReservationResult;
import com.swiftkart.contracts.StoreEvent;
import com.swiftkart.contracts.Topics;
import com.swiftkart.order.config.AppProperties;
import com.swiftkart.order.config.JsonCodec;
import com.swiftkart.order.inventory.InventoryClient;
import com.swiftkart.order.live.LiveUpdates;
import com.swiftkart.order.outbox.OutboxEvent;
import com.swiftkart.order.outbox.OutboxRepository;
import com.swiftkart.order.payment.MockUpiGateway;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orchestrates the order saga:
 * <pre>
 *   reserve stock ──► PAYMENT_PENDING ──pay──► commit stock ──► CONFIRMED ─► PACKING ─► PACKED ─► OUT_FOR_DELIVERY ─► DELIVERED
 *        │ out of stock: 409            │ declined: release stock ─► PAYMENT_FAILED
 *        │                              │ reservation expired: refund ─► EXPIRED
 *        └ cancel before pickup: release / refund + return to shelf ─► CANCELLED
 * </pre>
 * Every step is idempotent, so retries (from the app or from Kafka redelivery) are always safe.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final TypeReference<List<OrderLine>> LINES = new TypeReference<>() {
    };

    private final OrderRepository orders;
    private final TimelineRepository timeline;
    private final OutboxRepository outbox;
    private final InventoryClient inventory;
    private final MockUpiGateway payments;
    private final TransactionTemplate tx;
    private final JsonCodec json;
    private final AppProperties props;
    private final LiveUpdates live;
    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final StoreLoad storeLoad;

    public OrderService(OrderRepository orders, TimelineRepository timeline, OutboxRepository outbox,
                        InventoryClient inventory, MockUpiGateway payments, TransactionTemplate tx, JsonCodec json,
                        AppProperties props, LiveUpdates live, StringRedisTemplate redis, MeterRegistry meters,
                        StoreLoad storeLoad) {
        this.storeLoad = storeLoad;
        this.orders = orders;
        this.timeline = timeline;
        this.outbox = outbox;
        this.inventory = inventory;
        this.payments = payments;
        this.tx = tx;
        this.json = json;
        this.props = props;
        this.live = live;
        this.redis = redis;
        this.meters = meters;
    }

    // ------------------------------------------------------------------ checkout

    public OrderView create(PlaceOrder req, String customerId, String customerName, String idempotencyKey,
                            ReservationRequest.Mode mode) {
        if (idempotencyKey != null) {
            String existing = redis.opsForValue().get("idem:order:" + idempotencyKey);
            if (existing != null) return view(existing);
        }
        if (req.items() == null || req.items().isEmpty()) throw new IllegalArgumentException("Cart is empty");
        InventoryClient.Nearest nearest = inventory.nearest(req.lat(), req.lon());
        if (nearest == null || !nearest.serviceable()) {
            throw new IllegalArgumentException("Sorry, we don't deliver to this location yet");
        }
        String storeId = nearest.store().id();
        Map<String, InventoryClient.CatalogItem> catalog = inventory.catalog(storeId);
        List<OrderLine> lines = new ArrayList<>();
        BigDecimal itemTotal = BigDecimal.ZERO;
        int units = 0;
        for (PlaceOrder.Item item : req.items()) {
            InventoryClient.CatalogItem p = catalog.get(item.sku());
            if (p == null) throw new IllegalArgumentException("Unknown product " + item.sku());
            if (item.qty() < 1 || item.qty() > p.maxPerOrder()) {
                throw new IllegalArgumentException("You can order 1-" + p.maxPerOrder() + " of " + p.name());
            }
            lines.add(new OrderLine(p.sku(), p.name(), item.qty(), p.price()));
            itemTotal = itemTotal.add(p.price().multiply(BigDecimal.valueOf(item.qty())));
            units += item.qty();
        }

        String orderId = newOrderId();
        ReservationResult reservation = inventory.reserve(new ReservationRequest(orderId, storeId,
                lines.stream().map(l -> new ReservationRequest.Item(l.sku(), l.qty())).toList(),
                mode == null ? ReservationRequest.Mode.ATOMIC : mode));
        if (!reservation.reserved()) {
            meters.counter("orders.rejected", "reason", "out_of_stock").increment();
            List<String> names = reservation.unavailableSkus().stream()
                    .map(s -> catalog.containsKey(s) ? catalog.get(s).name() : s).toList();
            throw new OutOfStockException(reservation.unavailableSkus(), names);
        }

        long ahead = storeLoad.join(storeId, orderId);
        Duration eta = EtaEstimator.estimate(units, nearest.distanceKm(), ahead);
        int promised = EtaEstimator.promisedMinutes(eta);
        Instant promisedBy = Instant.now().plusMillis(props.realMillis(Duration.ofMinutes(promised)));
        BigDecimal fee = itemTotal.compareTo(props.freeDeliveryAbove()) >= 0 ? BigDecimal.ZERO : props.deliveryFee();
        Order order = new Order(orderId, customerId, customerName, storeId, json.write(lines), units, itemTotal, fee,
                req.lat(), req.lon(), req.address(), nearest.distanceKm(), promised, promisedBy,
                reservation.expiresAt(), req.source() == null ? "APP" : req.source());
        String placedDetail = units + (units == 1 ? " item from " : " items from ") + nearest.store().name()
                + " · stock reserved for " + props.reservationTtlSeconds() + " s while you pay";
        tx.executeWithoutResult(s -> {
            orders.saveAndFlush(order); // flush before the JDBC timeline insert that references it
            timeline.add(orderId, "Order placed", placedDetail);
        });
        if (idempotencyKey != null) {
            redis.opsForValue().set("idem:order:" + idempotencyKey, orderId, Duration.ofHours(24));
        }
        meters.counter("orders.placed").increment();
        return publishAndView(orderId);
    }

    public OrderView pay(String orderId, String customerId, String method, boolean forceFailure) {
        Order order = mine(orderId, customerId);
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) return view(orderId); // idempotent re-tap

        MockUpiGateway.Result payment = payments.charge(orderId, order.getTotal(), method, forceFailure,
                !"FLASH".equals(order.getSource()));
        if (!payment.captured()) {
            safeRelease(order);
            update(orderId, o -> {
                o.setPaymentId(payment.paymentId());
                o.cancel(OrderStatus.PAYMENT_FAILED, "Payment declined by the bank");
            }, "Payment failed", "UPI payment was declined. Reserved items went back on the shelf.");
            meters.counter("orders.payment_failed").increment();
            return publishAndView(orderId);
        }

        String outcome = inventory.commit(order.getStoreId(), orderId);
        if (!"COMMITTED".equals(outcome) && !"ALREADY_COMMITTED".equals(outcome)) {
            payments.refund(orderId);
            storeLoad.leave(order.getStoreId(), orderId);
            String reason = "EXPIRED".equals(outcome)
                    ? "Payment arrived after the stock hold expired; refunded"
                    : "Item not physically available (oversold); refunded";
            update(orderId, o -> {
                o.setPaymentId(payment.paymentId());
                o.cancel("EXPIRED".equals(outcome) ? OrderStatus.EXPIRED : OrderStatus.CANCELLED, reason);
            }, "Refunded", reason);
            meters.counter("orders.refunded_after_payment", "reason", outcome).increment();
            return publishAndView(orderId);
        }

        tx.executeWithoutResult(s -> {
            Order o = orders.findById(orderId).orElseThrow();
            o.setPaymentId(payment.paymentId());
            o.advance(OrderStatus.CONFIRMED);
            orders.save(o);
            timeline.add(orderId, "Payment received", "₹" + o.getTotal() + " via " + method + " · sent to the store");
            outbox.save(new OutboxEvent(Topics.ORDER_EVENTS, orderId, json.write(event(o, OrderEvent.Type.CONFIRMED, null))));
        });
        meters.counter("orders.confirmed").increment();
        return publishAndView(orderId);
    }

    public OrderView cancel(String orderId, String customerId, String reason) {
        Order order = mine(orderId, customerId);
        if (!order.getStatus().isCancellable()) {
            throw new IllegalStateException("This order can no longer be cancelled: the rider already has it");
        }
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            safeRelease(order);
            update(orderId, o -> o.cancel(OrderStatus.CANCELLED, reason), "Cancelled", reason);
        } else {
            payments.refund(orderId);
            storeLoad.leave(order.getStoreId(), orderId);
            tx.executeWithoutResult(s -> {
                Order o = orders.findById(orderId).orElseThrow();
                o.cancel(OrderStatus.CANCELLED, reason);
                orders.save(o);
                timeline.add(orderId, "Cancelled", reason + " · refund initiated, items returned to the shelf");
                outbox.save(new OutboxEvent(Topics.ORDER_EVENTS, orderId, json.write(event(o, OrderEvent.Type.CANCELLED, reason))));
            });
        }
        meters.counter("orders.cancelled").increment();
        return publishAndView(orderId);
    }

    // ------------------------------------------------------------------ events from other services

    public void onStoreEvent(StoreEvent e) {
        if (!timeline.markProcessed(e.eventId())) return;
        switch (e.type()) {
            case PACKING_STARTED -> update(e.orderId(), o -> o.advance(OrderStatus.PACKING), "Packing", e.detail(), e.at());
            case PACKED -> {
                update(e.orderId(), o -> o.advance(OrderStatus.PACKED), "Packed", e.detail(), e.at());
                storeLoad.leave(e.storeId(), e.orderId());
            }
            case RESERVATION_EXPIRED -> update(e.orderId(), o -> {
                if (o.getStatus() == OrderStatus.PAYMENT_PENDING) o.cancel(OrderStatus.EXPIRED, "Payment not completed in time");
            }, "Expired", e.detail(), e.at());
            default -> { }
        }
        if (e.type() == StoreEvent.Type.RESERVATION_EXPIRED) storeLoad.leave(e.storeId(), e.orderId());
        live.publish(e.orderId());
    }

    public void onDispatchEvent(DispatchEvent e) {
        if (!timeline.markProcessed(e.eventId())) return;
        Instant expected = e.etaSeconds() == null ? null
                : Instant.now().plusMillis(props.realMillis(Duration.ofSeconds(e.etaSeconds())));
        switch (e.type()) {
            case RIDER_ASSIGNED -> update(e.orderId(), o -> {
                o.assignRider(e.riderId(), e.riderName(), e.batchedWith());
                if (expected != null) o.setExpectedBy(expected);
            }, "Rider assigned", e.riderName() + (e.batchedWith() != null ? " (batched with another nearby order)" : ""), e.at());
            case ARRIVED_AT_STORE -> update(e.orderId(), o -> { }, "Rider at store", e.riderName() + " is at the dispatch counter", e.at());
            case PICKED_UP -> update(e.orderId(), o -> {
                o.advance(OrderStatus.OUT_FOR_DELIVERY);
                if (expected != null) o.setExpectedBy(expected);
            }, "Out for delivery", e.riderName() + " picked up your order", e.at());
            case DELIVERED -> update(e.orderId(), o -> o.advance(OrderStatus.DELIVERED), "Delivered", "Enjoy!", e.at());
        }
        if (e.type() == DispatchEvent.Type.DELIVERED) meters.counter("orders.delivered").increment();
        live.publish(e.orderId());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Applies a change with optimistic locking. Two events for the same order arriving at once
     * (say "packed" and "rider assigned") can't overwrite each other: the loser re-reads and retries.
     */
    void update(String orderId, Consumer<Order> change, String label, String detail) {
        update(orderId, change, label, detail, Instant.now());
    }

    void update(String orderId, Consumer<Order> change, String label, String detail, Instant at) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                tx.executeWithoutResult(s -> {
                    Order o = orders.findById(orderId).orElseThrow(() -> new NoSuchElementException(orderId));
                    OrderStatus before = o.getStatus();
                    String riderBefore = o.getRiderId();
                    change.accept(o);
                    orders.save(o);
                    if (o.getStatus() != before || (o.getRiderId() != null && !o.getRiderId().equals(riderBefore))
                            || label.startsWith("Rider at")) {
                        timeline.add(orderId, label, detail, at);
                    }
                });
                return;
            } catch (OptimisticLockingFailureException e) {
                log.debug("Concurrent update on {}, retrying", orderId);
            } catch (NoSuchElementException e) {
                return; // event for an order this service never saw (e.g. test traffic)
            }
        }
        throw new IllegalStateException("Could not update order " + orderId + " after 5 attempts");
    }

    private void safeRelease(Order order) {
        storeLoad.leave(order.getStoreId(), order.getId());
        try {
            inventory.release(order.getStoreId(), order.getId());
        } catch (RuntimeException e) {
            // The reservation will expire on its own; the sweeper returns the stock.
            log.warn("Release failed for {}, leaving it to expire: {}", order.getId(), e.getMessage());
        }
    }

    private Order mine(String orderId, String customerId) {
        Order o = orders.findById(orderId).orElseThrow(() -> new NoSuchElementException("Order not found"));
        if (customerId != null && !customerId.equals(o.getCustomerId())) throw new NoSuchElementException("Order not found");
        return o;
    }

    private OrderEvent event(Order o, OrderEvent.Type type, String reason) {
        return new OrderEvent(UUID.randomUUID().toString(), type, o.getId(), o.getStoreId(), o.getCustomerId(),
                lines(o), o.getCustomerLat(), o.getCustomerLon(), o.getPromisedBy(), reason, Instant.now());
    }

    private OrderView publishAndView(String orderId) {
        live.publish(orderId);
        return view(orderId);
    }

    public List<OrderLine> lines(Order o) {
        return json.read(o.getLines(), LINES);
    }

    public Optional<Order> find(String id) {
        return orders.findById(id);
    }

    public OrderView view(String orderId) {
        return toView(orders.findById(orderId).orElseThrow(() -> new NoSuchElementException("Order not found")), true);
    }

    public OrderView toView(Order o, boolean withTimeline) {
        Instant now = Instant.now();
        Double remaining = null;
        if (o.getStatus().isActive() && o.getExpectedBy() != null) {
            remaining = Math.max(0, round1(props.simulatedMinutes(Duration.between(now, o.getExpectedBy()))));
        }
        Instant end = o.getClosedAt() != null ? o.getClosedAt() : now;
        Instant clockStart = o.getPaidAt() != null ? o.getPaidAt() : o.getPlacedAt();
        Double elapsed = round1(props.simulatedMinutes(Duration.between(clockStart, end)));
        Double deliveredIn = o.getDeliveredAt() == null || o.getPaidAt() == null ? null
                : round1(props.simulatedMinutes(Duration.between(o.getPaidAt(), o.getDeliveredAt())));
        Boolean onTime = o.getDeliveredAt() == null ? null : !o.getDeliveredAt().isAfter(o.getPromisedBy());
        return new OrderView(o.getId(), o.getStatus().name(), o.getStoreId(), o.getCustomerId(), o.getCustomerName(),
                lines(o), o.getItemCount(), o.getItemTotal(), o.getDeliveryFee(), o.getTotal(), o.getCustomerLat(),
                o.getCustomerLon(), o.getAddress(), o.getDistanceKm(), o.getPromisedMinutes(), o.getPromisedBy(),
                o.getExpectedBy(), remaining, elapsed, deliveredIn, onTime, o.getReservationExpiresAt(), o.getRiderId(),
                o.getRiderName(), o.getBatchedWith(), o.getCancelReason(), o.getSource(), o.getPlacedAt(), o.getPaidAt(),
                o.getPackingAt(), o.getPackedAt(), o.getRiderAssignedAt(), o.getPickedUpAt(), o.getDeliveredAt(),
                withTimeline ? timeline.forOrder(o.getId()) : List.of());
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** Short, human-readable, time-sortable: SK-<base36 millis>-<4 random>. */
    static String newOrderId() {
        return "SK-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase() + "-"
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    public record PlaceOrder(double lat, double lon, String address, List<Item> items, String source) {
        public record Item(String sku, int qty) {
        }
    }

    public static class OutOfStockException extends RuntimeException {
        private final List<String> skus;

        public OutOfStockException(List<String> skus, List<String> names) {
            super("Sold out: " + String.join(", ", names));
            this.skus = skus;
        }

        public List<String> skus() {
            return skus;
        }
    }
}
