package com.swiftkart.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param timeScale           demo clock speed-up shared by every service (6 = six simulated seconds per real second)
 * @param inventoryUrl        inventory-service base URL
 * @param paymentFailureRate  share of mock UPI payments that fail, to exercise the compensation path
 * @param freeDeliveryAbove   orders above this value ship free
 */
@ConfigurationProperties(prefix = "swiftkart")
public record AppProperties(@DefaultValue("6") double timeScale,
                            @DefaultValue("http://localhost:8092") String inventoryUrl,
                            @DefaultValue("0.03") double paymentFailureRate,
                            @DefaultValue("199") BigDecimal freeDeliveryAbove,
                            @DefaultValue("25") BigDecimal deliveryFee,
                            @DefaultValue("120") long reservationTtlSeconds) {

    public long realMillis(Duration simulated) {
        return (long) (simulated.toMillis() / timeScale);
    }

    public double simulatedMinutes(Duration real) {
        return real.toMillis() * timeScale / 60_000.0;
    }
}
