package com.example.words.controller;

import com.example.words.dto.AssignDictionariesRequest;
import com.example.words.dto.ClassroomResponse;
import com.example.words.dto.CreateClassroomRequest;
import com.example.words.dto.UpdateClassroomRequest;
import com.example.words.dto.UserResponse;
import com.example.words.model.Dictionary;
import com.example.words.service.ClassroomDictionaryAssignmentService;
import com.example.words.service.ClassroomService;
import com.example.words.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classrooms")
public class ClassroomController {

    private final ClassroomService classroomService;
    private final ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService;
    private final CurrentUserService currentUserService;

    public ClassroomController(
            ClassroomService classroomService,
            ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService,
            CurrentUserService currentUserService) {
        this.classroomService = classroomService;
        this.classroomDictionaryAssignmentService = classroomDictionaryAssignmentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<ClassroomResponse>> listClassrooms() {
        return ResponseEntity.ok(classroomService.findVisibleClassrooms(currentUserService.getCurrentUser()));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Page<ClassroomResponse>> listClassroomsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(
                classroomService.findVisibleClassroomsPage(
                        currentUserService.getCurrentUser(),
                        page,
                        size,
                        keyword,
                        sortBy,
                        sortDir
                )
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ClassroomResponse> createClassroom(@Valid @RequestBody CreateClassroomRequest request) {
        return ResponseEntity.ok(classroomService.createClassroom(request, currentUserService.getCurrentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ClassroomResponse> updateClassroom(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassroomRequest request) {
        return ResponseEntity.ok(classroomService.updateClassroom(id, request, currentUserService.getCurrentUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> deleteClassroom(@PathVariable Long id) {
        boolean physicallyDeleted = classroomService.deleteClassroom(id, currentUserService.getCurrentUser());
        return ResponseEntity.ok(Map.of(
                "message", physicallyDeleted ? "Classroom deleted successfully" : "Classroom archived successfully",
                "id", id,
                "status", physicallyDeleted ? "DELETED" : "ARCHIVED"
        ));
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<UserResponse>> getClassroomStudents(@PathVariable Long id) {
        return ResponseEntity.ok(classroomService.getStudentsForClassroom(id, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{id}/dictionaries")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<Dictionary>> getClassroomDictionaries(@PathVariable Long id) {
        return ResponseEntity.ok(
                classroomDictionaryAssignmentService.findDictionariesForClassroom(id, currentUserService.getCurrentUser())
        );
    }

    @PostMapping("/{id}/dictionaries")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> assignDictionariesToClassroom(
            @PathVariable Long id,
            @Valid @RequestBody AssignDictionariesRequest request) {
        int assignedCount = classroomDictionaryAssignmentService.assignDictionariesToClassroom(
                id,
                request.getDictionaryIds(),
                currentUserService.getCurrentUser()
        );
        return ResponseEntity.ok(Map.of(
                "message", "Dictionaries assigned to classroom successfully",
                "classroomId", id,
                "assignedCount", assignedCount
        ));
    }

    @PostMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> addStudentToClassroom(@PathVariable Long id, @PathVariable Long studentId) {
        classroomService.addStudentToClassroom(id, studentId, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/dictionaries/{dictionaryId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> removeDictionaryFromClassroom(@PathVariable Long id, @PathVariable Long dictionaryId) {
        classroomDictionaryAssignmentService.removeDictionaryFromClassroom(
                id,
                dictionaryId,
                currentUserService.getCurrentUser()
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> removeStudentFromClassroom(@PathVariable Long id, @PathVariable Long studentId) {
        classroomService.removeStudentFromClassroom(id, studentId, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
