package com.example.words.repository;

import com.example.words.model.StudentPointEventAttempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPointEventAttemptRepository extends JpaRepository<StudentPointEventAttempt, Long> {

    List<StudentPointEventAttempt> findByEventIdOrderByAttemptNoAsc(Long eventId);

    Optional<StudentPointEventAttempt> findTopByEventIdOrderByAttemptNoDesc(Long eventId);
}
