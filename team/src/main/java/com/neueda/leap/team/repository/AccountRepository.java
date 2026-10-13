package com.neueda.leap.team.repository;

import com.neueda.leap.team.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {}
