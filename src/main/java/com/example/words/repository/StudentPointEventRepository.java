package com.example.words.repository;

import com.example.words.model.PointEventStatus;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPointEventRepository extends JpaRepository<StudentPointEvent, Long> {

    Optional<StudentPointEvent> findByIdempotencyKey(String idempotencyKey);

    Optional<StudentPointEvent> findBySourceTypeAndSourceId(PointSourceType sourceType, Long sourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from StudentPointEvent event
             where event.sourceType = :sourceType
               and event.sourceId = :sourceId
            """)
    Optional<StudentPointEvent> findBySourceTypeAndSourceIdForUpdate(
            @Param("sourceType") PointSourceType sourceType,
            @Param("sourceId") Long sourceId
    );

    boolean existsByRuleCodeAndStatusIn(String ruleCode, Collection<PointEventStatus> statuses);

    Page<StudentPointEvent> findByStatusIn(Collection<PointEventStatus> statuses, Pageable pageable);

    Page<StudentPointEvent> findByStatus(PointEventStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StudentPointEvent event
               set event.status = com.example.words.model.PointEventStatus.PROCESSING,
                   event.processingTriggerType = :triggerType,
                   event.processingOperatorId = :operatorId,
                   event.processingOperatorRole = :operatorRole,
                   event.processingReason = :reason,
                   event.processingStartedAt = :startedAt,
                   event.updatedAt = :startedAt
             where event.id = :eventId
               and event.status in (
                   com.example.words.model.PointEventStatus.PENDING,
                   com.example.words.model.PointEventStatus.FAILED
               )
               and (:automatic = false or event.autoAttemptCount < :maxAutoAttempts)
            """)
    int claimForProcessing(
            @Param("eventId") Long eventId,
            @Param("triggerType") PointAttemptTriggerType triggerType,
            @Param("operatorId") Long operatorId,
            @Param("operatorRole") String operatorRole,
            @Param("reason") String reason,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("automatic") boolean automatic,
            @Param("maxAutoAttempts") int maxAutoAttempts
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from StudentPointEvent event where event.id = :eventId")
    Optional<StudentPointEvent> findByIdForUpdate(@Param("eventId") Long eventId);

    @Query("""
            select event.id
              from StudentPointEvent event
             where event.status in (
                 com.example.words.model.PointEventStatus.PENDING,
                 com.example.words.model.PointEventStatus.FAILED
             )
               and event.autoAttemptCount < :maxAutoAttempts
               and (event.nextRetryAt is null or event.nextRetryAt <= :now)
             order by event.id
            """)
    List<Long> findDueEventIds(
            @Param("now") LocalDateTime now,
            @Param("maxAutoAttempts") int maxAutoAttempts,
            Pageable pageable
    );

    @Query("""
            select event.id
              from StudentPointEvent event
             where event.status = com.example.words.model.PointEventStatus.PROCESSING
               and (event.processingStartedAt is null or event.processingStartedAt < :cutoff)
               and event.id > :afterId
             order by event.id
            """)
    List<Long> findInterruptedProcessingEventIdsAfter(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Query("""
            select event.id
              from StudentPointEvent event
             where event.status = com.example.words.model.PointEventStatus.PROCESSING
               and (event.processingStartedAt is null or event.processingStartedAt < :cutoff)
               and event.id > :afterId
             order by event.id
            """)
    List<Long> findTimedOutProcessingEventIdsAfter(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("afterId") Long afterId,
            Pageable pageable
    );
}
