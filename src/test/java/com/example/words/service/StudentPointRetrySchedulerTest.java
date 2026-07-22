package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.config.StudentPointSchedulingConfig;
import com.example.words.model.StudentPointEvent;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class StudentPointRetrySchedulerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-22T02:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

    @Mock
    private StudentPointEventRepository eventRepository;

    @Mock
    private StudentPointEventProcessor processor;

    @Mock
    private StudentPointFailureRecorder failureRecorder;

    private StudentPointRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StudentPointRetryScheduler(eventRepository, processor, failureRecorder, CLOCK);
    }

    @Test
    void retryDueEventsShouldIsolatePerEventFailures() {
        when(eventRepository.findDueEventIds(NOW, 3, PageRequest.of(0, 100)))
                .thenReturn(List.of(1L, 2L, 3L));
        doAnswer(invocation -> {
            if (invocation.getArgument(0, Long.class).equals(2L)) {
                throw new IllegalStateException("one event failed");
            }
            return new StudentPointEvent();
        }).when(processor).process(
                anyLong(), eq(StudentPointEventService.AttemptContext.auto()));

        scheduler.retryDueEvents();

        verify(processor).process(1L, StudentPointEventService.AttemptContext.auto());
        verify(processor).process(2L, StudentPointEventService.AttemptContext.auto());
        verify(processor).process(3L, StudentPointEventService.AttemptContext.auto());
    }

    @Test
    void startupRecoveryShouldUseConstructionCutoffAndIgnoreEventsClaimedAfterIt() {
        when(eventRepository.findInterruptedProcessingEventIdsAfter(
                NOW, 0L, PageRequest.of(0, 100)))
                .thenReturn(List.of(4L));

        scheduler.recoverInterruptedProcessingEvents();

        verify(failureRecorder).recover(
                4L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
        verify(failureRecorder, never()).recover(
                5L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
    }

    @Test
    void startupRecoveryShouldContinueAfterFullPageUsingLastIdCursor() {
        List<Long> firstPage = new ArrayList<>();
        for (long id = 1; id <= 100; id++) {
            firstPage.add(id);
        }
        when(eventRepository.findInterruptedProcessingEventIdsAfter(
                NOW, 0L, PageRequest.of(0, 100)))
                .thenReturn(firstPage);
        when(eventRepository.findInterruptedProcessingEventIdsAfter(
                NOW, 100L, PageRequest.of(0, 100)))
                .thenReturn(List.of(101L, 102L));

        scheduler.recoverInterruptedProcessingEvents();

        verify(failureRecorder).recover(
                1L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
        verify(failureRecorder).recover(
                100L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
        verify(failureRecorder).recover(
                101L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
        verify(failureRecorder).recover(
                102L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
    }

    @Test
    void startupRecoveryShouldContinueAfterRecorderException() {
        when(eventRepository.findInterruptedProcessingEventIdsAfter(
                NOW, 0L, PageRequest.of(0, 100)))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("corrupt row"))
                .when(failureRecorder)
                .recover(2L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());

        scheduler.recoverInterruptedProcessingEvents();

        verify(failureRecorder).recover(
                1L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
        verify(failureRecorder).recover(
                2L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
        verify(failureRecorder).recover(
                3L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
    }

    @Test
    void timeoutRecoveryShouldRetryNullStartEventAfterStartupRecoveryFailed() {
        when(eventRepository.findInterruptedProcessingEventIdsAfter(
                NOW, 0L, PageRequest.of(0, 100)))
                .thenReturn(List.of(7L));
        doThrow(new IllegalStateException("temporary recovery failure"))
                .when(failureRecorder)
                .recover(7L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
        when(eventRepository.findTimedOutProcessingEventIdsAfter(
                NOW.minusMinutes(10), 0L, PageRequest.of(0, 100)))
                .thenReturn(List.of(7L));

        scheduler.recoverInterruptedProcessingEvents();
        scheduler.recoverTimedOutProcessingEvents();

        verify(failureRecorder).recover(
                7L, "PROCESSING_INTERRUPTED_BY_SERVER_RESTART", CLOCK.instant());
        verify(failureRecorder).recover(7L, "PROCESSING_TIMEOUT", CLOCK.instant());
    }

    @Test
    void timeoutRecoveryShouldUseTenMinuteCutoffAndExactCode() {
        when(eventRepository.findTimedOutProcessingEventIdsAfter(
                NOW.minusMinutes(10), 0L, PageRequest.of(0, 100)))
                .thenReturn(List.of(6L));

        scheduler.recoverTimedOutProcessingEvents();

        verify(failureRecorder).recover(6L, "PROCESSING_TIMEOUT", CLOCK.instant());
    }

    @Test
    void productionClockShouldUseApplicationDefaultTimezone() {
        Clock productionClock = new StudentPointSchedulingConfig().studentPointClock();

        assertEquals(ZoneId.systemDefault(), productionClock.getZone());
    }
}
