package com.example.words.repository;

import com.example.words.model.PaperReleaseTarget;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperReleaseTargetRepository extends JpaRepository<PaperReleaseTarget, Long> {

    List<PaperReleaseTarget> findByPaperReleaseId(Long paperReleaseId);

    List<PaperReleaseTarget> findByStudentId(Long studentId);
}
