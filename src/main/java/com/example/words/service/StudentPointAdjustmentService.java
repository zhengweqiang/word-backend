package com.example.words.service;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointAdjustmentRequestRepository;
import com.example.words.repository.StudentPointEventRepository;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentPointAdjustmentService {

    private static final String REQUEST_KEY_CONSTRAINT = "uk_student_point_adjustments_request_key";
    private static final int REQUEST_KEY_MAX_LENGTH = 64;
    private static final int REASON_MAX_LENGTH = 500;

    private final AppUserRepository userRepository;
    private final TeacherStudentService teacherStudentService;
    private final StudentPointAdjustmentTransaction adjustmentTransaction;
    private final StudentPointAdjustmentRequestRepository requestRepository;
    private final StudentPointEventRepository eventRepository;
    private final StudentPointEventProcessor eventProcessor;
    private final StudentPointAccountRepository accountRepository;

    public AdjustmentOutcome adjust(AppUser actor, AdjustmentCommand command) {
        NormalizedCommand normalized = validate(actor, command);
        WorkflowState workflow = requestRepository.findByRequestKey(normalized.requestKey())
                .map(request -> loadExisting(request, normalized, actor))
                .orElseGet(() -> createWorkflow(normalized, actor));
        return processOrReplay(workflow, actor, normalized.reason());
    }

    private WorkflowState createWorkflow(NormalizedCommand command, AppUser actor) {
        try {
            StudentPointAdjustmentTransaction.Workflow created = adjustmentTransaction.createWorkflow(
                    new StudentPointAdjustmentTransaction.CreateCommand(
                            command.requestKey(),
                            command.studentId(),
                            command.amount(),
                            command.reason(),
                            actor,
                            command.replacesAdjustmentRequestId()
                    )
            );
            return new WorkflowState(created.request(), created.event());
        } catch (DataIntegrityViolationException failure) {
            if (!isRequestKeyConstraint(failure)) {
                throw failure;
            }
            StudentPointAdjustmentRequest winner = requestRepository.findByRequestKey(command.requestKey())
                    .orElseThrow(() -> failure);
            return loadExisting(winner, command, actor);
        }
    }

    private WorkflowState loadExisting(
            StudentPointAdjustmentRequest request,
            NormalizedCommand command,
            AppUser actor
    ) {
        if (!Objects.equals(request.getStudentId(), command.studentId())
                || !Objects.equals(request.getAmount(), command.amount())
                || !Objects.equals(request.getReason(), command.reason())
                || !Objects.equals(request.getRequestedBy(), actor.getId())
                || !Objects.equals(request.getRequestedRole(), actor.getRole().name())
                || !Objects.equals(request.getReplacesRequestId(), command.replacesAdjustmentRequestId())) {
            throw error(
                    "IDEMPOTENCY_KEY_CONFLICT",
                    HttpStatus.CONFLICT,
                    "Adjustment request key is already used by a different payload"
            );
        }
        StudentPointEvent event = eventRepository.findBySourceTypeAndSourceId(
                        PointSourceType.MANUAL_ADJUSTMENT,
                        request.getId()
                )
                .orElseThrow(() -> error(
                        "MANUAL_ADJUSTMENT_STATE_INVALID",
                        HttpStatus.CONFLICT,
                        "Adjustment request has no matching event"
                ));
        if (!StudentPointManualAdjustmentIdentity.isValidEvent(event)
                || !StudentPointManualAdjustmentIdentity.matchesWorkflow(event, request)) {
            throw error(
                    "MANUAL_ADJUSTMENT_STATE_INVALID",
                    HttpStatus.CONFLICT,
                    "Adjustment request does not match its event"
            );
        }
        return new WorkflowState(request, event);
    }

    private AdjustmentOutcome processOrReplay(WorkflowState workflow, AppUser actor, String reason) {
        StudentPointEvent event = workflow.event();
        if (event.getStatus() == PointEventStatus.PENDING) {
            try {
                event = eventProcessor.process(
                        event.getId(),
                        StudentPointEventService.AttemptContext.manual(
                                actor.getId(), actor.getRole().name(), reason)
                );
            } catch (StudentPointOperationException conflict) {
                if (!isReplayRace(conflict)) {
                    throw conflict;
                }
                return currentOutcome(workflow.request().getId());
            }
        }
        return outcome(workflow.request(), event);
    }

    private AdjustmentOutcome currentOutcome(Long requestId) {
        StudentPointAdjustmentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> error(
                        "POINT_ADJUSTMENT_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Point adjustment request does not exist"
                ));
        StudentPointEvent event = eventRepository.findBySourceTypeAndSourceId(
                        PointSourceType.MANUAL_ADJUSTMENT,
                        requestId
                )
                .orElseThrow(() -> error(
                        "MANUAL_ADJUSTMENT_STATE_INVALID",
                        HttpStatus.CONFLICT,
                        "Adjustment request has no matching event"
                ));
        return outcome(request, event);
    }

    private AdjustmentOutcome outcome(StudentPointAdjustmentRequest request, StudentPointEvent event) {
        PointAdjustmentStatus status = switch (event.getStatus()) {
            case SUCCEEDED -> PointAdjustmentStatus.APPLIED;
            case FAILED -> PointAdjustmentStatus.FAILED;
            default -> request.getStatus();
        };
        Integer availableBalance = null;
        if (event.getStatus() == PointEventStatus.SUCCEEDED) {
            StudentPointAccount account = accountRepository.findByStudentId(request.getStudentId())
                    .orElseThrow(() -> error(
                            "POINT_ACCOUNT_NOT_FOUND",
                            HttpStatus.NOT_FOUND,
                            "Student point account does not exist"
                    ));
            availableBalance = account.getAvailablePoints();
        }
        return new AdjustmentOutcome(
                request.getId(),
                event.getId(),
                status,
                event.getTransactionId(),
                availableBalance
        );
    }

    private NormalizedCommand validate(AppUser actor, AdjustmentCommand command) {
        if (actor == null || actor.getId() == null || actor.getRole() == null) {
            throw error("POINT_ADJUSTMENT_FORBIDDEN", HttpStatus.FORBIDDEN, "Authenticated actor is required");
        }
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER) {
            throw error("POINT_ADJUSTMENT_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "Only teachers and administrators may adjust points");
        }
        if (command == null) {
            throw error("INVALID_POINT_ADJUSTMENT_REQUEST", HttpStatus.BAD_REQUEST,
                    "Adjustment command is required");
        }
        String requestKey = normalizeRequestKey(command.requestKey());
        if (command.studentId() == null || command.studentId() <= 0) {
            throw error("INVALID_STUDENT_ID", HttpStatus.BAD_REQUEST, "Student ID is invalid");
        }
        if (command.amount() == null || command.amount() == 0) {
            throw error("INVALID_POINT_AMOUNT", HttpStatus.BAD_REQUEST, "Point amount must not be zero");
        }
        String reason = normalizeReason(command.reason());
        if (command.replacesAdjustmentRequestId() != null && command.replacesAdjustmentRequestId() <= 0) {
            throw error("POINT_ADJUSTMENT_REPLACEMENT_INVALID", HttpStatus.BAD_REQUEST,
                    "Replacement request ID is invalid");
        }

        AppUser target = userRepository.findById(command.studentId())
                .orElseThrow(() -> error(
                        "POINT_STUDENT_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Target student does not exist"
                ));
        if (target.getRole() != UserRole.STUDENT) {
            throw error("POINT_TARGET_NOT_STUDENT", HttpStatus.BAD_REQUEST, "Target user is not a student");
        }
        if (actor.getRole() == UserRole.TEACHER
                && !teacherStudentService.isTeacherResponsibleForStudent(actor.getId(), command.studentId())) {
            throw error("POINT_STUDENT_NOT_MANAGED", HttpStatus.FORBIDDEN,
                    "Teacher is not responsible for this student");
        }
        return new NormalizedCommand(
                requestKey,
                command.studentId(),
                command.amount(),
                reason,
                command.replacesAdjustmentRequestId()
        );
    }

    private String normalizeRequestKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw error("POINT_ADJUSTMENT_REQUEST_KEY_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Adjustment request key is required");
        }
        String normalized = value.trim();
        if (normalized.length() > REQUEST_KEY_MAX_LENGTH) {
            throw error("POINT_ADJUSTMENT_REQUEST_KEY_TOO_LONG", HttpStatus.BAD_REQUEST,
                    "Adjustment request key must not exceed 64 characters");
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw error("POINT_ADJUSTMENT_REASON_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Adjustment reason is required");
        }
        String normalized = value.trim();
        if (normalized.length() > REASON_MAX_LENGTH) {
            throw error("POINT_ADJUSTMENT_REASON_TOO_LONG", HttpStatus.BAD_REQUEST,
                    "Adjustment reason must not exceed 500 characters");
        }
        return normalized;
    }

    private boolean isRequestKeyConstraint(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && REQUEST_KEY_CONSTRAINT.equalsIgnoreCase(constraintViolation.getConstraintName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(REQUEST_KEY_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isReplayRace(StudentPointOperationException failure) {
        return "POINT_EVENT_PROCESSING".equals(failure.getCode())
                || "POINT_EVENT_CANCELLED".equals(failure.getCode());
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }

    public record AdjustmentCommand(
            String requestKey,
            Long studentId,
            Integer amount,
            String reason,
            Long replacesAdjustmentRequestId
    ) {
    }

    public record AdjustmentOutcome(
            Long requestId,
            Long eventId,
            PointAdjustmentStatus status,
            Long transactionId,
            Integer availableBalance
    ) {
    }

    private record NormalizedCommand(
            String requestKey,
            Long studentId,
            Integer amount,
            String reason,
            Long replacesAdjustmentRequestId
    ) {
    }

    private record WorkflowState(
            StudentPointAdjustmentRequest request,
            StudentPointEvent event
    ) {
    }
}
