package com.example.words.service;

import com.example.words.model.PointAdjustmentStatus;
import com.example.words.model.StudentPointAdjustmentRequest;
import com.example.words.model.StudentPointEvent;
import java.util.Objects;

final class StudentPointManualAdjustmentIdentity {

    private static final String RULE_CODE = "MANUAL_ADJUSTMENT";
    private static final String SOURCE_PREFIX = "manual-adjustment:";

    private StudentPointManualAdjustmentIdentity() {
    }

    static boolean isValidEvent(StudentPointEvent event) {
        Long sourceId = event.getSourceId();
        if (sourceId == null || sourceId <= 0) {
            return false;
        }
        String expectedSourceKey = SOURCE_PREFIX + sourceId;
        return RULE_CODE.equals(event.getRuleCode())
                && expectedSourceKey.equals(event.getSourceKey())
                && (expectedSourceKey + ":" + RULE_CODE).equals(event.getIdempotencyKey());
    }

    static boolean matchesRequest(StudentPointEvent event, StudentPointAdjustmentRequest request) {
        boolean eligibleStatus = request.getStatus() == PointAdjustmentStatus.PENDING
                || request.getStatus() == PointAdjustmentStatus.FAILED;
        return eligibleStatus && matchesPayload(event, request);
    }

    static boolean matchesPayload(StudentPointEvent event, StudentPointAdjustmentRequest request) {
        return matchesWorkflow(event, request)
                && Objects.equals(request.getReason(), event.getReason());
    }

    static boolean matchesWorkflow(StudentPointEvent event, StudentPointAdjustmentRequest request) {
        return Objects.equals(request.getStudentId(), event.getStudentId())
                && Objects.equals(request.getAmount(), event.getPoints())
                && Objects.equals(request.getRequestedBy(), event.getOperatorId())
                && Objects.equals(request.getRequestedRole(), event.getOperatorRole());
    }
}
