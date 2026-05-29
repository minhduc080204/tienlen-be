package com.tienlen.be.repository;

import com.tienlen.be.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type IN :types AND t.status = :status")
    Long sumAmountByTypeInAndStatus(@org.springframework.data.repository.query.Param("types") List<String> types, @org.springframework.data.repository.query.Param("status") String status);

    boolean existsByTxHash(String txHash);
}
