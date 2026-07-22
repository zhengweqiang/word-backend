package com.example.words.service;

import com.example.words.repository.StudyDayTaskRepository;
import com.example.words.repository.StudyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentPointReconciliationService {

    static final int BATCH_SIZE = 100;

    private final StudyRecordRepository studyRecordRepository;
    private final StudyDayTaskRepository studyDayTaskRepository;
    private final StudentPointEventPublisher publisher;

    public void reconcileMissingEvents() {
        for (StudyRecordRepository.MissingCorrectPointSource source
                : studyRecordRepository.findMissingCorrectPointSources(BATCH_SIZE)) {
            publishRecord(source);
        }
        for (StudyDayTaskRepository.MissingCompletedPointSource source
                : studyDayTaskRepository.findMissingCompletedPointSources(BATCH_SIZE)) {
            publishTask(source);
        }
    }

    private void publishRecord(StudyRecordRepository.MissingCorrectPointSource source) {
        StudentPointEventPublisher.PublishRequest request = new StudentPointEventPublisher.PublishRequest(
                source.getStudentId(),
                source.getSourceId(),
                "study-record:" + source.getSourceId() + ":correct",
                "STUDY_RECORD_CORRECT"
        );
        publishSafely(request);
    }

    private void publishTask(StudyDayTaskRepository.MissingCompletedPointSource source) {
        StudentPointEventPublisher.PublishRequest request = new StudentPointEventPublisher.PublishRequest(
                source.getStudentId(),
                source.getSourceId(),
                "study-day-task:" + source.getSourceId() + ":completed",
                "DAILY_TASK_COMPLETED"
        );
        publishSafely(request);
    }

    private void publishSafely(StudentPointEventPublisher.PublishRequest request) {
        try {
            publisher.publishAfterCommit(request);
        } catch (RuntimeException exception) {
            log.error(
                    "Student point reconciliation failed: sourceId={}, sourceKey={}, ruleCode={}",
                    request.sourceId(),
                    request.sourceKey(),
                    request.ruleCode(),
                    exception
            );
        }
    }
}
