package com.example.words.service;

import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StudentPointRetryScheduler {

    private static final int PAGE_SIZE = 100;
    private static final int PROCESSING_TIMEOUT_MINUTES = 10;
    private static final String RESTART_ERROR = "PROCESSING_INTERRUPTED_BY_SERVER_RESTART";
    private static final String TIMEOUT_ERROR = "PROCESSING_TIMEOUT";

    private final StudentPointEventRepository eventRepository;
    private final StudentPointEventProcessor processor;
    private final StudentPointFailureRecorder failureRecorder;
    private final Clock clock;
    private final LocalDateTime startupCutoff;

    public StudentPointRetryScheduler(
            StudentPointEventRepository eventRepository,
            StudentPointEventProcessor processor,
            StudentPointFailureRecorder failureRecorder,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.processor = processor;
        this.failureRecorder = failureRecorder;
        this.clock = clock;
        this.startupCutoff = LocalDateTime.now(clock);
    }

    @Scheduled(fixedDelayString = "${student-points.retry-delay-ms:60000}")
    public void retryDueEvents() {
        LocalDateTime now = localNow();
        List<Long> eventIds = eventRepository.findDueEventIds(
                now,
                StudentPointProcessingPolicy.MAX_AUTO_ATTEMPTS,
                PageRequest.of(0, PAGE_SIZE)
        );
        for (Long eventId : eventIds) {
            try {
                processor.process(eventId, StudentPointEventService.AttemptContext.auto());
            } catch (RuntimeException failure) {
                log.warn("Automatic student point event retry failed for event {}", eventId, failure);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedProcessingEvents() {
        recoverAllProcessing(RESTART_ERROR, false);
    }

    @Scheduled(fixedDelayString = "${student-points.timeout-recovery-delay-ms:60000}")
    public void recoverTimedOutProcessingEvents() {
        recoverAllProcessing(TIMEOUT_ERROR, true);
    }

    private void recoverAllProcessing(String errorCode, boolean timedOutOnly) {
        long afterId = 0L;
        while (true) {
            List<Long> eventIds;
            if (timedOutOnly) {
                eventIds = eventRepository.findTimedOutProcessingEventIdsAfter(
                        localNow().minusMinutes(PROCESSING_TIMEOUT_MINUTES),
                        afterId,
                        PageRequest.of(0, PAGE_SIZE)
                );
            } else {
                eventIds = eventRepository.findInterruptedProcessingEventIdsAfter(
                        startupCutoff,
                        afterId,
                        PageRequest.of(0, PAGE_SIZE)
                );
            }
            if (eventIds.isEmpty()) {
                return;
            }
            for (Long eventId : eventIds) {
                try {
                    failureRecorder.recover(eventId, errorCode, clock.instant());
                } catch (RuntimeException failure) {
                    log.error("Student point PROCESSING recovery failed for event {}", eventId, failure);
                }
            }
            afterId = eventIds.get(eventIds.size() - 1);
            if (eventIds.size() < PAGE_SIZE) {
                return;
            }
        }
    }

    private LocalDateTime localNow() {
        return LocalDateTime.now(clock);
    }
}
