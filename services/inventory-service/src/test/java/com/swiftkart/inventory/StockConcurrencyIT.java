package com.swiftkart.inventory;

import com.swiftkart.contracts.ReservationRequest;
import com.swiftkart.contracts.ReservationResult;
import com.swiftkart.inventory.config.AppProperties;
import com.swiftkart.inventory.stock.Keys;
import com.swiftkart.inventory.stock.StockService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The flash-sale guarantee, proven against a real Redis and a real Postgres ledger. */
@Testcontainers
class StockConcurrencyIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static final String STORE = "BLR-KOR";
    static final String SKU = "FLASH-PS5";
    static StringRedisTemplate redis;
    static JdbcClient jdbc;
    static StockService stock;

    @BeforeAll
    static void setUp() throws Exception {
        LettuceConnectionFactory f = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        f.afterPropertiesSet();
        redis = new StringRedisTemplate(f);
        redis.afterPropertiesSet();
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = JdbcClient.create(ds);
        stock = new StockService(redis, jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)),
                new AppProperties(6, Duration.ofSeconds(120)), new SimpleMeterRegistry());
    }

    @BeforeEach
    void fiftyUnits() {
        jdbc.sql("UPDATE store_stock SET on_hand = 50 WHERE store_id = ? AND sku = ?").params(STORE, SKU).update();
        redis.opsForValue().set(Keys.stock(STORE, SKU), "50");
        redis.opsForValue().set(Keys.reserved(STORE, SKU), "0");
    }

    private int race(ReservationRequest.Mode mode, int buyers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(64);
        List<Callable<ReservationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            tasks.add(() -> stock.reserve(new ReservationRequest(UUID.randomUUID().toString(), STORE,
                    List.of(new ReservationRequest.Item(SKU, 1)), mode)));
        }
        int won = 0;
        for (Future<ReservationResult> f : pool.invokeAll(tasks)) if (f.get().reserved()) won++;
        pool.shutdown();
        return won;
    }

    @Test
    void atomicReservationNeverOversells() throws Exception {
        assertThat(race(ReservationRequest.Mode.ATOMIC, 300)).isEqualTo(50);
        assertThat(redis.opsForValue().get(Keys.stock(STORE, SKU))).isEqualTo("0");
        assertThat(redis.opsForValue().get(Keys.reserved(STORE, SKU))).isEqualTo("50");
    }

    @Test
    void naiveCheckThenActOversells() throws Exception {
        // The point of the demo: without atomicity, more buyers "win" than there are units.
        assertThat(race(ReservationRequest.Mode.NAIVE, 300)).isGreaterThan(50);
    }

    @Test
    void cartIsAllOrNothing() {
        redis.opsForValue().set(Keys.stock(STORE, "DA-MILK"), "10");
        ReservationResult r = stock.reserve(new ReservationRequest("order-1", STORE, List.of(
                new ReservationRequest.Item("DA-MILK", 2), new ReservationRequest.Item(SKU, 51)),
                ReservationRequest.Mode.ATOMIC));
        assertThat(r.reserved()).isFalse();
        assertThat(r.unavailableSkus()).containsExactly(SKU);
        assertThat(redis.opsForValue().get(Keys.stock(STORE, "DA-MILK"))).isEqualTo("10");
    }

    @Test
    void reserveIsIdempotentPerOrder() {
        var req = new ReservationRequest("order-2", STORE, List.of(new ReservationRequest.Item(SKU, 2)),
                ReservationRequest.Mode.ATOMIC);
        stock.reserve(req);
        stock.reserve(req);
        assertThat(redis.opsForValue().get(Keys.stock(STORE, SKU))).isEqualTo("48");
    }

    @Test
    void commitMovesUnitsFromReservedToTheLedgerExactlyOnce() {
        stock.reserve(new ReservationRequest("order-3", STORE, List.of(new ReservationRequest.Item(SKU, 3)),
                ReservationRequest.Mode.ATOMIC));
        assertThat(stock.commit(STORE, "order-3")).isEqualTo(StockService.CommitOutcome.COMMITTED);
        assertThat(stock.commit(STORE, "order-3")).isEqualTo(StockService.CommitOutcome.ALREADY_COMMITTED);
        Integer ledger = jdbc.sql("SELECT on_hand FROM store_stock WHERE store_id = ? AND sku = ?")
                .params(STORE, SKU).query(Integer.class).single();
        assertThat(ledger).isEqualTo(47);
        assertThat(redis.opsForValue().get(Keys.reserved(STORE, SKU))).isEqualTo("0");
        assertThat(redis.opsForValue().get(Keys.stock(STORE, SKU))).isEqualTo("47");
    }

    @Test
    void ledgerRejectionDropsTheReservationWithoutCreatingPhantomStock() {
        stock.reserve(new ReservationRequest("order-5", STORE, List.of(new ReservationRequest.Item(SKU, 2)),
                ReservationRequest.Mode.ATOMIC));
        // Someone else sold the physical units in the meantime: the ledger is now empty.
        jdbc.sql("UPDATE store_stock SET on_hand = 0 WHERE store_id = ? AND sku = ?").params(STORE, SKU).update();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> stock.commit(STORE, "order-5"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(redis.opsForValue().get(Keys.reserved(STORE, SKU))).isEqualTo("0");
        assertThat(redis.opsForValue().get(Keys.stock(STORE, SKU))).isEqualTo("48");
        assertThat(redis.hasKey(Keys.reservation(STORE, "order-5"))).isFalse();
    }

    @Test
    void releaseReturnsStockAndBlocksALaterCommit() {
        stock.reserve(new ReservationRequest("order-4", STORE, List.of(new ReservationRequest.Item(SKU, 5)),
                ReservationRequest.Mode.ATOMIC));
        assertThat(stock.release(STORE, "order-4")).isTrue();
        assertThat(stock.release(STORE, "order-4")).isFalse();
        assertThat(redis.opsForValue().get(Keys.stock(STORE, SKU))).isEqualTo("50");
        assertThat(stock.commit(STORE, "order-4")).isEqualTo(StockService.CommitOutcome.EXPIRED);
    }
}
