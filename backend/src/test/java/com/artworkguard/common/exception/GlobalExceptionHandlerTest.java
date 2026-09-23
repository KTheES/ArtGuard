package com.artworkguard.common.exception;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class GlobalExceptionHandlerTest {
    @RestController static class Fixture {
        record Input(@NotBlank String title) {}
        @PostMapping("/test") void validate(@Valid @RequestBody Input input) {}
        @GetMapping("/test") void fail() { throw new IllegalStateException("secret database password"); }
    }
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new Fixture()).setControllerAdvice(new GlobalExceptionHandler()).build();
    @Test void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
    @Test void hidesInternalDetails() throws Exception {
        mvc.perform(get("/test")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
    @Test void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
    }
}