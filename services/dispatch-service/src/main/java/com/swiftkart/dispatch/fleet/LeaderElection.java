package com.swiftkart.dispatch.fleet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Exactly one dispatch replica runs the assignment and movement loop; the others serve reads and
 * stand by. Leadership is a Redis key with a short TTL that the leader renews; renewal is a
 * compare-and-extend script, so a replica that paused (GC, network) and lost the key can never
 * extend someone else's lease.
 */
@Component
public class LeaderElection {

    private static final Logger log = LoggerFactory.getLogger(LeaderElection.class);
    static final String KEY = "dispatch:leader";
    private static final Duration LEASE = Duration.ofSeconds(5);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0""", Long.class);

    private final StringRedisTemplate redis;
    private final String me = UUID.randomUUID().toString();
    private volatile boolean leader;

    public LeaderElection(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Scheduled(fixedDelay = 1000)
    public void heartbeat() {
        try {
            boolean was = leader;
            if (leader) {
                Long ok = redis.execute(RENEW, List.of(KEY), me, String.valueOf(LEASE.toMillis()));
                leader = ok != null && ok == 1;
            } else {
                leader = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY, me, LEASE));
            }
            if (leader != was) log.info(leader ? "Became dispatch leader" : "Lost dispatch leadership");
        } catch (RuntimeException e) {
            leader = false;
        }
    }

    public boolean isLeader() {
        return leader;
    }

    public String instanceId() {
        return me;
    }
}
