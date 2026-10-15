package com.swiftkart.order.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRulesTest {

    private static Order order() {
        return new Order("SK-1", "cust-1", "A", "BLR-KOR", "[]", 3, new BigDecimal("150"), BigDecimal.ZERO,
                12.93, 77.62, "addr", 1.2, 8, Instant.now().plusSeconds(80), Instant.now().plusSeconds(120), "APP");
    }

    @Test
    void statusOnlyMovesForward() {
        Order o = order();
        assertThat(o.advance(OrderStatus.CONFIRMED)).isTrue();
        assertThat(o.advance(OrderStatus.PACKED)).isTrue();
        // A late "packing started" event must not move the order backwards.
        assertThat(o.advance(OrderStatus.PACKING)).isFalse();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PACKED);
    }

    @Test
    void terminalStatesAreFinal() {
        Order o = order();
        o.cancel(OrderStatus.CANCELLED, "changed mind");
        assertThat(o.advance(OrderStatus.CONFIRMED)).isFalse();
        assertThat(o.getCancelReason()).isEqualTo("changed mind");
        assertThat(o.getClosedAt()).isNotNull();
    }

    @Test
    void cancellationIsAllowedOnlyUntilPickup() {
        assertThat(OrderStatus.PACKED.isCancellable()).isTrue();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.isCancellable()).isFalse();
        assertThat(OrderStatus.DELIVERED.isCancellable()).isFalse();
    }

    @Test
    void promiseClockRestartsAtConfirmation() throws Exception {
        Order o = order();
        Instant before = o.getPromisedBy();
        Thread.sleep(30);
        o.advance(OrderStatus.CONFIRMED);
        assertThat(o.getPromisedBy()).isAfter(before);
        assertThat(o.getExpectedBy()).isEqualTo(o.getPromisedBy());
    }

    @Test
    void etaAddsPickingQueueAndRide() {
        Duration nearAndQuiet = EtaEstimator.estimate(3, 1.0, 0);
        Duration farAndBusy = EtaEstimator.estimate(3, 2.8, 9);
        assertThat(farAndBusy).isGreaterThan(nearAndQuiet);
        // 96 s picking + 60 s handover + 1 km * 1.4 / 20 km/h = 252 s ride
        assertThat(nearAndQuiet.toSeconds()).isEqualTo(96 + 60 + 252);
        assertThat(EtaEstimator.promisedMinutes(nearAndQuiet)).isEqualTo(8);
    }

    @Test
    void orderIdsAreShortAndUnique() {
        String a = OrderService.newOrderId();
        String b = OrderService.newOrderId();
        assertThat(a).startsWith("SK-").isNotEqualTo(b);
    }
}
