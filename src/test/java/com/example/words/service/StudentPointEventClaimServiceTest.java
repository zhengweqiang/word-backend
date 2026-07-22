package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventStatus;
import com.example.words.model.StudentPointEvent;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentPointEventClaimServiceTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-22T02:00:00Z");

    @Mock
    private StudentPointEventRepository eventRepository;

    @Mock
    private StudentPointEventCreationTransaction creationTransaction;

    private StudentPointEventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new StudentPointEventService(
                eventRepository,
                creationTransaction,
                new StudentPointEventFactory(),
                Clock.systemUTC());
    }

    @Test
    void manualClaimShouldWriteAuditMetadataInOneConditionalUpdate() {
        StudentPointEvent processing = event(8L, PointEventStatus.PROCESSING, 3);
        when(eventRepository.claimForProcessing(
                eq(8L), eq(PointAttemptTriggerType.MANUAL), eq(9L), eq("ADMIN"),
                eq("人工补发"), eq(LocalDateTime.of(2026, 7, 22, 2, 0)), eq(false), eq(3)))
                .thenReturn(1);
        when(eventRepository.findById(8L)).thenReturn(Optional.of(processing));

        StudentPointEvent result = eventService.claim(
                8L,
                StudentPointEventService.AttemptContext.manual(9L, " ADMIN ", " 人工补发 "),
                STARTED_AT
        );

        assertSame(processing, result);
        verify(eventRepository).claimForProcessing(
                8L, PointAttemptTriggerType.MANUAL, 9L, "ADMIN", "人工补发",
                LocalDateTime.of(2026, 7, 22, 2, 0), false, 3);
    }

    @Test
    void automaticClaimShouldRejectExhaustedEventButManualClaimCanExceedThree() {
        StudentPointEvent exhausted = event(8L, PointEventStatus.FAILED, 3);
        when(eventRepository.claimForProcessing(
                eq(8L), eq(PointAttemptTriggerType.AUTO), isNull(), isNull(), isNull(),
                any(), eq(true), eq(3)))
                .thenReturn(0);
        when(eventRepository.findById(8L)).thenReturn(Optional.of(exhausted));

        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> eventService.claim(
                        8L, StudentPointEventService.AttemptContext.auto(), STARTED_AT));
        assertEquals("POINT_EVENT_AUTO_RETRY_EXHAUSTED", failure.getCode());

        when(eventRepository.claimForProcessing(
                eq(8L), eq(PointAttemptTriggerType.MANUAL), eq(9L), eq("ADMIN"), eq("补发"),
                any(), eq(false), eq(3)))
                .thenReturn(1);
        exhausted.setStatus(PointEventStatus.PROCESSING);

        assertSame(exhausted, eventService.claim(
                8L,
                StudentPointEventService.AttemptContext.manual(9L, "ADMIN", "补发"),
                STARTED_AT
        ));
    }

    @Test
    void failedClaimShouldReturnSuccessIdempotentlyAndRejectProcessingOrCancelled() {
        when(eventRepository.claimForProcessing(
                eq(8L), eq(PointAttemptTriggerType.AUTO), isNull(), isNull(), isNull(),
                any(), anyBoolean(), anyInt()))
                .thenReturn(0);

        StudentPointEvent succeeded = event(8L, PointEventStatus.SUCCEEDED, 1);
        when(eventRepository.findById(8L)).thenReturn(Optional.of(succeeded));
        assertSame(succeeded, eventService.claim(
                8L, StudentPointEventService.AttemptContext.auto(), STARTED_AT));

        when(eventRepository.findById(8L))
                .thenReturn(Optional.of(event(8L, PointEventStatus.PROCESSING, 1)));
        assertCode("POINT_EVENT_PROCESSING", () -> eventService.claim(
                8L, StudentPointEventService.AttemptContext.auto(), STARTED_AT));

        when(eventRepository.findById(8L))
                .thenReturn(Optional.of(event(8L, PointEventStatus.CANCELLED, 1)));
        assertCode("POINT_EVENT_CANCELLED", () -> eventService.claim(
                8L, StudentPointEventService.AttemptContext.auto(), STARTED_AT));
    }

    private StudentPointEvent event(Long id, PointEventStatus status, int autoAttempts) {
        StudentPointEvent event = new StudentPointEvent();
        event.setId(id);
        event.setStatus(status);
        event.setAutoAttemptCount(autoAttempts);
        return event;
    }

    private void assertCode(String code, Runnable operation) {
        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class, operation::run);
        assertEquals(code, failure.getCode());
    }
}
