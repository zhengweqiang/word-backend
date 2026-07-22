package com.example.words.controller;

import com.example.words.dto.AdminStudentPointAccountResponse;
import com.example.words.dto.StudentPointAdjustmentRequestDto;
import com.example.words.dto.StudentPointAdminReasonRequest;
import com.example.words.dto.StudentPointEventAttemptResponse;
import com.example.words.dto.StudentPointEventResponse;
import com.example.words.dto.StudentPointRuleCreateRequest;
import com.example.words.dto.StudentPointRuleAuditResponse;
import com.example.words.dto.StudentPointRuleResponse;
import com.example.words.dto.StudentPointRuleUpdateRequest;
import com.example.words.dto.StudentPointTransactionResponse;
import com.example.words.model.PointEventStatus;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentPointAdjustmentService;
import com.example.words.service.StudentPointAdminQueryService;
import com.example.words.service.StudentPointAdminService;
import com.example.words.service.StudentPointRuleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/points")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminStudentPointController {

    private final CurrentUserService currentUserService;
    private final StudentPointAdminQueryService queryService;
    private final StudentPointAdjustmentService adjustmentService;
    private final StudentPointAdminService adminService;
    private final StudentPointRuleService ruleService;

    @GetMapping("/accounts")
    public ResponseEntity<Page<AdminStudentPointAccountResponse>> getAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.getAccounts(page, size));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<StudentPointTransactionResponse>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.getTransactions(page, size));
    }

    @GetMapping("/events")
    public ResponseEntity<Page<StudentPointEventResponse>> getEvents(
            @RequestParam(required = false) PointEventStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.getEvents(status, page, size));
    }

    @GetMapping("/events/{eventId}/attempts")
    public ResponseEntity<List<StudentPointEventAttemptResponse>> getAttempts(@PathVariable Long eventId) {
        return ResponseEntity.ok(queryService.getAttempts(eventId));
    }

    @PostMapping("/events/{eventId}/retry")
    public ResponseEntity<StudentPointEventResponse> retry(
            @PathVariable Long eventId,
            @Valid @RequestBody StudentPointAdminReasonRequest request
    ) {
        return ResponseEntity.ok(StudentPointEventResponse.from(adminService.retryEvent(
                currentUserService.getCurrentUser(), eventId, request.reason())));
    }

    @PostMapping("/events/{eventId}/cancel")
    public ResponseEntity<StudentPointEventResponse> cancel(
            @PathVariable Long eventId,
            @Valid @RequestBody StudentPointAdminReasonRequest request
    ) {
        return ResponseEntity.ok(StudentPointEventResponse.from(adminService.cancelEvent(
                currentUserService.getCurrentUser(), eventId, request.reason())));
    }

    @PostMapping("/transactions/{transactionId}/reverse")
    public ResponseEntity<StudentPointTransactionResponse> reverse(
            @PathVariable Long transactionId,
            @Valid @RequestBody StudentPointAdminReasonRequest request
    ) {
        return ResponseEntity.ok(StudentPointTransactionResponse.from(adminService.reverseTransaction(
                currentUserService.getCurrentUser(), transactionId, request.reason())));
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

    @GetMapping("/rules")
    public ResponseEntity<List<StudentPointRuleResponse>> getRules() {
        return ResponseEntity.ok(queryService.getRules());
    }

    @GetMapping("/rules/{ruleId}/audits")
    public ResponseEntity<List<StudentPointRuleAuditResponse>> getRuleAudits(@PathVariable Long ruleId) {
        return ResponseEntity.ok(queryService.getRuleAudits(ruleId));
    }

    @PostMapping("/rules")
    public ResponseEntity<StudentPointRuleResponse> createRule(
            @Valid @RequestBody StudentPointRuleCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StudentPointRuleResponse.from(ruleService.create(currentUserService.getCurrentUser(), request)));
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<StudentPointRuleResponse> updateRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody StudentPointRuleUpdateRequest request
    ) {
        return ResponseEntity.ok(StudentPointRuleResponse.from(
                ruleService.update(currentUserService.getCurrentUser(), ruleId, request)));
    }
}
