package com.swiftkart.order.payment;

import com.swiftkart.order.config.AppProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for a payment gateway (Razorpay, PayU, Juspay...). Idempotent per order, so a retried
 * "Pay" tap never charges twice; fails a configurable share of payments to exercise the saga's
 * compensation path.
 */
@Component
public class MockUpiGateway {

    private final JdbcClient jdbc;
    private final AppProperties props;

    public MockUpiGateway(JdbcClient jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public Result charge(String orderId, BigDecimal amount, String method, boolean forceFailure, boolean randomFailures) {
        Optional<Result> existing = jdbc.sql("SELECT id, status FROM payments WHERE order_id = ?").param(orderId)
                .query((rs, n) -> new Result(rs.getString("id"), "CAPTURED".equals(rs.getString("status")))).optional();
        if (existing.isPresent()) return existing.get();

        sleep(ThreadLocalRandom.current().nextLong(120, 350)); // bank round trip
        boolean ok = !forceFailure
                && (!randomFailures || ThreadLocalRandom.current().nextDouble() >= props.paymentFailureRate());
        String id = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        jdbc.sql("INSERT INTO payments (id, order_id, amount, method, status) VALUES (?, ?, ?, ?, ?) ON CONFLICT (order_id) DO NOTHING")
                .params(id, orderId, amount, method, ok ? "CAPTURED" : "DECLINED").update();
        return new Result(id, ok);
    }

    public void refund(String orderId) {
        jdbc.sql("UPDATE payments SET status = 'REFUNDED', refunded_at = now() WHERE order_id = ? AND status = 'CAPTURED'")
                .param(orderId).update();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record Result(String paymentId, boolean captured) {
    }
}
