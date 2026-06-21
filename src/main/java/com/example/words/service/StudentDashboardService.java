package com.example.words.service;

import com.example.words.dto.StudentDashboardRecordRequest;
import com.example.words.dto.StudentDashboardReminderResponse;
import com.example.words.dto.StudentDashboardResponse;
import com.example.words.dto.StudentDashboardTaskItemResponse;
import com.example.words.dto.StudentStudyPlanSummaryResponse;
import com.example.words.dto.StudyTaskItemResponse;
import com.example.words.dto.StudyTaskResponse;
import com.example.words.model.AppUser;
import com.example.words.model.StudentStudyPlanStatus;
import com.example.words.model.StudyTaskType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDashboardService {

    private static final Map<StudyTaskType, Integer> TASK_PRIORITY = Map.of(
            StudyTaskType.OVERDUE_REVIEW, 0,
            StudyTaskType.TODAY_REVIEW, 1,
            StudyTaskType.NEW_LEARN, 2
    );

    private final StudyPlanService studyPlanService;

    public StudentDashboardService(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @Transactional
    public StudentDashboardResponse getDashboard(AppUser actor) {
        List<StudentStudyPlanSummaryResponse> plans = studyPlanService.listStudentStudyPlans(actor).stream()
                .filter(plan -> plan.getStatus() == StudentStudyPlanStatus.ACTIVE)
                .toList();

        List<StudentDashboardTaskItemResponse> queue = new ArrayList<>();
        int overdueCount = 0;
        int reviewCount = 0;
        int newCount = 0;
        int completedCount = 0;
        LocalDate taskDate = null;

        for (StudentStudyPlanSummaryResponse plan : plans) {
            StudyTaskResponse task = studyPlanService.getTodayTask(plan.getStudentStudyPlanId(), actor);
            Map<Long, Integer> attempts = studyPlanService.countTodayAttempts(
                    plan.getStudentStudyPlanId(),
                    task.getTaskDate(),
                    actor
            );

            taskDate = taskDate == null ? task.getTaskDate() : taskDate;
            overdueCount += value(task.getOverdueCount());
            reviewCount += value(task.getReviewCount());
            newCount += value(task.getNewCount());
            completedCount += value(task.getCompletedCount());

            for (StudyTaskItemResponse item : task.getQueue()) {
                queue.add(toDashboardItem(plan, item, attempts.getOrDefault(item.getMetaWordId(), 0)));
            }
        }

        queue.sort(taskComparator());
        int totalCount = overdueCount + reviewCount + newCount;
        boolean allTasksCompleted = totalCount > 0 && queue.isEmpty();
        BigDecimal completionRate = totalCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completedCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);

        return new StudentDashboardResponse(
                taskDate,
                !plans.isEmpty(),
                allTasksCompleted,
                overdueCount,
                reviewCount,
                newCount,
                completedCount,
                totalCount,
                completionRate,
                reminders(overdueCount, totalCount - completedCount),
                queue
        );
    }

    @Transactional
    public StudentDashboardResponse record(StudentDashboardRecordRequest request, AppUser actor) {
        studyPlanService.recordStudy(
                request.getStudentStudyPlanId(),
                request.toRecordStudyRequest(),
                actor
        );
        return getDashboard(actor);
    }

    private StudentDashboardTaskItemResponse toDashboardItem(
            StudentStudyPlanSummaryResponse plan,
            StudyTaskItemResponse item,
            int attemptCount) {
        return new StudentDashboardTaskItemResponse(
                plan.getStudentStudyPlanId(),
                item.getStudyDayTaskItemId(),
                plan.getStudyPlanId(),
                plan.getPlanName(),
                plan.getPlanPublishedAt(),
                item.getMetaWordId(),
                item.getWord(),
                item.getDefinition(),
                item.getTranslation(),
                item.getPartOfSpeech(),
                item.getExampleSentence(),
                item.getPhonetic(),
                item.getPhoneticDetail(),
                item.getSyllableDetail(),
                item.getTaskType(),
                item.getPhase(),
                item.getDueDate(),
                attemptCount
        );
    }

    private Comparator<StudentDashboardTaskItemResponse> taskComparator() {
        return Comparator
                .comparing((StudentDashboardTaskItemResponse item) -> item.getAttemptCount() > 0)
                .thenComparing(item -> TASK_PRIORITY.getOrDefault(item.getTaskType(), Integer.MAX_VALUE))
                .thenComparing(StudentDashboardTaskItemResponse::getDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StudentDashboardTaskItemResponse::getPlanPublishedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StudentDashboardTaskItemResponse::getStudyDayTaskItemId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private List<StudentDashboardReminderResponse> reminders(int overdueCount, int unfinishedCount) {
        List<StudentDashboardReminderResponse> reminders = new ArrayList<>();
        if (unfinishedCount > 0) {
            reminders.add(new StudentDashboardReminderResponse(
                    "UNFINISHED_TODAY_TASK",
                    "今日学习任务尚未完成",
                    unfinishedCount
            ));
        }
        if (overdueCount > 0) {
            reminders.add(new StudentDashboardReminderResponse(
                    "OVERDUE_REVIEW",
                    "存在逾期复习",
                    overdueCount
            ));
        }
        return reminders;
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }
}
