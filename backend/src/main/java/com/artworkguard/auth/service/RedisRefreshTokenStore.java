package com.artworkguard.auth.service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            return 1
        end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        end
        return 0
        """, Long.class);
    private final StringRedisTemplate redis;
    public RedisRefreshTokenStore(StringRedisTemplate redis) { this.redis = redis; }
    @Override public void save(UUID userId, String hash, Duration ttl) { redis.opsForValue().set(key(userId), hash, ttl); }
    @Override public boolean rotate(UUID userId, String oldHash, String newHash, Duration ttl) {
        return Long.valueOf(1).equals(redis.execute(ROTATE, List.of(key(userId)), oldHash, newHash, Long.toString(ttl.toSeconds())));
    }
    @Override public boolean revoke(UUID userId, String hash) {
        return Long.valueOf(1).equals(redis.execute(REVOKE, List.of(key(userId)), hash));
    }
    private String key(UUID userId) { return "refresh-token:" + userId; }
}
