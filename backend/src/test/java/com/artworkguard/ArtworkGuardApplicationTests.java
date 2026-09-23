package com.artworkguard;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.apache.kafka.clients.admin.AdminClient;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArtworkGuardApplicationTests {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @Container static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        byte[] secret = new byte[32]; new java.security.SecureRandom().nextBytes(secret);
        registry.add("artworkguard.auth.jwt-secret", () -> java.util.Base64.getEncoder().encodeToString(secret));
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redisTemplate;
    @Test void infrastructureAndPublicEndpointsWork() throws Exception {
        assertThat(http.getForEntity("/actuator/health", String.class).getBody()).contains("\"status\":\"UP\"");
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity("/api/v1/artworks", String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname='vector'", Integer.class)).isEqualTo(1);
        redisTemplate.opsForValue().set("bootstrap:test", "ok", java.time.Duration.ofSeconds(10));
        assertThat(redisTemplate.opsForValue().get("bootstrap:test")).isEqualTo("ok");
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            assertThat(admin.describeCluster().nodes().get(20, TimeUnit.SECONDS)).isNotEmpty();
        }
    }
}
