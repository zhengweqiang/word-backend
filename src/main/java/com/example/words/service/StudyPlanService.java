package com.example.words.service;

import com.example.words.dto.AppendStudyPlanStudentsRequest;
import com.example.words.dto.CreateStudyPlanRequest;
import com.example.words.dto.RecordStudyRequest;
import com.example.words.dto.StudentAttentionDailyStatResponse;
import com.example.words.dto.StudentStudyPlanSummaryResponse;
import com.example.words.dto.StudyPlanOverviewResponse;
import com.example.words.dto.StudyPlanResponse;
import com.example.words.dto.StudyPlanStudentAttentionResponse;
import com.example.words.dto.StudyPlanStudentSummaryResponse;
import com.example.words.dto.StudyTaskItemResponse;
import com.example.words.dto.StudyTaskResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.model.StudentAttentionDailyStat;
import com.example.words.model.StudentStudyPlan;
import com.example.words.model.StudentStudyPlanStatus;
import com.example.words.model.StudyDayTask;
import com.example.words.model.StudyDayTaskItem;
import com.example.words.model.StudyDayTaskStatus;
import com.example.words.model.StudyPlan;
import com.example.words.model.StudyPlanClassroom;
import com.example.words.model.StudyPlanStatus;
import com.example.words.model.StudyRecord;
import com.example.words.model.StudyRecordResult;
import com.example.words.model.StudyTaskType;
import com.example.words.model.StudyWordProgress;
import com.example.words.model.StudyWordProgressStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.StudentAttentionDailyStatRepository;
import com.example.words.repository.StudentStudyPlanRepository;
import com.example.words.repository.StudyDayTaskItemRepository;
import com.example.words.repository.StudyDayTaskRepository;
import com.example.words.repository.StudyPlanClassroomRepository;
import com.example.words.repository.StudyPlanRepository;
import com.example.words.repository.StudyRecordRepository;
import com.example.words.repository.StudyWordProgressRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanClassroomRepository studyPlanClassroomRepository;
    private final StudentStudyPlanRepository studentStudyPlanRepository;
    private final StudyDayTaskRepository studyDayTaskRepository;
    private final StudyDayTaskItemRepository studyDayTaskItemRepository;
    private final StudyWordProgressRepository studyWordProgressRepository;
    private final StudyRecordRepository studyRecordRepository;
    private final StudentAttentionDailyStatRepository studentAttentionDailyStatRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final DictionaryService dictionaryService;
    private final DictionaryAssignmentService dictionaryAssignmentService;
    private final DictionaryWordRepository dictionaryWordRepository;
    private final MetaWordRepository metaWordRepository;
    private final AccessControlService accessControlService;
    private final UserService userService;
    private final StudentWordMemoryService studentWordMemoryService;
    private final ObjectMapper objectMapper;

    public StudyPlanService(
            StudyPlanRepository studyPlanRepository,
            StudyPlanClassroomRepository studyPlanClassroomRepository,
            StudentStudyPlanRepository studentStudyPlanRepository,
            StudyDayTaskRepository studyDayTaskRepository,
            StudyDayTaskItemRepository studyDayTaskItemRepository,
            StudyWordProgressRepository studyWordProgressRepository,
            StudyRecordRepository studyRecordRepository,
            StudentAttentionDailyStatRepository studentAttentionDailyStatRepository,
            ClassroomRepository classroomRepository,
            ClassroomMemberRepository classroomMemberRepository,
            DictionaryService dictionaryService,
            DictionaryAssignmentService dictionaryAssignmentService,
            DictionaryWordRepository dictionaryWordRepository,
            MetaWordRepository metaWordRepository,
            AccessControlService accessControlService,
            UserService userService,
            StudentWordMemoryService studentWordMemoryService,
            ObjectMapper objectMapper) {
        this.studyPlanRepository = studyPlanRepository;
        this.studyPlanClassroomRepository = studyPlanClassroomRepository;
        this.studentStudyPlanRepository = studentStudyPlanRepository;
        this.studyDayTaskRepository = studyDayTaskRepository;
        this.studyDayTaskItemRepository = studyDayTaskItemRepository;
        this.studyWordProgressRepository = studyWordProgressRepository;
        this.studyRecordRepository = studyRecordRepository;
        this.studentAttentionDailyStatRepository = studentAttentionDailyStatRepository;
        this.classroomRepository = classroomRepository;
        this.classroomMemberRepository = classroomMemberRepository;
        this.dictionaryService = dictionaryService;
        this.dictionaryAssignmentService = dictionaryAssignmentService;
        this.dictionaryWordRepository = dictionaryWordRepository;
        this.metaWordRepository = metaWordRepository;
        this.accessControlService = accessControlService;
        this.userService = userService;
        this.studentWordMemoryService = studentWordMemoryService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StudyPlanResponse createStudyPlan(CreateStudyPlanRequest request, AppUser actor) {
        ensureCanCreateStudyPlan(actor);
        validateRequest(request);

        List<Classroom> classrooms = resolveManagedClassrooms(request.getClassroomIds(), actor);
        Dictionary dictionary = dictionaryService.findById(request.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + request.getDictionaryId()));
        accessControlService.ensureCanViewDictionary(actor, dictionary);
        ensureDictionaryAvailableForClassrooms(request.getDictionaryId(), classrooms, actor);
        Long teacherId = resolvePlanTeacherId(classrooms, actor);

        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setName(request.getName().trim());
        studyPlan.setDescription(trimToNull(request.getDescription()));
        studyPlan.setTeacherId(teacherId);
        studyPlan.setDictionaryId(request.getDictionaryId());
        studyPlan.setStartDate(request.getStartDate());
        studyPlan.setEndDate(request.getEndDate());
        studyPlan.setTimezone(request.getTimezone().trim());
        studyPlan.setDailyNewCount(request.getDailyNewCount());
        studyPlan.setDailyReviewLimit(request.getDailyReviewLimit());
        studyPlan.setReviewMode(request.getReviewMode());
        studyPlan.setReviewIntervalsJson(serializeReviewIntervals(normalizeReviewIntervals(request.getReviewIntervals())));
        studyPlan.setCompletionThreshold(request.getCompletionThreshold().setScale(2, RoundingMode.HALF_UP));
        studyPlan.setDailyDeadlineTime(request.getDailyDeadlineTime());
        studyPlan.setAttentionTrackingEnabled(request.getAttentionTrackingEnabled());
        studyPlan.setMinFocusSecondsPerWord(request.getMinFocusSecondsPerWord());
        studyPlan.setMaxFocusSecondsPerWord(request.getMaxFocusSecondsPerWord());
        studyPlan.setLongStayWarningSeconds(request.getLongStayWarningSeconds());
        studyPlan.setIdleTimeoutSeconds(request.getIdleTimeoutSeconds());
        studyPlan.setStatus(StudyPlanStatus.DRAFT);

        StudyPlan savedStudyPlan = studyPlanRepository.save(studyPlan);
        for (Classroom classroom : classrooms) {
            studyPlanClassroomRepository.save(new StudyPlanClassroom(null, savedStudyPlan.getId(), classroom.getId(), null));
        }

        return toStudyPlanResponse(savedStudyPlan, dictionary, classrooms.stream().map(Classroom::getId).toList(), 0L);
    }

    @Transactional(readOnly = true)
    public List<StudyPlanResponse> listVisibleStudyPlans(AppUser actor) {
        List<StudyPlan> studyPlans = actor.getRole() == UserRole.ADMIN
                ? studyPlanRepository.findAll().stream()
                        .sorted(Comparator.comparing(StudyPlan::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .reversed())
                        .toList()
                : studyPlanRepository.findByTeacherIdOrderByCreatedAtDesc(actor.getId());

        return studyPlans.stream()
                .map(this::toStudyPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudyPlanResponse getStudyPlan(Long studyPlanId, AppUser actor) {
        StudyPlan studyPlan = getStudyPlanEntity(studyPlanId);
        ensureCanManageStudyPlan(actor, studyPlan);
        return toStudyPlanResponse(studyPlan);
    }

    @Transactional
    public StudyPlanResponse publishStudyPlan(Long studyPlanId, AppUser actor) {
        StudyPlan studyPlan = getStudyPlanEntity(studyPlanId);
        ensureCanManageStudyPlan(actor, studyPlan);

        Dictionary dictionary = dictionaryService.findById(studyPlan.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + studyPlan.getDictionaryId()));

        List<Long> classroomIds = studyPlanClassroomRepository.findByStudyPlanId(studyPlanId).stream()
                .map(StudyPlanClassroom::getClassroomId)
                .toList();
        List<Classroom> classrooms = resolveManagedClassrooms(classroomIds, actor);
        ensureDictionaryAvailableForClassrooms(studyPlan.getDictionaryId(), classrooms, actor);

        Set<Long> studentIds = classroomIds.isEmpty()
                ? Set.of()
                : classroomMemberRepository.findByClassroomIdIn(classroomIds).stream()
                        .map(ClassroomMember::getStudentId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Long> assignableStudentIds = new ArrayList<>();
        for (Long studentId : studentIds) {
            accessControlService.ensureCanAssignDictionaryToStudent(actor, dictionary, studentId);
            assignableStudentIds.add(studentId);
        }

        if (!assignableStudentIds.isEmpty()) {
            dictionaryAssignmentService.assignDictionaryToStudents(dictionary, actor, assignableStudentIds);
        }

        LocalDateTime joinedAt = resolveNow(studyPlan);
        for (Long studentId : assignableStudentIds) {
            getOrCreateStudentStudyPlan(studyPlanId, studentId, joinedAt);
        }

        studyPlan.setStatus(StudyPlanStatus.PUBLISHED);
        StudyPlan savedStudyPlan = studyPlanRepository.save(studyPlan);
        return toStudyPlanResponse(savedStudyPlan);
    }

    @Transactional
    public StudyPlanResponse appendStudents(Long studyPlanId, AppendStudyPlanStudentsRequest request, AppUser actor) {
        StudyPlan studyPlan = getStudyPlanEntity(studyPlanId);
        ensureCanManageStudyPlan(actor, studyPlan);
        ensurePublished(studyPlan);

        List<Long> classroomIds = studyPlanClassroomRepository.findByStudyPlanId(studyPlanId).stream()
                .map(StudyPlanClassroom::getClassroomId)
                .distinct()
                .toList();
        resolveManagedClassrooms(classroomIds, actor);

        List<Long> studentIds = request == null || request.getStudentIds() == null
                ? List.of()
                : request.getStudentIds().stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (studentIds.isEmpty()) {
            throw new BadRequestException("studentIds cannot be empty");
        }

        for (Long studentId : studentIds) {
            if (!classroomMemberRepository.existsByClassroomIdInAndStudentId(classroomIds, studentId)) {
                throw new BadRequestException(
                        "studentId is not a current member of the study plan classrooms: " + studentId);
            }
        }

        Dictionary dictionary = dictionaryService.findById(studyPlan.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + studyPlan.getDictionaryId()));

        for (Long studentId : studentIds) {
            accessControlService.ensureCanAssignDictionaryToStudent(actor, dictionary, studentId);
        }
        dictionaryAssignmentService.assignDictionaryToStudents(dictionary, actor, studentIds);

        LocalDateTime joinedAt = resolveNow(studyPlan);
        for (Long studentId : studentIds) {
            getOrCreateStudentStudyPlan(studyPlanId, studentId, joinedAt);
        }

        return toStudyPlanResponse(studyPlan);
    }

    @Transactional
    public StudyPlanOverviewResponse getOverview(Long studyPlanId, AppUser actor) {
        StudyPlan studyPlan = getStudyPlanEntity(studyPlanId);
        ensureCanManageStudyPlan(actor, studyPlan);

        List<StudentStudyPlan> studentStudyPlans = studentStudyPlanRepository.findByStudyPlanIdOrderByStudentIdAsc(studyPlanId);
        LocalDate taskDate = resolveToday(studyPlan);
        long completedStudents = 0;
        long notStartedStudents = 0;
        long inProgressStudents = 0;
        long missedStudents = 0;
        BigDecimal completionTotal = BigDecimal.ZERO;
        BigDecimal attentionTotal = BigDecimal.ZERO;

        for (StudentStudyPlan studentStudyPlan : studentStudyPlans) {
            markExpiredTasks(studentStudyPlan, taskDate);
            StudyDayTask studyDayTask = canGenerateTodayTask(studyPlan, taskDate)
                    ? getOrCreateTodayTask(studentStudyPlan, studyPlan, taskDate)
                    : null;
            if (studyDayTask == null) {
                notStartedStudents++;
                continue;
            }

            updateTaskStatusFromItems(studentStudyPlan, studyDayTask);
            switch (studyDayTask.getStatus()) {
                case COMPLETED -> completedStudents++;
                case IN_PROGRESS -> inProgressStudents++;
                case MISSED -> missedStudents++;
                case NOT_STARTED -> notStartedStudents++;
                default -> {
                }
            }
            completionTotal = completionTotal.add(studyDayTask.getCompletionRate());
            attentionTotal = attentionTotal.add(studyDayTask.getAttentionScore());
        }

        long totalStudents = studentStudyPlans.size();
        return new StudyPlanOverviewResponse(
                studyPlan.getId(),
                studyPlan.getName(),
                studyPlan.getStatus(),
                taskDate,
                totalStudents,
                completedStudents,
                notStartedStudents,
                inProgressStudents,
                missedStudents,
                average(completionTotal, totalStudents),
                average(attentionTotal, totalStudents)
        );
    }

    @Transactional
    public List<StudyPlanStudentSummaryResponse> listPlanStudents(Long studyPlanId, AppUser actor) {
        StudyPlan studyPlan = getStudyPlanEntity(studyPlanId);
        ensureCanManageStudyPlan(actor, studyPlan);

        LocalDate taskDate = resolveToday(studyPlan);
        List<StudentStudyPlan> studentStudyPlans = studentStudyPlanRepository.findByStudyPlanIdOrderByStudentIdAsc(studyPlanId);
        List<StudyPlanStudentSummaryResponse> responses = new ArrayList<>();
        for (StudentStudyPlan studentStudyPlan : studentStudyPlans) {
            markExpiredTasks(studentStudyPlan, taskDate);
            StudyDayTask studyDayTask = canGenerateTodayTask(studyPlan, taskDate)
                    ? getOrCreateTodayTask(studentStudyPlan, studyPlan, taskDate)
                    : null;
            if (studyDayTask != null) {
                updateTaskStatusFromItems(studentStudyPlan, studyDayTask);
            }
            AppUser student = userService.getUserEntity(studentStudyPlan.getStudentId());
            responses.add(toPlanStudentSummary(studentStudyPlan, student, studyDayTask, taskDate));
        }
        return responses;
    }

    @Transactional
    public List<StudentStudyPlanSummaryResponse> listStudentStudyPlans(AppUser actor) {
        if (actor.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can view personal study plans");
        }

        List<StudentStudyPlan> studentStudyPlans = studentStudyPlanRepository.findByStudentIdOrderByCreatedAtDesc(actor.getId());
        List<StudentStudyPlanSummaryResponse> responses = new ArrayList<>();
        for (StudentStudyPlan studentStudyPlan : studentStudyPlans) {
            StudyPlan studyPlan = getStudyPlanEntity(studentStudyPlan.getStudyPlanId());
            LocalDate taskDate = resolveToday(studyPlan);
            markExpiredTasks(studentStudyPlan, taskDate);
            StudyDayTask studyDayTask = canGenerateTodayTask(studyPlan, taskDate) && isPublished(studyPlan)
                    ? getOrCreateTodayTask(studentStudyPlan, studyPlan, taskDate)
                    : null;
            if (studyDayTask != null) {
                updateTaskStatusFromItems(studentStudyPlan, studyDayTask);
            }
            Dictionary dictionary = dictionaryService.findById(studyPlan.getDictionaryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + studyPlan.getDictionaryId()));
            responses.add(toStudentStudyPlanSummary(studentStudyPlan, studyPlan, dictionary, studyDayTask, taskDate));
        }
        return responses;
    }

    @Transactional
    public StudyTaskResponse getTodayTask(Long studentStudyPlanId, AppUser actor) {
        StudentStudyPlan studentStudyPlan = getStudentStudyPlanEntity(studentStudyPlanId);
        ensureStudentOwnsPlan(actor, studentStudyPlan);

        StudyPlan studyPlan = getStudyPlanEntity(studentStudyPlan.getStudyPlanId());
        ensurePublished(studyPlan);
        LocalDate taskDate = resolveToday(studyPlan);
        ensurePlanActiveOnDate(studyPlan, taskDate);

        markExpiredTasks(studentStudyPlan, taskDate);
        StudyDayTask studyDayTask = getOrCreateTodayTask(studentStudyPlan, studyPlan, taskDate);
        updateTaskStatusFromItems(studentStudyPlan, studyDayTask);
        return toStudyTaskResponse(studentStudyPlan, studyDayTask);
    }

    @Transactional
    public StudyTaskResponse recordStudy(Long studentStudyPlanId, RecordStudyRequest request, AppUser actor) {
        StudentStudyPlan studentStudyPlan = getStudentStudyPlanEntity(studentStudyPlanId);
        ensureStudentOwnsPlan(actor, studentStudyPlan);

        StudyPlan studyPlan = getStudyPlanEntity(studentStudyPlan.getStudyPlanId());
        ensurePublished(studyPlan);
        LocalDate taskDate = resolveToday(studyPlan);
        ensurePlanActiveOnDate(studyPlan, taskDate);

        markExpiredTasks(studentStudyPlan, taskDate);
        StudyDayTask studyDayTask = getOrCreateTodayTask(studentStudyPlan, studyPlan, taskDate);
        StudyDayTaskItem taskItem = findStudyDayTaskItem(studyDayTask.getId(), request.getMetaWordId())
                .orElseThrow(() -> new BadRequestException("Word is not scheduled for today's task"));

        if (!dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(studyPlan.getDictionaryId(), request.getMetaWordId())) {
            throw new BadRequestException("Word does not belong to the study plan dictionary");
        }

        List<StudyRecord> existingRecords = studyRecordRepository.findByStudentStudyPlanIdAndTaskDate(studentStudyPlanId, taskDate);
        boolean firstRecordToday = existingRecords.isEmpty();
        List<Integer> reviewIntervals = parseReviewIntervals(studyPlan.getReviewIntervalsJson());
        LocalDateTime now = resolveNow(studyPlan);

        StudyWordProgress studyWordProgress = findStudyWordProgress(studentStudyPlanId, request.getMetaWordId())
                .orElseGet(() -> createProgressForExistingItem(studentStudyPlanId, request.getMetaWordId(), taskDate, taskItem));

        int focusSeconds = normalizeFocusSeconds(request.getFocusSeconds(), request.getDurationSeconds(), studyPlan);
        int idleSeconds = normalizeIdleSeconds(request.getIdleSeconds(), request.getDurationSeconds(), focusSeconds);
        int durationSeconds = normalizeDurationSeconds(request.getDurationSeconds(), focusSeconds, idleSeconds);
        int interactionCount = request.getInteractionCount() == null ? 0 : request.getInteractionCount();
        int stageBefore = studyWordProgress.getPhase() == null ? 0 : studyWordProgress.getPhase();

        updateProgress(studyWordProgress, request.getResult(), reviewIntervals, now, focusSeconds);
        StudyWordProgress savedProgress = studyWordProgressRepository.save(studyWordProgress);

        StudyRecord studyRecord = new StudyRecord();
        studyRecord.setStudentStudyPlanId(studentStudyPlanId);
        studyRecord.setMetaWordId(request.getMetaWordId());
        studyRecord.setTaskDate(taskDate);
        studyRecord.setActionType(request.getActionType());
        studyRecord.setResult(request.getResult());
        studyRecord.setDurationSeconds(durationSeconds);
        studyRecord.setFocusSeconds(focusSeconds);
        studyRecord.setIdleSeconds(idleSeconds);
        studyRecord.setInteractionCount(interactionCount);
        studyRecord.setAttentionState(request.getAttentionState());
        studyRecord.setStageBefore(stageBefore);
        studyRecord.setStageAfter(savedProgress.getPhase());
        StudyRecord savedStudyRecord = studyRecordRepository.save(studyRecord);
        if (studentWordMemoryService != null) {
            studentWordMemoryService.recordPlanStudy(
                    studentStudyPlan.getStudentId(),
                    request.getMetaWordId(),
                    savedStudyRecord.getId(),
                    studyPlan.getDictionaryId(),
                    request.getResult(),
                    now
            );
        }

        if (firstRecordToday) {
            updateStreak(studentStudyPlan, taskDate, now);
        }
        studentStudyPlan.setLastStudyAt(now);

        if (studyDayTask.getStatus() == StudyDayTaskStatus.NOT_STARTED) {
            studyDayTask.setStatus(StudyDayTaskStatus.IN_PROGRESS);
            studyDayTask.setStartedAt(now);
        }

        if (StudyTaskCompletionPolicy.completesTask(request.getResult()) && taskItem.getCompletedAt() == null) {
            taskItem.setCompletedAt(now);
            studyDayTaskItemRepository.save(taskItem);
            studyDayTask.setCompletedCount(studyDayTask.getCompletedCount() + 1);
        }

        refreshDailyStats(studentStudyPlan, studyPlan, studyDayTask, taskDate);
        updateTaskStatusFromItems(studentStudyPlan, studyDayTask);
        refreshStudentStudyPlanMetrics(studentStudyPlan, studyPlan);

        studentStudyPlanRepository.save(studentStudyPlan);
        studyDayTaskRepository.save(studyDayTask);

        return toStudyTaskResponse(studentStudyPlan, studyDayTask);
    }

    @Transactional(readOnly = true)
    public List<StudentAttentionDailyStatResponse> getStudentAttentionStats(Long studentStudyPlanId, AppUser actor) {
        StudentStudyPlan studentStudyPlan = getStudentStudyPlanEntity(studentStudyPlanId);
        ensureStudentOwnsPlan(actor, studentStudyPlan);
        return studentAttentionDailyStatRepository.findByStudentStudyPlanIdOrderByTaskDateDesc(studentStudyPlanId).stream()
                .map(this::toStudentAttentionDailyStatResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> countTodayAttempts(
            Long studentStudyPlanId,
            LocalDate taskDate,
            AppUser actor) {
        StudentStudyPlan studentStudyPlan = getStudentStudyPlanEntity(studentStudyPlanId);
        ensureStudentOwnsPlan(actor, studentStudyPlan);
        Map<Long, Integer> attempts = new HashMap<>();
        for (StudyRecord record : studyRecordRepository.findByStudentStudyPlanIdAndTaskDate(
                studentStudyPlanId,
                taskDate)) {
            attempts.merge(record.getMetaWordId(), 1, Integer::sum);
        }
        return attempts;
    }

    @Transactional(readOnly = true)
    public StudyPlanStudentAttentionResponse getPlanStudentAttention(
            Long studyPlanId,
            Long studentId,
            AppUser actor) {
        StudyPlan studyPlan = getStudyPlanEntity(studyPlanId);
        ensureCanManageStudyPlan(actor, studyPlan);

        StudentStudyPlan studentStudyPlan = findStudentStudyPlanByPlanAndStudent(studyPlanId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student study plan not found for plan " + studyPlanId + " and student " + studentId));

        AppUser student = userService.getUserEntity(studentId);
        List<StudentAttentionDailyStatResponse> dailyStats = studentAttentionDailyStatRepository
                .findByStudentStudyPlanIdOrderByTaskDateDesc(studentStudyPlan.getId()).stream()
                .map(this::toStudentAttentionDailyStatResponse)
                .toList();
        return new StudyPlanStudentAttentionResponse(studentId, student.getDisplayName(), studyPlanId, dailyStats);
    }

    @Transactional(readOnly = true)
    public StudyPlan getStudyPlanEntity(Long studyPlanId) {
        return studyPlanRepository.findById(studyPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Study plan not found: " + studyPlanId));
    }

    @Transactional(readOnly = true)
    public StudentStudyPlan getStudentStudyPlanEntity(Long studentStudyPlanId) {
        return studentStudyPlanRepository.findById(studentStudyPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Student study plan not found: " + studentStudyPlanId));
    }

    private void ensureCanCreateStudyPlan(AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER) {
            throw new AccessDeniedException("Only admin or teacher can create study plans");
        }
    }

    private void ensureCanManageStudyPlan(AppUser actor, StudyPlan studyPlan) {
        if (actor.getRole() == UserRole.ADMIN || Objects.equals(actor.getId(), studyPlan.getTeacherId())) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to manage this study plan");
    }

    private void ensureStudentOwnsPlan(AppUser actor, StudentStudyPlan studentStudyPlan) {
        if (actor.getRole() == UserRole.STUDENT && Objects.equals(actor.getId(), studentStudyPlan.getStudentId())) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this study plan");
    }

    private void ensurePublished(StudyPlan studyPlan) {
        if (!isPublished(studyPlan)) {
            throw new BadRequestException("Study plan is not published");
        }
    }

    private boolean isPublished(StudyPlan studyPlan) {
        return studyPlan.getStatus() == StudyPlanStatus.PUBLISHED
                || studyPlan.getStatus() == StudyPlanStatus.COMPLETED;
    }

    private void validateRequest(CreateStudyPlanRequest request) {
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate cannot be earlier than startDate");
        }
        if (request.getMaxFocusSecondsPerWord() < request.getMinFocusSecondsPerWord()) {
            throw new BadRequestException("maxFocusSecondsPerWord cannot be less than minFocusSecondsPerWord");
        }
        normalizeReviewIntervals(request.getReviewIntervals());
    }

    private List<Classroom> resolveManagedClassrooms(Collection<Long> classroomIds, AppUser actor) {
        if (classroomIds == null || classroomIds.isEmpty()) {
            throw new BadRequestException("classroomIds cannot be empty");
        }

        List<Classroom> classrooms = classroomIds.stream()
                .distinct()
                .map(classroomId -> classroomRepository.findById(classroomId)
                        .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomId)))
                .toList();

        Set<Long> teacherIds = new LinkedHashSet<>();
        for (Classroom classroom : classrooms) {
            if (classroom.getStatus() == ClassroomStatus.ARCHIVED) {
                throw new AccessDeniedException("Archived classroom cannot be used for study plans");
            }
            teacherIds.add(classroom.getTeacherId());
            if (actor.getRole() == UserRole.TEACHER && !Objects.equals(actor.getId(), classroom.getTeacherId())) {
                throw new AccessDeniedException("You do not have permission to manage classroom " + classroom.getId());
            }
        }

        if (teacherIds.size() > 1) {
            throw new BadRequestException("All classrooms in one study plan must belong to the same teacher");
        }
        return classrooms;
    }

    private Long resolvePlanTeacherId(List<Classroom> classrooms, AppUser actor) {
        if (actor.getRole() == UserRole.TEACHER) {
            return actor.getId();
        }
        return classrooms.isEmpty() ? actor.getId() : classrooms.get(0).getTeacherId();
    }

    private void ensureDictionaryAvailableForClassrooms(Long dictionaryId, List<Classroom> classrooms, AppUser actor) {
        List<Long> classroomIds = classrooms.stream()
                .map(Classroom::getId)
                .toList();
        boolean available = dictionaryService.findVisibleDictionariesForClassrooms(classroomIds, actor).stream()
                .anyMatch(dictionary -> Objects.equals(dictionary.getId(), dictionaryId));
        if (!available) {
            throw new BadRequestException("dictionaryId is not associated with all selected classrooms");
        }
    }

    private StudyPlanResponse toStudyPlanResponse(StudyPlan studyPlan) {
        Dictionary dictionary = dictionaryService.findById(studyPlan.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + studyPlan.getDictionaryId()));
        List<Long> classroomIds = studyPlanClassroomRepository.findByStudyPlanId(studyPlan.getId()).stream()
                .map(StudyPlanClassroom::getClassroomId)
                .toList();
        long studentCount = studentStudyPlanRepository.findByStudyPlanIdOrderByStudentIdAsc(studyPlan.getId()).size();
        return toStudyPlanResponse(studyPlan, dictionary, classroomIds, studentCount);
    }

    private StudyPlanResponse toStudyPlanResponse(
            StudyPlan studyPlan,
            Dictionary dictionary,
            List<Long> classroomIds,
            Long studentCount) {
        return new StudyPlanResponse(
                studyPlan.getId(),
                studyPlan.getName(),
                studyPlan.getDescription(),
                studyPlan.getTeacherId(),
                studyPlan.getDictionaryId(),
                dictionary.getName(),
                classroomIds,
                studyPlan.getStartDate(),
                studyPlan.getEndDate(),
                studyPlan.getTimezone(),
                studyPlan.getDailyNewCount(),
                studyPlan.getDailyReviewLimit(),
                studyPlan.getReviewMode(),
                parseReviewIntervals(studyPlan.getReviewIntervalsJson()),
                studyPlan.getCompletionThreshold(),
                studyPlan.getDailyDeadlineTime(),
                studyPlan.getAttentionTrackingEnabled(),
                studyPlan.getMinFocusSecondsPerWord(),
                studyPlan.getMaxFocusSecondsPerWord(),
                studyPlan.getLongStayWarningSeconds(),
                studyPlan.getIdleTimeoutSeconds(),
                studyPlan.getStatus(),
                studentCount,
                studyPlan.getCreatedAt(),
                studyPlan.getUpdatedAt()
        );
    }

    private StudyPlanStudentSummaryResponse toPlanStudentSummary(
            StudentStudyPlan studentStudyPlan,
            AppUser student,
            StudyDayTask studyDayTask,
            LocalDate taskDate) {
        return new StudyPlanStudentSummaryResponse(
                student.getId(),
                student.getDisplayName(),
                studentStudyPlan.getId(),
                studentStudyPlan.getStatus(),
                taskDate,
                studyDayTask == null ? StudyDayTaskStatus.NOT_STARTED : studyDayTask.getStatus(),
                studyDayTask == null ? 0 : studyDayTask.getCompletedCount(),
                studyDayTask == null ? 0 : totalTaskCount(studyDayTask),
                studyDayTask == null ? BigDecimal.ZERO : studyDayTask.getCompletionRate(),
                studyDayTask == null ? 0 : studyDayTask.getTotalFocusSeconds(),
                studyDayTask == null ? BigDecimal.ZERO : studyDayTask.getAvgFocusSecondsPerWord(),
                studyDayTask == null ? BigDecimal.ZERO : studyDayTask.getAttentionScore(),
                studentStudyPlan.getCurrentStreak(),
                studentStudyPlan.getLastStudyAt()
        );
    }

    private StudentStudyPlanSummaryResponse toStudentStudyPlanSummary(
            StudentStudyPlan studentStudyPlan,
            StudyPlan studyPlan,
            Dictionary dictionary,
            StudyDayTask studyDayTask,
            LocalDate taskDate) {
        return new StudentStudyPlanSummaryResponse(
                studentStudyPlan.getId(),
                studyPlan.getId(),
                studyPlan.getName(),
                studyPlan.getCreatedAt(),
                dictionary.getId(),
                dictionary.getName(),
                studentStudyPlan.getStatus(),
                studentStudyPlan.getOverallProgress(),
                studentStudyPlan.getCurrentStreak(),
                studentStudyPlan.getLastStudyAt(),
                taskDate,
                studyDayTask == null ? StudyDayTaskStatus.NOT_STARTED : studyDayTask.getStatus(),
                studyDayTask == null ? 0 : totalTaskCount(studyDayTask),
                studyDayTask == null ? 0 : studyDayTask.getCompletedCount(),
                studyDayTask == null ? BigDecimal.ZERO : studyDayTask.getCompletionRate(),
                studentStudyPlan.getAvgFocusSeconds(),
                studentStudyPlan.getAttentionScore()
        );
    }

    private StudyTaskResponse toStudyTaskResponse(StudentStudyPlan studentStudyPlan, StudyDayTask studyDayTask) {
        List<StudyDayTaskItem> taskItems = studyDayTaskItemRepository.findByStudyDayTaskIdOrderByTaskOrderAsc(studyDayTask.getId());
        List<Long> metaWordIds = taskItems.stream()
                .filter(item -> item.getCompletedAt() == null)
                .map(StudyDayTaskItem::getMetaWordId)
                .toList();
        Map<Long, MetaWord> metaWordMap = loadMetaWords(metaWordIds);
        Map<Long, StudyWordProgress> progressMap = buildStudyWordProgressMap(
                studyWordProgressRepository.findByStudentStudyPlanId(studentStudyPlan.getId()));

        List<StudyTaskItemResponse> queue = new ArrayList<>();
        for (StudyDayTaskItem taskItem : taskItems) {
            if (taskItem.getCompletedAt() != null) {
                continue;
            }
            MetaWord metaWord = metaWordMap.get(taskItem.getMetaWordId());
            StudyWordProgress progress = progressMap.get(taskItem.getMetaWordId());
            queue.add(new StudyTaskItemResponse(
                    taskItem.getId(),
                    taskItem.getMetaWordId(),
                    metaWord == null ? null : metaWord.getWord(),
                    metaWord == null ? null : metaWord.getDefinition(),
                    metaWord == null ? null : metaWord.getTranslation(),
                    metaWord == null ? null : metaWord.getPartOfSpeech(),
                    metaWord == null ? null : metaWord.getExampleSentence(),
                    metaWord == null ? null : metaWord.getPhonetic(),
                    metaWord == null ? null : metaWord.getPhoneticDetail(),
                    metaWord == null ? null : metaWord.getSyllableDetail(),
                    taskItem.getTaskType(),
                    progress == null ? 0 : progress.getPhase(),
                    progress == null ? null : progress.getNextReviewDate()
            ));
        }

        return new StudyTaskResponse(
                studentStudyPlan.getId(),
                studyDayTask.getTaskDate(),
                studyDayTask.getStatus(),
                studyDayTask.getOverdueCount(),
                studyDayTask.getReviewCount(),
                studyDayTask.getNewCount(),
                studyDayTask.getCompletedCount(),
                studyDayTask.getTotalFocusSeconds(),
                studyDayTask.getCompletionRate(),
                studyDayTask.getAvgFocusSecondsPerWord(),
                studyDayTask.getAttentionScore(),
                queue
        );
    }

    private StudentAttentionDailyStatResponse toStudentAttentionDailyStatResponse(StudentAttentionDailyStat dailyStat) {
        return new StudentAttentionDailyStatResponse(
                dailyStat.getTaskDate(),
                dailyStat.getWordsVisited(),
                dailyStat.getWordsCompleted(),
                dailyStat.getTotalFocusSeconds(),
                dailyStat.getAvgFocusSecondsPerWord(),
                dailyStat.getMedianFocusSecondsPerWord(),
                dailyStat.getMaxFocusSecondsPerWord(),
                dailyStat.getLongStayWordCount(),
                dailyStat.getIdleInterruptCount(),
                dailyStat.getAttentionScore()
        );
    }

    private StudentStudyPlan createStudentStudyPlan(Long studyPlanId, Long studentId, LocalDateTime joinedAt) {
        StudentStudyPlan studentStudyPlan = new StudentStudyPlan();
        studentStudyPlan.setStudyPlanId(studyPlanId);
        studentStudyPlan.setStudentId(studentId);
        studentStudyPlan.setStatus(StudentStudyPlanStatus.ACTIVE);
        studentStudyPlan.setJoinedAt(joinedAt);
        return studentStudyPlan;
    }

    private StudyWordProgress createProgressForExistingItem(
            Long studentStudyPlanId,
            Long metaWordId,
            LocalDate taskDate,
            StudyDayTaskItem taskItem) {
        StudyWordProgress studyWordProgress = new StudyWordProgress();
        studyWordProgress.setStudentStudyPlanId(studentStudyPlanId);
        studyWordProgress.setMetaWordId(metaWordId);
        studyWordProgress.setAssignedDate(taskDate);
        studyWordProgress.setStatus(taskItem.getTaskType() == StudyTaskType.NEW_LEARN
                ? StudyWordProgressStatus.NEW
                : StudyWordProgressStatus.REVIEWING);
        return studyWordProgress;
    }

    private void updateProgress(
            StudyWordProgress studyWordProgress,
            StudyRecordResult result,
            List<Integer> reviewIntervals,
            LocalDateTime reviewTime,
            int focusSeconds) {
        int currentPhase = studyWordProgress.getPhase() == null ? 0 : studyWordProgress.getPhase();
        int currentTotalReviews = studyWordProgress.getTotalReviews() == null ? 0 : studyWordProgress.getTotalReviews();
        int nextTotalReviews = currentTotalReviews + 1;
        studyWordProgress.setTotalReviews(nextTotalReviews);
        studyWordProgress.setLastReviewAt(reviewTime);
        studyWordProgress.setLastResult(result);
        studyWordProgress.setLastFocusSeconds(focusSeconds);
        studyWordProgress.setMaxFocusSeconds(Math.max(
                studyWordProgress.getMaxFocusSeconds() == null ? 0 : studyWordProgress.getMaxFocusSeconds(),
                focusSeconds));
        studyWordProgress.setAvgFocusSeconds(updateAverage(
                studyWordProgress.getAvgFocusSeconds(),
                currentTotalReviews,
                focusSeconds));

        if (result == StudyRecordResult.CORRECT) {
            studyWordProgress.setCorrectTimes((studyWordProgress.getCorrectTimes() == null ? 0 : studyWordProgress.getCorrectTimes()) + 1);
            int nextPhase = Math.min(currentPhase + 1, reviewIntervals.size() - 1);
            studyWordProgress.setPhase(nextPhase);
            if (nextPhase >= reviewIntervals.size() - 1) {
                studyWordProgress.setStatus(StudyWordProgressStatus.MASTERED);
                studyWordProgress.setNextReviewDate(null);
            } else {
                studyWordProgress.setStatus(StudyWordProgressStatus.REVIEWING);
                studyWordProgress.setNextReviewDate(reviewTime.toLocalDate().plusDays(reviewIntervals.get(nextPhase)));
            }
        } else {
            studyWordProgress.setWrongTimes((studyWordProgress.getWrongTimes() == null ? 0 : studyWordProgress.getWrongTimes()) + 1);
            int fallbackPhase = currentPhase <= 1 ? 1 : currentPhase - 1;
            fallbackPhase = Math.min(fallbackPhase, reviewIntervals.size() - 1);
            studyWordProgress.setPhase(fallbackPhase);
            studyWordProgress.setStatus(StudyWordProgressStatus.REVIEWING);
            studyWordProgress.setNextReviewDate(reviewTime.toLocalDate().plusDays(reviewIntervals.get(fallbackPhase)));
        }

        int correctTimes = studyWordProgress.getCorrectTimes() == null ? 0 : studyWordProgress.getCorrectTimes();
        int totalReviews = studyWordProgress.getTotalReviews() == null ? 0 : studyWordProgress.getTotalReviews();
        studyWordProgress.setMasteryLevel(percentage(correctTimes, totalReviews));
    }

    private void refreshDailyStats(
            StudentStudyPlan studentStudyPlan,
            StudyPlan studyPlan,
            StudyDayTask studyDayTask,
            LocalDate taskDate) {
        List<StudyRecord> studyRecords = studyRecordRepository.findByStudentStudyPlanIdAndTaskDate(studentStudyPlan.getId(), taskDate);
        int totalFocusSeconds = studyRecords.stream()
                .map(StudyRecord::getFocusSeconds)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        int totalDurationSeconds = studyRecords.stream()
                .map(StudyRecord::getDurationSeconds)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        int totalIdleSeconds = studyRecords.stream()
                .map(StudyRecord::getIdleSeconds)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        int totalInteractions = studyRecords.stream()
                .map(StudyRecord::getInteractionCount)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        List<Integer> focusValues = studyRecords.stream()
                .map(StudyRecord::getFocusSeconds)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        int maxFocusSeconds = focusValues.isEmpty() ? 0 : focusValues.get(focusValues.size() - 1);
        int wordsVisited = (int) studyRecords.stream()
                .map(StudyRecord::getMetaWordId)
                .distinct()
                .count();
        int wordsCompleted = (int) studyDayTaskItemRepository.countByStudyDayTaskIdAndCompletedAtIsNotNull(studyDayTask.getId());
        int longStayWordCount = (int) studyRecords.stream()
                .map(StudyRecord::getFocusSeconds)
                .filter(Objects::nonNull)
                .filter(focusSeconds -> focusSeconds >= studyPlan.getLongStayWarningSeconds())
                .count();
        int idleInterruptCount = (int) studyRecords.stream()
                .filter(record -> record.getIdleSeconds() != null && record.getIdleSeconds() > 0)
                .count();
        BigDecimal avgFocusSeconds = average(BigDecimal.valueOf(totalFocusSeconds), wordsVisited);
        BigDecimal medianFocusSeconds = calculateMedian(focusValues);
        BigDecimal attentionScore = calculateAttentionScore(
                totalDurationSeconds,
                totalFocusSeconds,
                totalIdleSeconds,
                totalInteractions,
                wordsVisited,
                longStayWordCount,
                studyPlan);

        studyDayTask.setTotalFocusSeconds(totalFocusSeconds);
        studyDayTask.setAvgFocusSecondsPerWord(avgFocusSeconds);
        studyDayTask.setMaxFocusSecondsPerWord(maxFocusSeconds);
        studyDayTask.setAttentionScore(attentionScore);
        studyDayTask.setIdleInterruptCount(idleInterruptCount);

        StudentAttentionDailyStat dailyStat = studentAttentionDailyStatRepository
                .findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(studentStudyPlan.getId(), taskDate).stream()
                .max(Comparator.comparing(StudentAttentionDailyStat::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StudentAttentionDailyStat::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseGet(StudentAttentionDailyStat::new);
        dailyStat.setStudentStudyPlanId(studentStudyPlan.getId());
        dailyStat.setTaskDate(taskDate);
        dailyStat.setWordsVisited(wordsVisited);
        dailyStat.setWordsCompleted(wordsCompleted);
        dailyStat.setTotalFocusSeconds(totalFocusSeconds);
        dailyStat.setAvgFocusSecondsPerWord(avgFocusSeconds);
        dailyStat.setMedianFocusSecondsPerWord(medianFocusSeconds);
        dailyStat.setMaxFocusSecondsPerWord(maxFocusSeconds);
        dailyStat.setLongStayWordCount(longStayWordCount);
        dailyStat.setIdleInterruptCount(idleInterruptCount);
        dailyStat.setAttentionScore(attentionScore);
        studentAttentionDailyStatRepository.save(dailyStat);
    }

    private void refreshStudentStudyPlanMetrics(StudentStudyPlan studentStudyPlan, StudyPlan studyPlan) {
        long totalWords = dictionaryWordRepository.countDistinctMetaWordIdByDictionaryId(studyPlan.getDictionaryId());
        long reviewedWords = buildStudyWordProgressMap(studyWordProgressRepository.findByStudentStudyPlanId(studentStudyPlan.getId()))
                .values().stream()
                .filter(progress -> progress.getLastReviewAt() != null)
                .count();
        studentStudyPlan.setOverallProgress(percentage(reviewedWords, totalWords));

        List<StudentAttentionDailyStat> dailyStats = studentAttentionDailyStatRepository
                .findByStudentStudyPlanIdOrderByTaskDateDesc(studentStudyPlan.getId());
        BigDecimal avgFocusTotal = dailyStats.stream()
                .map(StudentAttentionDailyStat::getAvgFocusSecondsPerWord)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal attentionTotal = dailyStats.stream()
                .map(StudentAttentionDailyStat::getAttentionScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long dayCount = dailyStats.size();
        studentStudyPlan.setAvgFocusSeconds(average(avgFocusTotal, dayCount));
        studentStudyPlan.setAttentionScore(average(attentionTotal, dayCount));
    }

    private void updateTaskStatusFromItems(StudentStudyPlan studentStudyPlan, StudyDayTask studyDayTask) {
        int totalTaskCount = totalTaskCount(studyDayTask);
        if (totalTaskCount == 0) {
            studyDayTask.setCompletionRate(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP));
            if (studyDayTask.getStatus() != StudyDayTaskStatus.COMPLETED) {
                studyDayTask.setStatus(StudyDayTaskStatus.COMPLETED);
                studyDayTask.setCompletedAt(studyDayTask.getCompletedAt() == null ? studyDayTask.getCreatedAt() : studyDayTask.getCompletedAt());
            }
            return;
        }

        studyDayTask.setCompletionRate(percentage(studyDayTask.getCompletedCount(), totalTaskCount));
        if (studyDayTask.getCompletedCount() >= totalTaskCount) {
            if (studyDayTask.getStatus() != StudyDayTaskStatus.COMPLETED) {
                studyDayTask.setStatus(StudyDayTaskStatus.COMPLETED);
                studyDayTask.setCompletedAt(LocalDateTime.now());
                studentStudyPlan.setCompletedDays(studentStudyPlan.getCompletedDays() + 1);
            }
        } else if (studyDayTask.getCompletedCount() > 0 && studyDayTask.getStatus() == StudyDayTaskStatus.NOT_STARTED) {
            studyDayTask.setStatus(StudyDayTaskStatus.IN_PROGRESS);
        }
    }

    private void updateStreak(StudentStudyPlan studentStudyPlan, LocalDate taskDate, LocalDateTime now) {
        LocalDate lastStudyDate = studentStudyPlan.getLastStudyAt() == null
                ? null
                : studentStudyPlan.getLastStudyAt().toLocalDate();
        if (lastStudyDate == null) {
            studentStudyPlan.setCurrentStreak(1);
        } else if (lastStudyDate.isEqual(taskDate)) {
            return;
        } else if (lastStudyDate.plusDays(1).isEqual(taskDate)) {
            studentStudyPlan.setCurrentStreak(studentStudyPlan.getCurrentStreak() + 1);
        } else {
            studentStudyPlan.setCurrentStreak(1);
        }
        studentStudyPlan.setLastStudyAt(now);
    }

    private void markExpiredTasks(StudentStudyPlan studentStudyPlan, LocalDate taskDate) {
        List<StudyDayTask> expiredTasks = studyDayTaskRepository
                .findByStudentStudyPlanIdAndTaskDateBeforeOrderByTaskDateAsc(studentStudyPlan.getId(), taskDate);
        boolean updated = false;
        for (StudyDayTask expiredTask : expiredTasks) {
            if (expiredTask.getStatus() == StudyDayTaskStatus.COMPLETED || expiredTask.getStatus() == StudyDayTaskStatus.MISSED) {
                continue;
            }

            if (expiredTask.getCompletedCount() >= totalTaskCount(expiredTask)) {
                expiredTask.setStatus(StudyDayTaskStatus.COMPLETED);
                updated = true;
                continue;
            }

            expiredTask.setStatus(StudyDayTaskStatus.MISSED);
            updated = true;
            studentStudyPlan.setMissedDays(studentStudyPlan.getMissedDays() + 1);
            studentStudyPlan.setCurrentStreak(0);
        }

        if (updated) {
            studyDayTaskRepository.saveAll(expiredTasks);
            studentStudyPlanRepository.save(studentStudyPlan);
        }
    }

    private StudyDayTask getOrCreateTodayTask(StudentStudyPlan studentStudyPlan, StudyPlan studyPlan, LocalDate taskDate) {
        Optional<StudyDayTask> existingTask = findStudyDayTask(studentStudyPlan.getId(), taskDate);
        if (existingTask.isPresent()) {
            return existingTask.get();
        }

        try {
            return generateTodayTask(studentStudyPlan, studyPlan, taskDate);
        } catch (DataIntegrityViolationException ex) {
            return findStudyDayTask(studentStudyPlan.getId(), taskDate)
                    .orElseThrow(() -> ex);
        }
    }

    private StudyDayTask generateTodayTask(StudentStudyPlan studentStudyPlan, StudyPlan studyPlan, LocalDate taskDate) {
        List<StudyWordProgress> existingProgresses = studyWordProgressRepository.findByStudentStudyPlanId(studentStudyPlan.getId());
        Map<Long, StudyWordProgress> progressMap = buildStudyWordProgressMap(existingProgresses);

        List<StudyWordProgress> reviewCandidates = existingProgresses.stream()
                .filter(progress -> isDueOn(progress, taskDate))
                .filter(progress -> progress.getLastReviewAt() == null
                        || !progress.getLastReviewAt().toLocalDate().isEqual(taskDate))
                .sorted(Comparator.comparing(this::resolveDueDate).thenComparing(StudyWordProgress::getMetaWordId))
                .limit(studyPlan.getDailyReviewLimit())
                .toList();

        List<Long> newWordIds = resolveOrderedNewWordIds(
                studyPlan.getDictionaryId(),
                progressMap,
                studyPlan.getDailyNewCount());

        List<StudyWordProgress> newProgresses = new ArrayList<>();
        for (Long newWordId : newWordIds) {
            StudyWordProgress progress = new StudyWordProgress();
            progress.setStudentStudyPlanId(studentStudyPlan.getId());
            progress.setMetaWordId(newWordId);
            progress.setAssignedDate(taskDate);
            progress.setStatus(StudyWordProgressStatus.NEW);
            newProgresses.add(progress);
        }
        if (!newProgresses.isEmpty()) {
            studyWordProgressRepository.saveAll(newProgresses);
        }

        int overdueCount = (int) reviewCandidates.stream()
                .filter(progress -> resolveDueDate(progress).isBefore(taskDate))
                .count();
        int reviewCount = reviewCandidates.size() - overdueCount;

        StudyDayTask studyDayTask = new StudyDayTask();
        studyDayTask.setStudentStudyPlanId(studentStudyPlan.getId());
        studyDayTask.setTaskDate(taskDate);
        studyDayTask.setNewCount(newWordIds.size());
        studyDayTask.setReviewCount(reviewCount);
        studyDayTask.setOverdueCount(overdueCount);
        studyDayTask.setCompletedCount(0);
        studyDayTask.setCompletionRate(BigDecimal.ZERO);
        studyDayTask.setDeadlineAt(taskDate.atTime(studyPlan.getDailyDeadlineTime()));
        studyDayTask.setStatus(totalGeneratedTaskCount(reviewCandidates.size(), newWordIds.size()) == 0
                ? StudyDayTaskStatus.COMPLETED
                : StudyDayTaskStatus.NOT_STARTED);
        StudyDayTask savedStudyDayTask = studyDayTaskRepository.save(studyDayTask);

        List<StudyDayTaskItem> taskItems = new ArrayList<>();
        int taskOrder = 1;
        for (StudyWordProgress reviewCandidate : reviewCandidates) {
            taskItems.add(new StudyDayTaskItem(
                    null,
                    savedStudyDayTask.getId(),
                    reviewCandidate.getMetaWordId(),
                    resolveTaskType(reviewCandidate, taskDate),
                    taskOrder++,
                    null,
                    null
            ));
        }
        for (Long newWordId : newWordIds) {
            taskItems.add(new StudyDayTaskItem(
                    null,
                    savedStudyDayTask.getId(),
                    newWordId,
                    StudyTaskType.NEW_LEARN,
                    taskOrder++,
                    null,
                    null
            ));
        }
        if (!taskItems.isEmpty()) {
            studyDayTaskItemRepository.saveAll(taskItems);
        } else {
            savedStudyDayTask.setCompletionRate(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP));
            savedStudyDayTask.setCompletedAt(resolveNow(studyPlan));
            studyDayTaskRepository.save(savedStudyDayTask);
        }

        return savedStudyDayTask;
    }

    private List<Long> resolveOrderedNewWordIds(
            Long dictionaryId,
            Map<Long, StudyWordProgress> progressMap,
            Integer dailyNewCount) {
        Set<Long> selectedMetaWordIds = new LinkedHashSet<>();
        for (DictionaryWord dictionaryWord : dictionaryWordRepository.findByDictionaryIdOrderByDisplayOrder(dictionaryId)) {
            Long metaWordId = dictionaryWord.getMetaWordId();
            if (progressMap.containsKey(metaWordId) || selectedMetaWordIds.contains(metaWordId)) {
                continue;
            }
            selectedMetaWordIds.add(metaWordId);
            if (selectedMetaWordIds.size() >= dailyNewCount) {
                break;
            }
        }
        return List.copyOf(selectedMetaWordIds);
    }

    private boolean isDueOn(StudyWordProgress progress, LocalDate taskDate) {
        LocalDate dueDate = resolveDueDate(progress);
        return dueDate != null && !dueDate.isAfter(taskDate);
    }

    private LocalDate resolveDueDate(StudyWordProgress progress) {
        if (progress.getNextReviewDate() != null) {
            return progress.getNextReviewDate();
        }
        if (progress.getStatus() == StudyWordProgressStatus.NEW && progress.getLastReviewAt() == null) {
            return progress.getAssignedDate();
        }
        return null;
    }

    private StudyTaskType resolveTaskType(StudyWordProgress progress, LocalDate taskDate) {
        LocalDate dueDate = resolveDueDate(progress);
        if (dueDate != null && dueDate.isBefore(taskDate)) {
            return StudyTaskType.OVERDUE_REVIEW;
        }
        return StudyTaskType.TODAY_REVIEW;
    }

    private boolean canGenerateTodayTask(StudyPlan studyPlan, LocalDate taskDate) {
        return !taskDate.isBefore(studyPlan.getStartDate())
                && (studyPlan.getEndDate() == null || !taskDate.isAfter(studyPlan.getEndDate()));
    }

    private void ensurePlanActiveOnDate(StudyPlan studyPlan, LocalDate taskDate) {
        if (taskDate.isBefore(studyPlan.getStartDate())) {
            throw new BadRequestException("Study plan has not started yet");
        }
        if (studyPlan.getEndDate() != null && taskDate.isAfter(studyPlan.getEndDate())) {
            throw new BadRequestException("Study plan has already ended");
        }
    }

    private Map<Long, MetaWord> loadMetaWords(List<Long> metaWordIds) {
        if (metaWordIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, MetaWord> metaWordMap = new LinkedHashMap<>();
        for (MetaWord metaWord : metaWordRepository.findAllById(metaWordIds)) {
            metaWordMap.put(metaWord.getId(), metaWord);
        }
        return metaWordMap;
    }

    private List<Integer> normalizeReviewIntervals(List<Integer> reviewIntervals) {
        if (reviewIntervals == null || reviewIntervals.isEmpty()) {
            throw new BadRequestException("reviewIntervals cannot be empty");
        }

        List<Integer> normalized = new ArrayList<>();
        Integer previous = null;
        for (Integer interval : reviewIntervals) {
            if (interval == null || interval < 0) {
                throw new BadRequestException("reviewIntervals must contain non-negative integers");
            }
            if (previous != null && interval < previous) {
                throw new BadRequestException("reviewIntervals must be in non-decreasing order");
            }
            normalized.add(interval);
            previous = interval;
        }

        if (!Objects.equals(normalized.get(0), 0)) {
            throw new BadRequestException("reviewIntervals must start with 0");
        }
        return normalized;
    }

    private String serializeReviewIntervals(List<Integer> reviewIntervals) {
        try {
            return objectMapper.writeValueAsString(reviewIntervals);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Failed to serialize reviewIntervals");
        }
    }

    private List<Integer> parseReviewIntervals(String reviewIntervalsJson) {
        try {
            return objectMapper.readValue(reviewIntervalsJson, new TypeReference<List<Integer>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Failed to parse reviewIntervals");
        }
    }

    private BigDecimal updateAverage(BigDecimal currentAverage, int currentCount, int nextValue) {
        BigDecimal safeAverage = currentAverage == null ? BigDecimal.ZERO : currentAverage;
        BigDecimal total = safeAverage.multiply(BigDecimal.valueOf(currentCount)).add(BigDecimal.valueOf(nextValue));
        return average(total, currentCount + 1);
    }

    private BigDecimal calculateMedian(List<Integer> sortedValues) {
        if (sortedValues.isEmpty()) {
            return BigDecimal.ZERO;
        }

        int middleIndex = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return BigDecimal.valueOf(sortedValues.get(middleIndex)).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal left = BigDecimal.valueOf(sortedValues.get(middleIndex - 1));
        BigDecimal right = BigDecimal.valueOf(sortedValues.get(middleIndex));
        return left.add(right).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAttentionScore(
            int totalDurationSeconds,
            int totalFocusSeconds,
            int totalIdleSeconds,
            int totalInteractions,
            int wordsVisited,
            int longStayWordCount,
            StudyPlan studyPlan) {
        if (wordsVisited <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        double effectiveDuration = Math.max(totalDurationSeconds, totalFocusSeconds + totalIdleSeconds);
        double activeRatio = effectiveDuration <= 0 ? 0 : Math.min(1.0, totalFocusSeconds / effectiveDuration);
        double averageFocus = totalFocusSeconds / (double) wordsVisited;
        double normalizedFocus = studyPlan.getMaxFocusSecondsPerWord() <= 0
                ? 0
                : Math.min(1.0, averageFocus / studyPlan.getMaxFocusSecondsPerWord());
        double interactionRatio = Math.min(1.0, totalInteractions / Math.max(1.0, wordsVisited * 3.0));
        double longStayPenalty = Math.min(20.0, longStayWordCount * 3.0);

        double score = activeRatio * 50.0 + normalizedFocus * 30.0 + interactionRatio * 20.0 - longStayPenalty;
        score = Math.max(0.0, Math.min(100.0, score));
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal average(BigDecimal total, long count) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private int totalTaskCount(StudyDayTask studyDayTask) {
        return totalGeneratedTaskCount(
                studyDayTask.getReviewCount() + studyDayTask.getOverdueCount(),
                studyDayTask.getNewCount());
    }

    private int totalGeneratedTaskCount(int reviewItemCount, int newItemCount) {
        return reviewItemCount + newItemCount;
    }

    private int normalizeFocusSeconds(Integer focusSeconds, Integer durationSeconds, StudyPlan studyPlan) {
        int safeFocusSeconds = focusSeconds == null ? 0 : Math.max(0, focusSeconds);
        if (safeFocusSeconds > 0 && safeFocusSeconds < studyPlan.getMinFocusSecondsPerWord()) {
            safeFocusSeconds = 0;
        }
        safeFocusSeconds = Math.min(safeFocusSeconds, studyPlan.getMaxFocusSecondsPerWord());
        if (durationSeconds != null) {
            safeFocusSeconds = Math.min(safeFocusSeconds, Math.max(0, durationSeconds));
        }
        return safeFocusSeconds;
    }

    private int normalizeIdleSeconds(Integer idleSeconds, Integer durationSeconds, int focusSeconds) {
        int safeIdleSeconds = idleSeconds == null ? 0 : Math.max(0, idleSeconds);
        if (durationSeconds != null) {
            safeIdleSeconds = Math.min(safeIdleSeconds, Math.max(0, durationSeconds - focusSeconds));
        }
        return safeIdleSeconds;
    }

    private int normalizeDurationSeconds(Integer durationSeconds, int focusSeconds, int idleSeconds) {
        if (durationSeconds == null) {
            return focusSeconds + idleSeconds;
        }
        return Math.max(durationSeconds, focusSeconds + idleSeconds);
    }

    private LocalDate resolveToday(StudyPlan studyPlan) {
        return ZonedDateTime.now(resolveZoneId(studyPlan)).toLocalDate();
    }

    private LocalDateTime resolveNow(StudyPlan studyPlan) {
        return ZonedDateTime.now(resolveZoneId(studyPlan)).toLocalDateTime();
    }

    private ZoneId resolveZoneId(StudyPlan studyPlan) {
        try {
            return ZoneId.of(studyPlan.getTimezone());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid timezone: " + studyPlan.getTimezone());
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private StudentStudyPlan getOrCreateStudentStudyPlan(Long studyPlanId, Long studentId, LocalDateTime joinedAt) {
        Optional<StudentStudyPlan> existingStudentStudyPlan = findStudentStudyPlanByPlanAndStudent(studyPlanId, studentId);
        if (existingStudentStudyPlan.isPresent()) {
            return existingStudentStudyPlan.get();
        }

        try {
            return studentStudyPlanRepository.save(createStudentStudyPlan(studyPlanId, studentId, joinedAt));
        } catch (DataIntegrityViolationException ex) {
            return findStudentStudyPlanByPlanAndStudent(studyPlanId, studentId)
                    .orElseThrow(() -> ex);
        }
    }

    private Optional<StudentStudyPlan> findStudentStudyPlanByPlanAndStudent(Long studyPlanId, Long studentId) {
        return studentStudyPlanRepository.findByStudyPlanIdAndStudentIdOrderByCreatedAtAsc(studyPlanId, studentId).stream()
                .max(Comparator.comparing(StudentStudyPlan::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StudentStudyPlan::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private Optional<StudyDayTask> findStudyDayTask(Long studentStudyPlanId, LocalDate taskDate) {
        return studyDayTaskRepository.findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(studentStudyPlanId, taskDate).stream()
                .max(Comparator.comparingInt((StudyDayTask task) -> safeInt(task.getCompletedCount()))
                        .thenComparing(StudyDayTask::getCompletionRate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(task -> safeInt(task.getTotalFocusSeconds()))
                        .thenComparing(StudyDayTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StudyDayTask::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private Optional<StudyWordProgress> findStudyWordProgress(Long studentStudyPlanId, Long metaWordId) {
        return selectPreferredStudyWordProgress(
                studyWordProgressRepository.findByStudentStudyPlanIdAndMetaWordId(studentStudyPlanId, metaWordId));
    }

    private Map<Long, StudyWordProgress> buildStudyWordProgressMap(List<StudyWordProgress> studyWordProgresses) {
        return studyWordProgresses.stream()
                .collect(Collectors.toMap(
                        StudyWordProgress::getMetaWordId,
                        progress -> progress,
                        this::selectPreferredStudyWordProgress));
    }

    private Optional<StudyWordProgress> selectPreferredStudyWordProgress(List<StudyWordProgress> studyWordProgresses) {
        return studyWordProgresses.stream()
                .sorted(Comparator.comparingInt((StudyWordProgress progress) -> safeInt(progress.getTotalReviews())).reversed()
                        .thenComparing(StudyWordProgress::getMasteryLevel, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StudyWordProgress::getLastReviewAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StudyWordProgress::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst();
    }

    private StudyWordProgress selectPreferredStudyWordProgress(StudyWordProgress left, StudyWordProgress right) {
        return selectPreferredStudyWordProgress(List.of(left, right)).orElse(left);
    }

    private Optional<StudyDayTaskItem> findStudyDayTaskItem(Long studyDayTaskId, Long metaWordId) {
        return studyDayTaskItemRepository.findByStudyDayTaskIdAndMetaWordIdOrderByCreatedAtAsc(studyDayTaskId, metaWordId).stream()
                .min(Comparator.comparing(StudyDayTaskItem::getTaskOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StudyDayTaskItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(StudyDayTaskItem::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
