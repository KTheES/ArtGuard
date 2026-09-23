package com.artworkguard.evidence;
import com.artworkguard.auth.jwt.JwtConfig;
import com.artworkguard.common.config.SecurityConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EvidenceController.class) @Import({SecurityConfig.class,JwtConfig.class})
class EvidenceControllerTest {
 @Autowired MockMvc mvc;@MockitoBean EvidenceService service;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(b));}
 final UUID owner=UUID.randomUUID(),detection=UUID.randomUUID(),evidence=UUID.randomUUID();
 @Test void anonymousCannotReadEvidence()throws Exception{mvc.perform(get("/api/v1/detections/"+detection+"/evidence")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void listUsesJwtOwner()throws Exception{when(service.list(owner,detection)).thenReturn(List.of());mvc.perform(get("/api/v1/detections/"+detection+"/evidence").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk());verify(service).list(owner,detection);}
 @Test void screenshotHasLockedDownSvgHeaders()throws Exception{when(service.screenshot(owner,detection,evidence)).thenReturn("<svg/>".getBytes());mvc.perform(get("/api/v1/detections/"+detection+"/evidence/"+evidence+"/screenshot").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml")).andExpect(header().string("Content-Security-Policy","default-src 'none'; sandbox")).andExpect(header().string("X-Content-Type-Options","nosniff"));}
 @Test void imageIsReturnedAsNoStorePng()throws Exception{when(service.image(owner,detection,evidence)).thenReturn(new byte[]{1});mvc.perform(get("/api/v1/detections/"+detection+"/evidence/"+evidence+"/image").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));}
}
