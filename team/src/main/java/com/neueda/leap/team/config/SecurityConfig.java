package com.neueda.leap.team.config;

import com.neueda.leap.team.security.*;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean public AuthenticationProvider authenticationProvider(CustomUserDetailsService users, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return provider;
    }

    @Bean public AuthenticationManager authenticationManager(AuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            AuthenticationProvider provider, SecurityErrorHandler errors, UrlBasedCorsConfigurationSource cors) throws Exception {
        return http
                // This API authenticates only explicit bearer headers, never cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/openapi.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/users/*/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/*/revoke-sessions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/users/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/accounts", "/accounts/*/deposits", "/accounts/*/withdrawals").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/accounts", "/accounts/*", "/accounts/*/balance",
                                "/accounts/*/positions", "/accounts/*/orders", "/accounts/*/trades",
                                "/accounts/*/transactions", "/accounts/*/transactions/*", "/accounts/*/ledger")
                                .hasAnyRole("CLIENT", "ADMIN")
                        // Enable additional business routes only alongside their ownership/role checks.
                        .anyRequest().denyAll())
                .authenticationProvider(provider)
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // SecurityFilterChain is the only registration.
        return registration;
    }

    @Bean public UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        if (properties.allowedOrigins().stream().anyMatch(s -> s.isBlank() || s.contains("*"))) {
            throw new IllegalStateException("Configure explicit CORS origins.");
        }
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
