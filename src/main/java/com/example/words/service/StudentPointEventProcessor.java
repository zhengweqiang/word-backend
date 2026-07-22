package com.example.words.service;

import com.example.words.model.PointEventStatus;
import com.example.words.model.StudentPointEvent;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentPointEventProcessor {

    private final StudentPointEventService eventService;
    private final StudentPointPostingTransaction postingTransaction;
    private final StudentPointFailureRecorder failureRecorder;
    private final Clock clock;

    public StudentPointEvent process(
            Long eventId,
            StudentPointEventService.AttemptContext context
    ) {
        Instant startedAt = clock.instant();
        StudentPointEvent claimed = eventService.claim(eventId, context, startedAt);
        if (claimed.getStatus() == PointEventStatus.SUCCEEDED) {
            return claimed;
        }
        try {
            return postingTransaction.post(eventId, clock.instant());
        } catch (RuntimeException failure) {
            return failureRecorder.recordFailure(eventId, failure, clock.instant());
        }
    }
}
