package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointEvent;
import com.example.words.repository.StudentPointEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        StudentPointEventService.class,
        StudentPointEventFactory.class,
        StudentPointEventCreationTransaction.class,
        StudentPointConcurrentClaimIntegrationTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPointConcurrentClaimIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-22T02:00:00Z");

    @Autowired
    private StudentPointEventService eventService;

    @Autowired
    private StudentPointEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Test
    void exactlyOneConcurrentClaimShouldSucceed() throws Exception {
        StudentPointEvent event = new StudentPointEvent();
        event.setStudentId(42L);
        event.setSourceType(PointSourceType.STUDY_RECORD);
        event.setSourceId(88L);
        event.setSourceKey("record:88");
        event.setRuleCode("STUDY_RECORD_CORRECT");
        event.setRuleName("答对单词");
        event.setPoints(1);
        event.setIdempotencyKey("concurrent-claim:88");
        event.setStatus(PointEventStatus.PENDING);
        event.setAutoAttemptCount(0);
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> claim(eventId, ready, start));
            Future<String> second = executor.submit(() -> claim(eventId, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "claim workers did not become ready");
            start.countDown();

            List<String> outcomes = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, outcomes.stream().filter("POINT_EVENT_PROCESSING"::equals).count());
            assertEquals(PointEventStatus.PROCESSING,
                    eventRepository.findById(eventId).orElseThrow().getStatus());
        } finally {
            executor.shutdownNow();
        }
    }

    private String claim(Long eventId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            eventService.claim(eventId, StudentPointEventService.AttemptContext.auto(), NOW);
            return "SUCCESS";
        } catch (StudentPointOperationException failure) {
            return failure.getCode();
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
