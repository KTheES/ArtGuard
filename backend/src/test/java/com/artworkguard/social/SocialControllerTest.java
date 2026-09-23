package com.artworkguard.social;
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
@WebMvcTest(SocialController.class) @Import({SecurityConfig.class,JwtConfig.class})
class SocialControllerTest {
 @Autowired MockMvc mvc;@MockitoBean SocialService service;@MockitoBean RedisWorkGuard guard;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
 final UUID owner=UUID.randomUUID();final String own="/api/v1/social-checks",admin="/api/v1/admin/social-checks";
 @Test void anonymousAndNonAdminBlocked()throws Exception{mvc.perform(post(own)).andExpect(status().isUnauthorized());mvc.perform(get(admin).with(jwt())).andExpect(status().isForbidden());mvc.perform(patch(admin+"/"+owner).with(jwt())).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void startIsScopedAndRateLimited()throws Exception{mvc.perform(post(own).with(jwt().jwt(b->b.subject(owner.toString()))).contentType("application/json").content("{\"profileUrl\":\"https://example.com/profile\"}")).andExpect(status().isOk());var order=inOrder(guard,service);order.verify(guard).rate("social-start",owner,1);order.verify(service).start(eq(owner),any());}
 @Test void invalidPageRejected()throws Exception{mvc.perform(get(own+"?size=101").with(jwt())).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void explicitProfileReviewRequired()throws Exception{mvc.perform(patch(admin+"/"+owner).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{\"version\":0,\"approved\":true,\"reason\":\"checked\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void validReviewReachesAdminService()throws Exception{mvc.perform(patch(admin+"/"+owner).with(jwt().jwt(b->b.subject(owner.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{\"version\":0,\"approved\":false,\"profileChecked\":true,\"reason\":\"code absent\"}")).andExpect(status().isOk());verify(service).review(eq(owner),eq(owner),any());}
 @Test void revokeRequiresVersion()throws Exception{mvc.perform(patch(own+"/"+owner+"/revoke").with(jwt()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(service);}
}
