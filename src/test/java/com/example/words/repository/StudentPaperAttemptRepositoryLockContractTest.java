package com.example.words.repository;

import java.lang.reflect.Method;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudentPaperAttemptRepositoryLockContractTest {

    @Test
    void studentAttemptLookupUsesPessimisticWriteLock() throws Exception {
        Method method = StudentPaperAttemptRepository.class.getMethod(
                "findByIdAndStudentIdForUpdate", Long.class, Long.class);

        assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(Lock.class).value());
    }
}
