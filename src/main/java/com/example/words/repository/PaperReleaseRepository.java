package com.example.words.repository;

import com.example.words.model.PaperRelease;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaperReleaseRepository
        extends JpaRepository<PaperRelease, Long>, JpaSpecificationExecutor<PaperRelease> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT release FROM PaperRelease release WHERE release.id = :releaseId")
    Optional<PaperRelease> findByIdForUpdate(@Param("releaseId") Long releaseId);
}
