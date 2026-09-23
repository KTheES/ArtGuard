package com.artworkguard.audit;
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
@WebMvcTest(AuditController.class) @Import({SecurityConfig.class,JwtConfig.class})
class AuditControllerTest {
 @Autowired MockMvc mvc;@MockitoBean AuditService service;@MockitoBean RedisWorkGuard guard;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
 final UUID admin=UUID.randomUUID();final String url="/api/v1/admin/audit-events";
 @Test void anonymousAndNonAdminRejected()throws Exception{mvc.perform(get(url)).andExpect(status().isUnauthorized());mvc.perform(get(url).with(jwt())).andExpect(status().isForbidden());verifyNoInteractions(service,guard);}
 @Test void cursorAndTypeAreValidated()throws Exception{for(String query:List.of("beforeId=0","limit=101","limit=0","resourceType=UNKNOWN"))mvc.perform(get(url+"?"+query).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void jwtActorAndFiltersPassToService()throws Exception{mvc.perform(get(url+"?beforeId=100&resourceType=LOGIN&limit=10").with(jwt().jwt(b->b.subject(admin.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());verify(guard).rate("audit-read",admin,30);verify(service).list(admin,100L,null,AuditRepository.Type.LOGIN,10);}
}
