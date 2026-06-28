package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.UserResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.TeacherStudentRelation;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.TeacherStudentRelationRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeacherStudentServiceTest {

    @Mock
    private TeacherStudentRelationRepository teacherStudentRelationRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private UserService userService;

    private TeacherStudentService teacherStudentService;

    @BeforeEach
    void setUp() {
        teacherStudentService = new TeacherStudentService(
                teacherStudentRelationRepository,
                classroomRepository,
                classroomMemberRepository,
                appUserRepository,
                userService
        );
    }

    @Test
    void assignStudentShouldValidateRolesAndRemainIdempotent() {
        AppUser teacher = user(7L, UserRole.TEACHER, "老师甲");
        AppUser student = user(20L, UserRole.STUDENT, "学生甲");

        when(userService.getUserEntity(7L)).thenReturn(teacher);
        when(userService.getUserEntity(20L)).thenReturn(student);
        when(teacherStudentRelationRepository.existsByTeacherIdAndStudentId(7L, 20L)).thenReturn(true);

        teacherStudentService.assignStudent(7L, 20L);

        verify(teacherStudentRelationRepository, never()).save(any(TeacherStudentRelation.class));
    }

    @Test
    void assignStudentShouldRejectNonTeacher() {
        AppUser notTeacher = user(7L, UserRole.STUDENT, "误选学生");
        AppUser student = user(20L, UserRole.STUDENT, "学生甲");

        when(userService.getUserEntity(7L)).thenReturn(notTeacher);
        when(userService.getUserEntity(20L)).thenReturn(student);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> teacherStudentService.assignStudent(7L, 20L)
        );

        assertEquals("User is not a teacher: 7", exception.getMessage());
        verify(teacherStudentRelationRepository, never()).save(any(TeacherStudentRelation.class));
    }

    @Test
    void getStudentsForTeacherShouldUnionDirectRelationsAndClassroomMembers() {
        AppUser directStudent = user(20L, UserRole.STUDENT, "学生甲");
        AppUser classroomStudent = user(21L, UserRole.STUDENT, "学生乙");

        when(teacherStudentRelationRepository.findByTeacherId(7L))
                .thenReturn(List.of(new TeacherStudentRelation(1L, 7L, 20L, null)));
        when(classroomRepository.findByTeacherId(7L)).thenReturn(List.of(classroom(100L, 7L)));
        when(classroomMemberRepository.findByClassroomIdIn(List.of(100L)))
                .thenReturn(List.of(new ClassroomMember(1L, 100L, 21L, null)));
        when(userService.findById(20L)).thenReturn(UserResponse.from(directStudent));
        when(userService.findById(21L)).thenReturn(UserResponse.from(classroomStudent));

        List<UserResponse> students = teacherStudentService.getStudentsForTeacher(7L);

        assertEquals(2, students.size());
        assertEquals(Set.of(20L, 21L), Set.of(students.get(0).getId(), students.get(1).getId()));
    }

    @Test
    void isTeacherResponsibleForStudentShouldIncludeClassroomMembers() {
        when(teacherStudentRelationRepository.existsByTeacherIdAndStudentId(7L, 21L)).thenReturn(false);
        when(classroomRepository.findByTeacherId(7L)).thenReturn(List.of(classroom(100L, 7L)));
        when(classroomMemberRepository.existsByClassroomIdInAndStudentId(List.of(100L), 21L)).thenReturn(true);

        assertTrue(teacherStudentService.isTeacherResponsibleForStudent(7L, 21L));
    }

    @Test
    void getResponsibleTeacherIdsForStudentShouldUnionDirectRelationsAndClassroomOwners() {
        when(teacherStudentRelationRepository.findByStudentId(20L))
                .thenReturn(List.of(new TeacherStudentRelation(1L, 7L, 20L, null)));
        when(classroomMemberRepository.findByStudentId(20L))
                .thenReturn(List.of(new ClassroomMember(1L, 100L, 20L, null)));
        when(classroomRepository.findAllById(List.of(100L))).thenReturn(List.of(classroom(100L, 8L)));

        Set<Long> teacherIds = teacherStudentService.getResponsibleTeacherIdsForStudent(20L);

        assertEquals(Set.of(7L, 8L), teacherIds);
    }

    @Test
    void isTeacherResponsibleForStudentShouldRejectUnrelatedStudent() {
        when(teacherStudentRelationRepository.existsByTeacherIdAndStudentId(7L, 22L)).thenReturn(false);
        when(classroomRepository.findByTeacherId(7L)).thenReturn(List.of());

        assertFalse(teacherStudentService.isTeacherResponsibleForStudent(7L, 22L));
    }

    private AppUser user(Long id, UserRole role, String displayName) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setDisplayName(displayName);
        user.setRole(role);
        return user;
    }

    private Classroom classroom(Long id, Long teacherId) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setTeacherId(teacherId);
        return classroom;
    }
}
