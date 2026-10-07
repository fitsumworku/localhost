package com.neueda.leap.team.repository;

import com.neueda.leap.team.entity.CashTransaction;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {
    Optional<CashTransaction> findByAccountIdAndClientRequestId(long accountId, UUID clientRequestId);
}
