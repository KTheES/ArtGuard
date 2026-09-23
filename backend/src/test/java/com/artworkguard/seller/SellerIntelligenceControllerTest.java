package com.artworkguard.seller;
import com.artworkguard.common.config.SecurityConfig;
import com.artworkguard.auth.jwt.JwtConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(SellerIntelligenceController.class) @Import({SecurityConfig.class,JwtConfig.class})
class SellerIntelligenceControllerTest {
 @Autowired MockMvc mvc;@MockitoBean SellerIntelligenceService service;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
 final UUID owner=UUID.randomUUID();
 final String own="/api/v1/sellers/intelligence",admin="/api/v1/admin/sellers/intelligence";
 @Test void anonymousRejected()throws Exception{mvc.perform(get(own)).andExpect(status().isUnauthorized());mvc.perform(get(admin)).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void ordinaryUserCannotSeeGlobalCounts()throws Exception{mvc.perform(get(admin).with(jwt())).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void ownScopeUsesJwtNotQueryOwner()throws Exception{
  mvc.perform(get(own+"?ownerId="+UUID.randomUUID()).with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk());verify(service).own(owner,null,0,20);
 }
 @Test void adminRouteUsesAdminService()throws Exception{
  mvc.perform(get(admin).with(jwt().jwt(b->b.subject(owner.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());verify(service).admin(owner,null,0,20);
 }
 @Test void invalidPaginationAndMarketplaceRejected()throws Exception{
  for(String query:List.of("page=-1","page=100001","size=0","size=101","marketplace=INVALID"))
   mvc.perform(get(own+"?"+query).with(jwt())).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
}
