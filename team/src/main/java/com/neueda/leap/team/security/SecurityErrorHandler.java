package com.neueda.leap.team.security;

import com.neueda.leap.team.exception.ApiError;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final JsonMapper json;
    private final Clock clock;
    public SecurityErrorHandler(JsonMapper json, Clock clock) { this.json = json; this.clock = clock; }

    @Override public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, 401, "UNAUTHORIZED", "Sign in again to continue.");
    }

    @Override public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        write(response, 403, "FORBIDDEN", "You do not have permission for this action.");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json.writeValueAsString(new ApiError(clock.instant(), status, code, message, Map.of())));
    }
}
