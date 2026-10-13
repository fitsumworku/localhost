package com.neueda.leap.team.entity;

import com.neueda.leap.team.entity.enums.UserStatus;  
import com.neueda.leap.team.entity.enums.RoleName;  

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private UserStatus status;
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;
    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;
    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    protected User() {}

    public User(String name, String email, String passwordHash, Role role, Instant now) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.createdDate = now;
        this.updatedDate = now;
        this.tokenVersion = 0;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedDate() { return createdDate; }
    public Instant getUpdatedDate() { return updatedDate; }
    public long getTokenVersion() { return tokenVersion; }

    public void updateName(String name, Instant now) {
        this.name = name;
        this.updatedDate = now;
    }

    public void changeStatus(UserStatus status, Instant now) {
        if (this.status != status) {
            this.status = status;
            revokeTokens(now);
        }
    }

    public void revokeTokens(Instant now) {
        tokenVersion = Math.incrementExact(tokenVersion);
        updatedDate = now;
    }
    // No password/hash in toString, and controllers never serialize this entity.
}