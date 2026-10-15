package com.swiftkart.dispatch.fleet;

import com.swiftkart.contracts.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Every replica consumes Kafka (partitions are shared across the group), but only the leader
 * mutates the fleet. Consumed events are handed to the leader through a Redis list, preserving
 * their order.
 */
@Component
public class Inbox {

    private final FleetStore store;

    public Inbox(FleetStore store) {
        this.store = store;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "dispatch")
    public void onOrder(String message) {
        store.pushInbox("O" + message);
    }

    @KafkaListener(topics = Topics.STORE_EVENTS, groupId = "dispatch")
    public void onStore(String message) {
        store.pushInbox("S" + message);
    }
}
