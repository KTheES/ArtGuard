package com.artworkguard.subscription;

import com.artworkguard.auth.jwt.JwtConfig;
import com.artworkguard.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class) @Import({SecurityConfig.class,JwtConfig.class})
class SubscriptionControllerTest {
 @Autowired MockMvc mvc;@MockitoBean SubscriptionService service;
 @DynamicPropertySource static void secret(DynamicPropertyRegistry r){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(b));}
 @Test void planCatalogIsPublic()throws Exception{when(service.plans()).thenReturn(new SubscriptionDtos.Catalog(List.of()));mvc.perform(get("/api/v1/subscription/plans")).andExpect(status().isOk());}
 @Test void currentSubscriptionRequiresAuthentication()throws Exception{mvc.perform(get("/api/v1/subscription")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void currentSubscriptionUsesJwtSubject()throws Exception{UUID owner=UUID.randomUUID();var plan=new SubscriptionDtos.Plan("FREE","Free",3,168,25,0,false,false,false,false);when(service.current(owner)).thenReturn(new SubscriptionDtos.Current(plan,"FREE","ACTIVE",true,null,false,1,2));mvc.perform(get("/api/v1/subscription").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.data.plan.code").value("FREE")).andExpect(jsonPath("$.data.status").value("ACTIVE")).andExpect(jsonPath("$.data.remainingArtworks").value(2));}
}
