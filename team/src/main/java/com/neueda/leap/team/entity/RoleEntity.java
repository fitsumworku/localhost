 package com.neueda.leap.team.entity;

 import jakarta.persistence.*;
 import java.util.HashSet;
 import java.util.Set;

 @Entity
 @Table(name = "roles")
 public class RoleEntity {

     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
     private Long roleId;

     @Column(nullable = false, unique = true)
     private String roleName;  // TRADER, COMPLIANCE, USER_MANAGER, DISPUTE_REVIEWER, etc.

     @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
     private Set<UserEntity> users = new HashSet<>();

     public RoleEntity() {}

     public RoleEntity(String roleName) {
         this.roleName = roleName;
     }

     // Getters and Setters
     public Long getRoleId() {
         return roleId;
     }

     public String getRoleName() {
         return roleName;
     }

     public void setRoleName(String roleName) {
         this.roleName = roleName;
     }

     public Set<UserEntity> getUsers() {
         return users;
     }

     public void setUsers(Set<UserEntity> users) {
         this.users = users;
     }

 }
