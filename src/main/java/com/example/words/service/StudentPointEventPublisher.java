package com.example.words.service;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
public class StudentPointEventPublisher {

    private final StudentPointEventService eventService;
    private final TaskExecutor taskExecutor;

    public StudentPointEventPublisher(
            StudentPointEventService eventService,
            @Qualifier("studentPointEventTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.eventService = eventService;
        this.taskExecutor = taskExecutor;
    }

    public void publishAfterCommit(PublishRequest request) {
        if (!isValid(request)) {
            log.error("Invalid student point publish request");
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                log.error(
                        "Cannot defer student point event because transaction synchronization is inactive: "
                                + "sourceId={}, sourceKey={}, ruleCode={}",
                        request.sourceId(),
                        request.sourceKey(),
                        request.ruleCode()
                );
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitEventCreation(request);
                }
            });
            return;
        }

        createEvent(request);
    }

    private void submitEventCreation(PublishRequest request) {
        try {
            taskExecutor.execute(() -> createEvent(request));
        } catch (RejectedExecutionException exception) {
            log.error(
                    "Student point event publication queue rejected request: "
                            + "sourceId={}, sourceKey={}, ruleCode={}",
                    request.sourceId(),
                    request.sourceKey(),
                    request.ruleCode(),
                    exception
            );
        }
    }

    private void createEvent(PublishRequest request) {
        try {
            eventService.create(new StudentPointEventService.CreateRequest(
                    request.studentId(),
                    request.sourceId(),
                    request.sourceKey(),
                    request.ruleCode(),
                    null,
                    null
            ));
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to create student point event: sourceId={}, sourceKey={}, ruleCode={}",
                    request.sourceId(),
                    request.sourceKey(),
                    request.ruleCode(),
                    exception
            );
        }
    }

    private boolean isValid(PublishRequest request) {
        return request != null
                && request.studentId() != null
                && request.studentId() > 0
                && request.sourceId() != null
                && request.sourceId() > 0
                && request.sourceKey() != null
                && !request.sourceKey().isBlank()
                && request.ruleCode() != null
                && !request.ruleCode().isBlank();
    }

    public record PublishRequest(
            Long studentId,
            Long sourceId,
            String sourceKey,
            String ruleCode
    ) {
    }
}
