package com.example.words.service;

import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomDictionaryAssignment;
import com.example.words.model.ClassroomMember;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryAssignment;
import com.example.words.model.ReviewMode;
import com.example.words.model.StudentStudyPlan;
import com.example.words.model.StudentStudyPlanStatus;
import com.example.words.model.StudyPlan;
import com.example.words.model.StudyPlanClassroom;
import com.example.words.model.StudyPlanStatus;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryAssignmentRepository;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.StudentStudyPlanRepository;
import com.example.words.repository.StudyPlanClassroomRepository;
import com.example.words.repository.StudyPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SparkJunior1600SeedService {

    public static final String DICTIONARY_NAME = "星火初中英语词汇1600词";
    public static final String TEACHER_USERNAME = "spark_teacher";
    public static final String STUDENT_ONE_USERNAME = "spark_student_1";
    public static final String STUDENT_TWO_USERNAME = "spark_student_2";

    private static final String DEFAULT_PASSWORD = "12345678";
    private static final String TIMEZONE = "Asia/Shanghai";
    private static final String CLASSROOM_NAME = "星火初中英语词汇1600词班";
    private static final String STUDY_PLAN_NAME = "25天星火初中英语词汇1600词计划";
    private static final String REVIEW_INTERVALS_JSON = "[0,1,2,4,7,15]";
    private static final int STUDY_DAYS = 25;
    private static final int DAILY_NEW_COUNT = 64;
    private static final int DAILY_REVIEW_LIMIT = 128;

    private final AppUserRepository appUserRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;
    private final DictionaryAssignmentRepository dictionaryAssignmentRepository;
    private final DictionaryRepository dictionaryRepository;
    private final StudentStudyPlanRepository studentStudyPlanRepository;
    private final StudyPlanClassroomRepository studyPlanClassroomRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public SparkJunior1600SeedService(
            AppUserRepository appUserRepository,
            ClassroomRepository classroomRepository,
            ClassroomMemberRepository classroomMemberRepository,
            ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository,
            DictionaryAssignmentRepository dictionaryAssignmentRepository,
            DictionaryRepository dictionaryRepository,
            StudentStudyPlanRepository studentStudyPlanRepository,
            StudyPlanClassroomRepository studyPlanClassroomRepository,
            StudyPlanRepository studyPlanRepository,
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.classroomRepository = classroomRepository;
        this.classroomMemberRepository = classroomMemberRepository;
        this.classroomDictionaryAssignmentRepository = classroomDictionaryAssignmentRepository;
        this.dictionaryAssignmentRepository = dictionaryAssignmentRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.studentStudyPlanRepository = studentStudyPlanRepository;
        this.studyPlanClassroomRepository = studyPlanClassroomRepository;
        this.studyPlanRepository = studyPlanRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SeedResult reset() {
        deleteExistingSeedData();

        Dictionary dictionary = dictionaryRepository.findByName(DICTIONARY_NAME)
                .orElseThrow(() -> new BadRequestException("Required dictionary not found: " + DICTIONARY_NAME));

        AppUser teacher = saveUser(TEACHER_USERNAME, "星火词汇老师", UserRole.TEACHER);
        AppUser studentOne = saveUser(STUDENT_ONE_USERNAME, "星火学生一", UserRole.STUDENT);
        AppUser studentTwo = saveUser(STUDENT_TWO_USERNAME, "星火学生二", UserRole.STUDENT);
        List<AppUser> students = List.of(studentOne, studentTwo);

        Classroom classroom = classroomRepository.save(new Classroom(
                null,
                CLASSROOM_NAME,
                "固定种子数据：星火初中英语词汇1600词 25天计划",
                teacher.getId(),
                ClassroomStatus.ACTIVE,
                null,
                null,
                null
        ));
        for (AppUser student : students) {
            classroomMemberRepository.save(new ClassroomMember(null, classroom.getId(), student.getId(), null));
            dictionaryAssignmentRepository.save(new DictionaryAssignment(
                    null,
                    dictionary.getId(),
                    student.getId(),
                    teacher.getId(),
                    null
            ));
        }
        classroomDictionaryAssignmentRepository.save(new ClassroomDictionaryAssignment(
                null,
                classroom.getId(),
                dictionary.getId(),
                teacher.getId(),
                null
        ));

        StudyPlan studyPlan = saveStudyPlan(teacher, dictionary);
        studyPlanClassroomRepository.save(new StudyPlanClassroom(null, studyPlan.getId(), classroom.getId(), null));

        LocalDateTime joinedAt = LocalDateTime.now(ZoneId.of(TIMEZONE));
        for (AppUser student : students) {
            StudentStudyPlan studentStudyPlan = new StudentStudyPlan();
            studentStudyPlan.setStudyPlanId(studyPlan.getId());
            studentStudyPlan.setStudentId(student.getId());
            studentStudyPlan.setStatus(StudentStudyPlanStatus.ACTIVE);
            studentStudyPlan.setJoinedAt(joinedAt);
            studentStudyPlanRepository.save(studentStudyPlan);
        }

        return new SeedResult(
                teacher.getId(),
                List.of(studentOne.getId(), studentTwo.getId()),
                classroom.getId(),
                dictionary.getId(),
                studyPlan.getId()
        );
    }

    private void deleteExistingSeedData() {
        List<Long> seedUserIds = jdbcTemplate.queryForList(
                """
                        SELECT id
                        FROM users
                        WHERE username IN (:usernames)
                        """,
                Map.of("usernames", seedUsernames()),
                Long.class
        );
        if (seedUserIds.isEmpty()) {
            return;
        }

        Map<String, Object> params = Map.of("userIds", seedUserIds);
        update("""
                DELETE FROM study_day_task_items
                WHERE study_day_task_id IN (
                    SELECT task.id
                    FROM study_day_tasks task
                    JOIN student_study_plans student_plan ON student_plan.id = task.student_study_plan_id
                    LEFT JOIN study_plans plan ON plan.id = student_plan.study_plan_id
                    WHERE student_plan.student_id IN (:userIds)
                       OR plan.teacher_id IN (:userIds)
                )
                """, params);
        update("""
                DELETE FROM study_day_tasks
                WHERE student_study_plan_id IN (
                    SELECT student_plan.id
                    FROM student_study_plans student_plan
                    LEFT JOIN study_plans plan ON plan.id = student_plan.study_plan_id
                    WHERE student_plan.student_id IN (:userIds)
                       OR plan.teacher_id IN (:userIds)
                )
                """, params);
        update("""
                DELETE FROM study_word_progresses
                WHERE student_study_plan_id IN (
                    SELECT student_plan.id
                    FROM student_study_plans student_plan
                    LEFT JOIN study_plans plan ON plan.id = student_plan.study_plan_id
                    WHERE student_plan.student_id IN (:userIds)
                       OR plan.teacher_id IN (:userIds)
                )
                """, params);
        update("""
                DELETE FROM study_records
                WHERE student_study_plan_id IN (
                    SELECT student_plan.id
                    FROM student_study_plans student_plan
                    LEFT JOIN study_plans plan ON plan.id = student_plan.study_plan_id
                    WHERE student_plan.student_id IN (:userIds)
                       OR plan.teacher_id IN (:userIds)
                )
                """, params);
        update("""
                DELETE FROM student_attention_daily_stats
                WHERE student_study_plan_id IN (
                    SELECT student_plan.id
                    FROM student_study_plans student_plan
                    LEFT JOIN study_plans plan ON plan.id = student_plan.study_plan_id
                    WHERE student_plan.student_id IN (:userIds)
                       OR plan.teacher_id IN (:userIds)
                )
                """, params);
        update("""
                DELETE FROM student_study_plans
                WHERE student_id IN (:userIds)
                   OR study_plan_id IN (
                       SELECT id
                       FROM study_plans
                       WHERE teacher_id IN (:userIds)
                   )
                """, params);
        update("""
                DELETE FROM study_plan_classrooms
                WHERE study_plan_id IN (
                    SELECT id
                    FROM study_plans
                    WHERE teacher_id IN (:userIds)
                )
                   OR classroom_id IN (
                       SELECT id
                       FROM classrooms
                       WHERE teacher_id IN (:userIds)
                   )
                """, params);
        update("""
                DELETE FROM classroom_dictionary_assignments
                WHERE assigned_by_user_id IN (:userIds)
                   OR classroom_id IN (
                       SELECT id
                       FROM classrooms
                       WHERE teacher_id IN (:userIds)
                   )
                """, params);
        update("""
                DELETE FROM dictionary_assignments
                WHERE student_id IN (:userIds)
                   OR assigned_by_user_id IN (:userIds)
                """, params);
        update("""
                DELETE FROM classroom_members
                WHERE student_id IN (:userIds)
                   OR classroom_id IN (
                       SELECT id
                       FROM classrooms
                       WHERE teacher_id IN (:userIds)
                   )
                """, params);
        update("""
                DELETE FROM teacher_student_relations
                WHERE teacher_id IN (:userIds)
                   OR student_id IN (:userIds)
                """, params);
        update("""
                DELETE FROM exam_questions
                WHERE exam_id IN (
                    SELECT id
                    FROM exams
                    WHERE created_by_user_id IN (:userIds)
                       OR target_user_id IN (:userIds)
                )
                """, params);
        update("""
                DELETE FROM exams
                WHERE created_by_user_id IN (:userIds)
                   OR target_user_id IN (:userIds)
                """, params);
        update("""
                DELETE FROM ai_configs
                WHERE user_id IN (:userIds)
                """, params);
        update("""
                DELETE FROM study_plans
                WHERE teacher_id IN (:userIds)
                """, params);
        update("""
                DELETE FROM classrooms
                WHERE teacher_id IN (:userIds)
                """, params);
        update("""
                DELETE FROM users
                WHERE id IN (:userIds)
                """, params);
    }

    private AppUser saveUser(String username, String displayName, UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return appUserRepository.save(user);
    }

    private StudyPlan saveStudyPlan(AppUser teacher, Dictionary dictionary) {
        LocalDate startDate = LocalDate.now(ZoneId.of(TIMEZONE));
        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setName(STUDY_PLAN_NAME);
        studyPlan.setDescription("每天64个新词，25天完成星火初中英语词汇1600词。");
        studyPlan.setTeacherId(teacher.getId());
        studyPlan.setDictionaryId(dictionary.getId());
        studyPlan.setStartDate(startDate);
        studyPlan.setEndDate(startDate.plusDays(STUDY_DAYS - 1L));
        studyPlan.setTimezone(TIMEZONE);
        studyPlan.setDailyNewCount(DAILY_NEW_COUNT);
        studyPlan.setDailyReviewLimit(DAILY_REVIEW_LIMIT);
        studyPlan.setReviewMode(ReviewMode.EBBINGHAUS);
        studyPlan.setReviewIntervalsJson(REVIEW_INTERVALS_JSON);
        studyPlan.setCompletionThreshold(BigDecimal.valueOf(100).setScale(2));
        studyPlan.setDailyDeadlineTime(LocalTime.of(21, 30));
        studyPlan.setAttentionTrackingEnabled(true);
        studyPlan.setMinFocusSecondsPerWord(3);
        studyPlan.setMaxFocusSecondsPerWord(120);
        studyPlan.setLongStayWarningSeconds(60);
        studyPlan.setIdleTimeoutSeconds(15);
        studyPlan.setStatus(StudyPlanStatus.PUBLISHED);
        return studyPlanRepository.save(studyPlan);
    }

    private void update(String sql, Map<String, Object> params) {
        jdbcTemplate.update(sql, params);
    }

    private List<String> seedUsernames() {
        return List.of(TEACHER_USERNAME, STUDENT_ONE_USERNAME, STUDENT_TWO_USERNAME);
    }

    public record SeedResult(
            Long teacherId,
            List<Long> studentIds,
            Long classroomId,
            Long dictionaryId,
            Long studyPlanId) {
    }
}
