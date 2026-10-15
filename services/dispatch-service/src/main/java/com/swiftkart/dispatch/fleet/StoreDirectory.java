package com.swiftkart.dispatch.fleet;

import com.swiftkart.contracts.Store;
import com.swiftkart.dispatch.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Dark-store locations, read from inventory-service and cached (they almost never change). */
@Component
public class StoreDirectory {

    private static final Logger log = LoggerFactory.getLogger(StoreDirectory.class);

    private final RestClient rest;
    private final Map<String, Store> stores = new ConcurrentHashMap<>();

    public StoreDirectory(RestClient.Builder builder, AppProperties props) {
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        rf.setReadTimeout(Duration.ofSeconds(5));
        this.rest = builder.baseUrl(props.inventoryUrl()).requestFactory(rf).build();
    }

    public Map<String, Store> all() {
        if (stores.isEmpty()) {
            try {
                List<Store> list = rest.get().uri("/api/stores").retrieve().body(new ParameterizedTypeReference<>() {
                });
                if (list != null) list.forEach(s -> stores.put(s.id(), s));
            } catch (RuntimeException e) {
                log.info("Store directory not reachable yet: {}", e.getMessage());
            }
        }
        return stores;
    }

    public Store get(String id) {
        return all().get(id);
    }
}
