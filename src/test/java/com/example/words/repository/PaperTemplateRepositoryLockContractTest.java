package com.example.words.repository;

import java.lang.reflect.Method;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaperTemplateRepositoryLockContractTest {

    @Test
    void findByIdForUpdateUsesPessimisticWriteLock() throws NoSuchMethodException {
        Method method = PaperTemplateRepository.class.getMethod("findByIdForUpdate", Long.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
