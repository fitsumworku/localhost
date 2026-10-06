package com.neueda.leap.team.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService users;
    private final SecurityErrorHandler errors;
    private final AccountStatusUserDetailsChecker statusChecker = new AccountStatusUserDetailsChecker();

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService users, SecurityErrorHandler errors) {
        this.jwtService = jwtService; this.users = users; this.errors = errors;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        // An old token should not prevent a new login or public registration.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equals(request.getMethod()) && ("/auth/login".equals(path) || "/auth/register".equals(path));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) { chain.doFilter(request, response); return; }

        try {
            if (Collections.list(request.getHeaders("Authorization")).size() != 1
                    || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new BadCredentialsException("Invalid authorization header");
            }
            ValidatedJwt jwt = jwtService.parseAndValidate(header.substring(7).trim());
            AppUserDetails user = users.loadUserById(jwt.userId());
            // Manual JWT authentication MUST perform these checks; DaoAuthenticationProvider is not called here.
            statusChecker.check(user);
            if (jwt.tokenVersion() != user.getTokenVersion()) throw new BadCredentialsException("Revoked token");
            user.eraseCredentials();
            var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException | AuthenticationException e) {
            SecurityContextHolder.clearContext();
            errors.commence(request, response, new BadCredentialsException("Invalid bearer token"));
            return;
        }
        // Do not catch exceptions from controllers/services as JWT failures.
        chain.doFilter(request, response);
    }
}
