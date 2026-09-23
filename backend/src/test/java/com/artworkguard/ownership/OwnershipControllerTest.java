package com.artworkguard.ownership;
import com.artworkguard.common.config.SecurityConfig;
import com.artworkguard.auth.jwt.JwtConfig;
import com.artworkguard.redis.RedisWorkGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(OwnershipController.class) @Import({SecurityConfig.class,JwtConfig.class})
class OwnershipControllerTest {
 @Autowired MockMvc mvc;@MockitoBean OwnershipService service;@MockitoBean RedisWorkGuard guard;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
 final UUID owner=UUID.randomUUID(),art=UUID.randomUUID();final String admin="/api/v1/admin/ownership-claims";
 String own(){return "/api/v1/artworks/"+art+"/ownership-claims";}
 @Test void anonymousAndNonAdminAreBlocked()throws Exception{mvc.perform(get(own())).andExpect(status().isUnauthorized());mvc.perform(get(admin).with(jwt())).andExpect(status().isForbidden());mvc.perform(patch(admin+"/"+art).with(jwt())).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void validSubmitIsLimitedAndScoped()throws Exception{
  mvc.perform(post(own()).with(jwt().jwt(b->b.subject(owner.toString()))).contentType("application/json").content("{\"publicationUrl\":\"https://example.com/art\",\"statement\":\"source\",\"authorized\":true}")).andExpect(status().isOk());verify(guard).rate("ownership-submit",owner,3);verify(service).submit(eq(owner),eq(art),any());
 }
 @Test void missingDeclarationRejected()throws Exception{mvc.perform(post(own()).with(jwt()).contentType("application/json").content("{\"publicationUrl\":\"https://example.com/art\",\"statement\":\"source\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void invalidReviewRejected()throws Exception{mvc.perform(patch(admin+"/"+art).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{\"status\":1,\"reason\":\"\",\"version\":-1}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void adminQueueUsesCurrentActor()throws Exception{mvc.perform(get(admin).with(jwt().jwt(b->b.subject(owner.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());verify(service).queue(owner,OwnershipDtos.Status.PENDING,0,20);}
}
