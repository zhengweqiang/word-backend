package com.example.words.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class StudentPointReconciliationSchedulerTest {

    @Test
    void shouldRunAfterStartupAndOnSchedule() {
        StudentPointReconciliationService service = mock(StudentPointReconciliationService.class);
        StudentPointReconciliationScheduler scheduler = new StudentPointReconciliationScheduler(service);

        scheduler.reconcileAfterStartup();
        scheduler.reconcilePeriodically();

        verify(service, times(2)).reconcileMissingEvents();
    }
}
