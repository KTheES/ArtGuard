package com.artworkguard.auth.verification;
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
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(EmailVerificationController.class) @Import({SecurityConfig.class,JwtConfig.class})
class EmailVerificationControllerTest {
 @Autowired MockMvc mvc;@MockitoBean EmailVerificationService service;@MockitoBean RedisWorkGuard guard;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
 final UUID owner=UUID.randomUUID();final String url="/api/v1/auth/email-verification";
 @Test void anonymousCannotSendOrConfirm()throws Exception{
  mvc.perform(post(url+"/request")).andExpect(status().isUnauthorized());mvc.perform(post(url+"/confirm")).andExpect(status().isUnauthorized());verifyNoInteractions(service,guard);
 }
 @Test void requestIsRateLimitedAndUsesJwtOwner()throws Exception{
  when(service.request(owner)).thenReturn(new EmailVerificationService.Status(false));
  mvc.perform(post(url+"/request").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(false)).andExpect(jsonPath("$.data.token").doesNotExist());
  var order=inOrder(guard,service);order.verify(guard).rate("email-verification-request",owner,1);order.verify(service).request(owner);
 }
 @Test void confirmIsRateLimitedAndUsesBodyToken()throws Exception{
  String token="a".repeat(43);
  mvc.perform(post(url+"/confirm").with(jwt().jwt(b->b.subject(owner.toString()))).contentType("application/json").content("{\"token\":\""+token+"\"}")).andExpect(status().isOk());
  verify(guard).rate("email-verification-confirm",owner,5);verify(service).confirm(owner,token);
 }
 @Test void malformedTokenRejected()throws Exception{
  mvc.perform(post(url+"/confirm").with(jwt()).contentType("application/json").content("{\"token\":\"bad\"}")).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
}
