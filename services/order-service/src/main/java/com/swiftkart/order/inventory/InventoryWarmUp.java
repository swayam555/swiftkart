package com.swiftkart.order.inventory;

import com.swiftkart.contracts.ReservationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Exercises the inventory call paths (connection pool, JIT, Lua script cache on the other side)
 * before real traffic arrives. Without it, the first flash sale after a deploy can time out on
 * cold code paths and trip the circuit breaker.
 */
@Component
public class InventoryWarmUp {

    private static final Logger log = LoggerFactory.getLogger(InventoryWarmUp.class);
    private final InventoryClient inventory;

    public InventoryWarmUp(InventoryClient inventory) {
        this.inventory = inventory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long start = System.currentTimeMillis();
        try {
            for (InventoryClient.Store s : inventory.stores()) {
                inventory.nearest(s.lat(), s.lon());
                inventory.catalog(s.id());
                for (int i = 0; i < 5; i++) {
                    String id = "warmup-" + s.id() + "-" + i;
                    var r = inventory.reserve(new ReservationRequest(id, s.id(),
                            List.of(new ReservationRequest.Item("BV-WATER", 1)), ReservationRequest.Mode.ATOMIC));
                    if (r.reserved()) inventory.release(s.id(), id);
                }
            }
            log.info("Inventory paths warmed up in {} ms", System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            log.info("Inventory warm-up skipped: {}", e.getMessage());
        }
    }
}
