package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.model.PointAccountStatus;
import com.example.words.model.StudentPointAccount;
import com.example.words.repository.StudentPointAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class StudentPointAccountServiceTest {

    @Mock
    private StudentPointAccountRepository studentPointAccountRepository;

    private StudentPointAccountService studentPointAccountService;

    @BeforeEach
    void setUp() {
        studentPointAccountService = new StudentPointAccountService(studentPointAccountRepository);
    }

    @Test
    void createForStudentShouldSaveNewZeroBalanceAccount() {
        studentPointAccountService.createForStudent(42L);

        ArgumentCaptor<StudentPointAccount> accountCaptor = ArgumentCaptor.forClass(StudentPointAccount.class);
        verify(studentPointAccountRepository).save(accountCaptor.capture());
        StudentPointAccount savedAccount = accountCaptor.getValue();
        assertEquals(42L, savedAccount.getStudentId());
        assertEquals(0, savedAccount.getAvailablePoints());
        assertEquals(0, savedAccount.getFrozenPoints());
        assertEquals(0, savedAccount.getLifetimeEarnedPoints());
        assertEquals(0, savedAccount.getLifetimeSpentPoints());
        assertEquals(PointAccountStatus.ACTIVE, savedAccount.getStatus());
    }

    @Test
    void createForStudentShouldPropagateDuplicateAccountFailure() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate student account");
        when(studentPointAccountRepository.save(any(StudentPointAccount.class))).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> studentPointAccountService.createForStudent(42L)
        );

        assertSame(failure, thrown);
    }
}
