package com.swiftkart.inventory.api;

import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.contracts.ReservationResult;
import com.swiftkart.inventory.stock.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Called by order-service only; the gateway does not route {@code /internal/**}. */
@RestController
@RequestMapping("/internal/reservations")
public class ReservationController {

    private final StockService stock;

    public ReservationController(StockService stock) {
        this.stock = stock;
    }

    @PostMapping
    public ReservationResult reserve(@RequestBody ReservationRequest req) {
        return stock.reserve(req);
    }

    @PostMapping("/{storeId}/{orderId}/commit")
    public ResponseEntity<Map<String, String>> commit(@PathVariable String storeId, @PathVariable String orderId) {
        StockService.CommitOutcome outcome = stock.commit(storeId, orderId);
        return outcome == StockService.CommitOutcome.EXPIRED
                ? ResponseEntity.status(409).body(Map.of("outcome", outcome.name()))
                : ResponseEntity.ok(Map.of("outcome", outcome.name()));
    }

    @DeleteMapping("/{storeId}/{orderId}")
    public Map<String, Boolean> release(@PathVariable String storeId, @PathVariable String orderId) {
        return Map.of("released", stock.release(storeId, orderId));
    }
}
