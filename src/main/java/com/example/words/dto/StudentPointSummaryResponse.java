package com.example.words.dto;

import com.example.words.model.PointAccountStatus;
import com.example.words.model.StudentPointAccount;
import java.math.BigDecimal;

public record StudentPointSummaryResponse(
        Long accountId,
        Long studentId,
        BigDecimal availablePoints,
        BigDecimal frozenPoints,
        BigDecimal lifetimeEarnedPoints,
        BigDecimal lifetimeSpentPoints,
        BigDecimal todayEarnedPoints,
        PointAccountStatus status
) {
    public static StudentPointSummaryResponse from(StudentPointAccount account, BigDecimal todayEarnedPoints) {
        return new StudentPointSummaryResponse(
                account.getId(),
                account.getStudentId(),
                account.getAvailablePoints(),
                account.getFrozenPoints(),
                account.getLifetimeEarnedPoints(),
                account.getLifetimeSpentPoints(),
                todayEarnedPoints,
                account.getStatus()
        );
    }
}
