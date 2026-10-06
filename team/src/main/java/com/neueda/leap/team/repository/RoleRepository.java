package com.neueda.leap.team.repository;

import com.neueda.leap.team.entity.Role;
import com.neueda.leap.team.entity.enums.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
