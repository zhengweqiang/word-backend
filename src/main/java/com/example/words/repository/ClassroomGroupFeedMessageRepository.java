package com.example.words.repository;

import com.example.words.model.ClassroomGroupFeedMessage;
import com.example.words.model.ClassroomGroupFeedMessageType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomGroupFeedMessageRepository extends JpaRepository<ClassroomGroupFeedMessage, Long> {

    Page<ClassroomGroupFeedMessage> findByClassroomIdOrderByCreatedAtDesc(Long classroomId, Pageable pageable);

    Page<ClassroomGroupFeedMessage> findByClassroomIdAndMessageTypeOrderByCreatedAtDesc(
            Long classroomId,
            ClassroomGroupFeedMessageType messageType,
            Pageable pageable);

    Optional<ClassroomGroupFeedMessage> findFirstByClassroomIdOrderByCreatedAtDesc(Long classroomId);

    boolean existsByClassroomIdAndMessageTypeAndResourceId(
            Long classroomId,
            ClassroomGroupFeedMessageType messageType,
            Long resourceId);
}
