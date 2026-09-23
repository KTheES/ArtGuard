package com.artworkguard.marketplace;
import com.artworkguard.product.controller.CatalogController;
import com.artworkguard.product.service.CatalogService;
import com.artworkguard.marketplace.controller.MockCollectionController;
import com.artworkguard.marketplace.service.MockCollectionService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({CatalogController.class,MockCollectionController.class}) @Import({SecurityConfig.class,JwtConfig.class})
class CatalogControllerTest {
 @Autowired MockMvc mvc; @MockitoBean CatalogService catalog; @MockitoBean MockCollectionService collection;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
  byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);
  r.add("artworkguard.auth.jwt-secret",()->java.util.Base64.getEncoder().encodeToString(b));
 }
 @Test void anonymousCannotRead()throws Exception{
  mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());verifyNoInteractions(catalog);
 }
 @Test void ordinaryUserCannotCollect()throws Exception{
  mvc.perform(post("/api/v1/admin/marketplaces/MOCK/collect").with(jwt()).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(collection);
 }
 @Test void adminCanCollect()throws Exception{
  UUID id=UUID.randomUUID();
  mvc.perform(post("/api/v1/admin/marketplaces/MOCK/collect").with(jwt().jwt(b->b.subject(id.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{}")).andExpect(status().isOk());
  verify(collection).collect(eq(id),any());
 }
 @Test void rejectsInvalidPagination()throws Exception{
  mvc.perform(get("/api/v1/products?size=101").with(jwt())).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/products?page=-1").with(jwt())).andExpect(status().isBadRequest());
  verifyNoInteractions(catalog);
 }
 @Test void rejectsInvalidCollectLimit()throws Exception{
  mvc.perform(post("/api/v1/admin/marketplaces/MOCK/collect").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{\"limit\":101}")).andExpect(status().isBadRequest());verifyNoInteractions(collection);
 }
 @Test void previewUsesProductAndImageIdsAndPreventsCaching()throws Exception{
  var actor=UUID.randomUUID();var product=UUID.randomUUID();var image=UUID.randomUUID();
  when(catalog.preview(actor,product,image)).thenReturn(new byte[]{1,2});
  mvc.perform(get("/api/v1/products/"+product+"/images/"+image+"/preview").with(jwt().jwt(b->b.subject(actor.toString())))).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store"));
  verify(catalog).preview(actor,product,image);
 }
 @Test void rateAndRedisFailuresExposeRetryAfter()throws Exception{
  for(var code:java.util.List.of(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)){
   doThrow(new com.artworkguard.redis.RedisGuardException(code,"TEST_GUARD",5)).when(collection).collect(any(),any());
   mvc.perform(post("/api/v1/admin/marketplaces/MOCK/collect").with(jwt().jwt(b->b.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{}"))
    .andExpect(status().is(code.value())).andExpect(header().string("Retry-After","5"));
  }
 }}
