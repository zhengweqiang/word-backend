package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.words.dto.PaperReleaseResultOverviewResponse;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperReleaseTarget;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.TeacherStudentRelation;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.PaperReleaseTargetRepository;
import com.example.words.repository.StudentPaperAttemptRepository;
import com.example.words.repository.TeacherStudentRelationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        PaperResultReviewService.class,
        PaperResultReviewRepositoryIntegrationTest.ReviewTestConfiguration.class
})
@Testcontainers(disabledWithoutDocker = true)
class PaperResultReviewRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 10, 0);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private PaperResultReviewService service;

    @Autowired
    private PaperReleaseRepository releaseRepository;

    @Autowired
    private PaperReleaseTargetRepository targetRepository;

    @Autowired
    private StudentPaperAttemptRepository attemptRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomMemberRepository classroomMemberRepository;

    @Autowired
    private TeacherStudentRelationRepository relationRepository;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Test
    void transferredClassTeacherReviewsFrozenLeftStudentAndOnlyAuthorizedTargets() {
        PaperRelease release = releaseRepository.saveAndFlush(release());
        Classroom transferredClass = classroomRepository.saveAndFlush(classroom(5L, "Transferred class"));
        Classroom unrelatedClass = classroomRepository.saveAndFlush(classroom(8L, "Other class"));

        transferredClass.setTeacherId(7L);
        classroomRepository.saveAndFlush(transferredClass);

        targetRepository.saveAllAndFlush(List.of(
                target(release.getId(), 20L, "[" + transferredClass.getId() + "]"),
                target(release.getId(), 21L, "[]"),
                target(release.getId(), 22L, "[" + unrelatedClass.getId() + "]")));
        relationRepository.saveAndFlush(relation(7L, 21L));
        attemptRepository.saveAllAndFlush(List.of(
                attempt(release.getId(), 20L),
                attempt(release.getId(), 21L),
                attempt(release.getId(), 22L)));

        PaperReleaseResultOverviewResponse result = service.getOverview(
                release.getId(), user(7L, UserRole.TEACHER));

        assertEquals(List.of(20L, 21L), result.getStudents().stream()
                .map(student -> student.getStudentId())
                .toList());
        assertEquals(2, result.getAssignedCount());
        assertThrows(AccessDeniedException.class,
                () -> service.getOverview(release.getId(), user(9L, UserRole.TEACHER)));
    }

    @Test
    void currentClassTeacherReviewsStudentReleasedThroughDifferentFrozenClass() {
        PaperRelease release = releaseRepository.saveAndFlush(release());
        Classroom frozenSourceClass = classroomRepository.saveAndFlush(classroom(5L, "Class A"));
        Classroom currentClass = classroomRepository.saveAndFlush(classroom(7L, "Class B"));
        targetRepository.saveAndFlush(target(
                release.getId(), 30L, "[" + frozenSourceClass.getId() + "]"));
        attemptRepository.saveAndFlush(attempt(release.getId(), 30L));
        classroomMemberRepository.saveAndFlush(member(currentClass.getId(), 30L));

        PaperReleaseResultOverviewResponse result = service.getOverview(
                release.getId(), user(7L, UserRole.TEACHER));

        assertEquals(List.of(30L), result.getStudents().stream()
                .map(student -> student.getStudentId())
                .toList());
        assertThrows(AccessDeniedException.class,
                () -> service.getOverview(release.getId(), user(9L, UserRole.TEACHER)));
    }

    private PaperRelease release() {
        PaperRelease release = new PaperRelease();
        release.setPaperTemplateId(1L);
        release.setTitle("Frozen release");
        release.setPublishedByUserId(5L);
        release.setStatus(PaperReleaseStatus.OPEN);
        release.setQuestionCount(1);
        release.setTotalScore(new BigDecimal("5.00"));
        release.setDeadline(NOW.plusHours(1));
        release.setResultVisibility(PaperResultVisibility.SCORE_ONLY);
        release.setCreatedAt(NOW.minusHours(1));
        return release;
    }

    private Classroom classroom(Long teacherId, String name) {
        Classroom classroom = new Classroom();
        classroom.setName(name);
        classroom.setTeacherId(teacherId);
        classroom.setStatus(ClassroomStatus.ACTIVE);
        return classroom;
    }

    private PaperReleaseTarget target(Long releaseId, Long studentId, String sourceClassroomIdsJson) {
        PaperReleaseTarget target = new PaperReleaseTarget();
        target.setPaperReleaseId(releaseId);
        target.setStudentId(studentId);
        target.setSourceClassroomIdsJson(sourceClassroomIdsJson);
        target.setTargetedByUserId(5L);
        target.setCreatedAt(NOW.minusHours(1));
        return target;
    }

    private TeacherStudentRelation relation(Long teacherId, Long studentId) {
        TeacherStudentRelation relation = new TeacherStudentRelation();
        relation.setTeacherId(teacherId);
        relation.setStudentId(studentId);
        relation.setCreatedAt(NOW.minusHours(1));
        return relation;
    }

    private ClassroomMember member(Long classroomId, Long studentId) {
        ClassroomMember member = new ClassroomMember();
        member.setClassroomId(classroomId);
        member.setStudentId(studentId);
        return member;
    }

    private StudentPaperAttempt attempt(Long releaseId, Long studentId) {
        StudentPaperAttempt attempt = new StudentPaperAttempt();
        attempt.setPaperReleaseId(releaseId);
        attempt.setStudentId(studentId);
        attempt.setStatus(StudentPaperAttemptStatus.NOT_STARTED);
        attempt.setAnsweredCount(0);
        attempt.setCorrectCount(0);
        attempt.setEarnedScore(BigDecimal.ZERO);
        attempt.setTotalScore(new BigDecimal("5.00"));
        attempt.setCreatedAt(NOW.minusHours(1));
        attempt.setUpdatedAt(NOW.minusHours(1));
        return attempt;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    @TestConfiguration
    static class ReviewTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-29T02:00:00Z"), ZoneOffset.ofHours(8));
        }
    }
}
