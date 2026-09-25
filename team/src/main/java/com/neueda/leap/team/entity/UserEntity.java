package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

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

    @Column(name ="Password", nullable = false)
    private String password;

    @Column(name = "Created_Date", nullable = false, updatable = false)
    private LocalDateTime dateCreated;

    @Column(name = "Status", nullable = false)
    private String status;  // ACTIVE, SUSPENDED

    // TODO: re-add once AccountEntity is uncommented
    // @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    // private List<AccountEntity> accounts = new ArrayList<>();

    // TODO: re-add once RoleEntity is uncommented
    // @ManyToMany(fetch = FetchType.LAZY)
    // @JoinTable(
    //     name = "user_roles",
    //     joinColumns = @JoinColumn(name = "user_id"),
    //     inverseJoinColumns = @JoinColumn(name = "role_id")
    // )
    // private Set<RoleEntity> roles = new HashSet<>();

    public UserEntity() {}

    public UserEntity(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.dateCreated = LocalDateTime.now();
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
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // Backward-compatible aliases used by older service/controller code.
    public String getPasswordHash() {
        return password;
    }

    public void setPasswordHash(String passwordHash) {
        this.password = passwordHash;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // Authentication methods
    public boolean login(String email, String password) {
        if (!this.email.equals(email)) {
            return false;
        }
        if (!status.equals("ACTIVE")) {
            return false;
        }
        return true;
    }

    public boolean logout() {
        return true;
    }

    public boolean updateProfile(String name, String email) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
        if (email != null && !email.isEmpty()) {
            this.email = email;
        }
        return true;
    }
}
