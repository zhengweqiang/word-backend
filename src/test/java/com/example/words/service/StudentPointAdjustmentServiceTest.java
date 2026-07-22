package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class StudentPointAdjustmentServiceTest {

    private static final String REQUEST_KEY = "eac78a2e-2860-43a6-84cc-3bbdbe0a5f08";

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private TeacherStudentService teacherStudentService;
    @Mock
    private StudentPointAdjustmentTransaction adjustmentTransaction;
    @Mock
    private StudentPointAdjustmentRequestRepository requestRepository;
    @Mock
    private StudentPointEventRepository eventRepository;
    @Mock
    private StudentPointEventProcessor eventProcessor;
    @Mock
    private StudentPointAccountRepository accountRepository;

    private StudentPointAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new StudentPointAdjustmentService(
                userRepository,
                teacherStudentService,
                adjustmentTransaction,
                requestRepository,
                eventRepository,
                eventProcessor,
                accountRepository
        );
    }

    @Test
    void managedTeacherCreatesAtomicWorkflowAndGetsAppliedOutcome() {
        AppUser teacher = user(7L, UserRole.TEACHER);
        prepareStudent(42L);
        when(teacherStudentService.isTeacherResponsibleForStudent(7L, 42L)).thenReturn(true);
        StudentPointAdjustmentRequest request = adjustment(
                101L, REQUEST_KEY, 42L, 25, "class reward", teacher, null, PointAdjustmentStatus.PENDING);
        StudentPointEvent pending = event(201L, request, PointEventStatus.PENDING, null);
        when(requestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
        when(adjustmentTransaction.createWorkflow(any()))
                .thenReturn(new StudentPointAdjustmentTransaction.Workflow(request, pending));
        when(eventProcessor.process(any(), any()))
                .thenReturn(event(201L, request, PointEventStatus.SUCCEEDED, 301L));
        when(accountRepository.findByStudentId(42L)).thenReturn(Optional.of(account(42L, 125)));

        StudentPointAdjustmentService.AdjustmentOutcome outcome = service.adjust(
                teacher, command(REQUEST_KEY, 42L, 25, " class reward ", null));

        assertEquals(101L, outcome.requestId());
        assertEquals(201L, outcome.eventId());
        assertEquals(PointAdjustmentStatus.APPLIED, outcome.status());
        assertEquals(301L, outcome.transactionId());
        assertEquals(125, outcome.availableBalance());
        ArgumentCaptor<StudentPointAdjustmentTransaction.CreateCommand> created =
                ArgumentCaptor.forClass(StudentPointAdjustmentTransaction.CreateCommand.class);
        verify(adjustmentTransaction).createWorkflow(created.capture());
        assertEquals("class reward", created.getValue().reason());
        assertEquals(REQUEST_KEY, created.getValue().requestKey());
    }

    @Test
    void sameKeyAndExactPayloadReturnsSucceededWorkflowWithoutReprocessing() {
        AppUser admin = user(1L, UserRole.ADMIN);
        prepareStudent(42L);
        StudentPointAdjustmentRequest request = adjustment(
                101L, REQUEST_KEY, 42L, 9, "reward", admin, null, PointAdjustmentStatus.APPLIED);
        StudentPointEvent succeeded = event(201L, request, PointEventStatus.SUCCEEDED, 301L);
        when(requestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.of(request));
        when(eventRepository.findBySourceTypeAndSourceId(PointSourceType.MANUAL_ADJUSTMENT, 101L))
                .thenReturn(Optional.of(succeeded));
        when(accountRepository.findByStudentId(42L)).thenReturn(Optional.of(account(42L, 19)));

        StudentPointAdjustmentService.AdjustmentOutcome outcome = service.adjust(
                admin, command(REQUEST_KEY, 42L, 9, " reward ", null));

        assertEquals(PointAdjustmentStatus.APPLIED, outcome.status());
        assertEquals(301L, outcome.transactionId());
        assertEquals(19, outcome.availableBalance());
        verify(adjustmentTransaction, never()).createWorkflow(any());
        verify(eventProcessor, never()).process(any(), any());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        AppUser admin = user(1L, UserRole.ADMIN);
        prepareStudent(42L);
        StudentPointAdjustmentRequest request = adjustment(
                101L, REQUEST_KEY, 42L, 9, "reward", admin, null, PointAdjustmentStatus.PENDING);
        when(requestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.of(request));

        assertCode("IDEMPOTENCY_KEY_CONFLICT", () -> service.adjust(
                admin, command(REQUEST_KEY, 42L, 10, "reward", null)));
        verify(eventRepository, never()).findBySourceTypeAndSourceId(any(), any());
        verify(adjustmentTransaction, never()).createWorkflow(any());
    }

    @Test
    void committedPendingReplayProcessesOnce() {
        AppUser admin = user(1L, UserRole.ADMIN);
        prepareStudent(42L);
        StudentPointAdjustmentRequest request = adjustment(
                101L, REQUEST_KEY, 42L, 9, "reward", admin, null, PointAdjustmentStatus.PENDING);
        StudentPointEvent pending = event(201L, request, PointEventStatus.PENDING, null);
        when(requestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.of(request));
        when(eventRepository.findBySourceTypeAndSourceId(PointSourceType.MANUAL_ADJUSTMENT, 101L))
                .thenReturn(Optional.of(pending));
        when(eventProcessor.process(any(), any()))
                .thenReturn(event(201L, request, PointEventStatus.FAILED, null));

        StudentPointAdjustmentService.AdjustmentOutcome outcome = service.adjust(
                admin, command(REQUEST_KEY, 42L, 9, "reward", null));

        assertEquals(PointAdjustmentStatus.FAILED, outcome.status());
        verify(eventProcessor).process(any(), any());
        verify(adjustmentTransaction, never()).createWorkflow(any());
    }

    @Test
    void failedProcessingAndCancelledWorkflowsReplayWithoutProcessing() {
        AppUser admin = user(1L, UserRole.ADMIN);
        prepareStudent(42L);
        StudentPointAdjustmentRequest failedRequest = adjustment(
                101L, REQUEST_KEY, 42L, 9, "reward", admin, null, PointAdjustmentStatus.FAILED);
        when(requestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.of(failedRequest));
        when(eventRepository.findBySourceTypeAndSourceId(PointSourceType.MANUAL_ADJUSTMENT, 101L))
                .thenReturn(Optional.of(event(201L, failedRequest, PointEventStatus.FAILED, null)));

        StudentPointAdjustmentService.AdjustmentOutcome outcome = service.adjust(
                admin, command(REQUEST_KEY, 42L, 9, "reward", null));

        assertEquals(PointAdjustmentStatus.FAILED, outcome.status());
        assertNull(outcome.transactionId());
        verify(eventProcessor, never()).process(any(), any());
    }

    @Test
    void replacedRequestReplaysCancelledOutcomeDespiteBoundedAuditReason() {
        AppUser admin = user(1L, UserRole.ADMIN);
        prepareStudent(42L);
        StudentPointAdjustmentRequest replaced = adjustment(
                101L, REQUEST_KEY, 42L, 9, "reward", admin, null, PointAdjustmentStatus.REJECTED);
        replaced.setReplacedByRequestId(102L);
        StudentPointEvent cancelled = event(201L, replaced, PointEventStatus.CANCELLED, null);
        cancelled.setReason("Replaced by adjustment 102 by ADMIN#1: corrected");
        when(requestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.of(replaced));
        when(eventRepository.findBySourceTypeAndSourceId(PointSourceType.MANUAL_ADJUSTMENT, 101L))
                .thenReturn(Optional.of(cancelled));

        StudentPointAdjustmentService.AdjustmentOutcome outcome = service.adjust(
                admin, command(REQUEST_KEY, 42L, 9, "reward", null));

        assertEquals(PointAdjustmentStatus.REJECTED, outcome.status());
        verify(eventProcessor, never()).process(any(), any());
    }

    @Test
    void processingWorkflowReturnsCurrentPendingOutcomeWithoutReprocessing() {
        AppUser admin = user(1L, UserRole.ADMIN);
        prepareStudent(42L);
        StudentPointAdjustmentRequest request = adjustment(
                101L, REQUEST_KEY, 42L, 9, "reward", admin, null, PointAdjustmentStatus.PENDING);
        when(requestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.of(request));
        when(eventRepository.findBySourceTypeAndSourceId(PointSourceType.MANUAL_ADJUSTMENT, 101L))
                .thenReturn(Optional.of(event(201L, request, PointEventStatus.PROCESSING, null)));

        StudentPointAdjustmentService.AdjustmentOutcome outcome = service.adjust(
                admin, command(REQUEST_KEY, 42L, 9, "reward", null));

        assertEquals(PointAdjustmentStatus.PENDING, outcome.status());
        verify(eventProcessor, never()).process(any(), any());
    }

    @Test
    void namedRequestKeyRaceLoadsAndReturnsMatchingWinnerOutsideFailedTransaction() {
        AppUser admin = user(1L, UserRole.ADMIN);
        prepareStudent(42L);
        StudentPointAdjustmentRequest winner = adjustment(
                101L, REQUEST_KEY, 42L, 9, "reward", admin, null, PointAdjustmentStatus.FAILED);
        when(requestRepository.findByRequestKey(REQUEST_KEY))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(adjustmentTransaction.createWorkflow(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key violates constraint uk_student_point_adjustments_request_key"));
        when(eventRepository.findBySourceTypeAndSourceId(PointSourceType.MANUAL_ADJUSTMENT, 101L))
                .thenReturn(Optional.of(event(201L, winner, PointEventStatus.FAILED, null)));

        StudentPointAdjustmentService.AdjustmentOutcome outcome = service.adjust(
                admin, command(REQUEST_KEY, 42L, 9, "reward", null));

        assertEquals(101L, outcome.requestId());
        assertEquals(PointAdjustmentStatus.FAILED, outcome.status());
        verify(requestRepository, org.mockito.Mockito.times(2)).findByRequestKey(REQUEST_KEY);
        verify(eventProcessor, never()).process(any(), any());
    }

    @Test
    void validatesRequestKeyAmountAndReason() {
        AppUser admin = user(1L, UserRole.ADMIN);
        assertCode("POINT_ADJUSTMENT_REQUEST_KEY_REQUIRED", () -> service.adjust(
                admin, command("  ", 42L, 1, "reward", null)));
        assertCode("POINT_ADJUSTMENT_REQUEST_KEY_TOO_LONG", () -> service.adjust(
                admin, command("x".repeat(65), 42L, 1, "reward", null)));
        assertCode("INVALID_POINT_AMOUNT", () -> service.adjust(
                admin, command(REQUEST_KEY, 42L, 0, "reward", null)));
        assertCode("POINT_ADJUSTMENT_REASON_REQUIRED", () -> service.adjust(
                admin, command(REQUEST_KEY, 42L, 1, "  ", null)));
        assertCode("POINT_ADJUSTMENT_REASON_TOO_LONG", () -> service.adjust(
                admin, command(REQUEST_KEY, 42L, 1, "x".repeat(501), null)));
    }

    @Test
    void authorizationAndTargetRulesRemainEnforced() {
        assertCode("POINT_ADJUSTMENT_FORBIDDEN", () -> service.adjust(
                user(42L, UserRole.STUDENT), command(REQUEST_KEY, 42L, 10, "reward", null)));

        AppUser teacher = user(7L, UserRole.TEACHER);
        prepareStudent(42L);
        when(teacherStudentService.isTeacherResponsibleForStudent(7L, 42L)).thenReturn(false);
        assertCode("POINT_STUDENT_NOT_MANAGED", () -> service.adjust(
                teacher, command(REQUEST_KEY, 42L, 10, "reward", null)));
    }

    @Test
    void amountRemainsUnlimited() {
        AppUser admin = user(1L, UserRole.ADMIN);
        prepareStudent(42L);
        when(requestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
        StudentPointAdjustmentRequest request = adjustment(
                101L, REQUEST_KEY, 42L, 2_000_000_000, "unlimited", admin, null,
                PointAdjustmentStatus.PENDING);
        when(adjustmentTransaction.createWorkflow(any())).thenReturn(
                new StudentPointAdjustmentTransaction.Workflow(
                        request, event(201L, request, PointEventStatus.PENDING, null)));
        when(eventProcessor.process(any(), any()))
                .thenReturn(event(201L, request, PointEventStatus.FAILED, null));

        assertEquals(PointAdjustmentStatus.FAILED, service.adjust(
                admin, command(REQUEST_KEY, 42L, 2_000_000_000, "unlimited", null)).status());
    }

    private void prepareStudent(Long id) {
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, UserRole.STUDENT)));
    }

    private StudentPointAdjustmentService.AdjustmentCommand command(
            String requestKey, Long studentId, int amount, String reason, Long replacesRequestId) {
        return new StudentPointAdjustmentService.AdjustmentCommand(
                requestKey, studentId, amount, reason, replacesRequestId);
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private StudentPointAdjustmentRequest adjustment(
            Long id,
            String requestKey,
            Long studentId,
            int amount,
            String reason,
            AppUser actor,
            Long replacesRequestId,
            PointAdjustmentStatus status
    ) {
        StudentPointAdjustmentRequest request = StudentPointAdjustmentRequest.create(
                requestKey, studentId, amount, reason, actor.getId(), actor.getRole().name(), replacesRequestId);
        request.setId(id);
        request.setStatus(status);
        return request;
    }

    private StudentPointEvent event(
            Long id,
            StudentPointAdjustmentRequest request,
            PointEventStatus status,
            Long transactionId
    ) {
        StudentPointEvent event = new StudentPointEvent();
        event.setId(id);
        event.setStudentId(request.getStudentId());
        event.setSourceType(PointSourceType.MANUAL_ADJUSTMENT);
        event.setSourceId(request.getId());
        event.setSourceKey("manual-adjustment:" + request.getId());
        event.setRuleCode("MANUAL_ADJUSTMENT");
        event.setPoints(request.getAmount());
        event.setIdempotencyKey(event.getSourceKey() + ":MANUAL_ADJUSTMENT");
        event.setOperatorId(request.getRequestedBy());
        event.setOperatorRole(request.getRequestedRole());
        event.setReason(request.getReason());
        event.setStatus(status);
        event.setTransactionId(transactionId);
        return event;
    }

    private StudentPointAccount account(Long studentId, int availablePoints) {
        StudentPointAccount account = StudentPointAccount.create(studentId);
        account.setAvailablePoints(availablePoints);
        return account;
    }

    private void assertCode(String code, Runnable operation) {
        StudentPointOperationException failure = assertThrows(StudentPointOperationException.class, operation::run);
        assertEquals(code, failure.getCode());
    }
}
