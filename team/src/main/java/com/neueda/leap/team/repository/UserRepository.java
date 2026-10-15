package com.neueda.leap.team.repository;

import com.neueda.leap.team.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = "role")
    Optional<User> findById(Long id);

    boolean existsByEmail(String email);

    // Service transactions lock the user before changing status/token version.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
