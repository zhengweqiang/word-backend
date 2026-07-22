package com.example.words.repository;

import com.example.words.model.StudentPointTransaction;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPointTransactionRepository extends JpaRepository<StudentPointTransaction, Long> {

    Optional<StudentPointTransaction> findByIdempotencyKey(String idempotencyKey);

    Page<StudentPointTransaction> findByStudentId(Long studentId, Pageable pageable);

    @Query("""
            select coalesce(sum(transaction.amount), 0)
              from StudentPointTransaction transaction
             where transaction.studentId = :studentId
               and transaction.transactionType = com.example.words.model.PointTransactionType.EARN
               and transaction.createdAt >= :start
               and transaction.createdAt < :end
               and not exists (
                   select reversal.id
                     from StudentPointTransaction reversal
                    where reversal.transactionType = com.example.words.model.PointTransactionType.REVERSE
                      and reversal.reversedTransactionId = transaction.id
               )
            """)
    Long sumEarnedByStudentIdBetween(
            @Param("studentId") Long studentId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            select transaction.studentId as studentId, sum(transaction.amount) as total
              from StudentPointTransaction transaction
             where transaction.studentId in :studentIds
               and transaction.transactionType = com.example.words.model.PointTransactionType.EARN
               and transaction.createdAt >= :start
               and transaction.createdAt < :end
               and not exists (
                   select reversal.id
                     from StudentPointTransaction reversal
                    where reversal.transactionType = com.example.words.model.PointTransactionType.REVERSE
                      and reversal.reversedTransactionId = transaction.id
               )
             group by transaction.studentId
            """)
    List<StudentPointEarnedTotal> sumEarnedByStudentIdsBetween(
            @Param("studentIds") Collection<Long> studentIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    interface StudentPointEarnedTotal {

        Long getStudentId();

        Long getTotal();
    }
}
