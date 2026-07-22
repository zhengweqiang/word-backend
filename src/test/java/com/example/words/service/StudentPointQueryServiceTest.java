package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.StudentPointAccount;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentPointQueryServiceTest {

    @Mock
    private StudentPointAccountRepository accountRepository;

    @Mock
    private StudentPointTransactionRepository transactionRepository;

    @Mock
    private TeacherStudentService teacherStudentService;

    private StudentPointQueryService service;

    @BeforeEach
    void setUp() {
        service = new StudentPointQueryService(accountRepository, transactionRepository, teacherStudentService);
    }

    @Test
    void summaryUsesTrustedAccountAndTodayEarnedTotal() {
        StudentPointAccount account = StudentPointAccount.create(8L);
        account.setId(2L);
        account.setAvailablePoints(35);
        account.setLifetimeEarnedPoints(50);
        when(accountRepository.findByStudentId(8L)).thenReturn(Optional.of(account));
        when(transactionRepository.sumEarnedByStudentIdBetween(
                org.mockito.ArgumentMatchers.eq(8L), any(), any())).thenReturn(12L);

        var response = service.getSummary(8L);

        assertEquals(35, response.availablePoints());
        assertEquals(12L, response.todayEarnedPoints());
    }

    @Test
    void missingAccountReturnsStableErrorCode() {
        when(accountRepository.findByStudentId(8L)).thenReturn(Optional.empty());

        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class, () -> service.getSummary(8L));

        assertEquals("POINT_ACCOUNT_NOT_FOUND", failure.getCode());
    }

    @Test
    void unmanagedStudentIsForbiddenBeforeAccountQuery() {
        when(teacherStudentService.isTeacherResponsibleForStudent(5L, 8L)).thenReturn(false);

        StudentPointOperationException failure = assertThrows(
                StudentPointOperationException.class,
                () -> service.getManagedStudentSummary(5L, 8L));

        assertEquals("POINT_STUDENT_NOT_MANAGED", failure.getCode());
    }

    @Test
    void transactionPagesUseStableCreatedAtAndIdOrdering() {
        var pageable = service.page(0, 20);

        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                pageable.getSort().getOrderFor("createdAt").getDirection());
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC,
                pageable.getSort().getOrderFor("id").getDirection());
    }
}
