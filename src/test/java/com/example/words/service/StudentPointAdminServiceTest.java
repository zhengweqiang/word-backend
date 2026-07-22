package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointAttemptTriggerType;
import com.example.words.model.PointEventStatus;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointTransaction;
import com.example.words.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentPointAdminServiceTest {

    @Mock
    private StudentPointEventProcessor processor;

    @Mock
    private StudentPointAdminTransaction adminTransaction;

    @Mock
    private StudentPointLedgerService ledgerService;

    private StudentPointAdminService service;

    @BeforeEach
    void setUp() {
        service = new StudentPointAdminService(processor, adminTransaction, ledgerService);
    }

    @Test
    void retryUsesTrustedAdminAuditAndAllowsEventAfterThreeAutoFailures() {
        AppUser admin = actor(9L, UserRole.ADMIN);
        StudentPointEvent failed = new StudentPointEvent();
        failed.setId(77L);
        failed.setStatus(PointEventStatus.FAILED);
        failed.setAutoAttemptCount(3);
        when(processor.process(any(), any())).thenReturn(failed);

        service.retryEvent(admin, 77L, " 人工补发 ");

        ArgumentCaptor<StudentPointEventService.AttemptContext> context =
                ArgumentCaptor.forClass(StudentPointEventService.AttemptContext.class);
        verify(processor).process(org.mockito.ArgumentMatchers.eq(77L), context.capture());
        assertEquals(PointAttemptTriggerType.MANUAL, context.getValue().triggerType());
        assertEquals(9L, context.getValue().operatorId());
        assertEquals("ADMIN", context.getValue().operatorRole());
        assertEquals("人工补发", context.getValue().reason());
    }

    @Test
    void retryBeforeThreeAutoFailuresStillUsesManualAdminContext() {
        AppUser admin = actor(9L, UserRole.ADMIN);
        StudentPointEvent failed = new StudentPointEvent();
        failed.setId(78L);
        failed.setStatus(PointEventStatus.FAILED);
        failed.setAutoAttemptCount(1);
        when(processor.process(any(), any())).thenReturn(failed);

        service.retryEvent(admin, 78L, "retry before auto exhaustion");

        ArgumentCaptor<StudentPointEventService.AttemptContext> context =
                ArgumentCaptor.forClass(StudentPointEventService.AttemptContext.class);
        verify(processor).process(org.mockito.ArgumentMatchers.eq(78L), context.capture());
        assertEquals(PointAttemptTriggerType.MANUAL, context.getValue().triggerType());
        assertEquals(9L, context.getValue().operatorId());
        assertEquals("ADMIN", context.getValue().operatorRole());
        assertEquals("retry before auto exhaustion", context.getValue().reason());
    }

    @Test
    void cancelDelegatesTrustedAdminAudit() {
        AppUser admin = actor(9L, UserRole.ADMIN);
        StudentPointEvent cancelled = new StudentPointEvent();
        cancelled.setId(77L);
        cancelled.setStatus(PointEventStatus.CANCELLED);
        when(adminTransaction.cancelEvent(77L, admin, "数据异常")).thenReturn(cancelled);

        assertEquals(cancelled, service.cancelEvent(admin, 77L, " 数据异常 "));
    }

    @Test
    void reverseDelegatesTrustedAdminActorAndReason() {
        AppUser admin = actor(9L, UserRole.ADMIN);
        StudentPointTransaction reversal = new StudentPointTransaction();
        reversal.setId(88L);
        when(ledgerService.reverse(any(), any(), any())).thenReturn(reversal);

        assertEquals(reversal, service.reverseTransaction(admin, 77L, " 录入错误 "));

        ArgumentCaptor<StudentPointLedgerService.Actor> actor =
                ArgumentCaptor.forClass(StudentPointLedgerService.Actor.class);
        verify(ledgerService).reverse(org.mockito.ArgumentMatchers.eq(77L), actor.capture(),
                org.mockito.ArgumentMatchers.eq("录入错误"));
        assertEquals(9L, actor.getValue().operatorId());
        assertEquals("ADMIN", actor.getValue().operatorRole());
    }

    @Test
    void nonAdminIsForbiddenForAllOperations() {
        AppUser teacher = actor(7L, UserRole.TEACHER);
        assertCode("POINT_ADMIN_REQUIRED", () -> service.retryEvent(teacher, 1L, "重试"));
        assertCode("POINT_ADMIN_REQUIRED", () -> service.cancelEvent(teacher, 1L, "取消"));
        assertCode("POINT_ADMIN_REQUIRED", () -> service.reverseTransaction(teacher, 1L, "冲正"));
    }

    @Test
    void reasonMustBeNonblankAndBounded() {
        AppUser admin = actor(9L, UserRole.ADMIN);
        assertCode("POINT_ADMIN_REASON_REQUIRED", () -> service.retryEvent(admin, 1L, "  "));
        assertCode("POINT_ADMIN_REASON_TOO_LONG", () -> service.cancelEvent(admin, 1L, "x".repeat(501)));
    }

    private AppUser actor(Long id, UserRole role) {
        AppUser actor = new AppUser();
        actor.setId(id);
        actor.setRole(role);
        return actor;
    }

    private void assertCode(String code, Runnable operation) {
        StudentPointOperationException failure = assertThrows(StudentPointOperationException.class, operation::run);
        assertEquals(code, failure.getCode());
    }
}
