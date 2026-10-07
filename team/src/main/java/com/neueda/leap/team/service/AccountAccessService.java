package com.neueda.leap.team.service;

import com.neueda.leap.team.entity.*;
import com.neueda.leap.team.entity.enums.*;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.repository.*;
import com.neueda.leap.team.security.AppUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AccountAccessService {
    private final AccountRepository accounts;
    private final UserRepository users;
    public AccountAccessService(AccountRepository accounts, UserRepository users) {
        this.accounts = accounts; this.users = users;
    }

    public AppUserDetails principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails user)) {
            throw new BadCredentialsException("Sign in to continue");
        }
        return user;
    }
    public boolean isAdmin() {
        return principal().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
    public Account readable(long id) {
        Account account = accounts.findById(id).orElseThrow(this::notFound);
        if (!isAdmin() && account.getOwner().getId() != principal().getUserId()) throw notFound();
        return account;
    }
    public Account owned(long id, long ownerId) {
        Account account = accounts.findById(id).orElseThrow(this::notFound);
        if (account.getOwner().getId() != ownerId) throw notFound();
        return account;
    }
    /** First lock for ALL account writes: serialize with suspension and token revocation. */
    public User lockActiveClient() {
        AppUserDetails principal = principal();
        User user = users.findByIdForUpdate(principal.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User unavailable"));
        if (user.getStatus() != UserStatus.ACTIVE || user.getTokenVersion() != principal.getTokenVersion()) {
            throw new BadCredentialsException("User unavailable");
        }
        if (user.getRole().getName() != RoleName.CLIENT) throw new AccessDeniedException("Client login required");
        return user;
    }
    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found.");
    }
}
