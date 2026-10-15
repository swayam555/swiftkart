package com.swiftkart.order.order;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class StoreLoadIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static StoreLoad load;

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory f = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        f.afterPropertiesSet();
        StringRedisTemplate t = new StringRedisTemplate(f);
        t.afterPropertiesSet();
        load = new StoreLoad(t);
    }

    @Test
    void simultaneousCheckoutsGetDistinctQueuePositions() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Callable<Long>> joins = IntStream.range(0, 50)
                .<Callable<Long>>mapToObj(i -> () -> load.join("BURST", "order-" + i)).toList();
        List<Long> positions = pool.invokeAll(joins).stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).sorted().toList();
        pool.shutdown();
        // Every buyer sees a different number of orders ahead: 0, 1, 2 ... 49.
        assertThat(positions).containsExactlyElementsOf(IntStream.range(0, 50).mapToObj(Long::valueOf).toList());
    }

    @Test
    void leavingFreesAPlaceAndRejoiningIsIdempotent() {
        assertThat(load.join("S", "a")).isZero();
        assertThat(load.join("S", "b")).isEqualTo(1);
        assertThat(load.join("S", "b")).isEqualTo(2); // position reported, but no duplicate entry
        load.leave("S", "a");
        assertThat(load.join("S", "c")).isEqualTo(1);
    }
}
