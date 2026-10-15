package com.swiftkart.order.order;

import com.swiftkart.contracts.DispatchEvent;
import com.swiftkart.contracts.StoreEvent;
import com.swiftkart.contracts.Topics;
import com.swiftkart.order.config.JsonCodec;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventConsumers {

    private final OrderService orders;
    private final JsonCodec json;

    public EventConsumers(OrderService orders, JsonCodec json) {
        this.orders = orders;
        this.json = json;
    }

    @KafkaListener(topics = Topics.STORE_EVENTS, groupId = "order-service", concurrency = "3")
    public void onStore(String message) {
        orders.onStoreEvent(json.read(message, StoreEvent.class));
    }

    @KafkaListener(topics = Topics.DISPATCH_EVENTS, groupId = "order-service", concurrency = "3")
    public void onDispatch(String message) {
        orders.onDispatchEvent(json.read(message, DispatchEvent.class));
    }
}
