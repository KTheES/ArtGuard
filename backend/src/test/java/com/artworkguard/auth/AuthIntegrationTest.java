package com.artworkguard.auth;

import com.artworkguard.auth.service.RefreshTokens;
import com.artworkguard.auth.service.RefreshTokenStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.artworkguard.subscription.SubscriptionService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        registry.add("artworkguard.auth.jwt-secret", () -> Base64.getEncoder().encodeToString(bytes));
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired PasswordEncoder passwords;
    @Autowired RefreshTokenStore store;
    @Autowired SubscriptionService subscriptions;
    private static final String PASSWORD = "IntegrationPassword123!";

    @Test void signupLoginMeRotateReplayLogoutAndRelogin() {
        String email = UUID.randomUUID() + "@example.com";
        var signup = post("signup", Map.of("email", email.toUpperCase(Locale.ROOT), "password", PASSWORD, "nickname", "Creator", "role", "ROLE_ADMIN"));
        assertThat(signup.getStatusCode().value()).isEqualTo(201);
        JsonNode user = signup.getBody().path("data");
        UUID id = UUID.fromString(user.path("id").asText());
        assertThat(user.path("role").asText()).isEqualTo("ROLE_USER");
        assertThat(user.has("passwordHash")).isFalse();
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id);
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT plan_code FROM user_subscription WHERE user_id=?", String.class, id)).isEqualTo("FREE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscription_plan", Integer.class)).isEqualTo(4);
        assertThat(subscriptions.current(id).status()).isEqualTo("ACTIVE");
        jdbc.update("UPDATE user_subscription SET plan_code='PRO',status='PAST_DUE' WHERE user_id=?", id);
        var overdue = subscriptions.current(id);
        assertThat(overdue.subscribedPlanCode()).isEqualTo("PRO");
        assertThat(overdue.plan().code()).isEqualTo("FREE");
        assertThat(overdue.entitlementsActive()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_audit WHERE subject_user_id=? AND resource_type='SUBSCRIPTION' AND new_state->>'status'='PAST_DUE'", Integer.class,id)).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("DELETE FROM operational_audit WHERE subject_user_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("UPDATE user_subscription SET plan_code='FREE',status='ACTIVE' WHERE user_id=?", id);
        assertThat(post("signup", Map.of("email", email, "password", PASSWORD, "nickname", "Other")).getStatusCode().value()).isEqualTo(409);
        assertThat(post("login", Map.of("email", email, "password", "wrong-password")).getStatusCode().value()).isEqualTo(401);
        JsonNode login = post("login", Map.of("email", email, "password", PASSWORD)).getBody().path("data");
        String oldRefresh = login.path("refreshToken").asText();
        String access = login.path("accessToken").asText();
        assertThat(redisTemplate.opsForValue().get("refresh-token:" + id)).isEqualTo(RefreshTokens.hash(oldRefresh));
        assertThat(redisTemplate.getExpire("refresh-token:" + id)).isBetween(1L, Duration.ofDays(14).toSeconds());
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(access);
        var me = http.exchange("/api/v1/auth/me?userId=" + UUID.randomUUID(), HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().path("data").path("id").asText()).isEqualTo(id.toString());
        assertThat(http.getForEntity("/api/v1/auth/me", JsonNode.class).getStatusCode().value()).isEqualTo(401);
        JsonNode refreshed = post("refresh", Map.of("refreshToken", oldRefresh)).getBody().path("data");
        String current = refreshed.path("refreshToken").asText();
        assertThat(current).isNotBlank().isNotEqualTo(oldRefresh);
        assertThat(post("refresh", Map.of("refreshToken", oldRefresh)).getStatusCode().value()).isEqualTo(401);
        // Old tokens must never delete the replacement session.
        assertThat(post("logout", Map.of("refreshToken", oldRefresh)).getStatusCode().value()).isEqualTo(200);
        assertThat(redisTemplate.opsForValue().get("refresh-token:" + id)).isEqualTo(RefreshTokens.hash(current));
        assertThat(post("logout", Map.of("refreshToken", current)).getStatusCode().value()).isEqualTo(200);
        assertThat(redisTemplate.hasKey("refresh-token:" + id)).isFalse();
        assertThat(post("refresh", Map.of("refreshToken", current)).getStatusCode().value()).isEqualTo(401);
        JsonNode first = post("login", Map.of("email", email, "password", PASSWORD)).getBody().path("data");
        assertThat(post("login", Map.of("email", email, "password", PASSWORD)).getStatusCode().value()).isEqualTo(200);
        assertThat(post("refresh", Map.of("refreshToken", first.path("refreshToken").asText())).getStatusCode().value()).isEqualTo(401);
        jdbc.update("UPDATE app_user SET status='SUSPENDED' WHERE id=?", id);
        assertThat(post("login", Map.of("email", email, "password", PASSWORD)).getStatusCode().value()).isEqualTo(401);
        assertThat(http.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test void concurrentRefreshAllowsExactlyOneWinner() throws Exception {
        UUID id = UUID.randomUUID();
        store.save(id, "original-hash", Duration.ofMinutes(1));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> first = () -> { start.await(); return store.rotate(id, "original-hash", "first-hash", Duration.ofMinutes(1)); };
            Callable<Boolean> second = () -> { start.await(); return store.rotate(id, "original-hash", "second-hash", Duration.ofMinutes(1)); };
            var a = executor.submit(first); var b = executor.submit(second); start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test void expiredRefreshIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        store.save(id, "expiring-hash", Duration.ofMillis(20));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (Boolean.TRUE.equals(redisTemplate.hasKey("refresh-token:" + id)) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(store.rotate(id, "expiring-hash", "replacement", Duration.ofDays(14))).isFalse();
    }

    private ResponseEntity<JsonNode> post(String action, Map<String, String> body) {
        return http.postForEntity("/api/v1/auth/" + action, body, JsonNode.class);
    }
}
