package com.neueda.leap.team.repository;

import com.neueda.leap.team.entity.Dispute;
import com.neueda.leap.team.entity.enums.DisputeStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    @Query(value = "SELECT count(*) > 0 FROM transactions WHERE account_id = :accountId AND transaction_id = :transactionId", nativeQuery = true)
    boolean existsTransactionForAccount(@Param("accountId") long accountId, @Param("transactionId") long transactionId);

    @Query(value = "SELECT count(*) > 0 FROM trades WHERE account_id = :accountId AND trade_id = :tradeId", nativeQuery = true)
    boolean existsTradeForAccount(@Param("accountId") long accountId, @Param("tradeId") long tradeId);

    Optional<Dispute> findByAccountIdAndId(long accountId, long id);

    Page<Dispute> findByAccountId(long accountId, Pageable pageable);

    Page<Dispute> findByAccountIdAndStatus(long accountId, DisputeStatus status, Pageable pageable);
}
