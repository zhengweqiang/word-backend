package com.example.words.repository;

import com.example.words.model.StudentPointAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPointAccountRepository extends JpaRepository<StudentPointAccount, Long> {

    Optional<StudentPointAccount> findByStudentId(Long studentId);

    boolean existsByStudentId(Long studentId);

    List<StudentPointAccount> findAllByStudentIdIn(Collection<Long> studentIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from StudentPointAccount account where account.studentId = :studentId")
    Optional<StudentPointAccount> findByStudentIdForUpdate(@Param("studentId") Long studentId);
}
