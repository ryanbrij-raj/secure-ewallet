package com.ewallet.config;

import com.ewallet.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security configuration.
 *
 * <ul>
 *   <li>Stateless sessions — JWT replaces server-side sessions entirely.</li>
 *   <li>{@code @EnableMethodSecurity} — {@code @PreAuthorize("hasRole('ADMIN')")} etc. work on service methods.</li>
 *   <li>Security headers — HSTS, frame options, content-type sniffing prevention.</li>
 *   <li>BCrypt cost factor 12 — balances security with login latency.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── Disable CSRF (stateless API, JWT auth) ──────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── Stateless sessions ──────────────────────────────────────────
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Authorization rules ─────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh"
                ).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            // ── Security response headers ────────────────────────────────────
            .headers(headers -> headers
                .frameOptions(fo -> fo.deny())
                .contentTypeOptions(cto -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000))
                .referrerPolicy(rp -> rp
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            )

            // ── Plug in JWT filter ───────────────────────────────────────────
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt with cost factor 12.
     * Each password hash takes ~300 ms on modern hardware — makes brute-force
     * attacks on a stolen database prohibitively expensive.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
