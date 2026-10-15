package com.swiftkart.order.sim;

import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.order.inventory.InventoryClient;
import com.swiftkart.order.order.OrderService;
import com.swiftkart.order.order.OrderView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The 12 PM flash sale: a few hundred customers tap "Buy" on a limited deal in the same instant.
 * Every buyer goes through the real checkout (reserve → pay → commit). Run it in ATOMIC mode to
 * see exactly {@code units} orders confirmed, or in NAIVE mode to watch check-then-act sell more
 * consoles than exist and force refunds after payment.
 */
@Component
public class FlashSale {

    static final String SKU = "FLASH-PS5";

    private final OrderService orders;
    private final InventoryClient inventory;

    public FlashSale(OrderService orders, InventoryClient inventory) {
        this.orders = orders;
        this.inventory = inventory;
    }

    public synchronized Result run(String storeId, int units, int buyers, ReservationRequest.Mode mode) throws Exception {
        if (units < 1 || units > 500 || buyers < 1 || buyers > 1000) {
            throw new IllegalArgumentException("units must be 1-500 and buyers 1-1000");
        }
        InventoryClient.Store store = inventory.stores().stream().filter(s -> s.id().equals(storeId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown store " + storeId));
        inventory.setStock(storeId, SKU, units);

        CountDownLatch go = new CountDownLatch(1);
        Map<String, Integer> errorKinds = new java.util.concurrent.ConcurrentHashMap<>();
        long started;
        List<Future<Outcome>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < buyers; i++) {
                String customer = "flash-buyer-" + i;
                futures.add(pool.submit(() -> {
                    go.await();
                    double lat = store.lat() + ThreadLocalRandom.current().nextDouble(-0.01, 0.01);
                    double lon = store.lon() + ThreadLocalRandom.current().nextDouble(-0.01, 0.01);
                    try {
                        OrderView o = orders.create(new OrderService.PlaceOrder(lat, lon, "Flash sale buyer",
                                List.of(new OrderService.PlaceOrder.Item(SKU, 1)), "FLASH"), customer, "Flash buyer",
                                null, mode);
                        OrderView paid = orders.pay(o.id(), customer, "UPI", false);
                        return new Outcome(true, paid.status());
                    } catch (OrderService.OutOfStockException e) {
                        return new Outcome(false, "SOLD_OUT");
                    } catch (RuntimeException e) {
                        Throwable root = e;
                        while (root.getCause() != null) root = root.getCause();
                        errorKinds.merge(root.getClass().getSimpleName(), 1, Integer::sum);
                        return new Outcome(false, "ERROR");
                    }
                }));
            }
            started = System.nanoTime();
            go.countDown();
        }
        long millis = (System.nanoTime() - started) / 1_000_000;

        int reservedCount = 0, confirmed = 0, refunded = 0, soldOut = 0, errors = 0;
        for (Future<Outcome> f : futures) {
            Outcome o = f.get();
            if (o.reserved()) reservedCount++;
            if ("CONFIRMED".equals(o.status())) confirmed++;
            else if ("CANCELLED".equals(o.status()) || "EXPIRED".equals(o.status())) refunded++;
            else if ("SOLD_OUT".equals(o.status())) soldOut++;
            else if ("ERROR".equals(o.status())) errors++;
        }
        Map<String, Object> after = inventory.stockOf(storeId, SKU);
        return new Result(mode.name(), storeId, units, buyers, reservedCount, confirmed, soldOut, refunded, errors,
                Math.max(0, reservedCount - units), millis, after, errorKinds);
    }

    record Outcome(boolean reserved, String status) {
    }

    /**
     * @param toldYes         buyers whose checkout said "reserved, please pay"
     * @param refundedAfterPay paid but no console existed for them: refunded and cancelled
     * @param oversold         promises made beyond the units that physically exist
     */
    public record Result(String mode, String storeId, int units, int buyers, int toldYes, int confirmed, int soldOut,
                         int refundedAfterPay, int errors, int oversold, long durationMs, Map<String, Object> stockAfter,
                         Map<String, Integer> errorKinds) {
    }
}
