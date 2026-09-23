package com.artworkguard.common.config;

import com.artworkguard.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, error) -> {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getOutputStream(), ApiResponse.failure("UNAUTHORIZED", "인증이 필요합니다."));
        };
        AccessDeniedHandler forbidden = (request, response, error) -> {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getOutputStream(), ApiResponse.failure("FORBIDDEN", "접근할 수 없습니다."));
        };
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return http
            // Credentials use explicit JSON bodies or Authorization headers; no authentication cookies.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhooks/stripe").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/subscription/plans").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/email-verification").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/email-verification/request", "/api/v1/auth/email-verification/confirm").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/subscription").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/sellers/intelligence").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/sellers/intelligence").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/audit-events").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ownership-claims/*/source-file").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/ownership-claims/*/source-file").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/ownership-claims/*/source-file", "/api/v1/admin/ownership-claims/*/source-file/download").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/social-checks").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/social-checks").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/social-checks/*/revoke").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/social-checks").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/social-checks/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/ownership-claims").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/ownership-claims/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/checkout").authenticated()
                .requestMatchers("/api/v1/artworks", "/api/v1/artworks/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/marketplaces", "/api/v1/products", "/api/v1/products/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/marketplaces/MOCK/collect", "/api/v1/admin/marketplaces/ALIEXPRESS/collect").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/products/*/images/*/embedding").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/detections", "/api/v1/detections/**").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/detections/*/status").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/detections/*/takedown").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/detections/*/takedown").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
            .oauth2ResourceServer(resource -> resource.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
            .build();
    }
}
