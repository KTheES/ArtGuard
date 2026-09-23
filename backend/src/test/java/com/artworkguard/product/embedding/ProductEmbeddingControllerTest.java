package com.artworkguard.product.embedding;
import com.artworkguard.marketplace.service.*;
import com.artworkguard.product.repository.CatalogRepository;
import com.artworkguard.product.domain.ProductImage;
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
@WebMvcTest(ProductEmbeddingController.class) @Import({SecurityConfig.class,JwtConfig.class})
class ProductEmbeddingControllerTest {
 @Autowired MockMvc mvc;@MockitoBean CatalogAccess access;@MockitoBean CatalogRepository catalog;@MockitoBean ProductEmbeddingJobService jobs;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(bytes));}
 final UUID actor=UUID.randomUUID(),product=UUID.randomUUID(),image=UUID.randomUUID();
 String path(){return "/api/v1/products/"+product+"/images/"+image+"/embedding";}
 @Test void anonymousCannotRead()throws Exception{mvc.perform(get(path())).andExpect(status().isUnauthorized());verifyNoInteractions(jobs);}
 @Test void userCannotQueueSharedImages()throws Exception{mvc.perform(post(path()).with(jwt())).andExpect(status().isForbidden());verifyNoInteractions(jobs);}
 @Test void wrongProductDoesNotRevealStatus()throws Exception{
  when(catalog.image(product,image)).thenReturn(Optional.empty());
  mvc.perform(get(path()).with(jwt().jwt(b->b.subject(actor.toString())))).andExpect(status().isNotFound());verifyNoInteractions(jobs);
 }
 @Test void adminEnqueueRechecksDatabaseAuthority()throws Exception{
  when(catalog.image(product,image)).thenReturn(Optional.of(mock(ProductImage.class)));
  mvc.perform(post(path()).with(jwt().jwt(b->b.subject(actor.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());
  verify(access).admin(actor);verify(jobs).enqueue(image);
 }
}
