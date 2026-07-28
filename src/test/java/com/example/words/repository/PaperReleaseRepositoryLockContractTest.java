package com.example.words.repository;

import java.lang.reflect.Method;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperReleaseRepositoryLockContractTest {

    @Test
    void correctionLookupUsesPessimisticWriteLock() throws Exception {
        Method method = PaperReleaseRepository.class.getMethod("findByIdForUpdate", Long.class);

        assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(Lock.class).value());
    }

    @Test
    void releaseAttemptLookupUsesPessimisticWriteLock() throws Exception {
        Method method = StudentPaperAttemptRepository.class.getMethod(
                "findByPaperReleaseIdForUpdate", Long.class);

        assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(Lock.class).value());
    }
}
