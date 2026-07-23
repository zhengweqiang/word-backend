package com.example.words.dto;

import com.example.words.model.PointAccountStatus;
import com.example.words.model.StudentPointAccount;
import java.time.LocalDateTime;

public record AdminStudentPointAccountResponse(
        Long accountId,
        Long studentId,
        String studentUsername,
        String studentName,
        Integer availablePoints,
        Integer frozenPoints,
        Integer lifetimeEarnedPoints,
        Integer lifetimeSpentPoints,
        PointAccountStatus status,
        LocalDateTime updatedAt
) {
    public static AdminStudentPointAccountResponse from(StudentPointAccount account, String studentName) {
        return from(account, studentName, null);
    }

    public static AdminStudentPointAccountResponse from(
            StudentPointAccount account,
            String studentName,
            String studentUsername
    ) {
        return new AdminStudentPointAccountResponse(
                account.getId(), account.getStudentId(), studentUsername, studentName, account.getAvailablePoints(),
                account.getFrozenPoints(), account.getLifetimeEarnedPoints(), account.getLifetimeSpentPoints(),
                account.getStatus(), account.getUpdatedAt()
        );
    }
}
