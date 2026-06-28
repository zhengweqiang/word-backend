package com.example.words.controller;

import com.example.words.dto.ClassroomResponse;
import com.example.words.service.ClassroomService;
import com.example.words.service.CurrentUserService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/me/classrooms")
@PreAuthorize("hasRole('STUDENT')")
public class StudentClassroomController {

    private final ClassroomService classroomService;
    private final CurrentUserService currentUserService;

    public StudentClassroomController(ClassroomService classroomService, CurrentUserService currentUserService) {
        this.classroomService = classroomService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<ClassroomResponse>> listMyClassrooms() {
        return ResponseEntity.ok(classroomService.findStudentClassrooms(currentUserService.getCurrentUser()));
    }
}
