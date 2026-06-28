package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.CreateClassroomRequest;
import com.example.words.dto.UpdateClassroomRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.StudyPlanClassroomRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;

    @Mock
    private StudyPlanClassroomRepository studyPlanClassroomRepository;

    @Mock
    private ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;

    @Mock
    private UserService userService;

    private ClassroomService classroomService;

    @BeforeEach
    void setUp() {
        classroomService = new ClassroomService(
                classroomRepository,
                classroomMemberRepository,
                studyPlanClassroomRepository,
                classroomDictionaryAssignmentRepository,
                userService
        );
    }

    @Test
    void createClassroomShouldRejectDuplicateNameAcrossTeachers() {
        AppUser admin = admin();
        Classroom existing = classroom(100L, "一班", 7L);

        when(classroomRepository.findAll()).thenReturn(List.of(existing));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> classroomService.createClassroom(
                        new CreateClassroomRequest(" 一班 ", "新班级", 8L),
                        admin
                )
        );

        assertEquals("Classroom name already exists: 一班", exception.getMessage());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    void updateClassroomShouldRejectDuplicateName() {
        AppUser admin = admin();
        Classroom existing = classroom(100L, "一班", 7L);
        Classroom target = classroom(101L, "二班", 7L);

        when(classroomRepository.findById(101L)).thenReturn(Optional.of(target));
        when(classroomRepository.findAll()).thenReturn(List.of(existing, target));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> classroomService.updateClassroom(
                        101L,
                        new UpdateClassroomRequest("一班", null, 7L),
                        admin
                )
        );

        assertEquals("Classroom name already exists: 一班", exception.getMessage());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    void deleteClassroomWithHistoryShouldArchiveInsteadOfPhysicalDelete() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomMemberRepository.countByClassroomId(100L)).thenReturn(1L);

        classroomService.deleteClassroom(100L, teacher);

        assertEquals(ClassroomStatus.ARCHIVED, classroom.getStatus());
        verify(classroomRepository, never()).delete(any(Classroom.class));
        verify(classroomRepository).save(classroom);
    }

    @Test
    void deleteEmptyMistakenClassroomShouldPhysicallyDelete() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomMemberRepository.countByClassroomId(100L)).thenReturn(0L);
        when(studyPlanClassroomRepository.existsByClassroomId(100L)).thenReturn(false);
        when(classroomDictionaryAssignmentRepository.existsByClassroomId(100L)).thenReturn(false);

        classroomService.deleteClassroom(100L, teacher);

        verify(classroomRepository).delete(classroom);
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    void archivedClassroomShouldRejectNewMembers() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);
        classroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> classroomService.addStudentToClassroom(100L, 20L, teacher)
        );

        assertEquals("Archived classroom cannot accept new students", exception.getMessage());
        verify(classroomMemberRepository, never()).save(any(ClassroomMember.class));
    }

    @Test
    void listStudentClassroomsShouldReturnActiveMembershipClassroomsOnly() {
        AppUser student = student(20L);
        Classroom activeClassroom = classroom(100L, "一班", 7L);
        Classroom archivedClassroom = classroom(101L, "旧班级", 7L);
        archivedClassroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomMemberRepository.findByStudentId(20L)).thenReturn(List.of(
                new ClassroomMember(1L, 100L, 20L, null),
                new ClassroomMember(2L, 101L, 20L, null)
        ));
        when(classroomRepository.findAllById(Set.of(100L, 101L))).thenReturn(List.of(activeClassroom, archivedClassroom));
        when(userService.getUserEntity(7L)).thenReturn(teacher(7L));
        when(classroomMemberRepository.countByClassroomId(100L)).thenReturn(12L);

        List<String> classroomNames = classroomService.findStudentClassrooms(student).stream()
                .map(response -> response.getName())
                .toList();

        assertEquals(List.of("一班"), classroomNames);
    }

    private AppUser admin() {
        AppUser admin = new AppUser();
        admin.setId(1L);
        admin.setRole(UserRole.ADMIN);
        return admin;
    }

    private AppUser teacher(Long id) {
        AppUser teacher = new AppUser();
        teacher.setId(id);
        teacher.setRole(UserRole.TEACHER);
        teacher.setDisplayName("Teacher " + id);
        return teacher;
    }

    private AppUser student(Long id) {
        AppUser student = new AppUser();
        student.setId(id);
        student.setRole(UserRole.STUDENT);
        student.setDisplayName("Student " + id);
        return student;
    }

    private Classroom classroom(Long id, String name, Long teacherId) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setName(name);
        classroom.setTeacherId(teacherId);
        return classroom;
    }
}
