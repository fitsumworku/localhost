package com.neueda.leap.team.exception;

import java.time.Clock;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;
    public ApiExceptionHandler(Clock clock) { this.clock = clock; }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> applicationError(ApiException e) {
        return error(e.status(), e.code(), e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the submitted fields.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed request or unsupported fields.", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> invalidCredentials() {
        // Do not reveal whether an email is unknown, a password is wrong, or a user is disabled.
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect, or sign-in is unavailable.", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied() {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission for this action.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> constraint(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException c && "users_email_key".equals(c.getConstraintName())) {
                return error(HttpStatus.CONFLICT, "EMAIL_IN_USE", "Email is already registered.", Map.of());
            }
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed.", Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(clock.instant(), status.value(), code, message, fields));
    }
}
