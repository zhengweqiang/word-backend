package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointRule;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointRuleRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        StudentPointEventService.class,
        StudentPointEventFactory.class,
        StudentPointEventCreationTransaction.class,
        StudentPointEventCreationTransactionIntegrationTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPointEventCreationTransactionIntegrationTest {

    @Autowired
    private StudentPointEventService eventService;

    @Autowired
    private StudentPointEventRepository eventRepository;

    @Autowired
    private StudentPointEventCreationTransaction creationTransaction;

    @Autowired
    private StudentPointRuleRepository ruleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        ruleRepository.deleteAll();
        ruleRepository.saveAndFlush(StudentPointRule.create(
                "STUDY_RECORD_CORRECT", "答对单词", PointSourceType.STUDY_RECORD, 1));
    }

    @Test
    void creationShouldCommitInRequiresNewWhenCallerTransactionRollsBack() {
        TransactionTemplate caller = new TransactionTemplate(transactionManager);
        caller.executeWithoutResult(status -> {
            StudentPointEvent created = eventService.create(request("record:101", 101L));
            assertNotNull(created.getId());
            status.setRollbackOnly();
        });

        StudentPointEvent persisted = eventRepository
                .findByIdempotencyKey("record:101:STUDY_RECORD_CORRECT")
                .orElseThrow();
        assertEquals(PointEventStatus.PENDING, persisted.getStatus());
        assertEquals(1, persisted.getPoints());
    }

    @Test
    void namedUniqueRecoveryShouldRemainUsableInsideCallerTransaction() {
        StudentPointEvent existing = eventService.create(request("record:202", 202L));
        StudentPointEventService coordinator = new StudentPointEventService(
                repositoryWithFirstIdempotencyLookupMissing(),
                creationTransaction,
                new StudentPointEventFactory(),
                java.time.Clock.systemUTC()
        );

        TransactionTemplate caller = new TransactionTemplate(transactionManager);
        StudentPointEvent recovered = caller.execute(status ->
                coordinator.create(request("record:202", 202L))
        );

        assertEquals(existing.getId(), recovered.getId());
        assertEquals(1, eventRepository.count());
    }

    private StudentPointEventRepository repositoryWithFirstIdempotencyLookupMissing() {
        AtomicInteger idempotencyLookups = new AtomicInteger();
        return (StudentPointEventRepository) Proxy.newProxyInstance(
                StudentPointEventRepository.class.getClassLoader(),
                new Class<?>[]{StudentPointEventRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByIdempotencyKey")
                            && idempotencyLookups.getAndIncrement() == 0) {
                        return Optional.empty();
                    }
                    try {
                        return method.invoke(eventRepository, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
    }

    private StudentPointEventService.CreateRequest request(String sourceKey, Long sourceId) {
        return new StudentPointEventService.CreateRequest(
                42L,
                sourceId,
                sourceKey,
                "STUDY_RECORD_CORRECT",
                new StudentPointEventService.Actor(9L, "SYSTEM"),
                "答对单词"
        );
    }
}
