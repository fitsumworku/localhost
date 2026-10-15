package com.neueda.leap.team.service;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.entity.User;
import com.neueda.leap.team.entity.enums.*;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.repository.*;
import com.neueda.leap.team.security.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final Clock clock;
    private final EntityManager entityManager;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       Clock clock, EntityManager entityManager) {
        this.users = users; this.roles = roles; this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager; this.jwtService = jwtService;
        this.clock = clock; this.entityManager = entityManager;
    }

    @Transactional
    public UserDto register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE", "Email is already registered.");
        var clientRole = roles.findByName(RoleName.CLIENT)
                .orElseThrow(() -> new IllegalStateException("Seed CLIENT and ADMIN roles before registration."));
        User user = users.saveAndFlush(new User(req.name(), req.email(), passwordEncoder.encode(req.password()), clientRole, clock.instant()));
        return UserDto.from(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.email(), req.password()));
        var principal = (AppUserDetails) authentication.getPrincipal();
        User user = lockedUser(principal.getUserId());
        // DaoAuthenticationProvider may already have loaded this entity. Refresh after taking
        // the lock so a concurrent suspension/revocation cannot leave cached state in this login.
        entityManager.refresh(user);
        if (user.getStatus() != UserStatus.ACTIVE) throw new BadCredentialsException("User unavailable");
        IssuedJwt issued = jwtService.generateToken(user);
        long remaining = Math.max(0, Duration.between(clock.instant(), issued.expiresAt()).toMillis());
        return new AuthResponse(issued.token(), "Bearer", remaining, UserDto.from(user));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void logout() {
        AppUserDetails principal = principal();
        User user = lockedUser(principal.getUserId());
        if (user.getTokenVersion() != principal.getTokenVersion()) return;
        user.revokeTokens(clock.instant());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public UserDto getById(Long userId) {
        return UserDto.from(users.findById(userId).orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto getByEmail(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).map(UserDto::from).orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserPageResponse listUsers(UserStatus status, RoleName role, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw invalid("page must be nonnegative and size must be between 1 and 100.");
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !Set.of("userId", "name", "email", "createdDate").contains(parts[0])
                || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
            throw invalid("sort must be userId, name, email or createdDate followed by ,asc or ,desc.");
        }
        String field = parts[0].equals("userId") ? "id" : parts[0];
        var sorting = Sort.by(Sort.Direction.fromString(parts[1]), field).and(Sort.by("id"));
        Specification<User> spec = (root, query, cb) -> {
            var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (role != null) filters.add(cb.equal(root.get("role").get("name"), role));
            return cb.and(filters.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<User> result = users.findAll(spec, PageRequest.of(page, size, sorting));
        return new UserPageResponse(result.getContent().stream().map(UserDto::from).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    @PreAuthorize("#userId == authentication.principal.userId")
    public UserDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = lockedUser(userId);
        requireActivePrincipal(user);
        if (!user.getName().equals(request.name())) {
            user.updateName(request.name(), clock.instant());
        }
        return UserDto.from(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto updateStatus(Long userId, UpdateUserStatusRequest request) {
        User user = lockedClient(userId);
        UserStatus previous = user.getStatus();
        if (previous != request.status()) {
            user.changeStatus(request.status(), clock.instant());
        }
        return UserDto.from(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void revokeSessions(Long userId) {
        User user = lockedClient(userId);
        user.revokeTokens(clock.instant());
    }

    private User lockedUser(long id) { return users.findByIdForUpdate(id).orElseThrow(this::notFound); }
    private User lockedClient(long id) {
        User user = lockedUser(id);
        if (user.getRole().getName() != RoleName.CLIENT) throw invalid("This operation applies to clients only.");
        return user;
    }
    private AppUserDetails principal() { return (AppUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal(); }
    private void requireActivePrincipal(User user) {
        if (user.getStatus() != UserStatus.ACTIVE || user.getTokenVersion() != principal().getTokenVersion()) {
            throw new BadCredentialsException("User unavailable");
        }
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."); }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message); }
}
