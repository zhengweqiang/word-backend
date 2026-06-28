package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomDictionaryAssignment;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ClassroomDictionaryAssignmentServiceTest {

    @Mock
    private ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    private ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService;

    @BeforeEach
    void setUp() {
        classroomDictionaryAssignmentService = new ClassroomDictionaryAssignmentService(
                classroomDictionaryAssignmentRepository,
                classroomRepository,
                classroomMemberRepository,
                dictionaryRepository
        );
    }

    @Test
    void assignDictionariesShouldRejectArchivedClassroom() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);
        classroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> classroomDictionaryAssignmentService.assignDictionariesToClassroom(100L, List.of(10L), teacher)
        );

        assertEquals("Archived classroom cannot accept dictionary assignments", exception.getMessage());
        verify(classroomDictionaryAssignmentRepository, never()).save(any(ClassroomDictionaryAssignment.class));
    }

    private AppUser teacher(Long id) {
        AppUser teacher = new AppUser();
        teacher.setId(id);
        teacher.setRole(UserRole.TEACHER);
        return teacher;
    }

    private Classroom classroom(Long id, String name, Long teacherId) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setName(name);
        classroom.setTeacherId(teacherId);
        return classroom;
    }
}
