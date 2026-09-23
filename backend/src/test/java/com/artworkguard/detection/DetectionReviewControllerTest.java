package com.artworkguard.detection;
import com.artworkguard.detection.DetectionReviewDtos.*;
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
@WebMvcTest(DetectionReviewController.class) @Import({SecurityConfig.class,JwtConfig.class})
class DetectionReviewControllerTest {
 @Autowired MockMvc mvc;@MockitoBean DetectionReviewService service;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){byte[] b=new byte[32];new java.security.SecureRandom().nextBytes(b);r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(b));}
 final UUID owner=UUID.randomUUID(),id=UUID.randomUUID();
 @Test void anonymousCannotReadOrChange()throws Exception{
  mvc.perform(get("/api/v1/detections")).andExpect(status().isUnauthorized());
  mvc.perform(patch("/api/v1/detections/"+id+"/status").contentType("application/json").content("{\"status\":\"CONFIRMED\",\"version\":0}")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
 }
 @Test void listUsesJwtOwnerAndTypedFilters()throws Exception{
  mvc.perform(get("/api/v1/detections?status=NEW&severity=HIGH").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk());
  verify(service).list(owner,null,ReviewStatus.NEW,DetectionPolicy.Severity.HIGH,0,20);
 }
 @Test void rejectsInvalidPaginationAndEnums()throws Exception{
  for(String query:List.of("size=101","page=-1","status=ANY","severity=LOW","artworkId=invalid")){
   mvc.perform(get("/api/v1/detections?"+query).with(jwt())).andExpect(status().isBadRequest());
  }verifyNoInteractions(service);
 }
 @Test void patchRequiresExplicitVersionAndKnownStatus()throws Exception{
  for(String body:List.of("{\"status\":1,\"version\":0}","{}","{\"status\":\"CONFIRMED\"}","{\"status\":\"CONFIRMED\",\"version\":-1}","{\"status\":\"OTHER\",\"version\":0}")){
   mvc.perform(patch("/api/v1/detections/"+id+"/status").with(jwt()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  }verifyNoInteractions(service);
 }
 @Test void validPatchUsesOwnerAndVersion()throws Exception{
  mvc.perform(patch("/api/v1/detections/"+id+"/status").with(jwt().jwt(b->b.subject(owner.toString()))).contentType("application/json").content("{\"status\":\"CONFIRMED\",\"version\":7}")).andExpect(status().isOk());
  verify(service).update(owner,id,new UpdateRequest(ReviewStatus.CONFIRMED,7L));
 }
 @Test void hidesMissingOrForeignRecords()throws Exception{
  when(service.detail(owner,id)).thenThrow(DetectionReviewException.missing());
  mvc.perform(get("/api/v1/detections/"+id).with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("DETECTION_NOT_FOUND"));
 }
 @Test void stalePatchReturns409()throws Exception{
  when(service.update(eq(owner),eq(id),any())).thenThrow(DetectionReviewException.conflict());
  mvc.perform(patch("/api/v1/detections/"+id+"/status").with(jwt().jwt(b->b.subject(owner.toString()))).contentType("application/json").content("{\"status\":\"NEW\",\"version\":0}")).andExpect(status().isConflict());
 }
}
