package com.example.words.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentPointReconciliationScheduler {

    private final StudentPointReconciliationService reconciliationService;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileAfterStartup() {
        reconciliationService.reconcileMissingEvents();
    }

    @Scheduled(fixedDelayString = "${student-points.reconciliation-delay-ms:60000}")
    public void reconcilePeriodically() {
        reconciliationService.reconcileMissingEvents();
    }
}
