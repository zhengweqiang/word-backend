package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointTransaction;
import com.example.words.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentPointAdminService {

    private static final int REASON_MAX_LENGTH = 500;

    private final StudentPointEventProcessor processor;
    private final StudentPointAdminTransaction adminTransaction;
    private final StudentPointLedgerService ledgerService;

    public StudentPointEvent retryEvent(AppUser actor, Long eventId, String reason) {
        String normalizedReason = validate(actor, eventId, reason);
        return processor.process(
                eventId,
                StudentPointEventService.AttemptContext.manual(
                        actor.getId(),
                        actor.getRole().name(),
                        normalizedReason
                )
        );
    }

    public StudentPointEvent cancelEvent(AppUser actor, Long eventId, String reason) {
        return adminTransaction.cancelEvent(eventId, actor, validate(actor, eventId, reason));
    }

    public StudentPointTransaction reverseTransaction(AppUser actor, Long transactionId, String reason) {
        String normalizedReason = validate(actor, transactionId, reason);
        return ledgerService.reverse(
                transactionId,
                new StudentPointLedgerService.Actor(actor.getId(), actor.getRole().name()),
                normalizedReason
        );
    }

    private String validate(AppUser actor, Long targetId, String reason) {
        if (actor == null || actor.getId() == null || actor.getRole() != UserRole.ADMIN) {
            throw error("POINT_ADMIN_REQUIRED", HttpStatus.FORBIDDEN, "Administrator is required");
        }
        if (targetId == null || targetId <= 0) {
            throw error("POINT_ADMIN_TARGET_ID_INVALID", HttpStatus.BAD_REQUEST, "Target ID is invalid");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw error("POINT_ADMIN_REASON_REQUIRED", HttpStatus.BAD_REQUEST, "Reason is required");
        }
        String normalized = reason.trim();
        if (normalized.length() > REASON_MAX_LENGTH) {
            throw error("POINT_ADMIN_REASON_TOO_LONG", HttpStatus.BAD_REQUEST,
                    "Reason must not exceed 500 characters");
        }
        return normalized;
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }
}
