package com.swiftkart.order.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Moves committed outbox rows to Kafka. Delivery is at-least-once: if the relay crashes after
 * sending but before marking a row, the row is sent again, and consumers de-duplicate on txnId.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 500;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 100)
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outbox.lockNextBatch(BATCH);
        if (batch.isEmpty()) return;
        List<CompletableFuture<SendResult<String, String>>> sends = new ArrayList<>(batch.size());
        for (OutboxEvent e : batch) {
            sends.add(kafka.send(e.getTopic(), e.getEventKey(), e.getPayload()));
        }
        try {
            CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            // Roll back: nothing is marked, the whole batch is retried on the next tick.
            throw new IllegalStateException("Kafka publish failed, will retry batch of " + batch.size(), ex);
        }
        batch.forEach(OutboxEvent::markPublished);
        log.debug("Relayed {} outbox events", batch.size());
    }

    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public void purge() {
        int removed = outbox.deletePublishedBefore(Instant.now().minus(Duration.ofHours(6)));
        if (removed > 0) log.info("Purged {} relayed outbox events", removed);
    }
}
