package com.neueda.leap.team.repository;

import com.neueda.leap.team.entity.AccountCashBalance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccountCashBalanceRepository extends JpaRepository<AccountCashBalance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountCashBalance b where b.accountId = :accountId")
    Optional<AccountCashBalance> findByAccountIdForUpdate(@Param("accountId") long accountId);
}
