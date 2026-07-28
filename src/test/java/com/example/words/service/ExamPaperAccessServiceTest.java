package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperTemplate;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ExamPaperAccessServiceTest {

    @Mock
    private TeacherStudentService teacherStudentService;

    @Mock
    private ClassroomService classroomService;

    private ExamPaperAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new ExamPaperAccessService(teacherStudentService, classroomService);
    }

    @Test
    void adminCanManageAnyQuestion() {
        assertDoesNotThrow(() -> accessService.ensureCanManageQuestion(
                user(1L, UserRole.ADMIN), question(7L, 8L, QuestionBankItemStatus.ARCHIVED)));
    }

    @Test
    void teacherCanManageQuestionCreatedByThem() {
        assertDoesNotThrow(() -> accessService.ensureCanManageQuestion(
                user(7L, UserRole.TEACHER), question(7L, null, QuestionBankItemStatus.DRAFT)));
    }

    @Test
    void teacherCanManageQuestionImportedByThem() {
        assertDoesNotThrow(() -> accessService.ensureCanManageQuestion(
                user(7L, UserRole.TEACHER), question(8L, 7L, QuestionBankItemStatus.DRAFT)));
    }

    @Test
    void teacherCanUseAnotherTeachersActiveQuestionButCannotManageIt() {
        AppUser actor = user(7L, UserRole.TEACHER);
        QuestionBankItem sharedQuestion = question(8L, null, QuestionBankItemStatus.ACTIVE);

        assertDoesNotThrow(() -> accessService.ensureCanUseQuestion(actor, sharedQuestion));
        assertThrows(AccessDeniedException.class, () -> accessService.ensureCanManageQuestion(actor, sharedQuestion));
    }

    @Test
    void studentsAndInactiveQuestionsCannotBeUsed() {
        assertThrows(AccessDeniedException.class, () -> accessService.ensureCanUseQuestion(
                user(20L, UserRole.STUDENT), question(7L, null, QuestionBankItemStatus.ACTIVE)));
        assertThrows(AccessDeniedException.class, () -> accessService.ensureCanUseQuestion(
                user(7L, UserRole.TEACHER), question(7L, null, QuestionBankItemStatus.DRAFT)));
    }

    @Test
    void onlyOwningTeacherCanManagePaper() {
        PaperTemplate paper = new PaperTemplate();
        paper.setOwnerUserId(7L);

        assertDoesNotThrow(() -> accessService.ensureCanManagePaper(user(7L, UserRole.TEACHER), paper));
        assertThrows(AccessDeniedException.class,
                () -> accessService.ensureCanManagePaper(user(1L, UserRole.ADMIN), paper));
    }

    @Test
    void teacherCanPublishOnlyToCurrentResponsibleStudent() {
        AppUser teacher = user(7L, UserRole.TEACHER);
        when(teacherStudentService.isTeacherResponsibleForStudent(7L, 20L)).thenReturn(true);
        when(teacherStudentService.isTeacherResponsibleForStudent(7L, 21L)).thenReturn(false);

        assertDoesNotThrow(() -> accessService.ensureCanPublishToStudent(teacher, 20L));
        assertThrows(AccessDeniedException.class, () -> accessService.ensureCanPublishToStudent(teacher, 21L));
        assertDoesNotThrow(() -> accessService.ensureCanPublishToStudent(user(1L, UserRole.ADMIN), 21L));
    }

    @Test
    void teacherCanPublishOnlyToTheirActiveClassroom() {
        Classroom classroom = classroom(100L, 7L, ClassroomStatus.ACTIVE);
        when(classroomService.getClassroomEntity(100L)).thenReturn(classroom);

        assertDoesNotThrow(() -> accessService.ensureCanPublishToClassroom(user(1L, UserRole.ADMIN), 100L));
        assertDoesNotThrow(() -> accessService.ensureCanPublishToClassroom(user(7L, UserRole.TEACHER), 100L));
        assertThrows(AccessDeniedException.class,
                () -> accessService.ensureCanPublishToClassroom(user(8L, UserRole.TEACHER), 100L));
        verify(classroomService, times(3)).getClassroomEntity(100L);
    }

    @Test
    void archivedClassroomCannotReceivePaperRelease() {
        when(classroomService.getClassroomEntity(100L))
                .thenReturn(classroom(100L, 7L, ClassroomStatus.ARCHIVED));

        assertThrows(AccessDeniedException.class,
                () -> accessService.ensureCanPublishToClassroom(user(1L, UserRole.ADMIN), 100L));
    }

    @Test
    void unrelatedTeacherCannotReviewReleaseButPublisherAndResponsibleTeacherCan() {
        PaperRelease release = new PaperRelease();
        release.setPublishedByUserId(7L);
        AppUser responsibleTeacher = user(8L, UserRole.TEACHER);
        AppUser unrelatedTeacher = user(9L, UserRole.TEACHER);
        when(teacherStudentService.isTeacherResponsibleForStudent(8L, 20L)).thenReturn(true);
        when(teacherStudentService.isTeacherResponsibleForStudent(9L, 20L)).thenReturn(false);

        assertDoesNotThrow(() -> accessService.ensureCanReviewRelease(user(1L, UserRole.ADMIN), release, 20L));
        assertDoesNotThrow(() -> accessService.ensureCanReviewRelease(user(7L, UserRole.TEACHER), release, 20L));
        assertDoesNotThrow(() -> accessService.ensureCanReviewRelease(responsibleTeacher, release, 20L));
        assertThrows(AccessDeniedException.class,
                () -> accessService.ensureCanReviewRelease(unrelatedTeacher, release, 20L));
        assertThrows(AccessDeniedException.class,
                () -> accessService.ensureCanReviewRelease(user(20L, UserRole.STUDENT), release, 20L));
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private QuestionBankItem question(Long createdByUserId, Long importedByUserId, QuestionBankItemStatus status) {
        QuestionBankItem question = new QuestionBankItem();
        question.setCreatedByUserId(createdByUserId);
        question.setImportedByUserId(importedByUserId);
        question.setStatus(status);
        return question;
    }

    private Classroom classroom(Long id, Long teacherId, ClassroomStatus status) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setTeacherId(teacherId);
        classroom.setStatus(status);
        return classroom;
    }
}
