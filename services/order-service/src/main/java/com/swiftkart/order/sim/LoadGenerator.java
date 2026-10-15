package com.swiftkart.order.sim;

import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.order.inventory.InventoryClient;
import com.swiftkart.order.order.OrderService;
import com.swiftkart.order.order.OrderView;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synthetic customers ordering from random dark stores, through the real API path: reservation,
 * payment (with occasional declines), and the odd cancellation. Keeps the ops dashboard alive.
 */
@Component
public class LoadGenerator {

    private static final Logger log = LoggerFactory.getLogger(LoadGenerator.class);
    private static final String[] NAMES = {"Aarav", "Diya", "Vihaan", "Ananya", "Kabir", "Ishita", "Rohan", "Meera",
            "Arjun", "Sara", "Dev", "Tara", "Neel", "Kiara", "Aditya", "Riya"};
    private static final String[] AREAS = {"1st Cross", "Main Road", "Tower B, Flat 1204", "Villa 17", "2nd Floor",
            "Near Metro", "Sector 2", "Opp. Park"};

    private final OrderService orders;
    private final InventoryClient inventory;
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Random rnd = new Random();
    private final AtomicLong placed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final Map<String, Long> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> loop;
    private volatile double perMinute;
    private volatile String city;
    private double carry;

    public LoadGenerator(OrderService orders, InventoryClient inventory) {
        this.orders = orders;
        this.inventory = inventory;
    }

    public synchronized Status start(double ordersPerMinute, String city) {
        this.perMinute = Math.max(1, Math.min(ordersPerMinute, 600));
        this.city = city;
        if (loop == null || loop.isDone()) loop = ticker.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
        return status();
    }

    public synchronized Status stop() {
        if (loop != null) loop.cancel(false);
        loop = null;
        return status();
    }

    public Status status() {
        return new Status(loop != null && !loop.isDone(), perMinute, city, placed.get(), failed.get(), Map.copyOf(failures));
    }

    private void tick() {
        carry += perMinute / 60.0;
        int n = (int) carry;
        carry -= n;
        for (int i = 0; i < n; i++) workers.submit(this::oneCustomer);
    }

    private void oneCustomer() {
        try {
            List<InventoryClient.Store> stores = inventory.stores().stream()
                    .filter(s -> city == null || city.isBlank() || s.city().equalsIgnoreCase(city)).toList();
            if (stores.isEmpty()) return;
            InventoryClient.Store store = stores.get(rnd.nextInt(stores.size()));
            // A point within ~2.4 km of the store (1 degree latitude is about 111 km).
            double r = 0.004 + rnd.nextDouble() * 0.018;
            double angle = rnd.nextDouble() * 2 * Math.PI;
            double lat = store.lat() + r * Math.cos(angle);
            double lon = store.lon() + r * Math.sin(angle) / Math.cos(Math.toRadians(store.lat()));

            Map<String, InventoryClient.CatalogItem> catalog = inventory.catalog(store.id());
            List<InventoryClient.CatalogItem> everyday = catalog.values().stream()
                    .filter(c -> !c.sku().startsWith("FLASH") && c.available() > 0).toList();
            if (everyday.isEmpty()) return;
            List<OrderService.PlaceOrder.Item> cart = new ArrayList<>();
            int lines = 1 + rnd.nextInt(4);
            for (int i = 0; i < lines; i++) {
                var p = everyday.get(rnd.nextInt(everyday.size()));
                if (cart.stream().noneMatch(c -> c.sku().equals(p.sku()))) {
                    cart.add(new OrderService.PlaceOrder.Item(p.sku(), 1 + rnd.nextInt(3)));
                }
            }
            String name = NAMES[rnd.nextInt(NAMES.length)];
            String customer = "sim-" + name.toLowerCase() + "-" + rnd.nextInt(10_000);
            OrderView o = orders.create(new OrderService.PlaceOrder(lat, lon, AREAS[rnd.nextInt(AREAS.length)],
                    cart, "SIMULATOR"), customer, name, null, ReservationRequest.Mode.ATOMIC);
            placed.incrementAndGet();
            Thread.sleep(500 + rnd.nextInt(2500)); // customer finds their UPI app
            OrderView paid = orders.pay(o.id(), customer, "UPI", false);
            if ("CONFIRMED".equals(paid.status()) && rnd.nextDouble() < 0.02) {
                Thread.sleep(2000);
                orders.cancel(o.id(), customer, "Customer changed their mind");
            }
        } catch (OrderService.OutOfStockException e) {
            failed.incrementAndGet();
            failures.merge("Sold out", 1L, Long::sum);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            failed.incrementAndGet();
            failures.merge(e.getClass().getSimpleName() + ": " + e.getMessage(), 1L, Long::sum);
            log.debug("Simulated order failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        ticker.shutdownNow();
        workers.shutdownNow();
    }

    public record Status(boolean running, double ordersPerMinute, String city, long placed, long failed,
                         Map<String, Long> failureReasons) {
    }
}
