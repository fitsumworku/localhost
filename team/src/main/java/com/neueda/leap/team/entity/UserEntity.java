package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "Users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "User_ID")
    private Long userId;

    @Column(name = "Name", nullable = false)
    private String name;

    @Column(name = "Email", nullable = false, unique = true)
    private String email;

    @Column(name = "Password_Hash", nullable = false)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Role_ID", nullable = false)
    private RoleEntity role;

    @Column(name = "Status", nullable = false)
    private String status;

    @Column(name = "Created_Date", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @Column(name = "Updated_Date", nullable = false)
    private LocalDateTime updatedDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccountEntity> accounts = new ArrayList<>();

    @OneToMany(mappedBy = "userForAudit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuditLogEntity> actorAuditLogs = new ArrayList<>();

    @OneToMany(mappedBy = "subjectUser", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuditLogEntity> subjectAuditLog = new ArrayList<>();

    @OneToMany(mappedBy = "userForDispute", cascade = CascadeType.ALL, orphanRemoval =true)
    private List<DisputeEntity> disputes = new ArrayList<>();

    @Transient
    private Set<RoleEntity> roles = new HashSet<>();

    public UserEntity() {}

    public UserEntity(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.passwordHash = password;
        this.dateCreated = LocalDateTime.now();
        this.updatedDate = this.dateCreated;
        this.status = "ACTIVE";
    }

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return passwordHash;
    }

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    // Backward-compatible aliases used by older service/controller code.
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public RoleEntity getRole() {
        return role;
    }

    public void setRole(RoleEntity role) {
        this.role = role;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<AccountEntity> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<AccountEntity> accounts) {
        this.accounts = accounts;
    }

    public Set<RoleEntity> getRoles() {
        if (role != null) {
            roles.clear();
            roles.add(role);
        }
        return roles;
    }

    public void setRoles(Set<RoleEntity> roles) {
        this.roles = roles;
        if (roles != null && !roles.isEmpty()) {
            this.role = roles.iterator().next();
        }
    }
}
