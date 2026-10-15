package com.swiftkart.inventory.picking;

import com.swiftkart.contracts.OrderEvent;
import com.swiftkart.contracts.OrderLine;
import com.swiftkart.contracts.StoreEvent;
import com.swiftkart.contracts.Topics;
import com.swiftkart.inventory.config.AppProperties;
import com.swiftkart.inventory.config.JsonCodec;
import com.swiftkart.inventory.stock.Keys;
import com.swiftkart.inventory.stock.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Simulates the dark-store floor. Each store has a few pickers; a confirmed order goes to the
 * picker who frees up first, and takes 1 minute plus 12 seconds per item to pick and pack.
 *
 * <p>Future events are not timers in memory: they are rows in a Redis sorted set scored by due
 * time. A poller publishes whatever is due, so a restart or a second replica never loses or
 * duplicates a "packed" event (ZREM decides who publishes).
 */
@Service
public class PickingService {

    private static final Logger log = LoggerFactory.getLogger(PickingService.class);

    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;
    private final JsonCodec json;
    private final AppProperties props;
    private final StockService stock;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> pickSlot;

    public PickingService(StringRedisTemplate redis, KafkaTemplate<String, String> kafka, JsonCodec json,
                          AppProperties props, StockService stock) throws IOException {
        this.redis = redis;
        this.kafka = kafka;
        this.json = json;
        this.props = props;
        this.stock = stock;
        this.pickSlot = new DefaultRedisScript<>(new ClassPathResource("scripts/pick_slot.lua")
                .getContentAsString(StandardCharsets.UTF_8), List.class);
    }

    static Duration pickDuration(List<OrderLine> lines) {
        int units = lines.stream().mapToInt(OrderLine::qty).sum();
        return Duration.ofSeconds(60 + 12L * Math.min(units, 20));
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "inventory-picking")
    public void onOrder(String message) {
        OrderEvent e = json.read(message, OrderEvent.class);
        switch (e.type()) {
            case CONFIRMED -> schedule(e);
            case CANCELLED -> cancel(e);
        }
    }

    private void schedule(OrderEvent e) {
        Boolean first = redis.opsForValue().setIfAbsent("picking:seen:" + e.orderId(), "1", Duration.ofDays(1));
        if (!Boolean.TRUE.equals(first)) return;
        long now = System.currentTimeMillis();
        long duration = props.realMillis(pickDuration(e.lines()));
        @SuppressWarnings("unchecked")
        List<Object> slot = redis.execute(pickSlot, List.of(Keys.pickers(e.storeId())),
                String.valueOf(now), String.valueOf(duration));
        String picker = slot.get(0).toString();
        long start = Long.parseLong(slot.get(1).toString());
        long end = Long.parseLong(slot.get(2).toString());
        enqueue(e.orderId(), new StoreEvent(UUID.randomUUID().toString(), StoreEvent.Type.PACKING_STARTED,
                e.orderId(), e.storeId(), "Picker " + picker + " started packing", null), start);
        enqueue(e.orderId(), new StoreEvent(UUID.randomUUID().toString(), StoreEvent.Type.PACKED, e.orderId(),
                e.storeId(), "Packed by " + picker + ", waiting at the dispatch counter", null), end);
    }

    private void enqueue(String orderId, StoreEvent event, long dueAt) {
        String member = json.write(event);
        redis.opsForZSet().add(Keys.DUE_EVENTS, member, dueAt);
        redis.opsForSet().add("picking:order:" + orderId, member);
        redis.expire("picking:order:" + orderId, Duration.ofHours(6));
    }

    /** A paid order cancelled before pickup: stop picking it and put the items back on the shelf. */
    private void cancel(OrderEvent e) {
        Set<String> pending = redis.opsForSet().members("picking:order:" + e.orderId());
        if (pending != null) pending.forEach(m -> redis.opsForZSet().remove(Keys.DUE_EVENTS, m));
        if (e.lines() != null && !e.lines().isEmpty()) {
            Map<String, Integer> lines = new LinkedHashMap<>();
            e.lines().forEach(l -> lines.merge(l.sku(), l.qty(), Integer::sum));
            stock.returnToShelf(e.storeId(), e.orderId(), lines);
        }
    }

    @Scheduled(fixedDelay = 200)
    public void publishDue() {
        Set<String> due = redis.opsForZSet().rangeByScore(Keys.DUE_EVENTS, 0, System.currentTimeMillis(), 0, 200);
        if (due == null) return;
        for (String member : due) {
            Long removed = redis.opsForZSet().remove(Keys.DUE_EVENTS, member);
            if (removed == null || removed == 0) continue; // another replica published it
            StoreEvent template = json.read(member, StoreEvent.class);
            StoreEvent event = new StoreEvent(template.eventId(), template.type(), template.orderId(),
                    template.storeId(), template.detail(), Instant.now());
            kafka.send(Topics.STORE_EVENTS, event.orderId(), json.write(event));
            log.debug("{} for {}", event.type(), event.orderId());
        }
    }
}
