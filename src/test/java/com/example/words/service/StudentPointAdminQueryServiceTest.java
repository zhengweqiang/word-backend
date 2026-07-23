package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.words.model.AppUser;
import com.example.words.model.PointAccountStatus;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.PointTransactionType;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointTransaction;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointEventAttemptRepository;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointRuleAuditRepository;
import com.example.words.repository.StudentPointRuleRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class StudentPointAdminQueryServiceTest {

    @Mock
    private StudentPointAccountRepository accountRepository;

    @Mock
    private StudentPointTransactionRepository transactionRepository;

    @Mock
    private StudentPointEventRepository eventRepository;

    @Mock
    private StudentPointEventAttemptRepository attemptRepository;

    @Mock
    private StudentPointRuleRepository ruleRepository;

    @Mock
    private StudentPointRuleAuditRepository ruleAuditRepository;

    @Mock
    private AppUserRepository userRepository;

    private StudentPointAdminQueryService service;

    @BeforeEach
    void setUp() {
        service = new StudentPointAdminQueryService(
                accountRepository, transactionRepository, eventRepository, attemptRepository,
                ruleRepository, ruleAuditRepository, userRepository
        );
    }

    @Test
    void adminAccountRowsIncludeStudentUsername() {
        StudentPointAccount account = StudentPointAccount.create(42L);
        account.setId(10L);
        account.setStatus(PointAccountStatus.ACTIVE);
        AppUser student = student(42L, "student42", "小明");
        when(accountRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));
        when(userRepository.findAllById(List.of(42L))).thenReturn(List.of(student));

        var response = service.getAccounts(0, 20).getContent().get(0);

        assertEquals("student42", response.studentUsername());
        assertEquals("小明", response.studentName());
    }

    @Test
    void adminTransactionRowsIncludeStudentUsername() {
        StudentPointTransaction transaction = new StudentPointTransaction();
        transaction.setId(91L);
        transaction.setAccountId(10L);
        transaction.setStudentId(42L);
        transaction.setTransactionType(PointTransactionType.EARN);
        transaction.setAmount(5);
        transaction.setBalanceBefore(0);
        transaction.setBalanceAfter(5);
        transaction.setSourceType(PointSourceType.STUDY_RECORD);
        transaction.setSourceKey("study:13");
        when(transactionRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction)));
        when(userRepository.findAllById(List.of(42L))).thenReturn(List.of(student(42L, "student42", "小明")));

        var response = service.getTransactions(0, 20).getContent().get(0);

        assertEquals("student42", response.studentUsername());
        assertEquals("小明", response.studentName());
    }

    @Test
    void adminEventRowsIncludeStudentUsername() {
        StudentPointEvent event = new StudentPointEvent();
        event.setId(7L);
        event.setStudentId(42L);
        event.setSourceType(PointSourceType.STUDY_RECORD);
        event.setSourceKey("study:13");
        event.setRuleCode("STUDY_RECORD_COMPLETE");
        event.setRuleName("完成学习");
        event.setPoints(5);
        event.setStatus(PointEventStatus.FAILED);
        event.setAutoAttemptCount(3);
        when(eventRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(userRepository.findAllById(List.of(42L))).thenReturn(List.of(student(42L, "student42", "小明")));

        var response = service.getEvents(null, 0, 20).getContent().get(0);

        assertEquals("student42", response.studentUsername());
        assertEquals("小明", response.studentName());
    }

    private AppUser student(Long id, String username, String displayName) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }
}
