package com.example.words.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperTemplate;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.UserRole;

@Service
public class ExamPaperAccessService {

    private final TeacherStudentService teacherStudentService;
    private final ClassroomService classroomService;

    public ExamPaperAccessService(TeacherStudentService teacherStudentService, ClassroomService classroomService) {
        this.teacherStudentService = teacherStudentService;
        this.classroomService = classroomService;
    }

    public void ensureCanManageQuestion(AppUser actor, QuestionBankItem question) {
        if (isAdmin(actor) || (isTeacher(actor) && (actor.getId().equals(question.getCreatedByUserId())
                || actor.getId().equals(question.getImportedByUserId())))) {
            return;
        }
        throw denied("manage this question");
    }

    public void ensureCanUseQuestion(AppUser actor, QuestionBankItem question) {
        if (question.getStatus() == QuestionBankItemStatus.ACTIVE && (isAdmin(actor) || isTeacher(actor))) {
            return;
        }
        throw denied("use this question");
    }

    public void ensureCanManagePaper(AppUser actor, PaperTemplate paper) {
        if ((isAdmin(actor) || isTeacher(actor)) && actor.getId().equals(paper.getOwnerUserId())) {
            return;
        }
        throw denied("manage this paper");
    }

    public void ensureCanPublishToStudent(AppUser actor, Long studentId) {
        if (isAdmin(actor) || (isTeacher(actor)
                && teacherStudentService.isTeacherResponsibleForStudent(actor.getId(), studentId))) {
            return;
        }
        throw denied("publish to this student");
    }

    public void ensureCanPublishToClassroom(AppUser actor, Long classroomId) {
        Classroom classroom = classroomService.getClassroomEntity(classroomId);
        if (classroom.getStatus() == ClassroomStatus.ACTIVE
                && (isAdmin(actor) || (isTeacher(actor) && actor.getId().equals(classroom.getTeacherId())))) {
            return;
        }
        throw denied("publish to this classroom");
    }

    public void ensureCanReviewRelease(AppUser actor, PaperRelease release, Long studentId) {
        if (isAdmin(actor) || (isTeacher(actor) && actor.getId().equals(release.getPublishedByUserId()))
                || (isTeacher(actor)
                && teacherStudentService.isTeacherResponsibleForStudent(actor.getId(), studentId))) {
            return;
        }
        throw denied("review this release");
    }

    private boolean isAdmin(AppUser actor) {
        return actor != null && actor.getRole() == UserRole.ADMIN;
    }

    private boolean isTeacher(AppUser actor) {
        return actor != null && actor.getRole() == UserRole.TEACHER;
    }

    private AccessDeniedException denied(String action) {
        return new AccessDeniedException("You do not have permission to " + action);
    }
}
