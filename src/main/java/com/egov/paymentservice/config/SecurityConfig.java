package com.egov.paymentservice.config;

import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;

@Configuration
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${spring.security.oauth2.resourceserver.jwt.secret}") String secret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/payment/**").authenticated()
                        .anyRequest().permitAll()
                )
               // Exception handling for invalid/expired JWT
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            logger.warn("Unauthorized access attempt to {}", request.getRequestURI());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        })
                )
                // enable JWT validation for resource server
                .oauth2ResourceServer(oauth2 -> oauth2.jwt());

        return http.build();
    }


    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey secretKey= Keys.hmacShaKeyFor(secret.getBytes());

        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
    }
}