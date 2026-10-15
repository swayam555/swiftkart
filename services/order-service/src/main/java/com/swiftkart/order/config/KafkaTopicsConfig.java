package com.swiftkart.order.config;

import com.swiftkart.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Every service declares every topic it touches, with identical partition counts (auto-create is off). */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic orderEvents() {
        return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic storeEvents() {
        return TopicBuilder.name(Topics.STORE_EVENTS).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic dispatchEvents() {
        return TopicBuilder.name(Topics.DISPATCH_EVENTS).partitions(6).replicas(1).build();
    }
}
