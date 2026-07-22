package com.example.words.controller;

import com.example.words.dto.StudentPointSummaryResponse;
import com.example.words.dto.StudentPointTransactionResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentPointQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/me/points")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentPointController {

    private final CurrentUserService currentUserService;
    private final StudentPointQueryService queryService;

    @GetMapping
    public ResponseEntity<StudentPointSummaryResponse> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(currentUserService.getCurrentUser().getId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<StudentPointTransactionResponse>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.getTransactions(
                currentUserService.getCurrentUser().getId(), page, size));
    }
}
