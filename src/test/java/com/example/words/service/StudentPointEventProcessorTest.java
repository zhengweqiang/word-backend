package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventStatus;
import com.example.words.model.StudentPointEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StudentPointEventProcessorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-22T02:00:00Z"), ZoneOffset.UTC);

    @Mock
    private StudentPointEventService eventService;

    @Mock
    private StudentPointPostingTransaction postingTransaction;

    @Mock
    private StudentPointFailureRecorder failureRecorder;

    private StudentPointEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StudentPointEventProcessor(eventService, postingTransaction, failureRecorder, CLOCK);
    }

    @Test
    void processShouldClaimThenPost() {
        StudentPointEvent processing = event(10L, PointEventStatus.PROCESSING);
        StudentPointEvent succeeded = event(10L, PointEventStatus.SUCCEEDED);
        StudentPointEventService.AttemptContext context =
                StudentPointEventService.AttemptContext.auto();
        when(eventService.claim(10L, context, CLOCK.instant())).thenReturn(processing);
        when(postingTransaction.post(10L, CLOCK.instant())).thenReturn(succeeded);

        assertSame(succeeded, processor.process(10L, context));
        verify(postingTransaction).post(10L, CLOCK.instant());
        verify(failureRecorder, never()).recordFailure(any(), any(), any());
    }

    @Test
    void succeededClaimShouldReturnIdempotentlyWithoutPosting() {
        StudentPointEvent succeeded = event(10L, PointEventStatus.SUCCEEDED);
        StudentPointEventService.AttemptContext context =
                StudentPointEventService.AttemptContext.auto();
        when(eventService.claim(10L, context, CLOCK.instant())).thenReturn(succeeded);

        assertSame(succeeded, processor.process(10L, context));
        verify(postingTransaction, never()).post(any(), any());
    }

    @Test
    void postingFailureShouldBeRecordedIndependentlyAndReturned() {
        StudentPointEvent processing = event(10L, PointEventStatus.PROCESSING);
        StudentPointEvent failed = event(10L, PointEventStatus.FAILED);
        RuntimeException postingFailure = new IllegalStateException("database unavailable");
        StudentPointEventService.AttemptContext context =
                StudentPointEventService.AttemptContext.auto();
        when(eventService.claim(10L, context, CLOCK.instant())).thenReturn(processing);
        when(postingTransaction.post(10L, CLOCK.instant())).thenThrow(postingFailure);
        when(failureRecorder.recordFailure(10L, postingFailure, CLOCK.instant())).thenReturn(failed);

        assertSame(failed, processor.process(10L, context));
    }

    @Test
    void processingConflictShouldBeStable() {
        StudentPointEventService.AttemptContext context =
                StudentPointEventService.AttemptContext.auto();
        StudentPointOperationException conflict = new StudentPointOperationException(
                "POINT_EVENT_PROCESSING", HttpStatus.CONFLICT, "事件正在处理");
        when(eventService.claim(10L, context, CLOCK.instant())).thenThrow(conflict);

        StudentPointOperationException thrown = assertThrows(
                StudentPointOperationException.class,
                () -> processor.process(10L, context));

        assertEquals("POINT_EVENT_PROCESSING", thrown.getCode());
        verify(postingTransaction, never()).post(any(), any());
    }

    private StudentPointEvent event(Long id, PointEventStatus status) {
        StudentPointEvent event = new StudentPointEvent();
        event.setId(id);
        event.setStatus(status);
        event.setProcessingTriggerType(PointAttemptTriggerType.AUTO);
        return event;
    }
}
