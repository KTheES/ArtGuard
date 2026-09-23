package com.artworkguard.ownership;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(SourceFileController.class) @Import({SecurityConfig.class,JwtConfig.class})
class SourceFileControllerTest {
 @Autowired MockMvc mvc;@MockitoBean SourceFileService service;@MockitoBean RedisWorkGuard guard;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("artworkguard.auth.jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));}
 final UUID owner=UUID.randomUUID(),id=UUID.randomUUID();String own(){return "/api/v1/ownership-claims/"+id+"/source-file";}String admin(){return "/api/v1/admin/ownership-claims/"+id+"/source-file/download";}
 @Test void anonymousAndNonAdminBlocked()throws Exception{mvc.perform(get(own())).andExpect(status().isUnauthorized());mvc.perform(get(admin()).with(jwt())).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void uploadIsScopedAndRateLimited()throws Exception{byte[] data=SourceFileValidatorTest.psd();mvc.perform(multipart(own()).file(new MockMultipartFile("file","untrusted.psd","application/octet-stream",data)).param("format","PSD").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isOk());verify(guard).rate("source-file-upload",owner,2);verify(service).upload(owner,id,SourceFileValidator.Format.PSD,data);}
 @Test void emptyUploadRejected()throws Exception{mvc.perform(multipart(own()).file(new MockMultipartFile("file",new byte[0])).param("format","PSD").with(jwt().jwt(b->b.subject(owner.toString())))).andExpect(status().isBadRequest());verifyNoInteractions(service);}
 @Test void downloadForcesAttachment()throws Exception{when(service.download(owner,id)).thenReturn(new byte[]{1});mvc.perform(get(admin()).with(jwt().jwt(b->b.subject(owner.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk()).andExpect(content().contentType("application/octet-stream")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition","attachment; filename=\"source-"+id+".bin\""));}
}
