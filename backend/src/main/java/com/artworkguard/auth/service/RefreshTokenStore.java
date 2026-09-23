package com.artworkguard.auth.service;
import java.time.Duration;
import java.util.UUID;
public interface RefreshTokenStore {
    void save(UUID userId, String hash, Duration ttl);
    boolean rotate(UUID userId, String oldHash, String newHash, Duration ttl);
    boolean revoke(UUID userId, String hash);
}
