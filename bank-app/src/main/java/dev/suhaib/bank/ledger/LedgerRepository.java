package dev.suhaib.bank.ledger;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    @EntityGraph(attributePaths = "transfer")
    List<LedgerEntry> findByAccountIdOrderByIdDesc(Long accountId);
}
