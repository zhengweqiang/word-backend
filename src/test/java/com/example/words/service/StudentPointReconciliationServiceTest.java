package com.example.words.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.repository.StudyDayTaskRepository;
import com.example.words.repository.StudyRecordRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentPointReconciliationServiceTest {

    private StudyRecordRepository studyRecordRepository;
    private StudyDayTaskRepository studyDayTaskRepository;
    private StudentPointEventPublisher publisher;
    private StudentPointReconciliationService service;

    @BeforeEach
    void setUp() {
        studyRecordRepository = mock(StudyRecordRepository.class);
        studyDayTaskRepository = mock(StudyDayTaskRepository.class);
        publisher = mock(StudentPointEventPublisher.class);
        service = new StudentPointReconciliationService(
                studyRecordRepository,
                studyDayTaskRepository,
                publisher
        );
    }

    @Test
    void shouldPublishExactRequestsForMissingRecordsAndTasks() {
        StudyRecordRepository.MissingCorrectPointSource record = recordSource(11L, 20L);
        StudyDayTaskRepository.MissingCompletedPointSource task = taskSource(22L, 20L);
        when(studyRecordRepository.findMissingCorrectPointSources(100))
                .thenReturn(List.of(record));
        when(studyDayTaskRepository.findMissingCompletedPointSources(100))
                .thenReturn(List.of(task));

        service.reconcileMissingEvents();

        verify(publisher).publishAfterCommit(new StudentPointEventPublisher.PublishRequest(
                20L,
                11L,
                "study-record:11:correct",
                "STUDY_RECORD_CORRECT"
        ));
        verify(publisher).publishAfterCommit(new StudentPointEventPublisher.PublishRequest(
                20L,
                22L,
                "study-day-task:22:completed",
                "DAILY_TASK_COMPLETED"
        ));
    }

    @Test
    void shouldIsolateOneSourceFailureAndContinueTheBatch() {
        StudentPointEventPublisher.PublishRequest first = new StudentPointEventPublisher.PublishRequest(
                20L, 11L, "study-record:11:correct", "STUDY_RECORD_CORRECT");
        StudentPointEventPublisher.PublishRequest second = new StudentPointEventPublisher.PublishRequest(
                20L, 12L, "study-record:12:correct", "STUDY_RECORD_CORRECT");
        StudyRecordRepository.MissingCorrectPointSource firstSource = recordSource(11L, 20L);
        StudyRecordRepository.MissingCorrectPointSource secondSource = recordSource(12L, 20L);
        when(studyRecordRepository.findMissingCorrectPointSources(100))
                .thenReturn(List.of(firstSource, secondSource));
        when(studyDayTaskRepository.findMissingCompletedPointSources(100)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("missing rule"))
                .when(publisher).publishAfterCommit(first);

        service.reconcileMissingEvents();

        verify(publisher).publishAfterCommit(first);
        verify(publisher).publishAfterCommit(second);
    }

    private StudyRecordRepository.MissingCorrectPointSource recordSource(Long sourceId, Long studentId) {
        StudyRecordRepository.MissingCorrectPointSource source =
                mock(StudyRecordRepository.MissingCorrectPointSource.class);
        when(source.getSourceId()).thenReturn(sourceId);
        when(source.getStudentId()).thenReturn(studentId);
        return source;
    }

    private StudyDayTaskRepository.MissingCompletedPointSource taskSource(Long sourceId, Long studentId) {
        StudyDayTaskRepository.MissingCompletedPointSource source =
                mock(StudyDayTaskRepository.MissingCompletedPointSource.class);
        when(source.getSourceId()).thenReturn(sourceId);
        when(source.getStudentId()).thenReturn(studentId);
        return source;
    }
}
