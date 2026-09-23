package com.artworkguard.takedown;
import com.artworkguard.common.config.SecurityConfig;
import com.artworkguard.auth.jwt.JwtConfig;
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
@WebMvcTest(TakedownController.class) @Import({SecurityConfig.class,JwtConfig.class})
class TakedownControllerTest {
 @Autowired MockMvc mvc;@MockitoBean TakedownService service;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
 final UUID id=UUID.randomUUID(),owner=UUID.randomUUID();
 String url(){return "/api/v1/detections/"+id+"/takedown";}
 @Test void anonymousRejected()throws Exception{mvc.perform(get(url())).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
 @Test void missingConfirmationRejected()throws Exception{
  mvc.perform(post(url()).with(jwt()).contentType("application/json").content("{\"evidenceId\":\""+UUID.randomUUID()+"\",\"detectionVersion\":0}")).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
 @Test void invalidStatusAndVersionRejected()throws Exception{
  for(String body:List.of("{}","{\"status\":1,\"version\":0}","{\"status\":\"SUBMITTED\",\"version\":-1}"))
   mvc.perform(patch(url()).with(jwt()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
 @Test void ownerComesFromJwt()throws Exception{
  mvc.perform(get(url()).with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk());verify(service).get(owner,id);
 }
 @Test void authenticatedCreateAndUpdateReachService()throws Exception{
  UUID evidence=UUID.randomUUID();
  mvc.perform(post(url()).with(jwt().jwt(b->b.subject(owner.toString()))).contentType("application/json")
   .content("{\"evidenceId\":\""+evidence+"\",\"detectionVersion\":2,\"rightsConfirmed\":true}")).andExpect(status().isOk());
  verify(service).create(owner,id,new TakedownDtos.Create(evidence,2L,true));
  mvc.perform(patch(url()).with(jwt().jwt(b->b.subject(owner.toString()))).contentType("application/json")
   .content("{\"status\":\"SUBMITTED\",\"version\":0,\"externalReference\":\"receipt\"}")).andExpect(status().isOk());
  verify(service).update(owner,id,new TakedownDtos.Update(TakedownDtos.Status.SUBMITTED,0L,"receipt"));
 }
 @Test void conflictIsReviewable()throws Exception{
  when(service.get(owner,id)).thenThrow(new TakedownException("conflict"));
  mvc.perform(get(url()).with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TAKEDOWN_CONFLICT"));
 }
}
