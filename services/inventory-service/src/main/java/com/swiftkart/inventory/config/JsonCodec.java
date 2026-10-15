package com.swiftkart.inventory.config;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Events travel over Kafka as plain JSON strings. Serialising explicitly (rather than through
 * type headers) keeps producers and consumers decoupled: any service can read a topic with only
 * the contract record on its classpath.
 */
@Component
public class JsonCodec {

    private final JsonMapper mapper;

    public JsonCodec(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }
}
