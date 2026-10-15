package com.swiftkart.order.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class TimelineRepository {

    private final JdbcClient jdbc;

    public TimelineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void add(String orderId, String label, String detail) {
        add(orderId, label, detail, Instant.now());
    }

    /** Uses the time the event happened, not when this service processed it. */
    public void add(String orderId, String label, String detail, Instant at) {
        jdbc.sql("INSERT INTO order_timeline (order_id, label, detail, at) VALUES (?, ?, ?, ?)")
                .params(orderId, label, detail, java.sql.Timestamp.from(at == null ? Instant.now() : at)).update();
    }

    public List<Entry> forOrder(String orderId) {
        return jdbc.sql("SELECT at, label, detail FROM order_timeline WHERE order_id = ? ORDER BY at, id")
                .param(orderId).query(Entry.class).list();
    }

    /** Records the event id; false means it was already processed (duplicate delivery). */
    public boolean markProcessed(String eventId) {
        return jdbc.sql("INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT DO NOTHING")
                .param(eventId).update() == 1;
    }

    public record Entry(Instant at, String label, String detail) {
    }
}
