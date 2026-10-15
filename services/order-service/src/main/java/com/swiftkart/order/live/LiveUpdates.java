package com.swiftkart.order.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Pushes order changes to browsers over Server-Sent Events.
 *
 * <p>A customer's tracking page may be connected to any order-service replica, while the Kafka
 * event that changed the order may be consumed by another one. Every change is therefore
 * broadcast on a Redis pub/sub channel; each replica forwards it to the browsers connected to it.
 */
@Component
public class LiveUpdates {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdates.class);
    public static final String CHANNEL = "order-updates";

    private final StringRedisTemplate redis;
    private final Map<String, List<SseEmitter>> byOrder = new ConcurrentHashMap<>();
    private final List<SseEmitter> ops = new CopyOnWriteArrayList<>();
    private volatile Function<String, Object> renderer = id -> Map.of("id", id);

    public LiveUpdates(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Set by the controller layer, which knows how to turn an order id into its JSON view. */
    public void setRenderer(Function<String, Object> renderer) {
        this.renderer = renderer;
    }

    public void publish(String orderId) {
        redis.convertAndSend(CHANNEL, orderId);
    }

    public SseEmitter subscribe(String orderId) {
        SseEmitter emitter = register(byOrder.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()));
        send(emitter, "order", renderer.apply(orderId));
        return emitter;
    }

    public SseEmitter subscribeOps() {
        return register(ops);
    }

    void onMessage(String orderId) {
        List<SseEmitter> watchers = byOrder.get(orderId);
        if ((watchers == null || watchers.isEmpty()) && ops.isEmpty()) return;
        Object view;
        try {
            view = renderer.apply(orderId);
        } catch (RuntimeException e) {
            return;
        }
        if (watchers != null) watchers.forEach(e -> send(e, "order", view));
        ops.forEach(e -> send(e, "order", view));
    }

    private SseEmitter register(List<SseEmitter> list) {
        SseEmitter emitter = new SseEmitter(30 * 60_000L);
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            }
        } catch (IOException | IllegalStateException e) {
            byOrder.values().forEach(l -> l.remove(emitter));
            ops.remove(emitter);
            log.debug("Dropped SSE client: {}", e.getMessage());
        }
    }

    @Configuration
    static class Wiring {
        @Bean
        RedisMessageListenerContainer orderUpdatesListener(RedisConnectionFactory factory, LiveUpdates live) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            container.addMessageListener((message, pattern) -> live.onMessage(new String(message.getBody())),
                    new ChannelTopic(CHANNEL));
            return container;
        }
    }
}
