package com.artworkguard.auth.service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
public final class RefreshTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[A-Za-z0-9_-]{43}");
    private RefreshTokens() {}
    public static String generate(UUID userId) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return userId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static UUID userId(String token) {
        if (token == null || !FORMAT.matcher(token).matches()) throw AuthException.unauthorized();
        return UUID.fromString(token.substring(0, 36));
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
