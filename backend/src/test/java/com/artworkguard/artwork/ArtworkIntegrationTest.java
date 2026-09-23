package com.artworkguard.artwork;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
@Tag("integration") @Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArtworkIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 static final String SECRET=UUID.randomUUID().toString();
 @Container static GenericContainer<?> minio=new GenericContainer<>("minio/minio:RELEASE.2025-04-22T22-12-26Z")
  .withEnv("MINIO_ROOT_USER","integration-user").withEnv("MINIO_ROOT_PASSWORD",SECRET)
  .withCommand("server","/data").withExposedPorts(9000)
  .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/minio/health/ready").forPort(9000));
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
  r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
  r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString((SECRET+SECRET).getBytes()));
  r.add("artworkguard.storage.enabled",()->true);r.add("artworkguard.storage.region",()->"us-east-1");
  r.add("artworkguard.storage.bucket",()->"artworkguard-test");
  r.add("artworkguard.storage.endpoint",()->"http://"+minio.getHost()+":"+minio.getMappedPort(9000));
 }
 @org.springframework.boot.test.context.TestConfiguration
 static class TestCredentials {
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
  software.amazon.awssdk.services.s3.S3Client testClient(@org.springframework.beans.factory.annotation.Value("${artworkguard.storage.endpoint}") String endpoint) {
   return software.amazon.awssdk.services.s3.S3Client.builder().region(software.amazon.awssdk.regions.Region.US_EAST_1)
    .endpointOverride(URI.create(endpoint)).forcePathStyle(true)
    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("integration-user",SECRET))).build();
  }
  @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
  software.amazon.awssdk.services.s3.presigner.S3Presigner testPresigner(@org.springframework.beans.factory.annotation.Value("${artworkguard.storage.endpoint}") String endpoint) {
   return software.amazon.awssdk.services.s3.presigner.S3Presigner.builder().region(software.amazon.awssdk.regions.Region.US_EAST_1).endpointOverride(URI.create(endpoint))
    .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build())
    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("integration-user",SECRET))).build();
  }
 }
 @Autowired TestRestTemplate http;
 @Autowired S3Client s3;
 @BeforeEach void bucket(){
  try{s3.createBucket(CreateBucketRequest.builder().bucket("artworkguard-test").build());}catch(S3Exception e){if(e.statusCode()!=409)throw e;}
 }
 @Test void directUploadCrudPrivateImagesAndOwnership()throws Exception {
  String owner=login(),other=login();
  var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB),"png",out);byte[] image=out.toByteArray();
  var ticket=request(HttpMethod.POST,"/api/v1/artworks/upload-url",owner,Map.of("contentType","image/png","sizeBytes",image.length)).getBody().path("data");
  var upload=HttpRequest.newBuilder(URI.create(ticket.path("uploadUrl").asText())).PUT(HttpRequest.BodyPublishers.ofByteArray(image));
  ticket.path("requiredHeaders").fields().forEachRemaining(e->{if(!e.getKey().equalsIgnoreCase("content-length"))upload.header(e.getKey(),e.getValue().asText());});
  try(var browser=HttpClient.newHttpClient()) {
   assertThat(browser.send(upload.build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
   String uploadId=ticket.path("uploadId").asText();
   assertThat(request(HttpMethod.POST,"/api/v1/artworks",other,Map.of("uploadId",uploadId,"title","Foreign")).getStatusCode().value()).isEqualTo(404);
   var created=request(HttpMethod.POST,"/api/v1/artworks",owner,Map.of("uploadId",uploadId,"title","Artwork","description","Original"));
   assertThat(created.getStatusCode().value()).isEqualTo(201);
   String id=created.getBody().path("data").path("id").asText();
   assertThat(request(HttpMethod.POST,"/api/v1/artworks",owner,Map.of("uploadId",uploadId,"title","Retry")).getBody().path("data").path("id").asText()).isEqualTo(id);
   assertThat(request(HttpMethod.GET,"/api/v1/artworks",owner,null).getBody().path("data").path("totalElements").asInt()).isEqualTo(1);
   assertThat(request(HttpMethod.GET,"/api/v1/artworks",other,null).getBody().path("data").path("totalElements").asInt()).isZero();
   for(String path:List.of("/api/v1/artworks/"+id,"/api/v1/artworks/"+id+"/image-url"))
    assertThat(request(HttpMethod.GET,path,other,null).getStatusCode().value()).isEqualTo(404);
   assertThat(request(HttpMethod.PATCH,"/api/v1/artworks/"+id,other,Map.of("title","Changed","version",0)).getStatusCode().value()).isEqualTo(404);
   assertThat(request(HttpMethod.DELETE,"/api/v1/artworks/"+id,other,null).getStatusCode().value()).isEqualTo(404);
   String url=request(HttpMethod.GET,"/api/v1/artworks/"+id+"/image-url",owner,null).getBody().path("data").path("url").asText();
   assertThat(browser.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray()).statusCode()).isEqualTo(200);
   assertThat(browser.send(HttpRequest.newBuilder(URI.create(url.split("\\?")[0])).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
   assertThat(request(HttpMethod.PATCH,"/api/v1/artworks/"+id,owner,Map.of("title","Updated","version",0)).getStatusCode().value()).isEqualTo(200);
   assertThat(request(HttpMethod.PATCH,"/api/v1/artworks/"+id,owner,Map.of("title","Stale","version",0)).getStatusCode().value()).isEqualTo(409);
   assertThat(request(HttpMethod.DELETE,"/api/v1/artworks/"+id,owner,null).getStatusCode().value()).isEqualTo(200);
   assertThat(request(HttpMethod.GET,"/api/v1/artworks/"+id+"/image-url",owner,null).getStatusCode().value()).isEqualTo(404);
  }
 }
 private String login() {
  String email=UUID.randomUUID()+"@example.com";
  var account=Map.of("email",email,"password","IntegrationPassword123!","nickname","Creator");
  assertThat(http.postForEntity("/api/v1/auth/signup",account,JsonNode.class).getStatusCode().value()).isEqualTo(201);
  return http.postForEntity("/api/v1/auth/login",account,JsonNode.class).getBody().path("data").path("accessToken").asText();
 }
 private ResponseEntity<JsonNode> request(HttpMethod method,String path,String token,Object body) {
  var headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.APPLICATION_JSON);
  return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);
 }
}
