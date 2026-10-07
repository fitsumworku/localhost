package com.neueda.leap.team.repository;

import com.neueda.leap.team.entity.CashLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashLedgerRepository extends JpaRepository<CashLedgerEntry, Long> {}
