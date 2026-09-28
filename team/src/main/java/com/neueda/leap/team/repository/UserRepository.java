package com.neueda.leap.team.repository;

import com.neueda.leap.team.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Spring generates the SQL from the method names.
// save() and findById() come from JpaRepository.
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // SELECT * FROM Users WHERE Email = ?
    Optional<UserEntity> findByEmail(String email);

    // SELECT COUNT(*) > 0 FROM Users WHERE Email = ?
    boolean existsByEmail(String email);
}
