package com.neueda.leap.team.security;

import com.neueda.leap.team.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public CustomUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    @Transactional(readOnly = true)
    public AppUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null) throw new UsernameNotFoundException("User unavailable");
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User unavailable"));
    }

    @Transactional(readOnly = true)
    public AppUserDetails loadUserById(long userId) throws UsernameNotFoundException {
        return users.findById(userId).map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User unavailable"));
    }
}
