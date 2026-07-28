package com.example.words.repository;

import com.example.words.model.PaperRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaperReleaseRepository
        extends JpaRepository<PaperRelease, Long>, JpaSpecificationExecutor<PaperRelease> {
}
