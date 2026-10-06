package com.neueda.leap.team.entity;


import com.neueda.leap.team.entity.enums.RoleName;
import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 16)
    private RoleName name;

    protected Role() {}

    public Long getId() { return id; }
    public RoleName getName() { return name; }
}
