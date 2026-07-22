package com.example.words.controller;

import com.example.words.dto.StudentPointAdjustmentRequestDto;
import com.example.words.dto.StudentPointSummaryResponse;
import com.example.words.dto.StudentPointTransactionResponse;
import com.example.words.dto.TeacherStudentPointResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentPointAdjustmentService;
import com.example.words.service.StudentPointQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teachers/me/points")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherStudentPointController {

    private final CurrentUserService currentUserService;
    private final StudentPointQueryService queryService;
    private final StudentPointAdjustmentService adjustmentService;

    @GetMapping("/students")
    public ResponseEntity<Page<TeacherStudentPointResponse>> getStudents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok(queryService.getManagedStudents(
                currentUserService.getCurrentUser().getId(), page, size, name));
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<StudentPointSummaryResponse> getStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(queryService.getManagedStudentSummary(
                currentUserService.getCurrentUser().getId(), studentId));
    }

    @GetMapping("/students/{studentId}/transactions")
    public ResponseEntity<Page<StudentPointTransactionResponse>> getTransactions(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.getManagedStudentTransactions(
                currentUserService.getCurrentUser().getId(), studentId, page, size));
    }

    @PostMapping("/students/{studentId}/adjustments")
    public ResponseEntity<StudentPointAdjustmentService.AdjustmentOutcome> adjust(
            @PathVariable Long studentId,
            @Valid @RequestBody StudentPointAdjustmentRequestDto request
    ) {
        return ResponseEntity.ok(adjustmentService.adjust(
                currentUserService.getCurrentUser(),
                new StudentPointAdjustmentService.AdjustmentCommand(
                        request.requestKey(), studentId, request.amount(), request.reason(),
                        request.replacesAdjustmentRequestId()
                )
        ));
    }
}
