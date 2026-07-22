package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.words.dto.StudentDashboardResponse;
import com.example.words.dto.StudentStudyPlanSummaryResponse;
import com.example.words.dto.StudyTaskItemResponse;
import com.example.words.dto.StudyTaskResponse;
import com.example.words.model.AppUser;
import com.example.words.model.StudentStudyPlanStatus;
import com.example.words.model.StudyTaskType;
import com.example.words.model.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentDashboardServiceTest {

    private FakeStudyPlanService studyPlanService;
    private StudentDashboardService dashboardService;

    @BeforeEach
    void setUp() {
        studyPlanService = new FakeStudyPlanService();
        dashboardService = new StudentDashboardService(studyPlanService);
    }

    @Test
    void getDashboardShouldAggregateAndOrderTasksWithoutDeduplicatingWords() {
        LocalDate today = LocalDate.of(2026, 6, 22);
        AppUser student = student();

        StudentStudyPlanSummaryResponse firstPlan = planSummary(
                200L,
                50L,
                "晨读计划",
                LocalDateTime.of(2026, 6, 20, 9, 0),
                today
        );
        StudentStudyPlanSummaryResponse secondPlan = planSummary(
                201L,
                51L,
                "复习计划",
                LocalDateTime.of(2026, 6, 21, 9, 0),
                today
        );
        studyPlanService.plans = List.of(firstPlan, secondPlan);
        studyPlanService.tasks.put(200L, task(200L, today, 0, 1, 0, 0, List.of(
                item(401L, 1L, "resilient", StudyTaskType.TODAY_REVIEW, today)
        )));
        studyPlanService.tasks.put(201L, task(201L, today, 1, 0, 0, 0, List.of(
                item(402L, 1L, "resilient", StudyTaskType.OVERDUE_REVIEW, today.minusDays(2))
        )));

        StudentDashboardResponse response = dashboardService.getDashboard(student);

        assertTrue(response.getHasPlans());
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getQueue().size());
        assertEquals(402L, response.getQueue().get(0).getStudyDayTaskItemId());
        assertEquals(401L, response.getQueue().get(1).getStudyDayTaskItemId());
        assertEquals(201L, response.getQueue().get(0).getStudentStudyPlanId());
        assertEquals(200L, response.getQueue().get(1).getStudentStudyPlanId());
    }

    @Test
    void getDashboardShouldMoveAttemptedTaskBehindUntouchedTasks() {
        LocalDate today = LocalDate.of(2026, 6, 22);
        StudentStudyPlanSummaryResponse plan = planSummary(
                200L,
                50L,
                "晨读计划",
                LocalDateTime.of(2026, 6, 20, 9, 0),
                today
        );
        studyPlanService.plans = List.of(plan);
        studyPlanService.tasks.put(200L, task(200L, today, 2, 0, 0, 0, List.of(
                item(401L, 1L, "resilient", StudyTaskType.OVERDUE_REVIEW, today.minusDays(3)),
                item(402L, 2L, "persistent", StudyTaskType.OVERDUE_REVIEW, today.minusDays(1))
        )));
        studyPlanService.attempts.put(200L, Map.of(1L, 1));

        StudentDashboardResponse response = dashboardService.getDashboard(student());

        assertEquals(402L, response.getQueue().get(0).getStudyDayTaskItemId());
        assertEquals(401L, response.getQueue().get(1).getStudyDayTaskItemId());
        assertEquals(1, response.getQueue().get(1).getAttemptCount());
    }

    private AppUser student() {
        AppUser student = new AppUser();
        student.setId(20L);
        student.setRole(UserRole.STUDENT);
        return student;
    }

    private StudentStudyPlanSummaryResponse planSummary(
            Long studentStudyPlanId,
            Long studyPlanId,
            String planName,
            LocalDateTime planPublishedAt,
            LocalDate taskDate) {
        StudentStudyPlanSummaryResponse response = new StudentStudyPlanSummaryResponse();
        response.setStudentStudyPlanId(studentStudyPlanId);
        response.setStudyPlanId(studyPlanId);
        response.setPlanName(planName);
        response.setPlanPublishedAt(planPublishedAt);
        response.setStatus(StudentStudyPlanStatus.ACTIVE);
        response.setTaskDate(taskDate);
        return response;
    }

    private StudyTaskResponse task(
            Long studentStudyPlanId,
            LocalDate taskDate,
            int overdue,
            int review,
            int newWords,
            int completed,
            List<StudyTaskItemResponse> queue) {
        StudyTaskResponse response = new StudyTaskResponse();
        response.setStudentStudyPlanId(studentStudyPlanId);
        response.setTaskDate(taskDate);
        response.setOverdueCount(overdue);
        response.setReviewCount(review);
        response.setNewCount(newWords);
        response.setCompletedCount(completed);
        response.setCompletionRate(BigDecimal.ZERO);
        response.setQueue(queue);
        return response;
    }

    private StudyTaskItemResponse item(
            Long taskItemId,
            Long metaWordId,
            String word,
            StudyTaskType taskType,
            LocalDate dueDate) {
        StudyTaskItemResponse response = new StudyTaskItemResponse();
        response.setStudyDayTaskItemId(taskItemId);
        response.setMetaWordId(metaWordId);
        response.setWord(word);
        response.setTaskType(taskType);
        response.setDueDate(dueDate);
        return response;
    }

    private static final class FakeStudyPlanService extends StudyPlanService {

        private List<StudentStudyPlanSummaryResponse> plans = List.of();
        private final Map<Long, StudyTaskResponse> tasks = new HashMap<>();
        private final Map<Long, Map<Long, Integer>> attempts = new HashMap<>();

        private FakeStudyPlanService() {
            super(
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, new ObjectMapper()
            );
        }

        @Override
        public List<StudentStudyPlanSummaryResponse> listStudentStudyPlans(AppUser actor) {
            return plans;
        }

        @Override
        public StudyTaskResponse getTodayTask(Long studentStudyPlanId, AppUser actor) {
            return tasks.get(studentStudyPlanId);
        }

        @Override
        public Map<Long, Integer> countTodayAttempts(
                Long studentStudyPlanId,
                LocalDate taskDate,
                AppUser actor) {
            return attempts.getOrDefault(studentStudyPlanId, Map.of());
        }
    }
}
