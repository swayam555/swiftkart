package com.swiftkart.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param timeScale      demo clock speed-up shared by every service
 * @param ridersPerStore fleet size seeded at each dark store
 * @param inventoryUrl   where to read dark-store locations from
 */
@ConfigurationProperties(prefix = "swiftkart")
public record AppProperties(@DefaultValue("6") double timeScale,
                            @DefaultValue("8") int ridersPerStore,
                            @DefaultValue("http://localhost:8092") String inventoryUrl) {
}
