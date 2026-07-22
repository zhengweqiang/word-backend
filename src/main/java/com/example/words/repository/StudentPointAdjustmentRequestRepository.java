package com.example.words.repository;

import com.example.words.model.StudentPointAdjustmentRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPointAdjustmentRequestRepository
        extends JpaRepository<StudentPointAdjustmentRequest, Long> {

    Optional<StudentPointAdjustmentRequest> findByRequestKey(String requestKey);

    Page<StudentPointAdjustmentRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from StudentPointAdjustmentRequest request where request.id = :requestId")
    Optional<StudentPointAdjustmentRequest> findByIdForUpdate(@Param("requestId") Long requestId);
}
