package com.example.words.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.words.model.AttentionState;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentStudyPlan;
import com.example.words.model.StudyActionType;
import com.example.words.model.StudyDayTask;
import com.example.words.model.StudyDayTaskStatus;
import com.example.words.model.StudyRecord;
import com.example.words.model.StudyRecordResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StudentPointReconciliationRepositoryIntegrationTest {

    @Autowired
    private StudentStudyPlanRepository studentStudyPlanRepository;

    @Autowired
    private StudyRecordRepository studyRecordRepository;

    @Autowired
    private StudyDayTaskRepository studyDayTaskRepository;

    @Autowired
    private StudentPointEventRepository eventRepository;

    private Long studentStudyPlanId;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        studyRecordRepository.deleteAll();
        studyDayTaskRepository.deleteAll();
        studentStudyPlanRepository.deleteAll();

        StudentStudyPlan plan = new StudentStudyPlan();
        plan.setStudyPlanId(55L);
        plan.setStudentId(20L);
        studentStudyPlanId = studentStudyPlanRepository.saveAndFlush(plan).getId();
    }

    @Test
    void correctRecordQueryShouldExcludeExistingEventsAndHonorBatchLimit() {
        saveRecord("historical-correct", StudyRecordResult.CORRECT, false);
        StudyRecord first = saveRecord("record-1", StudyRecordResult.CORRECT, true);
        StudyRecord second = saveRecord("record-2", StudyRecordResult.CORRECT, true);
        saveRecord("record-3", StudyRecordResult.INCORRECT, true);
        saveExistingEvent(
                first.getId(),
                "study-record:" + first.getId() + ":correct",
                "STUDY_RECORD_CORRECT",
                PointSourceType.STUDY_RECORD
        );

        List<StudyRecordRepository.MissingCorrectPointSource> missing =
                studyRecordRepository.findMissingCorrectPointSources(1);

        assertEquals(1, missing.size());
        assertEquals(second.getId(), missing.get(0).getSourceId());
        assertEquals(20L, missing.get(0).getStudentId());
    }

    @Test
    void completedTaskQueryShouldExcludeExistingEventsAndHonorBatchLimit() {
        saveTask(StudyDayTaskStatus.COMPLETED, 1, 1, false);
        saveTask(StudyDayTaskStatus.COMPLETED, 0, 0, true);
        saveTask(StudyDayTaskStatus.COMPLETED, 2, 1, true);
        StudyDayTask first = saveTask(StudyDayTaskStatus.COMPLETED, 1, 1, true);
        StudyDayTask second = saveTask(StudyDayTaskStatus.COMPLETED, 1, 1, true);
        saveTask(StudyDayTaskStatus.IN_PROGRESS, 1, 0, true);
        saveExistingEvent(
                first.getId(),
                "study-day-task:" + first.getId() + ":completed",
                "DAILY_TASK_COMPLETED",
                PointSourceType.STUDY_TASK
        );

        List<StudyDayTaskRepository.MissingCompletedPointSource> missing =
                studyDayTaskRepository.findMissingCompletedPointSources(1);

        assertEquals(1, missing.size());
        assertEquals(second.getId(), missing.get(0).getSourceId());
        assertEquals(20L, missing.get(0).getStudentId());
    }

    private StudyRecord saveRecord(String requestKey, StudyRecordResult result, boolean pointsEligible) {
        return studyRecordRepository.saveAndFlush(StudyRecord.builder()
                .requestKey(requestKey)
                .studentStudyPlanId(studentStudyPlanId)
                .metaWordId(3L)
                .taskDate(LocalDate.of(2026, 7, 22))
                .actionType(StudyActionType.LEARN)
                .result(result)
                .durationSeconds(10)
                .focusSeconds(9)
                .idleSeconds(1)
                .interactionCount(1)
                .attentionState(AttentionState.FOCUSED)
                .pointsEligible(pointsEligible)
                .build());
    }

    private StudyDayTask saveTask(
            StudyDayTaskStatus status,
            int totalTaskCount,
            int completedCount,
            boolean pointsEligible
    ) {
        StudyDayTask task = new StudyDayTask();
        task.setStudentStudyPlanId(studentStudyPlanId);
        task.setTaskDate(LocalDate.of(2026, 7, 22));
        task.setNewCount(totalTaskCount);
        task.setCompletedCount(completedCount);
        task.setPointsEligible(pointsEligible);
        task.setStatus(status);
        task.setDeadlineAt(LocalDateTime.of(2026, 7, 22, 21, 0));
        return studyDayTaskRepository.saveAndFlush(task);
    }

    private void saveExistingEvent(Long sourceId, String sourceKey, String ruleCode, PointSourceType sourceType) {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(20L);
        event.setSourceType(sourceType);
        event.setSourceId(sourceId);
        event.setSourceKey(sourceKey);
        event.setRuleCode(ruleCode);
        event.setRuleName(ruleCode);
        event.setPoints(1);
        event.setIdempotencyKey(sourceKey + ":" + ruleCode);
        event.setStatus(PointEventStatus.PENDING);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.saveAndFlush(event);
    }
}
