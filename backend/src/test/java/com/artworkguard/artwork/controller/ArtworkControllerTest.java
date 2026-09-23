package com.artworkguard.artwork.controller;
import com.artworkguard.artwork.service.ArtworkService;
import com.artworkguard.common.config.SecurityConfig;
import com.artworkguard.auth.jwt.JwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ArtworkController.class) @Import({SecurityConfig.class,JwtConfig.class})
class ArtworkControllerTest {
 @Autowired MockMvc mvc; @MockitoBean ArtworkService service;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
  byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
  r.add("artworkguard.auth.jwt-secret",()->java.util.Base64.getEncoder().encodeToString(bytes));
 }
 @Test void anonymousCannotListOrUpload()throws Exception {
  mvc.perform(get("/api/v1/artworks")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/artworks/upload-url").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  verifyNoInteractions(service);
 }
 @Test void listUsesAuthenticatedOwner()throws Exception {
  var owner=UUID.randomUUID();
  mvc.perform(get("/api/v1/artworks").param("userId",UUID.randomUUID().toString()).with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk());
  verify(service).list(owner,0,20);
 }
 @Test void rejectsInvalidPagination()throws Exception {
  mvc.perform(get("/api/v1/artworks?size=101").with(jwt())).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/artworks?page=-1").with(jwt())).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
 @Test void rejectsUnsupportedOrOversizedUpload()throws Exception {
  for(String body:java.util.List.of("{\"contentType\":\"image/svg+xml\",\"sizeBytes\":10}","{\"contentType\":\"image/png\",\"sizeBytes\":20971521}")) {
   mvc.perform(post("/api/v1/artworks/upload-url").with(jwt()).contentType("application/json").content(body))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }
  verifyNoInteractions(service);
 }
 @Test void rejectsMalformedUuid()throws Exception {
  mvc.perform(get("/api/v1/artworks/not-a-uuid").with(jwt())).andExpect(status().isBadRequest());
 }
 @Test void rejectsBlankPatchTitleAndMissingVersion()throws Exception {
  for(String body:java.util.List.of("{\"title\":\"   \",\"version\":0}","{\"title\":\"New\"}")) {
   mvc.perform(patch("/api/v1/artworks/"+UUID.randomUUID()).with(jwt()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  }
  verifyNoInteractions(service);
 }
}
