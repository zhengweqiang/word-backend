package com.example.words.dto;

import com.example.words.model.PointAccountStatus;
import com.example.words.model.StudentPointAccount;

public record StudentPointSummaryResponse(
        Long accountId,
        Long studentId,
        Integer availablePoints,
        Integer frozenPoints,
        Integer lifetimeEarnedPoints,
        Integer lifetimeSpentPoints,
        Long todayEarnedPoints,
        PointAccountStatus status
) {
    public static StudentPointSummaryResponse from(StudentPointAccount account, long todayEarnedPoints) {
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
