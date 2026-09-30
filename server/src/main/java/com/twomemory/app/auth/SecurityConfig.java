package com.twomemory.app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcTemplate jdbcTemplate)
            throws Exception {
        DeviceSessionAuthenticationFilter deviceSessionFilter =
                new DeviceSessionAuthenticationFilter(jdbcTemplate);
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/v1/bootstrap",
                                "/api/v1/couple/pair",
                                "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(deviceSessionFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
