package com.artworkguard.auth.controller;

import com.artworkguard.auth.service.AuthService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService service;
    @DynamicPropertySource static void secret(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        registry.add("artworkguard.auth.jwt-secret", () -> java.util.Base64.getEncoder().encodeToString(bytes));
    }
    @Test void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }
    @Test void malformedBearerTokenUsesCommonErrorResponse() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
    }
    @Test void meUsesTokenSubjectInsteadOfRequestParameter() throws Exception {
        var id = java.util.UUID.randomUUID();
        mvc.perform(get("/api/v1/auth/me").param("userId", java.util.UUID.randomUUID().toString())
            .with(jwt().jwt(builder -> builder.subject(id.toString())))).andExpect(status().isOk());
        verify(service).me(id);
    }
    @Test void rejectsInvalidSignupAndOversizedUtf8Password() throws Exception {
        for (String json : java.util.List.of("{}", "{\"email\":\"test@example.com\",\"password\":\"" + "가".repeat(25) + "\",\"nickname\":\"Creator\"}")) {
            mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }
    @Test void signupAcceptsJsonWithoutCsrfOrSessionAndDoesNotCache() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"password\":\"ValidPassword123!\",\"nickname\":\"Creator\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(cookie().doesNotExist("JSESSIONID"));
    }
    @Test void otherApisRemainDeniedForAuthenticatedUsers() throws Exception {
        mvc.perform(get("/api/v1/admin").with(jwt())).andExpect(status().isForbidden());
    }
}
