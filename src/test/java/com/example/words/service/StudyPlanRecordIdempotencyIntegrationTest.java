package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

import com.example.words.dto.RecordStudyRequest;
import com.example.words.model.AppUser;
import com.example.words.model.AttentionState;
import com.example.words.model.DictionaryWord;
import com.example.words.model.PointSourceType;
import com.example.words.model.ReviewMode;
import com.example.words.model.StudentPointRule;
import com.example.words.model.StudentStudyPlan;
import com.example.words.model.StudyActionType;
import com.example.words.model.StudyDayTask;
import com.example.words.model.StudyDayTaskItem;
import com.example.words.model.StudyDayTaskStatus;
import com.example.words.model.StudyPlan;
import com.example.words.model.StudyPlanStatus;
import com.example.words.model.StudyRecord;
import com.example.words.model.StudyRecordResult;
import com.example.words.model.StudyTaskType;
import com.example.words.model.StudyWordProgress;
import com.example.words.model.UserRole;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.StudentAttentionDailyStatRepository;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointRuleRepository;
import com.example.words.repository.StudentStudyPlanRepository;
import com.example.words.repository.StudyDayTaskItemRepository;
import com.example.words.repository.StudyDayTaskRepository;
import com.example.words.repository.StudyPlanRepository;
import com.example.words.repository.StudyRecordRepository;
import com.example.words.repository.StudyWordProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        StudyPlanService.class,
        StudentPointEventPublisher.class,
        StudentPointEventService.class,
        StudentPointEventCreationTransaction.class,
        StudentPointEventFactory.class,
        StudentPointReconciliationService.class,
        StudyPlanRecordIdempotencyIntegrationTest.Configuration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudyPlanRecordIdempotencyIntegrationTest {

    @Autowired
    private StudyPlanService studyPlanService;

    @Autowired
    private StudyPlanRepository studyPlanRepository;

    @Autowired
    private StudentStudyPlanRepository studentStudyPlanRepository;

    @Autowired
    private StudyDayTaskRepository studyDayTaskRepository;

    @SpyBean
    private StudyDayTaskItemRepository studyDayTaskItemRepository;

    @Autowired
    private StudyWordProgressRepository studyWordProgressRepository;

    @Autowired
    private StudyRecordRepository studyRecordRepository;

    @Autowired
    private StudentAttentionDailyStatRepository attentionRepository;

    @Autowired
    private DictionaryWordRepository dictionaryWordRepository;

    @Autowired
    private StudentPointRuleRepository ruleRepository;

    @Autowired
    private StudentPointEventRepository eventRepository;

    @Autowired
    private StudentPointReconciliationService reconciliationService;

    @Autowired
    private ControllableTaskExecutor pointExecutor;

    @MockBean
    private DictionaryService dictionaryService;

    @MockBean
    private DictionaryAssignmentService dictionaryAssignmentService;

    @MockBean
    private AccessControlService accessControlService;

    @MockBean
    private UserService userService;

    @MockBean
    private StudentWordMemoryService studentWordMemoryService;

    private Long studentStudyPlanId;
    private Long taskId;

    @BeforeEach
    void setUp() {
        pointExecutor.clear();
        eventRepository.deleteAll();
        ruleRepository.deleteAll();
        attentionRepository.deleteAll();
        studyRecordRepository.deleteAll();
        studyWordProgressRepository.deleteAll();
        studyDayTaskItemRepository.deleteAll();
        studyDayTaskRepository.deleteAll();
        studentStudyPlanRepository.deleteAll();
        studyPlanRepository.deleteAll();
        dictionaryWordRepository.deleteAll();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        StudyPlan plan = new StudyPlan();
        plan.setName("Integration plan");
        plan.setTeacherId(7L);
        plan.setDictionaryId(10L);
        plan.setStartDate(today.minusDays(1));
        plan.setTimezone("Asia/Shanghai");
        plan.setDailyNewCount(1);
        plan.setDailyReviewLimit(1);
        plan.setReviewMode(ReviewMode.EBBINGHAUS);
        plan.setReviewIntervalsJson("[0,1,2]");
        plan.setCompletionThreshold(BigDecimal.valueOf(100));
        plan.setDailyDeadlineTime(LocalTime.of(21, 30));
        plan.setStatus(StudyPlanStatus.PUBLISHED);
        plan = studyPlanRepository.saveAndFlush(plan);

        StudentStudyPlan studentPlan = new StudentStudyPlan();
        studentPlan.setStudyPlanId(plan.getId());
        studentPlan.setStudentId(20L);
        studentStudyPlanId = studentStudyPlanRepository.saveAndFlush(studentPlan).getId();

        StudyDayTask task = new StudyDayTask();
        task.setStudentStudyPlanId(studentStudyPlanId);
        task.setTaskDate(today);
        task.setNewCount(1);
        task.setStatus(StudyDayTaskStatus.NOT_STARTED);
        task.setDeadlineAt(today.atTime(21, 30));
        task = studyDayTaskRepository.saveAndFlush(task);
        taskId = task.getId();

        StudyDayTaskItem item = new StudyDayTaskItem();
        item.setStudyDayTaskId(taskId);
        item.setMetaWordId(3L);
        item.setTaskType(StudyTaskType.NEW_LEARN);
        item.setTaskOrder(1);
        studyDayTaskItemRepository.saveAndFlush(item);

        StudyWordProgress progress = new StudyWordProgress();
        progress.setStudentStudyPlanId(studentStudyPlanId);
        progress.setMetaWordId(3L);
        progress.setAssignedDate(today);
        studyWordProgressRepository.saveAndFlush(progress);

        dictionaryWordRepository.saveAndFlush(new DictionaryWord(10L, 3L));
        ruleRepository.saveAndFlush(StudentPointRule.create(
                "STUDY_RECORD_CORRECT", "Correct record", PointSourceType.STUDY_RECORD, 1));
        ruleRepository.saveAndFlush(StudentPointRule.create(
                "DAILY_TASK_COMPLETED", "Completed task", PointSourceType.STUDY_TASK, 10));
    }

    @Test
    void committedReplayShouldUseGeneratedIdAndCreateOneRecordAndTwoEvents() {
        RecordStudyRequest request = request("integration-request");

        studyPlanService.recordStudy(studentStudyPlanId, request, student());
        StudyRecord record = studyRecordRepository.findByRequestKey("integration-request").orElseThrow();
        assertNotNull(record.getId());
        org.junit.jupiter.api.Assertions.assertTrue(record.isPointsEligible());
        org.junit.jupiter.api.Assertions.assertTrue(
                studyDayTaskRepository.findById(taskId).orElseThrow().isPointsEligible());
        pointExecutor.runAll();

        studyPlanService.recordStudy(studentStudyPlanId, request, student());
        pointExecutor.runAll();

        assertEquals(1, studyRecordRepository.count());
        assertEquals(2, eventRepository.count());
        assertEquals(
                record.getId(),
                eventRepository.findByIdempotencyKey(
                        "study-record:" + record.getId() + ":correct:STUDY_RECORD_CORRECT"
                ).orElseThrow().getSourceId()
        );
    }

    @Test
    void rollbackAfterPublicationRegistrationShouldCreateNeitherRecordNorEvent() {
        doThrow(new IllegalStateException("response rendering failed"))
                .when(studyDayTaskItemRepository)
                .findByStudyDayTaskIdOrderByTaskOrderAsc(taskId);

        assertThrows(
                IllegalStateException.class,
                () -> studyPlanService.recordStudy(studentStudyPlanId, request("rollback-request"), student())
        );

        pointExecutor.runAll();
        assertEquals(0, studyRecordRepository.count());
        assertEquals(0, eventRepository.count());
    }

    @Test
    void concurrentSameRequestKeyShouldCreateOneRecordAndOnePairOfEvents() throws Exception {
        RecordStudyRequest request = request("concurrent-request");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> invokeAfter(start, request));
            Future<?> second = executor.submit(() -> invokeAfter(start, request));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        pointExecutor.runAll();
        assertEquals(1, studyRecordRepository.count());
        assertEquals(2, eventRepository.count());
    }

    @Test
    void reconciliationShouldPersistMissingEventWithGeneratedRecordId() {
        StudyDayTask task = studyDayTaskRepository.findById(taskId).orElseThrow();
        StudyRecord record = studyRecordRepository.saveAndFlush(StudyRecord.builder()
                .requestKey("reconciliation-request")
                .studentStudyPlanId(studentStudyPlanId)
                .metaWordId(3L)
                .taskDate(task.getTaskDate())
                .actionType(StudyActionType.LEARN)
                .result(StudyRecordResult.CORRECT)
                .durationSeconds(10)
                .focusSeconds(9)
                .idleSeconds(1)
                .interactionCount(1)
                .pointsEligible(true)
                .attentionState(AttentionState.FOCUSED)
                .stageBefore(0)
                .stageAfter(1)
                .build());

        reconciliationService.reconcileMissingEvents();

        assertEquals(1, eventRepository.count());
        assertEquals(
                record.getId(),
                eventRepository.findByIdempotencyKey(
                        "study-record:" + record.getId() + ":correct:STUDY_RECORD_CORRECT"
                ).orElseThrow().getSourceId()
        );
    }

    private void invokeAfter(CountDownLatch start, RecordStudyRequest request) {
        try {
            start.await(5, TimeUnit.SECONDS);
            studyPlanService.recordStudy(studentStudyPlanId, request, student());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private RecordStudyRequest request(String requestKey) {
        return new RecordStudyRequest(
                3L,
                StudyActionType.LEARN,
                StudyRecordResult.CORRECT,
                10,
                9,
                1,
                1,
                AttentionState.FOCUSED,
                requestKey
        );
    }

    private AppUser student() {
        AppUser student = new AppUser();
        student.setId(20L);
        student.setRole(UserRole.STUDENT);
        return student;
    }

    @TestConfiguration
    static class Configuration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean(name = "studentPointEventTaskExecutor")
        ControllableTaskExecutor pointExecutor() {
            return new ControllableTaskExecutor();
        }
    }

    static final class ControllableTaskExecutor implements TaskExecutor {

        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }

        void clear() {
            tasks.clear();
        }
    }
}
