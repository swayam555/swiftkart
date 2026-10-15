package com.swiftkart.inventory;

import com.swiftkart.contracts.OrderLine;
import com.swiftkart.inventory.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PickDurationTest {

    @Test
    void demoClockScalesSimulatedTime() {
        AppProperties props = new AppProperties(6, Duration.ofSeconds(120));
        assertThat(props.realMillis(Duration.ofMinutes(1))).isEqualTo(10_000);
    }

    @Test
    void pickingTakesAMinutePlusTwelveSecondsPerUnit() throws Exception {
        var method = com.swiftkart.inventory.picking.PickingService.class.getDeclaredMethod("pickDuration", List.class);
        method.setAccessible(true);
        Duration d = (Duration) method.invoke(null, List.of(new OrderLine("A", "a", 3, BigDecimal.ONE),
                new OrderLine("B", "b", 2, BigDecimal.ONE)));
        assertThat(d).isEqualTo(Duration.ofSeconds(120));
    }
}
