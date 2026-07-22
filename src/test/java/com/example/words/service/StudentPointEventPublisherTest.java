package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

class StudentPointEventPublisherTest {

    private final StudentPointEventService eventService = mock(StudentPointEventService.class);
    private final StudentPointEventPublisher publisher =
            new StudentPointEventPublisher(eventService, new SyncTaskExecutor());

    @Test
    void shouldCreateImmediatelyWithoutActiveTransaction() {
        StudentPointEventPublisher.PublishRequest request = request(101L);

        publisher.publishAfterCommit(request);

        verify(eventService).create(new StudentPointEventService.CreateRequest(
                20L,
                101L,
                "study-record:101:correct",
                "STUDY_RECORD_CORRECT",
                null,
                null
        ));
    }

    @Test
    void shouldSwallowEventCreationFailure() {
        StudentPointEventPublisher.PublishRequest request = request(102L);
        when(eventService.create(new StudentPointEventService.CreateRequest(
                20L,
                102L,
                "study-record:102:correct",
                "STUDY_RECORD_CORRECT",
                null,
                null
        ))).thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> publisher.publishAfterCommit(request));
    }

    private StudentPointEventPublisher.PublishRequest request(Long sourceId) {
        return new StudentPointEventPublisher.PublishRequest(
                20L,
                sourceId,
                "study-record:" + sourceId + ":correct",
                "STUDY_RECORD_CORRECT"
        );
    }
}
