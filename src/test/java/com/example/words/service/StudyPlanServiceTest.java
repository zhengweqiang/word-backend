package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.example.words.dto.AppendStudyPlanStudentsRequest;
import com.example.words.dto.CreateStudyPlanRequest;
import com.example.words.dto.RecordStudyRequest;
import com.example.words.dto.StudyPlanResponse;
import com.example.words.dto.StudyTaskResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.AttentionState;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.model.ReviewMode;
import com.example.words.model.StudentAttentionDailyStat;
import com.example.words.model.StudentStudyPlan;
import com.example.words.model.StudentStudyPlanStatus;
import com.example.words.model.StudyActionType;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class StudyPlanServiceTest {

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private StudyPlanClassroomRepository studyPlanClassroomRepository;

    @Mock
    private StudentStudyPlanRepository studentStudyPlanRepository;

    @Mock
    private StudyDayTaskRepository studyDayTaskRepository;

    @Mock
    private StudyDayTaskItemRepository studyDayTaskItemRepository;

    @Mock
    private StudyWordProgressRepository studyWordProgressRepository;

    @Mock
    private StudyRecordRepository studyRecordRepository;

    @Mock
    private StudentAttentionDailyStatRepository studentAttentionDailyStatRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;

    @Mock
    private DictionaryService dictionaryService;

    @Mock
    private DictionaryAssignmentService dictionaryAssignmentService;

    @Mock
    private DictionaryWordRepository dictionaryWordRepository;

    @Mock
    private MetaWordRepository metaWordRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private UserService userService;

    @Mock
    private StudentWordMemoryService studentWordMemoryService;

    private StudyPlanService studyPlanService;

    @BeforeEach
    void setUp() {
        studyPlanService = new StudyPlanService(
                studyPlanRepository,
                studyPlanClassroomRepository,
                studentStudyPlanRepository,
                studyDayTaskRepository,
                studyDayTaskItemRepository,
                studyWordProgressRepository,
                studyRecordRepository,
                studentAttentionDailyStatRepository,
                classroomRepository,
                classroomMemberRepository,
                dictionaryService,
                dictionaryAssignmentService,
                dictionaryWordRepository,
                metaWordRepository,
                accessControlService,
                userService,
                studentWordMemoryService,
                new ObjectMapper()
        );
    }

    @Test
    void createStudyPlanShouldPersistDraftPlan() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        CreateStudyPlanRequest request = new CreateStudyPlanRequest(
                "高一春季计划",
                "按遗忘曲线推进",
                10L,
                List.of(100L, 101L),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "Asia/Shanghai",
                20,
                40,
                ReviewMode.EBBINGHAUS,
                List.of(0, 1, 2, 4),
                BigDecimal.valueOf(100),
                LocalTime.of(21, 30),
                true,
                3,
                120,
                60,
                15
        );

        Dictionary dictionary = new Dictionary();
        dictionary.setId(10L);
        dictionary.setName("高考词汇");

        Classroom classroom1 = classroom(100L, "一班", 7L);
        Classroom classroom2 = classroom(101L, "二班", 7L);

        when(dictionaryService.findById(10L)).thenReturn(Optional.of(dictionary));
        when(dictionaryService.findVisibleDictionariesForClassrooms(List.of(100L, 101L), teacher))
                .thenReturn(List.of(dictionary));
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom1));
        when(classroomRepository.findById(101L)).thenReturn(Optional.of(classroom2));
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(invocation -> {
            StudyPlan studyPlan = invocation.getArgument(0);
            studyPlan.setId(55L);
            studyPlan.setCreatedAt(LocalDateTime.now());
            studyPlan.setUpdatedAt(LocalDateTime.now());
            return studyPlan;
        });

        StudyPlanResponse response = studyPlanService.createStudyPlan(request, teacher);

        assertEquals(55L, response.getId());
        assertEquals("高一春季计划", response.getName());
        assertEquals(StudyPlanStatus.DRAFT, response.getStatus());
        assertEquals(List.of(100L, 101L), response.getClassroomIds());
        assertEquals(List.of(0, 1, 2, 4), response.getReviewIntervals());
        verify(studyPlanClassroomRepository, times(2)).save(any(StudyPlanClassroom.class));
        verify(studyPlanRepository).save(any(StudyPlan.class));
    }

    @Test
    void createStudyPlanShouldRejectDictionaryOutsideSelectedClassroomIntersection() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        CreateStudyPlanRequest request = new CreateStudyPlanRequest(
                "高一春季计划",
                "按遗忘曲线推进",
                10L,
                List.of(100L, 101L),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "Asia/Shanghai",
                20,
                40,
                ReviewMode.EBBINGHAUS,
                List.of(0, 1, 2, 4),
                BigDecimal.valueOf(100),
                LocalTime.of(21, 30),
                true,
                3,
                120,
                60,
                15
        );

        Dictionary dictionary = new Dictionary();
        dictionary.setId(10L);
        dictionary.setName("高考词汇");

        Classroom classroom1 = classroom(100L, "一班", 7L);
        Classroom classroom2 = classroom(101L, "二班", 7L);

        when(dictionaryService.findById(10L)).thenReturn(Optional.of(dictionary));
        when(dictionaryService.findVisibleDictionariesForClassrooms(List.of(100L, 101L), teacher))
                .thenReturn(List.of());
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom1));
        when(classroomRepository.findById(101L)).thenReturn(Optional.of(classroom2));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> studyPlanService.createStudyPlan(request, teacher)
        );

        assertEquals("dictionaryId is not associated with all selected classrooms", exception.getMessage());
    }

    @Test
    void createStudyPlanShouldRejectArchivedClassroom() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        CreateStudyPlanRequest request = new CreateStudyPlanRequest(
                "高一春季计划",
                "按遗忘曲线推进",
                10L,
                List.of(100L),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "Asia/Shanghai",
                20,
                40,
                ReviewMode.EBBINGHAUS,
                List.of(0, 1, 2, 4),
                BigDecimal.valueOf(100),
                LocalTime.of(21, 30),
                true,
                3,
                120,
                60,
                15
        );

        Classroom classroom = classroom(100L, "一班", 7L);
        classroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> studyPlanService.createStudyPlan(request, teacher)
        );

        assertEquals("Archived classroom cannot be used for study plans", exception.getMessage());
    }

    @Test
    void publishStudyPlanShouldRejectDictionaryNoLongerAvailableForClassrooms() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        StudyPlan studyPlan = studyPlan(55L, 7L, 10L, StudyPlanStatus.DRAFT);
        Dictionary dictionary = dictionary(10L, "高考词汇");
        List<StudyPlanClassroom> planClassrooms = List.of(
                new StudyPlanClassroom(1L, 55L, 100L, null),
                new StudyPlanClassroom(2L, 55L, 101L, null)
        );

        when(studyPlanRepository.findById(55L)).thenReturn(Optional.of(studyPlan));
        when(dictionaryService.findById(10L)).thenReturn(Optional.of(dictionary));
        when(studyPlanClassroomRepository.findByStudyPlanId(55L)).thenReturn(planClassrooms);
        lenient().when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom(100L, "一班", 7L)));
        lenient().when(classroomRepository.findById(101L)).thenReturn(Optional.of(classroom(101L, "二班", 7L)));
        lenient().when(dictionaryService.findVisibleDictionariesForClassrooms(List.of(100L, 101L), teacher))
                .thenReturn(List.of());

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> studyPlanService.publishStudyPlan(55L, teacher)
        );

        assertEquals("dictionaryId is not associated with all selected classrooms", exception.getMessage());
        verify(studyPlanRepository, never()).save(any(StudyPlan.class));
    }

    @Test
    void publishStudyPlanShouldRejectArchivedClassroom() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        StudyPlan studyPlan = studyPlan(55L, 7L, 10L, StudyPlanStatus.DRAFT);
        Dictionary dictionary = dictionary(10L, "高考词汇");
        Classroom archivedClassroom = classroom(100L, "一班", 7L);
        archivedClassroom.setStatus(ClassroomStatus.ARCHIVED);

        when(studyPlanRepository.findById(55L)).thenReturn(Optional.of(studyPlan));
        when(dictionaryService.findById(10L)).thenReturn(Optional.of(dictionary));
        when(studyPlanClassroomRepository.findByStudyPlanId(55L))
                .thenReturn(List.of(new StudyPlanClassroom(1L, 55L, 100L, null)));
        lenient().when(classroomRepository.findById(100L)).thenReturn(Optional.of(archivedClassroom));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> studyPlanService.publishStudyPlan(55L, teacher)
        );

        assertEquals("Archived classroom cannot be used for study plans", exception.getMessage());
        verify(studyPlanRepository, never()).save(any(StudyPlan.class));
    }

    @Test
    void appendStudentsShouldAddCurrentClassroomStudentToPublishedPlan() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        StudyPlan studyPlan = studyPlan(55L, 7L, 10L, StudyPlanStatus.PUBLISHED);
        Dictionary dictionary = dictionary(10L, "高考词汇");
        StudyPlanClassroom planClassroom = new StudyPlanClassroom(1L, 55L, 100L, null);

        when(studyPlanRepository.findById(55L)).thenReturn(Optional.of(studyPlan));
        when(studyPlanClassroomRepository.findByStudyPlanId(55L)).thenReturn(List.of(planClassroom));
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom(100L, "一班", 7L)));
        when(classroomMemberRepository.existsByClassroomIdInAndStudentId(List.of(100L), 22L)).thenReturn(true);
        when(dictionaryService.findById(10L)).thenReturn(Optional.of(dictionary));
        when(studentStudyPlanRepository.findByStudyPlanIdAndStudentIdOrderByCreatedAtAsc(55L, 22L))
                .thenReturn(List.of());
        when(studentStudyPlanRepository.save(any(StudentStudyPlan.class))).thenAnswer(invocation -> {
            StudentStudyPlan studentStudyPlan = invocation.getArgument(0);
            studentStudyPlan.setId(222L);
            return studentStudyPlan;
        });
        when(studentStudyPlanRepository.findByStudyPlanIdOrderByStudentIdAsc(55L))
                .thenReturn(List.of(new StudentStudyPlan(), new StudentStudyPlan()));

        StudyPlanResponse response = studyPlanService.appendStudents(
                55L,
                new AppendStudyPlanStudentsRequest(List.of(22L)),
                teacher
        );

        assertEquals(2L, response.getStudentCount());
        verify(accessControlService).ensureCanAssignDictionaryToStudent(teacher, dictionary, 22L);
        verify(dictionaryAssignmentService).assignDictionaryToStudents(dictionary, teacher, List.of(22L));
        verify(studentStudyPlanRepository).save(any(StudentStudyPlan.class));
    }

    @Test
    void appendStudentsShouldRejectStudentOutsideCurrentPlanClassrooms() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        StudyPlan studyPlan = studyPlan(55L, 7L, 10L, StudyPlanStatus.PUBLISHED);
        StudyPlanClassroom planClassroom = new StudyPlanClassroom(1L, 55L, 100L, null);

        when(studyPlanRepository.findById(55L)).thenReturn(Optional.of(studyPlan));
        when(studyPlanClassroomRepository.findByStudyPlanId(55L)).thenReturn(List.of(planClassroom));
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom(100L, "一班", 7L)));
        when(classroomMemberRepository.existsByClassroomIdInAndStudentId(List.of(100L), 20L)).thenReturn(false);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> studyPlanService.appendStudents(
                        55L,
                        new AppendStudyPlanStudentsRequest(List.of(20L)),
                        teacher
                )
        );

        assertEquals("studentId is not a current member of the study plan classrooms: 20", exception.getMessage());
        verify(dictionaryAssignmentService, never()).assignDictionaryToStudents(any(), any(), any());
        verify(studentStudyPlanRepository, never()).save(any(StudentStudyPlan.class));
    }

    @Test
    void getTodayTaskShouldGenerateReviewAndNewWords() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        AppUser student = new AppUser();
        student.setId(20L);
        student.setRole(UserRole.STUDENT);

        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setId(55L);
        studyPlan.setTeacherId(7L);
        studyPlan.setDictionaryId(10L);
        studyPlan.setName("春季计划");
        studyPlan.setTimezone("Asia/Shanghai");
        studyPlan.setStartDate(today.minusDays(1));
        studyPlan.setDailyNewCount(2);
        studyPlan.setDailyReviewLimit(2);
        studyPlan.setReviewIntervalsJson("[0,1,2]");
        studyPlan.setDailyDeadlineTime(LocalTime.of(21, 30));
        studyPlan.setStatus(StudyPlanStatus.PUBLISHED);

        StudentStudyPlan studentStudyPlan = new StudentStudyPlan();
        studentStudyPlan.setId(200L);
        studentStudyPlan.setStudyPlanId(55L);
        studentStudyPlan.setStudentId(20L);
        studentStudyPlan.setStatus(StudentStudyPlanStatus.ACTIVE);

        StudyWordProgress dueProgress = new StudyWordProgress();
        dueProgress.setId(1L);
        dueProgress.setStudentStudyPlanId(200L);
        dueProgress.setMetaWordId(1L);
        dueProgress.setPhase(1);
        dueProgress.setNextReviewDate(today.minusDays(1));
        dueProgress.setStatus(StudyWordProgressStatus.REVIEWING);

        StudyWordProgress futureProgress = new StudyWordProgress();
        futureProgress.setId(2L);
        futureProgress.setStudentStudyPlanId(200L);
        futureProgress.setMetaWordId(2L);
        futureProgress.setPhase(1);
        futureProgress.setNextReviewDate(today.plusDays(1));
        futureProgress.setStatus(StudyWordProgressStatus.REVIEWING);

        List<StudyWordProgress> generatedProgresses = new ArrayList<>();
        generatedProgresses.add(dueProgress);
        generatedProgresses.add(futureProgress);

        List<StudyDayTaskItem> generatedItems = new ArrayList<>();

        when(studentStudyPlanRepository.findById(200L)).thenReturn(Optional.of(studentStudyPlan));
        when(studyPlanRepository.findById(55L)).thenReturn(Optional.of(studyPlan));
        when(studyDayTaskRepository.findByStudentStudyPlanIdAndTaskDateBeforeOrderByTaskDateAsc(200L, today))
                .thenReturn(List.of());
        when(studyDayTaskRepository.findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(200L, today)).thenReturn(List.of());
        when(studyWordProgressRepository.findByStudentStudyPlanId(200L))
                .thenReturn(List.of(dueProgress, futureProgress), generatedProgresses);
        when(dictionaryWordRepository.findByDictionaryIdOrderByDisplayOrder(10L)).thenReturn(List.of(
                new DictionaryWord(1L, 10L, 1L, (java.time.LocalDateTime) null),
                new DictionaryWord(2L, 10L, 2L, (java.time.LocalDateTime) null),
                new DictionaryWord(3L, 10L, 3L, (java.time.LocalDateTime) null),
                new DictionaryWord(4L, 10L, 4L, (java.time.LocalDateTime) null)
        ));
        when(studyWordProgressRepository.saveAll(any())).thenAnswer(invocation -> {
            List<StudyWordProgress> progressList = invocation.getArgument(0);
            generatedProgresses.addAll(progressList);
            return progressList;
        });
        when(studyDayTaskRepository.save(any(StudyDayTask.class))).thenAnswer(invocation -> {
            StudyDayTask studyDayTask = invocation.getArgument(0);
            studyDayTask.setId(300L);
            studyDayTask.setCreatedAt(LocalDateTime.now());
            return studyDayTask;
        });
        when(studyDayTaskItemRepository.saveAll(any())).thenAnswer(invocation -> {
            List<StudyDayTaskItem> items = invocation.getArgument(0);
            generatedItems.clear();
            generatedItems.addAll(items);
            return items;
        });
        when(studyDayTaskItemRepository.findByStudyDayTaskIdOrderByTaskOrderAsc(300L)).thenAnswer(invocation -> generatedItems);
        when(metaWordRepository.findAllById(List.of(1L, 3L, 4L))).thenReturn(List.of(
                metaWord(1L, "abandon"),
                metaWord(3L, "benefit"),
                metaWord(4L, "capture")
        ));

        StudyTaskResponse response = studyPlanService.getTodayTask(200L, student);

        assertEquals(1, response.getOverdueCount());
        assertEquals(0, response.getReviewCount());
        assertEquals(2, response.getNewCount());
        assertEquals(3, response.getQueue().size());
        assertEquals(StudyTaskType.OVERDUE_REVIEW, response.getQueue().get(0).getTaskType());
        assertEquals(Long.valueOf(1L), response.getQueue().get(0).getMetaWordId());
        assertEquals(StudyTaskType.NEW_LEARN, response.getQueue().get(1).getTaskType());
        assertEquals(StudyTaskType.NEW_LEARN, response.getQueue().get(2).getTaskType());
    }

    @Test
    void recordStudyShouldUpdateTaskAndAttentionStats() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        AtomicReference<StudyRecord> savedRecordRef = new AtomicReference<>();
        AtomicReference<StudentAttentionDailyStat> savedDailyStatRef = new AtomicReference<>();

        AppUser student = new AppUser();
        student.setId(20L);
        student.setRole(UserRole.STUDENT);

        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setId(55L);
        studyPlan.setTeacherId(7L);
        studyPlan.setDictionaryId(10L);
        studyPlan.setName("春季计划");
        studyPlan.setTimezone("Asia/Shanghai");
        studyPlan.setStartDate(today.minusDays(1));
        studyPlan.setDailyNewCount(2);
        studyPlan.setDailyReviewLimit(2);
        studyPlan.setReviewIntervalsJson("[0,1,2]");
        studyPlan.setDailyDeadlineTime(LocalTime.of(21, 30));
        studyPlan.setStatus(StudyPlanStatus.PUBLISHED);
        studyPlan.setMinFocusSecondsPerWord(3);
        studyPlan.setMaxFocusSecondsPerWord(120);
        studyPlan.setLongStayWarningSeconds(60);

        StudentStudyPlan studentStudyPlan = new StudentStudyPlan();
        studentStudyPlan.setId(200L);
        studentStudyPlan.setStudyPlanId(55L);
        studentStudyPlan.setStudentId(20L);
        studentStudyPlan.setStatus(StudentStudyPlanStatus.ACTIVE);

        StudyDayTask studyDayTask = new StudyDayTask();
        studyDayTask.setId(300L);
        studyDayTask.setStudentStudyPlanId(200L);
        studyDayTask.setTaskDate(today);
        studyDayTask.setNewCount(1);
        studyDayTask.setReviewCount(0);
        studyDayTask.setOverdueCount(0);
        studyDayTask.setCompletedCount(0);
        studyDayTask.setStatus(StudyDayTaskStatus.NOT_STARTED);
        studyDayTask.setDeadlineAt(today.atTime(21, 30));

        StudyDayTaskItem studyDayTaskItem = new StudyDayTaskItem();
        studyDayTaskItem.setId(400L);
        studyDayTaskItem.setStudyDayTaskId(300L);
        studyDayTaskItem.setMetaWordId(3L);
        studyDayTaskItem.setTaskType(StudyTaskType.NEW_LEARN);
        studyDayTaskItem.setTaskOrder(1);

        StudyWordProgress studyWordProgress = new StudyWordProgress();
        studyWordProgress.setId(500L);
        studyWordProgress.setStudentStudyPlanId(200L);
        studyWordProgress.setMetaWordId(3L);
        studyWordProgress.setAssignedDate(today);
        studyWordProgress.setPhase(0);
        studyWordProgress.setStatus(StudyWordProgressStatus.NEW);

        when(studentStudyPlanRepository.findById(200L)).thenReturn(Optional.of(studentStudyPlan));
        when(studyPlanRepository.findById(55L)).thenReturn(Optional.of(studyPlan));
        when(studyDayTaskRepository.findByStudentStudyPlanIdAndTaskDateBeforeOrderByTaskDateAsc(200L, today))
                .thenReturn(List.of());
        when(studyDayTaskRepository.findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(200L, today))
                .thenReturn(List.of(studyDayTask));
        when(studyDayTaskItemRepository.findByStudyDayTaskIdAndMetaWordIdOrderByCreatedAtAsc(300L, 3L))
                .thenReturn(List.of(studyDayTaskItem));
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(10L, 3L)).thenReturn(true);
        when(studyRecordRepository.findByStudentStudyPlanIdAndTaskDate(200L, today))
                .thenAnswer(invocation -> savedRecordRef.get() == null ? List.of() : List.of(savedRecordRef.get()));
        when(studyWordProgressRepository.findByStudentStudyPlanIdAndMetaWordId(200L, 3L))
                .thenReturn(List.of(studyWordProgress));
        when(studyWordProgressRepository.save(any(StudyWordProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyRecordRepository.save(any(StudyRecord.class))).thenAnswer(invocation -> {
            StudyRecord studyRecord = invocation.getArgument(0);
            savedRecordRef.set(studyRecord);
            return studyRecord;
        });
        when(studyDayTaskItemRepository.save(any(StudyDayTaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyDayTaskItemRepository.countByStudyDayTaskIdAndCompletedAtIsNotNull(300L)).thenReturn(1L);
        when(studentAttentionDailyStatRepository.findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(200L, today))
                .thenReturn(List.of());
        when(studentAttentionDailyStatRepository.save(any(StudentAttentionDailyStat.class))).thenAnswer(invocation -> {
            StudentAttentionDailyStat dailyStat = invocation.getArgument(0);
            savedDailyStatRef.set(dailyStat);
            return dailyStat;
        });
        when(studentAttentionDailyStatRepository.findByStudentStudyPlanIdOrderByTaskDateDesc(200L))
                .thenAnswer(invocation -> savedDailyStatRef.get() == null ? List.of() : List.of(savedDailyStatRef.get()));
        when(studyDayTaskItemRepository.findByStudyDayTaskIdOrderByTaskOrderAsc(300L)).thenReturn(List.of(studyDayTaskItem));
        when(studyWordProgressRepository.findByStudentStudyPlanId(200L)).thenReturn(List.of(studyWordProgress));

        RecordStudyRequest request = new RecordStudyRequest(
                3L,
                StudyActionType.LEARN,
                StudyRecordResult.CORRECT,
                26,
                22,
                4,
                5,
                AttentionState.FOCUSED
        );

        StudyTaskResponse response = studyPlanService.recordStudy(200L, request, student);

        assertEquals(StudyDayTaskStatus.COMPLETED, response.getStatus());
        assertEquals(1, response.getCompletedCount());
        assertTrue(response.getQueue().isEmpty());
        assertEquals(BigDecimal.valueOf(100).setScale(2), response.getCompletionRate());
        assertNotNull(savedDailyStatRef.get());
        assertEquals(22, savedDailyStatRef.get().getTotalFocusSeconds());
        verify(studyRecordRepository).save(any(StudyRecord.class));
        verify(studentWordMemoryService).recordPlanStudy(
                eq(20L),
                eq(3L),
                eq(null),
                eq(10L),
                eq(StudyRecordResult.CORRECT),
                any(LocalDateTime.class)
        );
        verify(studentAttentionDailyStatRepository).save(any(StudentAttentionDailyStat.class));
    }

    @Test
    void recordStudyShouldPreferMostAdvancedProgressWhenDuplicateRowsExist() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        AtomicReference<StudyRecord> savedRecordRef = new AtomicReference<>();
        AtomicReference<StudentAttentionDailyStat> savedDailyStatRef = new AtomicReference<>();
        AtomicReference<StudyWordProgress> savedProgressRef = new AtomicReference<>();

        AppUser student = new AppUser();
        student.setId(20L);
        student.setRole(UserRole.STUDENT);

        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setId(55L);
        studyPlan.setTeacherId(7L);
        studyPlan.setDictionaryId(10L);
        studyPlan.setName("春季计划");
        studyPlan.setTimezone("Asia/Shanghai");
        studyPlan.setStartDate(today.minusDays(1));
        studyPlan.setDailyNewCount(2);
        studyPlan.setDailyReviewLimit(2);
        studyPlan.setReviewIntervalsJson("[0,1,2]");
        studyPlan.setDailyDeadlineTime(LocalTime.of(21, 30));
        studyPlan.setStatus(StudyPlanStatus.PUBLISHED);
        studyPlan.setMinFocusSecondsPerWord(3);
        studyPlan.setMaxFocusSecondsPerWord(120);
        studyPlan.setLongStayWarningSeconds(60);

        StudentStudyPlan studentStudyPlan = new StudentStudyPlan();
        studentStudyPlan.setId(200L);
        studentStudyPlan.setStudyPlanId(55L);
        studentStudyPlan.setStudentId(20L);
        studentStudyPlan.setStatus(StudentStudyPlanStatus.ACTIVE);

        StudyDayTask studyDayTask = new StudyDayTask();
        studyDayTask.setId(300L);
        studyDayTask.setStudentStudyPlanId(200L);
        studyDayTask.setTaskDate(today);
        studyDayTask.setNewCount(1);
        studyDayTask.setReviewCount(0);
        studyDayTask.setOverdueCount(0);
        studyDayTask.setCompletedCount(0);
        studyDayTask.setStatus(StudyDayTaskStatus.NOT_STARTED);
        studyDayTask.setDeadlineAt(today.atTime(21, 30));

        StudyDayTaskItem studyDayTaskItem = new StudyDayTaskItem();
        studyDayTaskItem.setId(400L);
        studyDayTaskItem.setStudyDayTaskId(300L);
        studyDayTaskItem.setMetaWordId(3L);
        studyDayTaskItem.setTaskType(StudyTaskType.NEW_LEARN);
        studyDayTaskItem.setTaskOrder(1);

        StudyWordProgress staleProgress = new StudyWordProgress();
        staleProgress.setId(500L);
        staleProgress.setStudentStudyPlanId(200L);
        staleProgress.setMetaWordId(3L);
        staleProgress.setAssignedDate(today.minusDays(2));
        staleProgress.setPhase(0);
        staleProgress.setTotalReviews(0);
        staleProgress.setStatus(StudyWordProgressStatus.NEW);

        StudyWordProgress preferredProgress = new StudyWordProgress();
        preferredProgress.setId(501L);
        preferredProgress.setStudentStudyPlanId(200L);
        preferredProgress.setMetaWordId(3L);
        preferredProgress.setAssignedDate(today.minusDays(2));
        preferredProgress.setPhase(2);
        preferredProgress.setTotalReviews(3);
        preferredProgress.setMasteryLevel(BigDecimal.valueOf(55));
        preferredProgress.setLastReviewAt(today.minusDays(1).atTime(10, 0));
        preferredProgress.setStatus(StudyWordProgressStatus.REVIEWING);

        when(studentStudyPlanRepository.findById(200L)).thenReturn(Optional.of(studentStudyPlan));
        when(studyPlanRepository.findById(55L)).thenReturn(Optional.of(studyPlan));
        when(studyDayTaskRepository.findByStudentStudyPlanIdAndTaskDateBeforeOrderByTaskDateAsc(200L, today))
                .thenReturn(List.of());
        when(studyDayTaskRepository.findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(200L, today))
                .thenReturn(List.of(studyDayTask));
        when(studyDayTaskItemRepository.findByStudyDayTaskIdAndMetaWordIdOrderByCreatedAtAsc(300L, 3L))
                .thenReturn(List.of(studyDayTaskItem));
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(10L, 3L)).thenReturn(true);
        when(studyRecordRepository.findByStudentStudyPlanIdAndTaskDate(200L, today))
                .thenAnswer(invocation -> savedRecordRef.get() == null ? List.of() : List.of(savedRecordRef.get()));
        when(studyWordProgressRepository.findByStudentStudyPlanIdAndMetaWordId(200L, 3L))
                .thenReturn(List.of(staleProgress, preferredProgress));
        when(studyWordProgressRepository.save(any(StudyWordProgress.class))).thenAnswer(invocation -> {
            StudyWordProgress savedProgress = invocation.getArgument(0);
            savedProgressRef.set(savedProgress);
            return savedProgress;
        });
        when(studyRecordRepository.save(any(StudyRecord.class))).thenAnswer(invocation -> {
            StudyRecord studyRecord = invocation.getArgument(0);
            savedRecordRef.set(studyRecord);
            return studyRecord;
        });
        when(studyDayTaskItemRepository.save(any(StudyDayTaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyDayTaskItemRepository.countByStudyDayTaskIdAndCompletedAtIsNotNull(300L)).thenReturn(1L);
        when(studentAttentionDailyStatRepository.findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(200L, today))
                .thenReturn(List.of());
        when(studentAttentionDailyStatRepository.save(any(StudentAttentionDailyStat.class))).thenAnswer(invocation -> {
            StudentAttentionDailyStat dailyStat = invocation.getArgument(0);
            savedDailyStatRef.set(dailyStat);
            return dailyStat;
        });
        when(studentAttentionDailyStatRepository.findByStudentStudyPlanIdOrderByTaskDateDesc(200L))
                .thenAnswer(invocation -> savedDailyStatRef.get() == null ? List.of() : List.of(savedDailyStatRef.get()));
        when(studyDayTaskItemRepository.findByStudyDayTaskIdOrderByTaskOrderAsc(300L)).thenReturn(List.of(studyDayTaskItem));
        when(studyWordProgressRepository.findByStudentStudyPlanId(200L)).thenReturn(List.of(staleProgress, preferredProgress));

        RecordStudyRequest request = new RecordStudyRequest(
                3L,
                StudyActionType.LEARN,
                StudyRecordResult.CORRECT,
                26,
                22,
                4,
                5,
                AttentionState.FOCUSED
        );

        StudyTaskResponse response = studyPlanService.recordStudy(200L, request, student);

        assertEquals(StudyDayTaskStatus.COMPLETED, response.getStatus());
        assertNotNull(savedProgressRef.get());
        assertEquals(501L, savedProgressRef.get().getId());
        assertNotNull(savedRecordRef.get());
        assertEquals(2, savedRecordRef.get().getStageBefore());
    }

    private Classroom classroom(Long id, String name, Long teacherId) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setName(name);
        classroom.setTeacherId(teacherId);
        return classroom;
    }

    private Dictionary dictionary(Long id, String name) {
        Dictionary dictionary = new Dictionary();
        dictionary.setId(id);
        dictionary.setName(name);
        return dictionary;
    }

    private StudyPlan studyPlan(Long id, Long teacherId, Long dictionaryId, StudyPlanStatus status) {
        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setId(id);
        studyPlan.setName("高一春季计划");
        studyPlan.setTeacherId(teacherId);
        studyPlan.setDictionaryId(dictionaryId);
        studyPlan.setTimezone("Asia/Shanghai");
        studyPlan.setStartDate(LocalDate.of(2026, 4, 1));
        studyPlan.setDailyNewCount(20);
        studyPlan.setDailyReviewLimit(40);
        studyPlan.setReviewIntervalsJson("[0,1,2,4]");
        studyPlan.setDailyDeadlineTime(LocalTime.of(21, 30));
        studyPlan.setCompletionThreshold(BigDecimal.valueOf(100));
        studyPlan.setStatus(status);
        return studyPlan;
    }

    private MetaWord metaWord(Long id, String word) {
        MetaWord metaWord = new MetaWord();
        metaWord.setId(id);
        metaWord.setWord(word);
        metaWord.setTranslation(word + "译");
        metaWord.setPhonetic("/" + word + "/");
        return metaWord;
    }
}
