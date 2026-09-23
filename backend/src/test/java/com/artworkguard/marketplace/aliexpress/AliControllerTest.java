package com.artworkguard.marketplace.aliexpress;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AliController.class) @Import({SecurityConfig.class,JwtConfig.class})
class AliControllerTest {
 @Autowired MockMvc mvc;@MockitoBean AliCollectionService service;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(b));}
 final String path="/api/v1/admin/marketplaces/ALIEXPRESS/collect";
 @Test void userCannotStartExternalCollection()throws Exception{
  mvc.perform(post(path).with(jwt()).contentType("application/json").content("{\"query\":\"art\",\"page\":1,\"limit\":1}")).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
 @Test void adminRequestUsesAuthenticatedActor()throws Exception{
  UUID actor=UUID.randomUUID();
  mvc.perform(post(path).with(jwt().jwt(b->b.subject(actor.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{\"query\":\"art\",\"page\":1,\"limit\":1}")).andExpect(status().isOk());
  verify(service).collect(actor,new AliDtos.SearchRequest("art",1,1));
 }
 @Test void rejectsMissingQueryOrExcessiveBatch()throws Exception{
  for(String body:List.of("{}","{\"query\":\"\",\"page\":1,\"limit\":1}","{\"query\":\"art\",\"page\":1,\"limit\":6}")){
   mvc.perform(post(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  }verifyNoInteractions(service);
 }
 @Test void missingCredentialsProduceSanitized503()throws Exception{
  when(service.collect(any(),any())).thenThrow(new AliException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"ALIEXPRESS_NOT_CONFIGURED"));
  mvc.perform(post(path).with(jwt().jwt(b->b.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{\"query\":\"art\",\"page\":1,\"limit\":1}")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("ALIEXPRESS_NOT_CONFIGURED"));
 }
}
