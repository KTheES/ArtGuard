package com.artworkguard.detection;
import com.artworkguard.common.config.SecurityConfig;
import com.artworkguard.auth.jwt.JwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(DetectionController.class) @Import({SecurityConfig.class,JwtConfig.class})
class DetectionControllerTest {
 @Autowired MockMvc mvc;@MockitoBean com.artworkguard.detection.queue.DetectionJobService service;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(b));}
 final UUID artwork=UUID.randomUUID(),owner=UUID.randomUUID();
 String path(){return "/api/v1/artworks/"+artwork+"/detections";}
 @Test void requiresLogin()throws Exception{mvc.perform(post(path())).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void usesJwtOwnerAndDefaultLimit()throws Exception{
  mvc.perform(post(path()).param("userId",UUID.randomUUID().toString()).with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isAccepted());
  verify(service).request(owner,artwork,100);
 }
 @Test void rejectsUnboundedRuns()throws Exception{
  mvc.perform(post(path()+"?limit=501").with(jwt())).andExpect(status().isBadRequest());
  mvc.perform(post(path()+"?limit=0").with(jwt())).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
 @Test void missingEmbeddingReturnsConflict()throws Exception{
  when(service.request(owner,artwork,100)).thenThrow(new DetectionException());
  mvc.perform(post(path()).with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ARTWORK_EMBEDDING_NOT_READY"));
 }
}
