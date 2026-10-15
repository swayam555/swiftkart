package com.swiftkart.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param timeScale      demo clock speed-up: 6 means one real second is six simulated seconds,
 *                       so a 10-minute delivery plays out in under two minutes
 * @param reservationTtl how long a cart's stock is held while the customer pays (real time)
 */
@ConfigurationProperties(prefix = "swiftkart")
public record AppProperties(@DefaultValue("6") double timeScale,
                            @DefaultValue("120s") Duration reservationTtl) {

    /** Converts a simulated duration into real milliseconds. */
    public long realMillis(Duration simulated) {
        return (long) (simulated.toMillis() / timeScale);
    }
}
