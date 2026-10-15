package com.neueda.leap.team.security;

import com.neueda.leap.team.entity.User;
import com.neueda.leap.team.entity.enums.UserStatus;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class AppUserDetails extends org.springframework.security.core.userdetails.User {
    private static final long serialVersionUID = 1L;
    private final long userId;
    private final long tokenVersion;

    public AppUserDetails(User user) {
        super(user.getEmail(), user.getPasswordHash(), user.getStatus() == UserStatus.ACTIVE,
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName().name())));
        this.userId = user.getId();
        this.tokenVersion = user.getTokenVersion();
    }

    public long getUserId() { return userId; }
    public long getTokenVersion() { return tokenVersion; }
    @Override public String toString() { return "AppUserDetails[userId=" + userId + "]"; }
}
