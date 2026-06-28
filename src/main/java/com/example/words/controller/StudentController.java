package com.example.words.controller;

import com.example.words.model.Dictionary;
import com.example.words.service.CurrentUserService;
import com.example.words.service.DictionaryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final CurrentUserService currentUserService;
    private final DictionaryService dictionaryService;

    public StudentController(
            CurrentUserService currentUserService,
            DictionaryService dictionaryService) {
        this.currentUserService = currentUserService;
        this.dictionaryService = dictionaryService;
    }

    @GetMapping("/me/dictionaries")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Dictionary>> getMyAssignedDictionaries() {
        return ResponseEntity.ok(
                dictionaryService.findAssignedDictionariesForStudent(currentUserService.getCurrentUser().getId()));
    }
}
