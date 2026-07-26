package com.example.words.dto;

import java.math.BigDecimal;

public record TeacherStudentPointResponse(
        Long studentId,
        String studentName,
        BigDecimal availablePoints,
        BigDecimal lifetimeEarnedPoints,
        BigDecimal lifetimeSpentPoints,
        BigDecimal todayEarnedPoints
) {
}
