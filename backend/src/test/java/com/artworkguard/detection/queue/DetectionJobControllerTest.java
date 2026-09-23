package com.artworkguard.detection.queue;
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
@WebMvcTest(DetectionJobController.class) @Import({SecurityConfig.class,JwtConfig.class})
class DetectionJobControllerTest {
 @Autowired MockMvc mvc;@MockitoBean DetectionJobService service;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(b));}
 final UUID owner=UUID.randomUUID(),artwork=UUID.randomUUID(),job=UUID.randomUUID();
 String path(){return "/api/v1/artworks/"+artwork+"/detection-jobs/"+job;}
 @Test void requiresLogin()throws Exception{mvc.perform(get(path())).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void statusUsesAuthenticatedOwner()throws Exception{
  mvc.perform(get(path()).with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk());
  verify(service).status(owner,artwork,job);
 }
 @Test void retryUsesAuthenticatedOwner()throws Exception{
  mvc.perform(post(path()+"/retry").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk());
  verify(service).retry(owner,artwork,job);
 }
}
