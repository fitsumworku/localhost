 package com.neueda.leap.team.entity;

 import jakarta.persistence.*;
 import java.util.HashSet;
 import java.util.Set;

 @Entity
 @Table(name = "Roles")
 public class RoleEntity {

     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Role_ID")
     private Long roleId;

     @Column(name = "Name", nullable = false, unique = true)
    private String roleName;

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
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
